package home.torr.remote;

import android.app.Activity;
import android.os.Bundle;
import android.content.*;
import android.speech.RecognizerIntent;
import android.view.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.util.TypedValue;
import android.widget.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public class Remote extends Activity {
    private String host, macAddress, broadcast;

    EditText field(LinearLayout form, String title, String value) {
        form.addView(label(title, 15, WHITE, false));
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(value);
        form.addView(input);
        return input;
    }
    static boolean ipv4(String text) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return false;
        }
        return true;
    }
    void settings() {
        if (busy) {status.setText("Дождитесь завершения команды"); return;}
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(18),dp(6),dp(18),dp(6));
        form.setOrientation(LinearLayout.VERTICAL);
        EditText address = field(form, "IPv4 компьютера", host);
        EditText mac = field(form, "MAC для пробуждения (необязательно)", macAddress);
        EditText dest = field(form, "Широковещательный IPv4 (по умолчанию для сети /24)", broadcast);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
            .setTitle("Настройки подключения").setView(scroll)
            .setPositiveButton("Сохранить", null).setNegativeButton("Закрыть", null).create();
        dialog.show();
        dialog.getButton(-1).setOnClickListener(v -> {
            String ip = address.getText().toString().trim();
            String hardware = mac.getText().toString().trim().replace('-', ':');
            String target = dest.getText().toString().trim();
            if (!ipv4(ip)) {address.requestFocus(); address.setError("Введите IPv4"); return;}
            if (!hardware.isEmpty() && !hardware.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) {
                mac.requestFocus(); mac.setError("Формат: AA:BB:CC:DD:EE:FF"); return;
            }
            if (!target.isEmpty() && !ipv4(target)) {dest.requestFocus(); dest.setError("Введите IPv4"); return;}
            host=ip; macAddress=hardware; broadcast=target;
            getPreferences(0).edit().putString("host",ip).putString("mac",hardware).putString("broadcast",target).apply();
            dialog.dismiss(); run("status");
        });
    }
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    TextView status, voiceFeedback;
    LinearLayout panel;
    boolean busy;
    boolean checking;
    String pending;

    static final int WHITE = 0xfff3f7f7, MUTED = 0xffa5b3b4, MINT = 0xffa3eee0;
    final View[] cards = new View[7];

    int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    GradientDrawable surface(int left, int right, int radius, int border, int stroke) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{left, right});
        g.setCornerRadius(dp(radius));
        if (stroke > 0) g.setStroke(dp(stroke), border);
        return g;
    }
    TextView label(String text, int size, int color, boolean medium) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        v.setTextColor(color);
        v.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        v.setIncludeFontPadding(false);
        return v;
    }
    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        return r;
    }
    void section(String title) {
        TextView heading = label(title, 17, WHITE, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(17),0,dp(8));
        panel.addView(heading,lp);
    }
    public void onCreate(Bundle data) {
        super.onCreate(data);
        host=getPreferences(0).getString("host","");
        macAddress=getPreferences(0).getString("mac","");
        broadcast=getPreferences(0).getString("broadcast","");
        ScrollView viewport = new ScrollView(this);
        viewport.setFillViewport(true);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.setFocusable(false);
        viewport.setBackground(surface(0xff12181b,0xff0d1114,0,0,0));
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(44),dp(22),dp(44),dp(22));
        panel.setClipToPadding(false);
        panel.setClipChildren(false);
        viewport.addView(panel);
        setContentView(viewport);

        LinearLayout header = row();
        panel.addView(header);
        header.addView(label("Торр Сервер",30,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout badge = row();
        badge.setPadding(dp(12),dp(7),dp(12),dp(7));
        badge.setBackground(surface(0xff222b2f,0xff20292c,18,0x224f7770,1));
        header.addView(badge,new LinearLayout.LayoutParams(-2,-2));
        View dot = new View(this);
        badge.addView(dot,new LinearLayout.LayoutParams(dp(7),dp(7)));
        status = label("",14,WHITE,true);
        status.setMaxWidth(dp(330));
        status.setPadding(dp(8),0,0,0);
        badge.addView(status);
        status.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void afterTextChanged(Editable s){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                int color = "Сервер работает".contentEquals(s) ? 0xff78e5ab :
                    "Сервер остановлен".contentEquals(s) ? 0xffabb5bd : 0xffe8bb77;
                dot.setBackground(surface(color,color,8,0,0));
            }
        });
        status.setText("Проверка связи…");

        LinearLayout hero = row();
        LinearLayout.LayoutParams top = new LinearLayout.LayoutParams(-1,-2);
        top.topMargin = dp(18);
        panel.addView(hero,top);
        card(hero,0,"Запустить и открыть Lampa","watch",108);
        section("Управление сервером");
        LinearLayout server = row();
        panel.addView(server);
        card(server,1,"Запустить сервер","start",62);
        card(server,2,"Остановить сервер","stop",62);
        card(server,3,"Перезапустить сервер","restart",62);
        section("Дополнительно");
        LinearLayout tools = row();
        panel.addView(tools);
        card(tools,4,"Голосовая команда","voice",54);
        card(tools,5,"Разбудить ПК","wake",54);
        card(tools,6,"Настройки","settings",54);
        voiceFeedback = label("",15,MINT,false);
        voiceFeedback.setPadding(0,dp(10),0,0);
        panel.addView(voiceFeedback);
        voiceFeedback.setVisibility(View.GONE);

        // Left, right, up and down for each position in the 1 / 3 / 2 layout.
        int[][] links = {{0,0,0,1},{1,2,0,4},{1,3,0,4},{2,3,0,6},{4,5,1,4},{4,6,2,5},{5,6,3,6}};
        for(int i=0;i<cards.length;i++){
            View v=cards[i];
            v.setNextFocusLeftId(cards[links[i][0]].getId());
            v.setNextFocusRightId(cards[links[i][1]].getId());
            v.setNextFocusUpId(cards[links[i][2]].getId());
            v.setNextFocusDownId(cards[links[i][3]].getId());
        }
        cards[0].requestFocus();
        if(host.isEmpty()) settings();
    }
    void card(LinearLayout parent,int index,String text,String action,int height){
        boolean hero = index == 0;
        FrameLayout tile = new FrameLayout(this);
        cards[index]=tile;
        tile.setId(View.generateViewId());
        tile.setFocusable(true);
        tile.setClipToOutline(true);
        tile.setContentDescription(text);
        tile.setOnClickListener(v -> {if ("settings".equals(action)) settings(); else if ("voice".equals(action)) voice(); else run(action);});
        tile.setOnLongClickListener(v -> { voice(); return true; });
        tile.setAccessibilityDelegate(new View.AccessibilityDelegate(){
            public void onInitializeAccessibilityNodeInfo(View v,android.view.accessibility.AccessibilityNodeInfo info){
                super.onInitializeAccessibilityNodeInfo(v,info);
                info.setClassName(Button.class.getName());
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,dp(height),1);
        if(parent.getChildCount()>0) lp.leftMargin=dp(12);
        parent.addView(tile,lp);
        if(hero) tile.addView(new Orbit(),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=row();
        content.setPadding(dp(hero?24:14),0,dp(10),0);
        tile.addView(content,new FrameLayout.LayoutParams(-1,-1));
        Glyph glyph=new Glyph(index);
        content.addView(glyph,new LinearLayout.LayoutParams(dp(hero?42:24),dp(hero?42:24)));
        TextView caption=label(text,hero?27:17,WHITE,true);
        caption.setPadding(dp(hero?16:10),0,0,0);
        content.addView(caption);
        View.OnFocusChangeListener focus=(v,selected)->{
            int left = selected ? (hero?0xff194e4c:0xff284342) : (hero?0xff193736:0xff20272c);
            int right = selected ? 0xff183435 : (hero?0xff132b30:0xff1c2328);
            tile.setBackground(surface(left,right,13,selected?MINT:0x306f8887,selected?2:1));
            float scale=selected?1.013f:1f;
            tile.animate().scaleX(scale).scaleY(scale).setDuration(115).start();
            glyph.color=selected || hero ? MINT : 0xffb8c8c8;
            glyph.invalidate();
        };
        tile.setOnFocusChangeListener(focus);
        focus.onFocusChange(tile,false);
    }
    // Icons share a 24-unit coordinate system.
    class Glyph extends View {
        final int kind;
        int color;
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        Glyph(int kind){super(Remote.this);this.kind=kind;}
        protected void onDraw(Canvas c){
            c.save(); c.scale(getWidth()/24f,getHeight()/24f);
            p.setColor(color); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f);
            p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
            switch(kind){
                case 0: case 1:
                    if(kind==0)c.drawCircle(12,12,10.8f,p);
                    Path play=new Path(); play.moveTo(9,6.8f); play.lineTo(17,12); play.lineTo(9,17.2f); play.close();
                    p.setStyle(Paint.Style.FILL); c.drawPath(play,p); break;
                case 2: c.drawRoundRect(5,5,19,19,2,2,p); break;
                case 3: c.drawArc(4,4,20,20,30,277,false,p); c.drawLine(18,3,18,8,p); c.drawLine(13,8,18,8,p); break;
                case 4: c.drawRoundRect(9,2,15,14,3,3,p); c.drawArc(5,5,19,18,0,180,false,p); c.drawLine(12,18,12,22,p); c.drawLine(9,22,15,22,p); break;
                case 6: c.drawCircle(12,12,7,p); c.drawCircle(12,12,2,p); for(int i=0;i<8;i++){c.drawLine(12,2,12,5,p); c.rotate(45,12,12);} break;
                case 5: c.drawRoundRect(2,4,22,17,2,2,p); c.drawLine(12,18,12,21,p); c.drawLine(7,21,17,21,p); break;
            }
            c.restore();
        }
    }
    class Orbit extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Orbit(){super(Remote.this);}
        protected void onDraw(Canvas c){
            float h=getHeight();
            c.save(); c.translate(getWidth()-h*1.15f,h*.62f);
            c.rotate(-19); c.scale(h/100,h/100);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1);
            p.setColor(0x406ac6bb); c.drawOval(-91,-25,91,25,p);
            p.setStyle(Paint.Style.FILL);
            p.setShader(new RadialGradient(-20,-26,82,new int[]{0xff81c5b9,0xff376d69,0xff1a3e42,0xff152e33},null,Shader.TileMode.CLAMP));
            c.drawCircle(0,0,43,p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setColor(0xff76b3ab); c.drawArc(-91,-25,91,25,0,180,false,p);
            p.setColor(0x506ac6bb); c.drawArc(-98,-28,98,28,0,180,false,p);
            c.restore();
        }
    }
    protected void onResume() { super.onResume(); if (!busy) run("status"); }
    protected void onDestroy() { worker.shutdownNow(); super.onDestroy(); }
    // Handle remote shortcuts only while this activity owns the input window.
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key = event.getKeyCode();
        if (hasWindowFocus() && (key == KeyEvent.KEYCODE_SEARCH ||
                key == KeyEvent.KEYCODE_VOICE_ASSIST || key == KeyEvent.KEYCODE_ASSIST)) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) voice();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onSearchRequested() {
        if (!hasWindowFocus()) return false;
        voice();
        return true;
    }
    void voice() {
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Включи сервер, выключи сервер, перезапусти сервер или открой Лампу");
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        i.setPackage("com.google.android.tts");
        if(i.resolveActivity(getPackageManager()) == null) i.setPackage(null);
        try { startActivityForResult(i, 7); }
        catch (ActivityNotFoundException e) { showVoice("Распознавание речи недоступно"); }
    }
    void showVoice(String text) {
        voiceFeedback.setText(text);
        voiceFeedback.setVisibility(android.view.View.VISIBLE);
    }
    static String command(String words) {
        String s = words.toLowerCase(Locale.ROOT).replaceAll("[.!?,:;–—-]", " ").replaceAll("\\s+", " ").trim();
        s = s.replaceAll("(?:торр? ?сервер|torr ?server)", "сервер");
        if (s.matches("(открой|запусти) (лампу|lampa)")) return "watch";
        if (s.matches("(допусти|отпусти) сервер") || s.matches("(включи|запусти|включить|запустить)( сервер)?")) return "start";
        if (s.matches("(выключи|останови|выключить|остановить)( сервер)?")) return "stop";
        if (s.matches("(перезапусти|перезагрузи|перезапустить|перезагрузить)( сервер)?")) return "restart";
        return null;
    }
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if (request != 7) return;
        ArrayList<String> words = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (result != RESULT_OK || words == null || words.isEmpty() || words.get(0) == null || words.get(0).trim().isEmpty()) {
            android.util.Log.i("TorrVoice", "Recognizer returned no input, result=" + result);
            showVoice("Голосовой ввод не вернул текст. Нажмите «Голосовая команда» и повторите.");
            return;
        }
        String phrase = words.get(0);
        String action = command(phrase);
        android.util.Log.i("TorrVoice", action == null ? "Unmatched input" : "Recognized command: " + action);
        showVoice("Услышано: «" + phrase + "»" + (action == null ? ". Скажите, например, «Включи сервер»." : ""));
        if (action != null) run(action);
    }
    JSONObject request(String action) throws Exception {
        HttpURLConnection h = (HttpURLConnection)new URL("http://" + host + ":8091/" + action).openConnection();
        h.setConnectTimeout(1800); h.setReadTimeout(16000);
        try {
            if (!action.equals("status")) {
                h.setRequestMethod("POST"); h.setRequestProperty("X-Torr-Control", "1");
            }
            if (h.getResponseCode()!=200) throw new IOException("HTTP " + h.getResponseCode());
            try (BufferedReader r = new BufferedReader(new InputStreamReader(h.getInputStream(),"UTF-8"))) {
                return new JSONObject(r.readLine());
            }
        } finally { h.disconnect(); }
    }
    void wake() throws IOException {
        if(macAddress.isEmpty()) throw new IOException("Укажите MAC в настройках для пробуждения");
        String[] octets=macAddress.split(":");
        byte[] mac=new byte[6];
        for(int i=0;i<mac.length;i++) mac[i]=(byte)Integer.parseInt(octets[i],16);
        byte[] packet = new byte[102];
        Arrays.fill(packet,0,6,(byte)255);
        for (int i=6;i<packet.length;i+=6) System.arraycopy(mac,0,packet,i,6);
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            s.send(new DatagramPacket(packet,packet.length,InetAddress.getByName(broadcast.isEmpty() ? host.substring(0,host.lastIndexOf('.')+1)+"255" : broadcast),9));
        }
    }
    void ensureAwake() throws Exception {
        try { request("status"); return; } catch(IOException offline) { if(macAddress.isEmpty()) throw offline; wake(); }
        runOnUiThread(() -> status.setText("Пробуждение ПК…"));
        long until = android.os.SystemClock.elapsedRealtime()+60000;
        do {
            Thread.sleep(2000);
            try { request("status"); return; } catch(IOException waiting) {}
        } while(android.os.SystemClock.elapsedRealtime()<until);
        throw new IOException("ПК не ответил после пробуждения");
    }
    void resumePending() {
        if (pending != null) {String next=pending; pending=null; run(next);}
    }
    void run(String action) {
        if(host.isEmpty()) {status.setText("Укажите IPv4 компьютера в настройках"); if(!action.equals("status")) settings(); return;}
        if (busy && checking && !action.equals("status")) pending=action;
        if (busy || worker.isShutdown()) return;
        checking=action.equals("status");
        busy=true;
        status.setText(action.equals("status") ? "Проверка связи…" : "Выполнение команды…");
        worker.execute(() -> {
            try {
                if (action.equals("wake")) {
                    wake();
                    runOnUiThread(() -> {busy=false; status.setText("Пакет пробуждения отправлен");});
                    return;
                }
                boolean watch = action.equals("watch");
                if(watch || action.equals("start")) ensureAwake();
                JSONObject reply = request(watch ? "start" : action);
                String state = reply.getString("state");
                String text = state.equals("running") ? "Сервер работает" : state.equals("stopped") ? "Сервер остановлен" : state.equals("starting") ? "Сервер запускается" : "Порт сервера занят";
                runOnUiThread(() -> {
                    if(isDestroyed()) return;
                    busy=false; status.setText(text); resumePending();
                    if(watch && state.equals("running")) {
                        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("top.rootu.lampa");
                        if(i != null) startActivity(i); else status.setText("Lampa не найдена на ТВ");
                    }
                });
            } catch(Exception e) {
                runOnUiThread(() -> { busy=false; if(!isDestroyed()) {status.setText("Нет ответа: " + e.getMessage()); resumePending();} });
            }
        });
    }
}
