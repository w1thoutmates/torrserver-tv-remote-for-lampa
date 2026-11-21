using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Net;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

internal sealed class Tray : ApplicationContext
{
    readonly NotifyIcon icon = new NotifyIcon();
    readonly System.Windows.Forms.Timer timer = new System.Windows.Forms.Timer();
    readonly ContextMenuStrip menu = new ContextMenuStrip();
    ToolStripMenuItem start, stop, restart;
    bool busy, closed;
    string queuedAction;
    readonly Icon active = Planet(Color.LimeGreen), idle = Planet(Color.SlateGray),
        pending = Planet(Color.Gold), warning = Planet(Color.OrangeRed);

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    static extern bool DestroyIcon(IntPtr handle);

    static Icon Planet(Color badge)
    {
        using (var image = new Bitmap(32, 32))
        using (var g = Graphics.FromImage(image))
        using (var rim = new Pen(Color.FromArgb(35, 45, 58), 5))
        using (var ring = new Pen(Color.White, 2))
        using (var spot = new SolidBrush(badge))
        {
            g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            g.TranslateTransform(15, 15);
            g.RotateTransform(-28);
            g.FillEllipse(Brushes.White, -10, -10, 20, 20);
            g.DrawArc(rim, -15, -5, 30, 10, 0, 180);
            g.DrawEllipse(ring, -15, -5, 30, 10);
            g.ResetTransform();
            g.FillEllipse(Brushes.Black, 18, 18, 14, 14);
            g.FillEllipse(spot, 20, 20, 10, 10);
            var handle = image.GetHicon();
            try { using (var borrowed = Icon.FromHandle(handle)) return (Icon)borrowed.Clone(); }
            finally { DestroyIcon(handle); }
        }
    }

    [STAThread] static void Main()
    {
        bool first;
        using (var mutex = new Mutex(true, @"Local\PublicTorrServerMatrixTray", out first))
        {
            if (!first) return;
            Application.EnableVisualStyles();
            using (var tray = new Tray()) Application.Run(tray);
        }
    }

    Tray()
    {
        start = Add("Запустить TorrServer", "start");
        stop = Add("Остановить TorrServer", "stop");
        restart = Add("Перезапустить TorrServer", "restart");
        menu.Items.Add("Открыть веб-интерфейс", null, delegate {
            try { Process.Start("http://" + NetworkConfig.Address + ":8090/"); }
            catch (Exception ex) { Warn(ex.Message); }
        });
        menu.Items.Add("Выход", null, delegate { ExitThread(); });
        icon.ContextMenuStrip = menu;
        State("Проверка", pending, false, false);
        icon.Visible = true;
        timer.Interval = 5000;
        timer.Tick += delegate { Refresh(null); };
        timer.Start();
        Application.Idle += First;
    }

    void First(object sender, EventArgs e)
    {
        Application.Idle -= First;
        Refresh(null);
    }
    ToolStripMenuItem Add(string label, string action)
    {
        return (ToolStripMenuItem)menu.Items.Add(label, null, delegate { Refresh(action); });
    }
    void State(string text, Icon image, bool canStart, bool canStop)
    {
        icon.Text = "TorrServer — " + text;
        icon.Icon = image;
        start.Enabled = canStart;
        restart.Enabled = stop.Enabled = canStop;
    }
    void Warn(string text) { icon.ShowBalloonTip(5000, "TorrServer", text, ToolTipIcon.Warning); }
    static string Request(string action)
    {
        var req = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:8091/" + action);
        req.Proxy = null;
        req.Timeout = req.ReadWriteTimeout = action == "status" ? 2000 : 30000;
        if (action != "status") {
            req.Method = "POST";
            req.ContentLength = 0;
            req.Headers["X-Torr-Control"] = "1";
        }
        using (var response = req.GetResponse())
        using (var reader = new StreamReader(response.GetResponseStream())) return reader.ReadToEnd();
    }

    async void Refresh(string action)
    {
        if (closed) return;
        if (busy) {
            if (action != null && queuedAction == null) queuedAction = action;
            return;
        }
        busy = true;
        try {
            if (action != null) {
                State("Выполнение команды", pending, false, false);
                await Task.Run(() => Request(action));
            }
            var json = await Task.Run(() => Request("status"));
            if (closed) return;
            var data = new JavaScriptSerializer().Deserialize<System.Collections.Generic.Dictionary<string,object>>(json);
            switch ((string)data["state"]) {
                case "running": State("Запущен", active, false, true); break;
                case "starting": State("Запускается", pending, false, true); break;
                case "stopped": State("Остановлен", idle, true, false); break;
                case "conflict": State("Порт 8090 занят", warning, false, false); break;
                default: State("Неизвестное состояние", warning, false, false); break;
            }
        } catch (Exception ex) {
            if (closed) return;
            State("Ошибка связи с контроллером", warning, false, false);
            if (action != null) Warn(ex.Message);
        } finally {
            busy = false;
            var next = queuedAction;
            queuedAction = null;
            if (next != null && !closed) Refresh(next);
        }
    }
    protected override void ExitThreadCore()
    {
        closed = true;
        timer.Dispose();
        icon.Visible = false;
        icon.Dispose();
        menu.Dispose();
        active.Dispose();
        idle.Dispose();
        pending.Dispose();
        warning.Dispose();
        base.ExitThreadCore();
    }
}
