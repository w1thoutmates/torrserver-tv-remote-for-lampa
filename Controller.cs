using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Net.NetworkInformation;
using System.Text;
using System.Threading;

class Controller {
    static readonly string root = AppDomain.CurrentDomain.BaseDirectory;
    static readonly string binary = Path.Combine(root,"TorrServer-matrix-windows-amd64.exe");
    static readonly string lan = NetworkConfig.Address.ToString();
    static readonly object actions = new object(), logs = new object();
    static readonly Semaphore slots = new Semaphore(12,12);
    static void Log(string text) {
        lock(logs) try {
            string file = Path.Combine(root,"controller.log");
            if(File.Exists(file) && new FileInfo(file).Length > 1048576) {
                File.Copy(file,file+".1",true); File.WriteAllText(file,"");
            }
            File.AppendAllText(file,DateTime.Now.ToString("s")+" "+text+Environment.NewLine);
        } catch(IOException) {}
    }
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    static extern bool QueryFullProcessImageName(IntPtr process, int flags, StringBuilder path, ref int length);
    static Process Find() {
        foreach(Process p in Process.GetProcessesByName(Path.GetFileNameWithoutExtension(binary))) {
            try {
                int length=32768;
                var path=new StringBuilder(length);
                if(!QueryFullProcessImageName(p.Handle,0,path,ref length)) {
                    if(!p.HasExited)throw new Win32Exception();
                } else if(string.Equals(path.ToString(),binary,StringComparison.OrdinalIgnoreCase))return p;
            }
            catch(InvalidOperationException) {}
            p.Dispose();
        }
        return null;
    }
    static string State() {
        using(Process p=Find()) {
            bool ready=IPGlobalProperties.GetIPGlobalProperties().GetActiveTcpListeners().Any(ep=>ep.Port==8090);
            return p==null ? (ready?"conflict":"stopped") : (ready?"running":"starting");
        }
    }
    static void Start() {
        string state=State();
        if(state=="conflict") throw new InvalidOperationException("Port 8090 is occupied");
        if(state!="stopped")return;
        var psi=new ProcessStartInfo(binary,"--port 8090 --ip 127.0.0.1 --ip "+lan+" --path . --logpath matrix.log");
        psi.WorkingDirectory=root; psi.CreateNoWindow=true; psi.UseShellExecute=false;
        using(Process p=Process.Start(psi)) {
            for(int i=0;i<80;i++) {
                if(p.HasExited)throw new InvalidOperationException("MatriX exited during startup");
                if(State()=="running")return;
                Thread.Sleep(100);
            }
        }
    }
    static void Stop() {
        using(Process p=Find()) {
            if(p==null)return;
            var req=(HttpWebRequest)WebRequest.Create("http://127.0.0.1:8090/shutdown");
            req.Proxy=null; req.Timeout=3000; req.AllowAutoRedirect=false;
            try {using(req.GetResponse()) {}} catch(WebException) {}
            if(!p.WaitForExit(3000)) {
                Log("Graceful shutdown timed out; stopping verified MatriX PID "+p.Id);
                p.Kill();
                if(!p.WaitForExit(5000))throw new InvalidOperationException("Shutdown timed out");
            }
        }
    }
    const string page=@"<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'><title>TorrServer</title><style>body{font:24px sans-serif;background:#172331;color:white;margin:8%}button{font:inherit;padding:.6em;margin:.25em}button:focus{outline:4px solid orange}</style><h1>TorrServer MatriX</h1><p id=s></p><button autofocus onclick='run(1)'>Запустить</button><button onclick='run(2)'>Остановить</button><button onclick='run(3)'>Перезапустить</button><script>async function run(i){try{let r=await fetch('/'+['status','start','stop','restart'][i],i?{method:'POST',headers:{'X-Torr-Control':'1'}}:{});let j=await r.json();s.textContent=({running:'Запущен',stopped:'Остановлен',starting:'Запускается',conflict:'Порт занят'})[j.state]||j.error}catch(e){s.textContent='Нет связи'}}run(0)</script>";
    static void Reply(NetworkStream s,int code,string body,bool html=false) {
        byte[] data=Encoding.UTF8.GetBytes(body);
        byte[] head=Encoding.ASCII.GetBytes("HTTP/1.1 "+code+" "+(code==200?"OK":"Error")+"\r\nConnection: close\r\nCache-Control: no-store\r\nX-Frame-Options: DENY\r\nContent-Type: "+(html?"text/html":"application/json")+"; charset=utf-8\r\nContent-Length: "+data.Length+"\r\n\r\n");
        s.Write(head,0,head.Length); s.Write(data,0,data.Length);
    }
    static void Handle(TcpClient c) {
        using(c) {
            NetworkStream s=c.GetStream();
            byte[] buf=new byte[8192]; int n=0,end; string h;
            var elapsed=Stopwatch.StartNew();
            do {
                if(n==buf.Length || elapsed.ElapsedMilliseconds>=3000)return;
                s.ReadTimeout=Math.Max(1,3000-(int)elapsed.ElapsedMilliseconds);
                int k=s.Read(buf,n,buf.Length-n); if(k==0)return;
                n+=k; h=Encoding.ASCII.GetString(buf,0,n); end=h.IndexOf("\r\n\r\n",StringComparison.Ordinal);
            } while(end<0);
            s.WriteTimeout=3000;
            string[] lines=h.Substring(0,end).Split(new[]{"\r\n"},StringSplitOptions.None);
            var headers=new Dictionary<string,string>(StringComparer.OrdinalIgnoreCase);
            foreach(string line in lines.Skip(1)) {
                int idx=line.IndexOf(':'); if(idx<1)return;
                string key=line.Substring(0,idx); if(headers.ContainsKey(key))return;
                headers.Add(key,line.Substring(idx+1).Trim());
            }
            string host,origin,token;
            string[] r=lines[0].Split(' ');
            if(r.Length!=3 || !headers.TryGetValue("Host",out host) || (host!=lan+":8091" && host!="127.0.0.1:8091" && host!="localhost:8091") || (headers.TryGetValue("Origin",out origin) && origin!="http://"+host)) {
                Reply(s,403,"{}");return;
            }
            if(r[0]=="GET" && r[1]=="/"){Reply(s,200,page,true);return;}
            if(!(r[0]=="GET" && r[1]=="/status")){
                if(r[0]!="POST" || !headers.TryGetValue("X-Torr-Control",out token) || token!="1"){Reply(s,403,"{}");return;}
                lock(actions) try {
                    switch(r[1]){case "/start":Start();break;case "/stop":Stop();break;case "/restart":Stop();Start();break;default:Reply(s,404,"{}");return;}
                    Log(((IPEndPoint)c.Client.RemoteEndPoint).Address+" "+r[1]);
                } catch(Exception e) {Log(e.Message);Reply(s,409,"{\"error\":\"See controller.log\"}");return;}
            }
            string state=State(); Reply(s,200,"{\"state\":\""+state+"\",\"running\":"+(state=="running"?"true":"false")+"}");
        }
    }
    static void Listen(string ip) {
        for(;;) {
            var listener=new TcpListener(IPAddress.Parse(ip),8091);
            try {
                listener.ExclusiveAddressUse=true;listener.Start();Log("Listening "+ip+":8091");
                for(;;) {
                    var c=listener.AcceptTcpClient();
                    var addr=((IPEndPoint)c.Client.RemoteEndPoint).Address;
                    if(!(IPAddress.IsLoopback(addr)||NetworkConfig.Allows(addr)) || !slots.WaitOne(0)) {c.Close();continue;}
                    ThreadPool.QueueUserWorkItem(delegate {try{Handle(c);}catch(Exception e){if(!(e is IOException))Log(e.Message);}finally{slots.Release();}});
                }
            } catch(SocketException e){Log(ip+" "+e.Message);}finally{listener.Stop();}
            Thread.Sleep(15000);
        }
    }
    static void Main() {
        bool first;
        using(var mutex=new Mutex(true,@"Global\PublicTorrMatrixControl8091",out first)){
            if(!first)return;
            var thread=new Thread(delegate(){Listen(lan);}); thread.IsBackground=true;thread.Start(); Listen("127.0.0.1");
        }
    }
}

