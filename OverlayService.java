package com.vtuber.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.webkit.WebViewAssetLoader;

public class OverlayService extends Service {

    static final String ACTION_STOP = "com.vtuber.overlay.STOP";
    static final String ACTION_RELOAD_CHARACTER = "com.vtuber.overlay.RELOAD_CHARACTER";
    private static final String CHANNEL_ID = "vtuber_overlay";
    private static final int NOTIF_ID = 1;

    // tamanho inicial (largura x altura em dp). Depois o tamanho é livre: gesto de pinça com dois dedos.
    private static final int[][] SIZES = {{190, 260}, {260, 360}, {340, 470}};

    static volatile boolean appVisible = false;
    private static OverlayService instance;
    static boolean isRunning() { return instance != null; }

    /** Comandos vindos da tela inicial (engrenagem = configurações do modelo). Retorna false se o overlay não está ligado. */
    static boolean remote(final String cmd) {
        final OverlayService s = instance;
        if (s == null) return false;
        s.handler.post(new Runnable() {
            @Override
            public void run() {
                if (s.web == null) return;
                if ("calibrate".equals(cmd)) s.web.evaluateJavascript("window.calibrate && calibrate()", null);
                else if ("green".equals(cmd)) s.web.evaluateJavascript("window.toggleGreen && toggleGreen()", null);
            }
        });
        return true;
    }

    /** Adiciona uma imagem (acessório) sobre o modelo. Retorna false se o overlay não está ligado. */
    static boolean addAccessory(final String path, final boolean back) {
        final OverlayService s = instance;
        if (s == null) return false;
        s.handler.post(new Runnable() { @Override public void run() { s.addAccessoryInternal(path, back); } });
        return true;
    }

    /** Dados de um acessório, para a janela de Camadas. */
    static final class AccInfo {
        final int id;
        final String path;
        final boolean back;
        final boolean follow;
        AccInfo(int id, String path, boolean back, boolean follow) { this.id = id; this.path = path; this.back = back; this.follow = follow; }
    }

    static java.util.ArrayList<AccInfo> accessoryInfo() {
        java.util.ArrayList<AccInfo> out = new java.util.ArrayList<AccInfo>();
        OverlayService s = instance;
        if (s == null) return out;
        for (Accessory a : s.accessories) out.add(new AccInfo(a.id, a.path, a.back, a.follow));
        return out;
    }

    static boolean setAccessoryBack(final int id, final boolean back) {
        final OverlayService s = instance;
        if (s == null) return false;
        s.handler.post(new Runnable() { @Override public void run() { Accessory a = s.accessoryById(id); if (a != null) s.setLayer(a, back); } });
        return true;
    }

    /** Objeto parado (padrão) fica onde está quando o Vtuber é arrastado; "seguir" faz ele acompanhar o modelo. */
    static boolean setAccessoryFollow(final int id, final boolean follow) {
        final OverlayService s = instance;
        if (s == null) return false;
        s.handler.post(new Runnable() { @Override public void run() { Accessory a = s.accessoryById(id); if (a != null) a.follow = follow; } });
        return true;
    }

    static boolean removeAccessoryById(final int id) {
        final OverlayService s = instance;
        if (s == null) return false;
        s.handler.post(new Runnable() { @Override public void run() { Accessory a = s.accessoryById(id); if (a != null) s.removeAccessory(a); } });
        return true;
    }

    /** Reordena os acessórios de uma camada (frente ou atrás). A lista vem de cima (mais na frente) para baixo. */
    static boolean reorderAccessories(final boolean back, final java.util.ArrayList<Integer> topToBottom) {
        final OverlayService s = instance;
        if (s == null) return false;
        s.handler.post(new Runnable() { @Override public void run() {
            java.util.ArrayList<Accessory> grp = new java.util.ArrayList<Accessory>();
            for (int i = topToBottom.size() - 1; i >= 0; i--) {
                Accessory a = s.accessoryById(topToBottom.get(i));
                if (a != null && a.back == back) grp.add(a);
            }
            int count = 0;
            for (Accessory x : s.accessories) if (x.back == back) count++;
            if (grp.size() != count) return;
            int k = 0;
            for (int i = 0; i < s.accessories.size(); i++) if (s.accessories.get(i).back == back) s.accessories.set(i, grp.get(k++));
            s.reorderWindows();
        } });
        return true;
    }

    /**
     * Arrastar na janela de Camadas: põe o acessório/objeto na frente ou atrás do modelo, na posição indicada
     * (0 = o mais na frente do seu lado). Acessório (na frente) gruda no Vtuber; Objeto (atrás) fica fixo.
     */
    static boolean placeAccessory(final int id, final boolean back, final int indexFromTop) {
        final OverlayService s = instance;
        if (s == null) return false;
        s.handler.post(new Runnable() { @Override public void run() {
            Accessory a = s.accessoryById(id);
            if (a == null) return;
            s.accessories.remove(a);
            a.back = back;
            a.follow = !back;
            java.util.ArrayList<Integer> side = new java.util.ArrayList<Integer>();
            for (int i = 0; i < s.accessories.size(); i++) if (s.accessories.get(i).back == back) side.add(i);
            int n = side.size();
            int fromBottom = Math.max(0, Math.min(n, n - indexFromTop));
            int at = fromBottom >= n ? (n == 0 ? s.accessories.size() : side.get(n - 1) + 1) : side.get(fromBottom);
            s.accessories.add(at, a);
            s.reorderWindows();
        } });
        return true;
    }

    static boolean clearAccessories() {
        final OverlayService s = instance;
        if (s == null) return false;
        s.handler.post(new Runnable() { @Override public void run() { s.clearAccessoriesInternal(); } });
        return true;
    }

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private DragLayout root;
    private WebView web;
    private HorizontalScrollView controls;
    private LinearLayout shortcutRow;
    private boolean hasShortcuts = false;
    private String pendingScript = null;
    private boolean rootAttached = false;
    private final java.util.ArrayList<Accessory> accessories = new java.util.ArrayList<Accessory>();
    private int accSeq = 0;
    private final int sizeIdx = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControls = new Runnable() {
        @Override
        public void run() {
            if (controls != null) controls.setVisibility(View.GONE);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        instance = this;
        startAsForeground();
        if (root == null) {
            createOverlay();
        } else if (intent != null && ACTION_RELOAD_CHARACTER.equals(intent.getAction()) && web != null) {
            loadCharacter();
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Vtuber na tela", NotificationManager.IMPORTANCE_LOW));

        Intent stopIntent = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent openPi = PendingIntent.getActivity(this, 2,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);

        Notification.Action stopAction = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Parar", stopPi).build();

        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("Vtuber ativo")
                .setContentText("Toque para abrir. Use Parar para fechar.")
                .setContentIntent(openPi)
                .addAction(stopAction)
                .setOngoing(true)
                .build();

        boolean png = new java.io.File(getFilesDir(), "characters/current/pngtuber.flag").exists();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, png ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        : ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (SecurityException e) {
            // permissão de câmera/microfone ausente: não derruba o app
            stopSelf();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        handler.postDelayed(new Runnable() { public void run() { keepOnScreen(); } }, 300);
    }

    /** Girou a tela: se o modelo ficou fora da área visível, traz de volta (sempre sobra um pedaço visível). */
    private void keepOnScreen() {
        if (root == null || lp == null || wm == null || !rootAttached) return;
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int keep = dp(60);
        int nx = Math.max(keep - lp.width, Math.min(dm.widthPixels - keep, lp.x));
        int ny = Math.max(keep - lp.height, Math.min(dm.heightPixels - keep, lp.y));
        if (nx != lp.x || ny != lp.y) {
            lp.x = nx;
            lp.y = ny;
            try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
        }
    }

    private void createOverlay() {
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        lp = new WindowManager.LayoutParams(
                dp(SIZES[sizeIdx][0]), dp(SIZES[sizeIdx][1]),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        // Começa no centro da tela; depois o usuário arrasta para onde quiser.
        android.util.DisplayMetrics cdm = getResources().getDisplayMetrics();
        lp.x = Math.max(0, (cdm.widthPixels - lp.width) / 2);
        lp.y = Math.max(0, (cdm.heightPixels - lp.height) / 2);
        if (!appVisible) lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        root = new DragLayout(this);

        web = new WebView(getApplicationContext());
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/imported/", new WebViewAssetLoader.InternalStoragePathHandler(this, new java.io.File(getFilesDir(), "characters/current")))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (pendingScript != null) view.evaluateJavascript(pendingScript, null);
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                web.post(new Runnable() {
                    @Override
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Ao tocar no modelo aparecem os ATALHOS dele (pasta Atalhos do ZIP). Sem atalhos, nada aparece.
        controls = new HorizontalScrollView(this);
        controls.setHorizontalScrollBarEnabled(false);
        shortcutRow = new LinearLayout(this);
        shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
        shortcutRow.setGravity(Gravity.CENTER);
        controls.addView(shortcutRow);
        controls.setVisibility(View.GONE);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        cp.bottomMargin = dp(6);
        root.addView(controls, cp);

        wm.addView(root, lp);
        rootAttached = true;
        loadCharacter();
        startExtras();
    }

    private java.io.File findCharacterHtml(java.io.File dir) {
        java.io.File a = new java.io.File(dir, "vtuber.html");
        if (a.exists()) return a;
        java.io.File b = new java.io.File(dir, "index.html");
        if (b.exists()) return b;
        java.io.File[] files = dir.listFiles();
        if (files != null) for (java.io.File f : files) if (f.isDirectory()) {
            java.io.File found = findCharacterHtml(f);
            if (found != null) return found;
        }
        return null;
    }

    private void loadCharacter() {
        if (web == null) return;
        java.io.File folder = new java.io.File(getFilesDir(), "characters/current");
        java.io.File html = folder.exists() ? findCharacterHtml(folder) : null;
        web.clearCache(true);
        if (html != null) {
            buildShortcuts(folder, html);
            String relative = html.getAbsolutePath().substring(folder.getAbsolutePath().length() + 1).replace("\\", "/");
            web.loadUrl("https://appassets.androidplatform.net/imported/" + android.net.Uri.encode(relative, "/"));
        } else {
            // Verônica padrão (embutida): os atalhos dela também vêm dos assets.
            buildShortcuts(prepareDefaultPack(), null);
            web.loadUrl("https://appassets.androidplatform.net/assets/vtuber.html");
        }
    }

    /** Copia os atalhos da Verônica padrão (assets) para uma pasta, no mesmo formato de um ZIP de personagem (Atalhos/atalhos.json). */
    private java.io.File prepareDefaultPack() {
        java.io.File base = new java.io.File(getFilesDir(), "characters/_default");
        try {
            java.io.File dir = new java.io.File(base, "Atalhos");
            dir.mkdirs();
            String[][] f = {{"default_atalhos.json", "atalhos.json"}, {"default_careca.png", "careca.png"}, {"default_energetico.png", "energetico.png"}};
            for (String[] p : f) {
                java.io.InputStream in = getAssets().open(p[0]);
                java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(dir, p[1]));
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
                out.close();
                in.close();
            }
        } catch (Exception ignored) {
        }
        return base;
    }

    /** Fora do app o Vtuber vira fantasma: sem toque, sem botões. Dentro do app volta a ter toque. */
    static void setAppVisible(boolean v) {
        appVisible = v;
        OverlayService s = instance;
        if (s != null) s.applyGhost(!v);
        CollabService.appVisibilityChanged();
    }

    // ---------- Sala atual (esconde o Vtuber), movimentos para o Collab e camadas ----------
    private static final String POSE_JS = "(function(){try{if(typeof T!=='undefined'&&T&&typeof T.x==='number')return 'T'+[T.x,T.y,T.z,T.open,T.oL,T.oR,T.mouth,T.tongue].map(function(v){return Math.round(v*1000)/1000}).join(',');if(typeof talking!=='undefined')return 'P'+(talking?1:0);}catch(e){}return '';})()";
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener roomListener;
    private final Runnable poseTick = new Runnable() {
        @Override public void run() {
            if (web != null && CollabService.wantPose()) {
                web.evaluateJavascript(POSE_JS, new android.webkit.ValueCallback<String>() {
                    @Override public void onReceiveValue(String v) { CollabService.sendPose(v); }
                });
            }
            handler.postDelayed(this, 40);
        }
    };

    private void startExtras() {
        applyRoomVisibility();
        if (roomListener == null) {
            roomListener = new android.content.SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override public void onSharedPreferenceChanged(android.content.SharedPreferences sp, String key) {
                    handler.post(new Runnable() { @Override public void run() { applyRoomVisibility(); } });
                }
            };
            AppSettings.main(this).registerOnSharedPreferenceChangeListener(roomListener);
            AppSettings.p(this).registerOnSharedPreferenceChangeListener(roomListener);
        }
        handler.removeCallbacks(poseTick);
        handler.post(poseTick);
    }

    /** Sala de espera e tela final: o Vtuber (e os acessórios) ficam escondidos, se a opção estiver ligada. */
    private void applyRoomVisibility() {
        boolean hide = AppSettings.hideModelNow(this);
        if (root != null) root.setVisibility(hide ? View.GONE : View.VISIBLE);
        for (Accessory a : accessories) a.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    /** Camadas: acessórios "atrás" ficam abaixo do modelo; os "na frente" ficam acima (a ordem das janelas é a ordem em que foram adicionadas). */
    private void reorderWindows() {
        if (wm == null) return;
        boolean anyBack = false;
        for (Accessory x : accessories) if (x.back) { anyBack = true; break; }
        try {
            for (Accessory x : accessories) if (x.back) { wm.removeViewImmediate(x); wm.addView(x, x.p); }
            if (root != null && anyBack) { wm.removeViewImmediate(root); wm.addView(root, lp); }
            for (Accessory x : accessories) if (!x.back) { wm.removeViewImmediate(x); wm.addView(x, x.p); }
        } catch (Exception ignored) { }
    }

    /** Muda o acessório de camada: na frente ou atrás do modelo. */
    private void setLayer(Accessory a, boolean back) {
        if (a.back == back) return;
        a.back = back;
        a.follow = !back;
        reorderWindows();
    }

    private Accessory accessoryById(int id) {
        for (Accessory a : accessories) if (a.id == id) return a;
        return null;
    }

    private void applyGhost(final boolean ghost) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (root == null || lp == null) return;
                if (ghost) {
                    lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                    handler.removeCallbacks(hideControls);
                    if (controls != null) controls.setVisibility(View.GONE);
                } else {
                    lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                }
                try {
                    wm.updateViewLayout(root, lp);
                } catch (Exception ignored) {
                }
            }
        });
    }

    private TextView pill(String text, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        int h = dp(10), v = dp(7);
        t.setPadding(h, v, h, v);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(200, 20, 16, 28));
        bg.setCornerRadius(dp(14));
        t.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(3);
        p.rightMargin = dp(3);
        t.setLayoutParams(p);
        t.setOnClickListener(l);
        return t;
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 6000);
    }

    private void toggleControls() {
        if (!hasShortcuts) return;
        if (controls.getVisibility() == View.VISIBLE) {
            controls.setVisibility(View.GONE);
            handler.removeCallbacks(hideControls);
        } else {
            controls.setVisibility(View.VISIBLE);
            scheduleHide();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(poseTick);
        if (roomListener != null) {
            try { AppSettings.main(this).unregisterOnSharedPreferenceChangeListener(roomListener); AppSettings.p(this).unregisterOnSharedPreferenceChangeListener(roomListener); } catch (Exception ignored) { }
            roomListener = null;
        }
        if (instance == this) instance = null;
        handler.removeCallbacks(hideControls);
        clearAccessoriesInternal();
        if (root != null) {
            try {
                wm.removeView(root);
            } catch (Exception ignored) {
            }
            root = null;
        }
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    // ---------- Atalhos do modelo ----------
    private java.io.File findDirIgnoreCase(java.io.File dir, String name, int depth) {
        java.io.File[] fs = dir == null ? null : dir.listFiles();
        if (fs == null) return null;
        for (java.io.File f : fs) if (f.isDirectory() && f.getName().equalsIgnoreCase(name)) return f;
        if (depth > 1) for (java.io.File f : fs) if (f.isDirectory()) {
            java.io.File x = findDirIgnoreCase(f, name, depth - 1);
            if (x != null) return x;
        }
        return null;
    }

    private java.io.File resolveFile(java.io.File dir, String rel) {
        java.io.File f = new java.io.File(dir, rel);
        if (f.exists()) return f;
        java.io.File[] fs = dir.listFiles();
        if (fs != null) for (java.io.File x : fs) if (x.isFile() && x.getName().equalsIgnoreCase(rel)) return x;
        return null;
    }

    private String readText(java.io.File f) throws Exception {
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) bo.write(buf, 0, r);
        in.close();
        return bo.toString("UTF-8");
    }

    private Bitmap decodeScaled(java.io.File f, int max) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int sm = 1;
        while (Math.max(o.outWidth, o.outHeight) / sm > max) sm *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = sm;
        return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
    }

    /** Lê Atalhos/atalhos.json do modelo atual e cria os botões. Sem a pasta ou sem o arquivo, não há atalhos. */
    private void buildShortcuts(java.io.File folder, java.io.File html) {
        hasShortcuts = false;
        pendingScript = null;
        if (shortcutRow == null) return;
        shortcutRow.removeAllViews();
        if (controls != null) controls.setVisibility(View.GONE);
        try {
            java.io.File dir = findDirIgnoreCase(folder, "atalhos", 3);
            if (dir == null) return;
            java.io.File json = resolveFile(dir, "atalhos.json");
            if (json == null) return;
            String txt = readText(json).trim();
            org.json.JSONArray arr;
            if (txt.startsWith("[")) {
                arr = new org.json.JSONArray(txt);
            } else {
                org.json.JSONObject o = new org.json.JSONObject(txt);
                arr = o.optJSONArray("atalhos");
                String sc = o.optString("script", "");
                if (sc.length() > 0) {
                    java.io.File sf = resolveFile(dir, sc);
                    if (sf != null) pendingScript = readText(sf);
                }
            }
            if (arr == null) return;
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject it = arr.optJSONObject(i);
                if (it == null) continue;
                addShortcutButton(dir, it.optString("nome", ""), it.optString("imagem", ""),
                        it.optString("acao", ""), it.optString("desligar", ""));
            }
            hasShortcuts = shortcutRow.getChildCount() > 0;
        } catch (Exception ignored) {
        }
    }

    private void addShortcutButton(java.io.File dir, String nome, String imagem, final String acao, final String desligar) {
        java.io.File img = imagem.length() > 0 ? resolveFile(dir, imagem) : null;
        Bitmap b = img != null ? decodeScaled(img, 256) : null;
        View v;
        if (b != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(b);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pd = dp(5);
            iv.setPadding(pd, pd, pd, pd);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Color.argb(200, 20, 16, 28));
            iv.setBackground(g);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(46), dp(46));
            bp.leftMargin = dp(3);
            bp.rightMargin = dp(3);
            iv.setLayoutParams(bp);
            v = iv;
        } else {
            v = pill(nome.length() > 0 ? nome : "?", null);
        }
        final boolean[] on = {false};
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View x) {
                String js = (desligar.length() > 0 && on[0]) ? desligar : acao;
                on[0] = !on[0];
                if (web != null && js.length() > 0) web.evaluateJavascript(js, null);
                scheduleHide();
            }
        });
        shortcutRow.addView(v);
    }

    // ---------- Acessórios ----------
    /** Só os objetos marcados para seguir acompanham o modelo quando ele é arrastado; os demais ficam parados. */
    private void moveAccessories(int dx, int dy) {
        if (dx == 0 && dy == 0) return;
        for (Accessory a : accessories) {
            if (!a.follow) continue;
            a.p.x += dx;
            a.p.y += dy;
            try { wm.updateViewLayout(a, a.p); } catch (Exception ignored) { }
        }
    }

    private void addAccessoryInternal(String path, boolean back) {
        if (wm == null) return;
        android.graphics.drawable.Drawable anim = null;
        int aw = 0, ah = 0;
        Bitmap b = null;
        String low = path.toLowerCase();
        // GIF e WebP animados: usa o ImageDecoder (Android 9+) e deixa o acessório animar em loop.
        if (android.os.Build.VERSION.SDK_INT >= 28 && (low.endsWith(".gif") || low.endsWith(".webp"))) {
            try {
                android.graphics.ImageDecoder.Source src = android.graphics.ImageDecoder.createSource(new java.io.File(path));
                anim = android.graphics.ImageDecoder.decodeDrawable(src, new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
                    @Override public void onHeaderDecoded(android.graphics.ImageDecoder d, android.graphics.ImageDecoder.ImageInfo info, android.graphics.ImageDecoder.Source s) {
                        int w = info.getSize().getWidth(), h = info.getSize().getHeight(), m = Math.max(w, h);
                        if (m > 1024) d.setTargetSize(Math.max(1, w * 1024 / m), Math.max(1, h * 1024 / m));
                    }
                });
                aw = Math.max(1, anim.getIntrinsicWidth());
                ah = Math.max(1, anim.getIntrinsicHeight());
                if (anim instanceof android.graphics.drawable.AnimatedImageDrawable) {
                    android.graphics.drawable.AnimatedImageDrawable ad = (android.graphics.drawable.AnimatedImageDrawable) anim;
                    ad.setRepeatCount(android.graphics.drawable.AnimatedImageDrawable.REPEAT_INFINITE);
                    ad.start();
                }
            } catch (Throwable t) { anim = null; }
        }
        if (anim == null) {
            b = decodeScaled(new java.io.File(path), 1024);
            if (b == null) return;
        }
        Accessory a = new Accessory(this, b, anim, aw, ah);
        a.id = ++accSeq;
        a.path = path;
        a.back = back;
        a.follow = !back;   // Acessório (na frente) gruda no Vtuber; Objeto (atrás) é fixo na tela
        try {
            wm.addView(a, a.p);
        } catch (Exception e) {
            return;
        }
        accessories.add(a);
        reorderWindows();
        applyRoomVisibility();
    }

    private void removeAccessory(Accessory a) {
        try { wm.removeView(a); } catch (Exception ignored) { }
        accessories.remove(a);
    }

    private void clearAccessoriesInternal() {
        for (Accessory a : new java.util.ArrayList<Accessory>(accessories)) removeAccessory(a);
    }

    /** Imagem solta sobre a tela: arrastar move, pinça muda o tamanho e um toque rápido remove (dentro e fora do app). */
    private class Accessory extends FrameLayout {
        WindowManager.LayoutParams p;
        int id;
        String path;
        private float aspect, downX, downY, sx, sy;
        private boolean dragging, pinching;
        private int slop;
        private android.view.ScaleGestureDetector sc;
        boolean back = false;
        boolean follow = false;

        Accessory(Context c, Bitmap b, android.graphics.drawable.Drawable anim, int aw, int ah) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            aspect = anim != null ? (float) ah / Math.max(1, aw) : (float) b.getHeight() / Math.max(1, b.getWidth());
            ImageView iv = new ImageView(c);
            if (anim != null) iv.setImageDrawable(anim); else iv.setImageBitmap(b);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            addView(iv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            int w = dp(130);
            int h = Math.max(dp(30), Math.round(w * aspect));
            p = new WindowManager.LayoutParams(w, h,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.TOP | Gravity.START;
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            p.x = Math.max(0, (dm.widthPixels - w) / 2);
            p.y = Math.max(0, (dm.heightPixels - h) / 2);
            sc = new android.view.ScaleGestureDetector(c, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(android.view.ScaleGestureDetector d) {
                    int nw = Math.max(dp(24), Math.min(12000, Math.round(p.width * d.getScaleFactor())));
                    int nh = Math.max(dp(24), Math.round(nw * aspect));
                    p.x += (p.width - nw) / 2;
                    p.y += (p.height - nh) / 2;
                    p.width = nw;
                    p.height = nh;
                    try { wm.updateViewLayout(Accessory.this, p); } catch (Exception ignored) { }
                    return true;
                }
            });
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            sc.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX();
                    downY = e.getRawY();
                    sx = p.x;
                    sy = p.y;
                    dragging = false;
                    pinching = false;
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    pinching = true;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!pinching) {
                        if (!dragging && (Math.abs(e.getRawX() - downX) > slop || Math.abs(e.getRawY() - downY) > slop)) dragging = true;
                        if (dragging) {
                            p.x = (int) (sx + e.getRawX() - downX);
                            p.y = (int) (sy + e.getRawY() - downY);
                            try { wm.updateViewLayout(this, p); } catch (Exception ignored) { }
                        }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    // toque rápido, sem arrastar nem pinça: remove o acessório
                    boolean tap = !dragging && !pinching && e.getEventTime() - e.getDownTime() < ViewConfiguration.getLongPressTimeout();
                    dragging = false;
                    pinching = false;
                    if (tap) removeAccessory(Accessory.this);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    pinching = false;
                    return true;
                default:
                    return true;
            }
        }
    }

    /** Arrastar com o dedo move a janela; um toque rápido mostra ou esconde os botões. */
    private class DragLayout extends FrameLayout {
        private float downX, downY, startX, startY;
        private boolean dragging, pinching, wasPinch;
        private final int slop;
        private final android.view.ScaleGestureDetector scaler;

        DragLayout(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            // Pinça com dois dedos muda o tamanho do personagem, sem limite máximo prático.
            scaler = new android.view.ScaleGestureDetector(c, new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(android.view.ScaleGestureDetector d) {
                    if (lp == null || wm == null) return true;
                    float f = d.getScaleFactor();
                    int nw = Math.max(dp(60), Math.min(12000, Math.round(lp.width * f)));
                    int nh = Math.max(dp(80), Math.round(lp.height * (float) nw / Math.max(1, lp.width)));
                    lp.x += (lp.width - nw) / 2;
                    lp.y += (lp.height - nh) / 2;
                    lp.width = nw;
                    lp.height = nh;
                    try { wm.updateViewLayout(DragLayout.this, lp); } catch (Exception ignored) { }
                    return true;
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            scaler.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    pinching = true;
                    wasPinch = true;
                    dragging = false;
                    break;
                case MotionEvent.ACTION_DOWN:
                    pinching = false;
                    wasPinch = false;
                    downX = e.getRawX();
                    downY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    dragging = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && (Math.abs(e.getRawX() - downX) > slop
                            || Math.abs(e.getRawY() - downY) > slop)) {
                        dragging = true;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!dragging && !wasPinch && controls != null) {
                        post(new Runnable() {
                            @Override
                            public void run() {
                                toggleControls();
                            }
                        });
                    }
                    break;
                default:
                    break;
            }
            return dragging || pinching;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            scaler.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    pinching = true;
                    wasPinch = true;
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (dragging && !pinching) {
                        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                        // posição livre: o modelo pode sair da tela (ex.: só de cintura para cima). Fica sempre um pedaço visível para poder puxar de volta.
                        int keep = dp(60);
                        int minX = keep - lp.width, maxX = dm.widthPixels - keep;
                        int minY = keep - lp.height, maxY = dm.heightPixels - keep;
                        int ox = lp.x, oy = lp.y;
                        lp.x = Math.max(minX, Math.min(maxX, (int) (startX + e.getRawX() - downX)));
                        lp.y = Math.max(minY, Math.min(maxY, (int) (startY + e.getRawY() - downY)));
                        wm.updateViewLayout(this, lp);
                        moveAccessories(lp.x - ox, lp.y - oy);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    pinching = false;
                    return true;
                default:
                    return super.onTouchEvent(e);
            }
        }
    }
}
