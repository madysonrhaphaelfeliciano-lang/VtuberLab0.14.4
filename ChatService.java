package com.vtuber.overlay;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat da live do YouTube numa janela flutuante.
 * Abre o chat pop-out do próprio YouTube (youtube.com/live_chat?is_popout=1&v=ID) num WebView e aplica CSS por cima
 * (cor do nome, cor da letra, tamanho, transparência). Sem API, sem chave, sem servidor.
 * Como a Live captura a tela inteira, o chat aparece na transmissão sozinho.
 * Janela travada: o toque passa direto para o app de baixo. Modo "Mover": alça no topo para arrastar e canto para redimensionar.
 */
public class ChatService extends Service {
    static final String ACTION_SHOW = "com.vtuber.overlay.CHAT_SHOW";
    static final String ACTION_HIDE = "com.vtuber.overlay.CHAT_HIDE";
    static final String ACTION_EDIT = "com.vtuber.overlay.CHAT_EDIT";
    static final String ACTION_STYLE = "com.vtuber.overlay.CHAT_STYLE";
    private static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    private static ChatService inst;
    static boolean isRunning() { return inst != null; }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("chat", MODE_PRIVATE); }
    static int nameColor(Context c) { return prefs(c).getInt("name", 0xFFFFCE31); }
    static int textColor(Context c) { return prefs(c).getInt("text", 0xFFFFFFFF); }
    static int textSize(Context c) { return prefs(c).getInt("size", 16); }
    static int bgPercent(Context c) { return prefs(c).getInt("bg", 30); }

    /** Aceita o link da live (watch?v=, youtu.be/, /live/) ou só o ID de 11 caracteres. */
    static String parseVideoId(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.matches("[A-Za-z0-9_-]{11}")) return s;
        Matcher m = Pattern.compile("(?:[?&]v=|youtu\\.be/|/live/|/shorts/)([A-Za-z0-9_-]{11})").matcher(s);
        return m.find() ? m.group(1) : "";
    }

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private WebView web;
    private LinearLayout handle;
    private View resizer;
    private boolean editing = false;
    private Handler ui;
    private final Runnable reinject = new Runnable() {
        @Override public void run() { if (root != null) { injectStyle(); ui.postDelayed(this, 8000); } }
    };

    @Override public void onCreate() { super.onCreate(); inst = this; ui = new Handler(Looper.getMainLooper()); }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        String a = i == null ? ACTION_SHOW : i.getAction();
        if (ACTION_HIDE.equals(a)) { stopSelf(); return START_NOT_STICKY; }
        if (root == null) { try { createWindow(); } catch (Exception e) { stopSelf(); return START_NOT_STICKY; } }
        if (ACTION_EDIT.equals(a)) setEditing(true);
        else if (ACTION_STYLE.equals(a)) injectStyle();
        else loadChat();
        return START_NOT_STICKY;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void createWindow() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        SharedPreferences p = prefs(this);
        lp = new WindowManager.LayoutParams(p.getInt("w", dp(320)), p.getInt("h", dp(220)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = p.getInt("x", dp(16)); lp.y = p.getInt("y", dp(80));

        root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setUserAgentString(DESKTOP_UA);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String url) { injectStyle(); }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        // Alça de mover (topo) com o botão "Pronto"
        handle = new LinearLayout(this);
        handle.setOrientation(LinearLayout.HORIZONTAL); handle.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable hg = new GradientDrawable(); hg.setColor(0xFFFFCE31); hg.setCornerRadius(dp(10)); handle.setBackground(hg);
        TextView drag = new TextView(this);
        drag.setText("Arraste para mover"); drag.setTextColor(Color.BLACK); drag.setTextSize(13); drag.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        drag.setPadding(dp(12), 0, dp(8), 0); drag.setGravity(Gravity.CENTER_VERTICAL);
        handle.addView(drag, new LinearLayout.LayoutParams(0, -1, 1f));
        TextView done = new TextView(this);
        done.setText("\u2713 Pronto"); done.setTextColor(Color.BLACK); done.setTextSize(13); done.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        done.setPadding(dp(12), 0, dp(12), 0); done.setGravity(Gravity.CENTER_VERTICAL);
        done.setOnClickListener(v -> setEditing(false));
        handle.addView(done, new LinearLayout.LayoutParams(-2, -1));
        handle.setVisibility(View.GONE);
        root.addView(handle, new FrameLayout.LayoutParams(-1, dp(34), Gravity.TOP));
        drag.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: dx = e.getRawX(); dy = e.getRawY(); sx = lp.x; sy = lp.y; return true;
                    case MotionEvent.ACTION_MOVE: lp.x = sx + (int) (e.getRawX() - dx); lp.y = sy + (int) (e.getRawY() - dy); wm.updateViewLayout(root, lp); return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: savePlace(); return true;
                    default: return false;
                }
            }
        });

        // Canto para redimensionar (embaixo, à direita)
        resizer = new View(this);
        GradientDrawable rg = new GradientDrawable(); rg.setColor(0xFFFFCE31); rg.setCornerRadius(dp(10)); resizer.setBackground(rg);
        resizer.setVisibility(View.GONE);
        root.addView(resizer, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.END));
        resizer.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sw, sh;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: dx = e.getRawX(); dy = e.getRawY(); sw = lp.width; sh = lp.height; return true;
                    case MotionEvent.ACTION_MOVE:
                        lp.width = Math.max(dp(150), sw + (int) (e.getRawX() - dx));
                        lp.height = Math.max(dp(100), sh + (int) (e.getRawY() - dy));
                        wm.updateViewLayout(root, lp); return true;
                    case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: savePlace(); return true;
                    default: return false;
                }
            }
        });

        wm.addView(root, lp);
        ui.postDelayed(reinject, 8000);
    }

    private void savePlace() {
        prefs(this).edit().putInt("x", lp.x).putInt("y", lp.y).putInt("w", lp.width).putInt("h", lp.height).apply();
    }

    private void setEditing(boolean on) {
        editing = on;
        if (root == null) return;
        if (on) lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        handle.setVisibility(on ? View.VISIBLE : View.GONE);
        resizer.setVisibility(on ? View.VISIBLE : View.GONE);
        root.setBackgroundColor(on ? 0x66000000 : Color.TRANSPARENT);
        wm.updateViewLayout(root, lp);
        if (!on) savePlace();
    }

    private void loadChat() {
        String id = parseVideoId(prefs(this).getString("video", ""));
        if (id.isEmpty()) {
            web.loadDataWithBaseURL(null, "<html><body style='margin:12px;color:#fff;font:15px sans-serif;background:transparent'>"
                + "Cole o link da live em Mais fun\u00e7\u00f5es &gt; Chat.</body></html>", "text/html", "utf-8", null);
        } else {
            web.loadUrl("https://www.youtube.com/live_chat?is_popout=1&dark_theme=1&v=" + id);
        }
    }

    private static String hex(int c) { return String.format("#%06X", c & 0xFFFFFF); }

    private void injectStyle() {
        if (web == null) return;
        int size = textSize(this);
        float alpha = bgPercent(this) / 100f;
        String css =
            "html,body,yt-live-chat-app,yt-live-chat-renderer,yt-live-chat-item-list-renderer,#chat,#item-list,#contents{background:transparent !important;}" +
            "body{overflow:hidden !important;}" +
            "yt-live-chat-header-renderer,yt-live-chat-message-input-renderer,#input-panel,yt-live-chat-ticker-renderer{display:none !important;}" +
            "yt-live-chat-item-list-renderer #item-scroller{overflow:hidden !important;}" +
            "::-webkit-scrollbar{display:none !important;}" +
            "yt-live-chat-text-message-renderer{background:rgba(0,0,0," + alpha + ") !important;border-radius:8px !important;margin:2px 0 !important;}" +
            "yt-live-chat-text-message-renderer #author-name,yt-live-chat-text-message-renderer yt-live-chat-author-chip #author-name{color:" + hex(nameColor(this)) + " !important;font-size:" + size + "px !important;font-weight:700 !important;}" +
            "yt-live-chat-text-message-renderer #message{color:" + hex(textColor(this)) + " !important;font-size:" + size + "px !important;line-height:1.3 !important;}" +
            "yt-live-chat-text-message-renderer #timestamp{display:none !important;}";
        String js = "(function(){var s=document.getElementById('vl-style');if(!s){s=document.createElement('style');s.id='vl-style';(document.head||document.documentElement).appendChild(s);}s.textContent=" + JSONObject.quote(css) + ";})();";
        web.evaluateJavascript(js, null);
    }

    @Override public void onDestroy() {
        inst = null;
        if (ui != null) ui.removeCallbacks(reinject);
        try { if (root != null && wm != null) wm.removeView(root); } catch (Exception ignore) {}
        if (web != null) { web.destroy(); web = null; }
        root = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
