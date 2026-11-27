package com.vtuber.overlay;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Collab: conexão direta (WebRTC) entre o anfitrião e UM convidado, sem servidor.
 * Fica num serviço para continuar ligada com o overlay, a gravação e a live.
 * O convidado envia o modelo (ZIP, em pedaços) e depois só os movimentos (8 números, ~25 vezes por segundo).
 * O anfitrião roda o modelo do convidado numa caixa isolada: sem internet, sem ponte com o app, sem acesso aos arquivos do app.
 */
public class CollabService extends Service {
    interface Listener { void onChanged(); }

    static volatile Listener listener;
    private static CollabService inst;

    // ---- Estado visível para a janela do Collab ----
    static int role = 0;              // 0 nenhum | 1 anfitrião | 2 convidado
    static int mode = 0;              // 0 parado | 1 anfitrião esperando a resposta | 2 convidado esperando o anfitrião | 3 conectado
    static String code = "";
    static String status = "Crie uma sala ou entre na sala de um amigo.";
    static boolean connectedOk = false;
    static String pendingHello = null;   // nome do modelo do convidado esperando "aceitar?"

    static final String A_INIT = "init", A_HOST = "host", A_GUEST = "guest", A_PASTE = "paste", A_CLOSE = "close", A_YES = "yes", A_NO = "no";
    private static final long MAX_BYTES = 80L * 1024 * 1024;
    private static final String HOST = "appassets.androidplatform.net";

    static void send(Context c, String action, String arg) {
        Intent i = new Intent(c, CollabService.class).setAction(action);
        if (arg != null) i.putExtra("arg", arg);
        c.startService(i);
    }

    /** Aceitar/recusar o modelo do convidado: executa na hora para a janela não perguntar duas vezes. */
    static void answer(Context c, boolean yes) {
        CollabService s = inst;
        if (s != null) s.respond(yes); else send(c, yes ? A_YES : A_NO, null);
    }

    static boolean wantPose() { CollabService s = inst; return s != null && s.streamPose; }

    /** Chamado pelo overlay (convidado): "T..." (8 números) ou "P0/P1" (Pngtuber falando). */
    static void sendPose(String v) {
        CollabService s = inst;
        if (s == null || !s.streamPose || v == null) return;
        String t = v.replace("\"", "").trim();
        if (t.isEmpty() || t.equals("null")) return;
        s.js("collabPose('" + t + "')");
    }

    static void appVisibilityChanged() {
        final CollabService s = inst;
        if (s != null && s.ui != null) s.ui.post(new Runnable() { @Override public void run() { s.updateGuestTouch(); } });
    }

    private Handler ui;
    private WebView web;
    private boolean pageReady = false;
    private final ArrayList<String> queue = new ArrayList<String>();
    private boolean streamPose = false;

    // ---- Protocolo ----
    private String guestKind = "default", guestName = "";
    private File outIn;                     // (convidado) ZIP a enviar
    private FileInputStream outStream;
    private long outTotal = 0, outSent = 0;
    private int lastPct = -1;
    private FileOutputStream inOut;         // (anfitrião) ZIP recebido
    private long inTotal = 0, inGot = 0;
    private final File inZip() { return new File(getCacheDir(), "collab_in.zip"); }

    // ---- Janela do modelo do convidado (anfitrião) ----
    private FrameLayout guestRoot;
    private WebView guestWeb;
    private WindowManager wm;
    private WindowManager.LayoutParams guestLp;
    private View resizer;
    private boolean testing = false;
    private String testKind = "";
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

    private final Runnable pinger = new Runnable() {
        @Override public void run() { if (mode == 3) { js("collabPing()"); ui.postDelayed(this, 2000); } }
    };

    @Override public void onCreate() {
        super.onCreate();
        inst = this;
        ui = new Handler(Looper.getMainLooper());
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildWeb();
        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override public void onSharedPreferenceChanged(SharedPreferences sp, String key) { ui.post(new Runnable() { @Override public void run() { applyRoomVisibility(); } }); }
        };
        AppSettings.main(this).registerOnSharedPreferenceChangeListener(prefListener);
        AppSettings.p(this).registerOnSharedPreferenceChangeListener(prefListener);
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null || i.getAction() == null) return START_NOT_STICKY;
        String a = i.getAction(), arg = i.getStringExtra("arg");
        boolean stun = AppSettings.stun(this);
        switch (a) {
            case A_HOST:
                closeAll(false); role = 1; mode = 0; status = "Gerando código…"; changed();
                js("collabHost(" + stun + ")"); break;
            case A_GUEST:
                closeAll(false); role = 2; mode = 0; status = "Copie o código que o anfitrião mandou e toque em Colar."; changed(); break;
            case A_PASTE:
                if (arg == null) break;
                if (role == 2) { status = "Gerando a resposta…"; changed(); js("collabJoin(" + JSONObject.quote(arg) + "," + stun + ")"); }
                else if (role == 1 && mode == 1) { status = "Conectando…"; changed(); js("collabAccept(" + JSONObject.quote(arg) + ")"); }
                break;
            case A_YES: respond(true); break;
            case A_NO: respond(false); break;
            case A_CLOSE: closeAll(true); stopSelf(); break;
            default: break;
        }
        return START_NOT_STICKY;
    }

    // ---------- Estado e interface ----------
    private void changed() {
        final Listener l = listener;
        if (l != null) ui.post(new Runnable() { @Override public void run() { l.onChanged(); } });
    }

    private void setStatus(String s, boolean ok) { status = s; connectedOk = ok; changed(); }

    private void js(final String code) {
        if (web == null) return;
        if (!pageReady) { queue.add(code); return; }
        web.evaluateJavascript(code, null);
    }

    private void closeAll(boolean reset) {
        ui.removeCallbacks(pinger);
        streamPose = false;
        js("collabClose(false)");
        closeGuestWindow();
        try { if (outStream != null) outStream.close(); } catch (Exception ignored) { }
        try { if (inOut != null) inOut.close(); } catch (Exception ignored) { }
        outStream = null; inOut = null;
        mode = 0; code = ""; pendingHello = null; connectedOk = false;
        if (reset) role = 0;
        status = "Crie uma sala ou entre na sala de um amigo.";
        changed();
    }

    // ---------- Convidado: dados do próprio modelo ----------
    private String selectedId() { return AppSettings.main(this).getString("selected_model", "default"); }

    private String modelNameFor(String id) {
        if (id.equals("default")) return "Verônica";
        if (id.equals("default_png")) return "Verônica (Pngtuber)";
        try {
            BufferedReader r = new BufferedReader(new FileReader(new File(new File(new File(getFilesDir(), "models"), id), "name.txt")));
            String n = r.readLine(); r.close();
            if (n != null && !n.trim().isEmpty()) return n.trim();
        } catch (Exception ignored) { }
        return "Modelo";
    }

    private void sendJson(String t, String k, String v) {
        try { JSONObject o = new JSONObject(); o.put("t", t); if (k != null) o.put(k, v); js("collabSend(" + JSONObject.quote(o.toString()) + ")"); }
        catch (Exception ignored) { }
    }

    private void sendHello() {
        String id = selectedId();
        try {
            JSONObject o = new JSONObject();
            o.put("t", "hello"); o.put("kind", id.equals("default") ? "default" : "zip"); o.put("name", modelNameFor(id));
            js("collabSend(" + JSONObject.quote(o.toString()) + ")");
        } catch (Exception ignored) { }
        setStatus("Conectado. Aguardando o anfitrião aceitar o seu modelo…", true);
    }

    /** Compacta characters/current (o modelo que está ativo) e começa o envio. */
    private void prepareAndSendModel() {
        setStatus("Preparando o modelo…", true);
        new Thread(new Runnable() { @Override public void run() {
            try {
                File base = new File(getFilesDir(), "characters/current");
                if (!base.exists()) throw new Exception("modelo não encontrado");
                final File z = new File(getCacheDir(), "collab_out.zip");
                ZipOutputStream zo = new ZipOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(z)));
                long[] total = {0};
                zipTree(zo, base, base, total);
                zo.close();
                final long size = z.length();
                if (size > MAX_BYTES) throw new Exception("o modelo passa de 80 MB, grande demais para enviar");
                ui.post(new Runnable() { @Override public void run() {
                    try {
                        outIn = z; outStream = new FileInputStream(z); outTotal = size; outSent = 0; lastPct = -1;
                        JSONObject o = new JSONObject(); o.put("t", "file"); o.put("size", size);
                        js("collabSend(" + JSONObject.quote(o.toString()) + ")");
                        js("collabSendFile()");
                    } catch (Exception e) { setStatus("Não consegui enviar o modelo: " + Friendly.why(e), true); }
                } });
            } catch (final Exception e) {
                ui.post(new Runnable() { @Override public void run() { setStatus("Não consegui preparar o modelo para enviar: " + Friendly.why(e), true); } });
            }
        } }).start();
    }

    private void zipTree(ZipOutputStream zo, File base, File f, long[] total) throws Exception {
        if (f.isDirectory()) { File[] a = f.listFiles(); if (a != null) for (File c : a) zipTree(zo, base, c, total); return; }
        total[0] += f.length();
        if (total[0] > MAX_BYTES * 2) throw new Exception("modelo grande demais");
        String rel = f.getAbsolutePath().substring(base.getAbsolutePath().length() + 1).replace('\\', '/');
        zo.putNextEntry(new ZipEntry(rel));
        FileInputStream in = new FileInputStream(f);
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) zo.write(buf, 0, n);
        in.close(); zo.closeEntry();
    }

    // ---------- Anfitrião: aceitar, receber, filtrar, testar ----------
    private void respond(boolean accept) {
        if (pendingHello == null) return;
        String name = pendingHello;
        pendingHello = null;
        if (!accept) {
            sendJson("reject", "reason", "O anfitrião recusou o modelo.");
            setStatus("Você recusou o modelo de " + name + ".", true);
            return;
        }
        sendJson("accept", null, null);
        if (guestKind.equals("default")) {
            showGuestWindow(false, null);
            sendJson("ready", null, null);
            setStatus("conectado | " + name + " na colaboração", true);
        } else setStatus("Aguardando o modelo de " + name + "…", true);
    }

    private static boolean allowedExt(String n) {
        String l = n.toLowerCase();
        int d = l.lastIndexOf('.');
        String e = d < 0 ? "" : l.substring(d + 1);
        switch (e) {
            case "html": case "htm": case "js": case "mjs": case "css": case "json": case "png": case "jpg": case "jpeg": case "webp": case "gif":
            case "svg": case "ico": case "woff": case "woff2": case "ttf": case "otf": case "txt": case "flag": return true;
            default: return false;
        }
    }

    private void deleteTree(File f) {
        if (f.isDirectory()) { File[] a = f.listFiles(); if (a != null) for (File c : a) deleteTree(c); }
        f.delete();
    }

    /** Filtro de importação: só tipos de arquivo esperados, limites de tamanho e de quantidade, e nada fora da pasta. */
    private String extractGuest(File zip) throws Exception {
        File dir = new File(getFilesDir(), "collab/guest");
        deleteTree(dir); dir.mkdirs();
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)));
        ZipEntry e; byte[] buf = new byte[16384]; long total = 0; int files = 0;
        String html = null;
        while ((e = zis.getNextEntry()) != null) {
            String n = e.getName().replace('\\', '/');
            if (n.startsWith("/") || n.contains("../") || n.equals("..")) throw new Exception("o modelo tem um arquivo em um lugar inválido");
            File out = new File(dir, n);
            if (!out.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator)) throw new Exception("o modelo tem um arquivo em um lugar inválido");
            if (e.isDirectory()) { out.mkdirs(); continue; }
            if (!allowedExt(n)) throw new Exception("o modelo tem um arquivo que o app não aceita (" + n + ")");
            if (++files > 600) throw new Exception("o modelo tem arquivos demais");
            File par = out.getParentFile(); if (par != null) par.mkdirs();
            FileOutputStream fo = new FileOutputStream(out); int r; long one = 0;
            while ((r = zis.read(buf)) > 0) {
                total += r; one += r;
                if (total > MAX_BYTES || one > 40L * 1024 * 1024) { fo.close(); throw new Exception("modelo grande demais"); }
                fo.write(buf, 0, r);
            }
            fo.close();
            String low = n.toLowerCase();
            if (low.equals("index.html") || low.equals("vtuber.html")) html = n;
            else if (html == null && (low.endsWith("/index.html") || low.endsWith("/vtuber.html"))) html = n;
        }
        zis.close();
        if (html == null) throw new Exception("o modelo não tem uma página principal (index.html ou vtuber.html)");
        return html;
    }

    private void onModelReceived() {
        final String name = guestName;
        setStatus("Verificando o modelo de " + name + "…", true);
        new Thread(new Runnable() { @Override public void run() {
            try {
                final String html = extractGuest(inZip());
                ui.post(new Runnable() { @Override public void run() { showGuestWindow(true, html); } });
            } catch (final Exception ex) {
                ui.post(new Runnable() { @Override public void run() { rejectModel(ex.getMessage()); } });
            }
        } }).start();
    }

    private void rejectModel(String why) {
        closeGuestWindow();
        sendJson("fail", "reason", why);
        setStatus("Modelo de " + guestName + " recusado: " + why, true);
    }

    // ---------- Janela do modelo do convidado ----------
    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void showGuestWindow(final boolean foreign, final String htmlRel) {
        closeGuestWindow();
        new File(getFilesDir(), "collab/guest").mkdirs();
        int side = (int) (Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels) * 0.45f);
        guestLp = new WindowManager.LayoutParams(side, side, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT);
        guestLp.gravity = Gravity.TOP | Gravity.START;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        guestLp.x = dp(8); guestLp.y = Math.max(0, dm.heightPixels - side - dp(8));

        guestRoot = new FrameLayout(this);
        guestWeb = new WebView(getApplicationContext());
        guestWeb.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = guestWeb.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBlockNetworkLoads(true);                    // caixa isolada: sem internet
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/guest/", new WebViewAssetLoader.InternalStoragePathHandler(this, new File(getFilesDir(), "collab/guest")))
            .build();
        guestWeb.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (HOST.equals(r.getUrl().getHost())) {
                    String p = r.getUrl().getPath();
                    // o modelo do convidado só enxerga a própria pasta; o modelo padrão só enxerga os assets
                    if (foreign && p != null && p.startsWith("/guest/")) return loader.shouldInterceptRequest(r.getUrl());
                    if (!foreign && p != null && p.startsWith("/assets/")) return loader.shouldInterceptRequest(r.getUrl());
                }
                return new WebResourceResponse("text/plain", "utf-8", 403, "Blocked", new HashMap<String, String>(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) { return true; }
            @Override public void onPageFinished(WebView v, String url) {
                v.evaluateJavascript("(function(){var s=document.createElement('style');s.textContent='#msg{display:none !important}';(document.head||document.documentElement).appendChild(s);})()", null);
                if (foreign) ui.postDelayed(new Runnable() { @Override public void run() { startMovementTest(); } }, 1500);
            }
        });
        guestWeb.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(PermissionRequest r) { r.deny(); }
        });
        guestRoot.addView(guestWeb, new FrameLayout.LayoutParams(-1, -1));

        resizer = new View(this);
        GradientDrawable rg = new GradientDrawable(); rg.setColor(0xCCFFCE31); rg.setCornerRadius(dp(10)); resizer.setBackground(rg);
        guestRoot.addView(resizer, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.BOTTOM | Gravity.END));
        resizer.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sw, sh;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: dx = e.getRawX(); dy = e.getRawY(); sw = guestLp.width; sh = guestLp.height; return true;
                    case MotionEvent.ACTION_MOVE:
                        guestLp.width = Math.max(dp(110), sw + (int) (e.getRawX() - dx));
                        guestLp.height = Math.max(dp(110), sh + (int) (e.getRawY() - dy));
                        try { wm.updateViewLayout(guestRoot, guestLp); } catch (Exception ignored) { }
                        return true;
                    default: return true;
                }
            }
        });
        guestRoot.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; int sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: dx = e.getRawX(); dy = e.getRawY(); sx = guestLp.x; sy = guestLp.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        guestLp.x = sx + (int) (e.getRawX() - dx); guestLp.y = sy + (int) (e.getRawY() - dy);
                        try { wm.updateViewLayout(guestRoot, guestLp); } catch (Exception ignored) { }
                        return true;
                    default: return true;
                }
            }
        });
        testing = foreign;
        try { wm.addView(guestRoot, guestLp); } catch (Exception e) { guestRoot = null; guestWeb = null; setStatus("Não consegui mostrar o modelo do seu amigo. Permita \"aparecer sobre outros apps\" para o VTuber Lab.", true); return; }
        updateGuestTouch();
        guestWeb.loadUrl(foreign ? "https://" + HOST + "/guest/" + android.net.Uri.encode(htmlRel, "/") : "https://" + HOST + "/assets/vtuber.html");
    }

    private void updateGuestTouch() {
        if (guestRoot == null || guestLp == null) return;
        if (OverlayService.appVisible) guestLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else guestLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if (resizer != null) resizer.setVisibility(OverlayService.appVisible ? View.VISIBLE : View.GONE);
        try { wm.updateViewLayout(guestRoot, guestLp); } catch (Exception ignored) { }
    }

    private void applyRoomVisibility() {
        if (guestRoot == null) return;
        guestRoot.setVisibility(!testing && AppSettings.hideModelNow(this) ? View.GONE : View.VISIBLE);
    }

    private void closeGuestWindow() {
        testing = false;
        if (guestRoot != null) { try { wm.removeView(guestRoot); } catch (Exception ignored) { } }
        if (guestWeb != null) { try { guestWeb.destroy(); } catch (Exception ignored) { } }
        guestRoot = null; guestWeb = null; resizer = null;
    }

    /** Teste de movimento: o modelo precisa reagir de verdade a um movimento de teste (boca/rotação ou fala do Pngtuber). */
    private void startMovementTest() {
        if (guestWeb == null || !testing) return;
        guestWeb.evaluateJavascript("(function(){try{if(typeof T==='object'&&typeof P==='object'&&T!==null){T.mouth=1;T.x=0.3;return 'T';}"
            + "if(typeof setTalking==='function'&&typeof talking!=='undefined'){setTalking(true);return 'P';}}catch(e){}return '';})()",
            new android.webkit.ValueCallback<String>() { @Override public void onReceiveValue(String v) {
                testKind = v == null ? "" : v.replace("\"", "");
                if (testKind.isEmpty()) { rejectModel("o modelo não reage ao movimento"); return; }
                ui.postDelayed(new Runnable() { @Override public void run() { finishMovementTest(); } }, 900);
            } });
    }

    private void finishMovementTest() {
        if (guestWeb == null) return;
        String check = testKind.equals("T")
            ? "(function(){try{var ok=P.mouth>0.3;T.mouth=0;T.x=0;return ok;}catch(e){return false;}})()"
            : "(function(){try{var ok=(talking===true&&!!idleC);setTalking(false);return ok;}catch(e){return false;}})()";
        guestWeb.evaluateJavascript(check, new android.webkit.ValueCallback<String>() { @Override public void onReceiveValue(String v) {
            if (v != null && v.contains("true")) {
                testing = false; applyRoomVisibility();
                sendJson("ready", null, null);
                setStatus("conectado | " + guestName + " na colaboração", true);
            } else rejectModel("o modelo não reage ao movimento");
        } });
    }

    // ---------- Movimentos recebidos (anfitrião) ----------
    private void applyPose(String s) {
        if (guestWeb == null || testing || s == null || s.length() < 2) return;
        String js;
        if (s.charAt(0) == 'T') {
            String[] a = s.substring(1).split(",");
            if (a.length < 8) return;
            for (String x : a) if (!x.matches("-?[0-9.]+(e-?[0-9]+)?")) return;
            js = "(function(){if(typeof T==='undefined')return;T.x=" + a[0] + ";T.y=" + a[1] + ";T.z=" + a[2] + ";T.open=" + a[3] + ";T.oL=" + a[4] + ";T.oR=" + a[5] + ";T.mouth=" + a[6] + ";T.tongue=" + a[7] + ";})()";
        } else if (s.charAt(0) == 'P') {
            boolean on = s.length() > 1 && s.charAt(1) == '1';
            js = "(function(){if(typeof setTalking==='function'&&typeof talking!=='undefined'){var t=" + on + ";if(t!==talking){setTalking(t);if(t&&typeof hop==='function')hop();}}})()";
        } else return;
        guestWeb.evaluateJavascript(js, null);
    }

    // ---------- Ponte com o collab.html ----------
    private void buildWeb() {
        web = new WebView(getApplicationContext());
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "AndroidCollab");
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return loader.shouldInterceptRequest(r.getUrl()); }
            @Override public void onPageFinished(WebView v, String url) {
                pageReady = true;
                for (String js : new ArrayList<String>(queue)) v.evaluateJavascript(js, null);
                queue.clear();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest r) {
                // Só a nossa própria página (assets do app) recebe o microfone (necessário para mostrar o endereço de rede real).
                if (r.getOrigin().toString().startsWith("https://" + HOST)) r.grant(r.getResources()); else r.deny();
            }
        });
        web.loadUrl("https://" + HOST + "/assets/collab.html");
    }

    private class Bridge {
        @JavascriptInterface public void onEvent(final String t, final String d) { ui.post(new Runnable() { @Override public void run() { handle(t, d); } }); }

        @JavascriptInterface public void onPose(final String s) { ui.post(new Runnable() { @Override public void run() { applyPose(s); } }); }

        @JavascriptInterface public void onChunk(final String b64) {
            try {
                if (inOut == null) return;
                byte[] d = Base64.decode(b64, Base64.NO_WRAP);
                inOut.write(d); inGot += d.length;
                if (inGot > MAX_BYTES + 1024) { inOut.close(); inOut = null; ui.post(new Runnable() { @Override public void run() { rejectModel("modelo grande demais"); } }); return; }
                final int pct = inTotal > 0 ? (int) (inGot * 100 / inTotal) : 0;
                if (pct != lastPct && pct % 5 == 0) { lastPct = pct; ui.post(new Runnable() { @Override public void run() { setStatus("Recebendo o modelo… " + pct + "%", true); } }); }
            } catch (Exception ignored) { }
        }

        @JavascriptInterface public String nextChunk() {
            try {
                if (outStream == null) return "";
                byte[] b = new byte[16384];
                int n = outStream.read(b);
                if (n <= 0) { outStream.close(); outStream = null; return ""; }
                outSent += n;
                final int pct = outTotal > 0 ? (int) (outSent * 100 / outTotal) : 0;
                if (pct != lastPct && pct % 5 == 0) { lastPct = pct; ui.post(new Runnable() { @Override public void run() { setStatus("Enviando o modelo… " + pct + "%", true); } }); }
                return Base64.encodeToString(b, 0, n, Base64.NO_WRAP);
            } catch (Exception e) { return ""; }
        }
    }

    private void handle(String t, String d) {
        switch (t) {
            case "offer": mode = 1; code = d; status = "Copie seu código, mande ao amigo e cole a resposta dele."; changed(); break;
            case "answer": mode = 2; code = d; status = "Copie seu código, mande ao anfitrião e aguarde a conexão…"; changed(); break;
            case "open":
                mode = 3; ui.removeCallbacks(pinger); ui.post(pinger);
                if (role == 2) sendHello(); else setStatus("Conectado. Aguardando o convidado enviar o modelo…", true);
                break;
            case "rtt": if (connectedOk && status.startsWith("conectado")) { status = "conectado | ping: " + d + " ms"; changed(); } break;
            case "state": if ("connecting".equals(d) && mode != 3) setStatus("Conectando…", false); break;
            case "closed": ui.removeCallbacks(pinger); streamPose = false; closeGuestWindow(); if (mode == 3) { mode = 0; setStatus("A conexão com o seu amigo foi encerrada.", false); } break;
            case "failed": ui.removeCallbacks(pinger); mode = 0; setStatus("Não foi possível conectar com o seu amigo. Os dois celulares precisam estar com internet. Crie uma sala nova e troque os códigos de novo; se não funcionar, tente com outra rede (Wi-Fi ou dados móveis).", false); break;
            case "warn": status = d; changed(); break;
            case "error": mode = 0; setStatus(d, false); break;
            case "msg": handleMessage(d); break;
            default: break;
        }
    }

    private void handleMessage(String json) {
        try {
            JSONObject o = new JSONObject(json);
            String t = o.optString("t");
            switch (t) {
                case "hello":    // anfitrião: o convidado quer entrar com este modelo
                    guestKind = o.optString("kind", "default"); guestName = o.optString("name", "Modelo");
                    pendingHello = guestName; changed(); break;
                case "accept":   // convidado: o anfitrião aceitou
                    if (selectedId().equals("default")) setStatus("O anfitrião aceitou. Aguardando…", true); else prepareAndSendModel();
                    break;
                case "reject": setStatus(o.optString("reason", "O anfitrião recusou o modelo."), true); break;
                case "file":     // anfitrião: começa a receber o ZIP
                    inTotal = o.optLong("size", 0); inGot = 0; lastPct = -1;
                    try { if (inOut != null) inOut.close(); inOut = new FileOutputStream(inZip()); } catch (Exception e) { rejectModel("sem espaço para receber"); }
                    break;
                case "fileend":
                    try { if (inOut != null) inOut.close(); } catch (Exception ignored) { }
                    inOut = null; onModelReceived(); break;
                case "ready":    // convidado: o modelo foi aceito e verificado
                    streamPose = true; setStatus("conectado | o anfitrião está vendo o seu modelo", true); break;
                case "fail": setStatus("O modelo foi recusado: " + o.optString("reason"), true); break;
                default: break;
            }
        } catch (Exception ignored) { }
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    @Override public void onDestroy() {
        inst = null;
        ui.removeCallbacks(pinger);
        try { AppSettings.main(this).unregisterOnSharedPreferenceChangeListener(prefListener); AppSettings.p(this).unregisterOnSharedPreferenceChangeListener(prefListener); } catch (Exception ignored) { }
        closeGuestWindow();
        if (web != null) { try { web.evaluateJavascript("collabClose(false)", null); web.destroy(); } catch (Exception ignored) { } web = null; }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
