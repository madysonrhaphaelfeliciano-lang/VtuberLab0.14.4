package com.vtuber.overlay;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Configurações do app: botões laterais, sala de espera na live, transições e mais. */
public class SettingsActivity extends Activity {
    private static final int PANEL = 0xFF424242, YELLOW = 0xFFFFCE31, TEAL = 0xFF7FD3CC, GRAY = 0xFF6B6B6B;
    private static final String[] TRANS = {"Nenhuma", "Suave", "Vídeo 1", "Vídeo 2", "Vídeo 3"};
    private TextView transBtn;

    @Override protected void onCreate(Bundle b) { super.onCreate(b); build(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable rounded(int c, int r) { GradientDrawable g = new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp(r)); return g; }

    private TextView pill(String t, int bgColor, int fg, View.OnClickListener l) {
        TextView x = new TextView(this);
        x.setText(t); x.setTextColor(fg); x.setTextSize(13); x.setTypeface(Typeface.DEFAULT, Typeface.BOLD); x.setGravity(Gravity.CENTER);
        x.setBackground(rounded(bgColor, 20)); x.setPadding(dp(14), 0, dp(14), 0); x.setOnClickListener(l);
        return x;
    }

    private LinearLayout line(String label) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(0, dp(5), 0, dp(5));
        TextView t = new TextView(this); t.setText(label); t.setTextColor(Color.WHITE); t.setTextSize(13);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    private LinearLayout toggle(String label, final String file, final String key, boolean def, final Runnable after) {
        LinearLayout r = line(label);
        final android.content.SharedPreferences sp = getSharedPreferences(file, MODE_PRIVATE);
        final TextView[] t = new TextView[1];
        t[0] = pill(sp.getBoolean(key, def) ? "Ligado" : "Desligado", sp.getBoolean(key, def) ? YELLOW : GRAY, sp.getBoolean(key, def) ? Color.BLACK : Color.WHITE, v -> {
            boolean on = !sp.getBoolean(key, def);
            sp.edit().putBoolean(key, on).apply();
            t[0].setText(on ? "Ligado" : "Desligado"); t[0].setTextColor(on ? Color.BLACK : Color.WHITE); t[0].setBackground(rounded(on ? YELLOW : GRAY, 20));
            if (after != null) after.run();
        });
        r.addView(t[0], new LinearLayout.LayoutParams(dp(104), dp(34)));
        return r;
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> finish());
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setBackgroundColor(PANEL); panel.setElevation(dp(10));
        panel.setPadding(dp(20), dp(14), dp(20), dp(10)); panel.setClickable(true);

        TextView title = new TextView(this);
        title.setText("Configurações do app"); title.setTextColor(Color.WHITE); title.setTextSize(22); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title);

        panel.addView(toggle("Mostrar os botões laterais", "settings", "side_buttons", true, null));
        panel.addView(toggle("Sala de espera ao iniciar a live", "settings", "waiting_on_live", true, null));
        panel.addView(toggle("Esconder o Vtuber na sala de espera e na tela final", "settings", "hide_wait_end", true, null));
        panel.addView(toggle("Conexão avançada (STUN) no Collab", "collab", "stun", true, null));

        LinearLayout tr = line("Transição ao trocar de sala");
        transBtn = pill(TRANS[AppSettings.transition(this)], YELLOW, Color.BLACK, v -> cycleTransition());
        tr.addView(transBtn, new LinearLayout.LayoutParams(dp(104), dp(34)));
        panel.addView(tr);

        LinearLayout vids = new LinearLayout(this);
        vids.setOrientation(LinearLayout.HORIZONTAL); vids.setGravity(Gravity.END);
        for (int i = 1; i <= 3; i++) {
            final int slot = i;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34)); lp.leftMargin = dp(6);
            vids.addView(pill("Importar vídeo " + i, 0xFF8A8A8A, Color.WHITE, v -> pickVideo(slot)), lp);
        }
        panel.addView(vids);
        TextView hint = new TextView(this);
        hint.setText("Vídeos de transição: MP4 de até 5 segundos."); hint.setTextColor(0xFFC8C8C8); hint.setTextSize(11); hint.setGravity(Gravity.END);
        panel.addView(hint);

        LinearLayout guide = line("Guia da primeira vez");
        guide.addView(pill("Abrir", YELLOW, Color.BLACK, v -> startActivity(new Intent(this, OnboardingActivity.class))), new LinearLayout.LayoutParams(dp(104), dp(34)));
        panel.addView(guide);

        LinearLayout credits = line("Créditos e licenças");
        credits.addView(pill("Ver", YELLOW, Color.BLACK, v -> showCredits()), new LinearLayout.LayoutParams(dp(104), dp(34)));
        panel.addView(credits);

        LinearLayout reset = line("Restaurar as configurações para o padrão");
        reset.addView(pill("Restaurar", 0xFF9B4A4A, Color.WHITE, v -> askReset()), new LinearLayout.LayoutParams(dp(104), dp(34)));
        panel.addView(reset);

        TextView ver = new TextView(this);
        ver.setText(versionText()); ver.setTextColor(0xFFC8C8C8); ver.setTextSize(11); ver.setPadding(0, dp(8), 0, 0);
        panel.addView(ver);

        LinearLayout foot = new LinearLayout(this); foot.setGravity(Gravity.END);
        TextView close = new TextView(this);
        close.setText("FECHAR"); close.setTextColor(TEAL); close.setTextSize(15); close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setPadding(dp(14), dp(10), dp(4), dp(8)); close.setOnClickListener(v -> finish());
        foot.addView(close);
        panel.addView(foot);

        ScrollView sv = new ScrollView(this);
        sv.addView(panel);
        root.addView(sv, new FrameLayout.LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * 0.62f), -2, Gravity.CENTER));
        setContentView(root);
    }

    // ---------- Versão, créditos e restaurar padrão ----------
    private String versionText() {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
            return "Versão instalada: VTuber Lab " + pi.versionName + " (build " + code + ")";
        } catch (Exception e) { return "VTuber Lab"; }
    }

    private void showCredits() {
        String t = versionText() + "\n\n"
            + "O VTuber Lab usa os projetos abaixo. Obrigado a todas as pessoas que os criam e mantêm.\n\n"
            + "LIVE (transmissão)\n"
            + "\u2022 RootEncoder, de pedroSG94, licença Apache 2.0.\n  github.com/pedroSG94/RootEncoder\n\n"
            + "COLLAB (conexão entre celulares)\n"
            + "\u2022 STUN: servidor público do Google (stun.l.google.com). Ele só ajuda os celulares a descobrirem o endereço de internet um do outro; nenhum vídeo, modelo ou movimento passa por ele.\n"
            + "\u2022 WebRTC, que vem no Android System WebView (Chromium), licença BSD de 3 cláusulas.\n\n"
            + "RASTREAMENTO DO ROSTO\n"
            + "\u2022 MediaPipe (Face Landmarker), do Google, licença Apache 2.0. É carregado pela internet quando a câmera liga.\n\n"
            + "BASE DO APP\n"
            + "\u2022 AndroidX WebKit e outras bibliotecas AndroidX, do Google, licença Apache 2.0.\n"
            + "\u2022 Kotlin, da JetBrains, licença Apache 2.0 (usado pelo RootEncoder).\n\n"
            + "CHAT DA LIVE\n"
            + "\u2022 O chat usa a página de chat do YouTube. YouTube é marca da Google LLC. O VTuber Lab é independente e não é afiliado, patrocinado nem aprovado pelo YouTube ou pelo Google.\n\n"
            + "Os textos completos das licenças estão nos sites de cada projeto.";
        TextView tv = new TextView(this);
        tv.setText(t); tv.setTextSize(13); tv.setPadding(dp(20), dp(8), dp(20), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new android.app.AlertDialog.Builder(this).setTitle("Créditos e licenças").setView(sv).setPositiveButton("Fechar", null).show();
    }

    private void askReset() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("Restaurar configurações?")
            .setMessage("Isto volta as opções desta tela (botões laterais, sala de espera na live, esconder o Vtuber, transição, STUN, aparência do chat e camada dos acessórios) ao padrão.\n\n"
                + "NÃO apaga seus personagens, cenários, acessórios, vídeos de transição nem a chave da Live.")
            .setPositiveButton("Restaurar", (d, w) -> resetAll())
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void resetAll() {
        boolean onb = AppSettings.onboardingDone(this);
        AppSettings.p(this).edit().clear().putBoolean("onboarding_done", onb).apply();
        getSharedPreferences("collab", MODE_PRIVATE).edit().remove("stun").apply();
        android.content.SharedPreferences cp = getSharedPreferences("chat", MODE_PRIVATE);
        String vid = cp.getString("video", "");
        cp.edit().clear().putString("video", vid).apply();   // guarda só o link da live; a aparência volta ao padrão
        AppSettings.main(this).edit().remove("acc_back").remove("rec_landscape").remove("rec_orient").apply();
        if (ChatService.isRunning()) startService(new Intent(this, ChatService.class).setAction(ChatService.ACTION_STYLE));
        Toast.makeText(this, "Configurações restauradas para o padrão.", Toast.LENGTH_SHORT).show();
        recreate();
    }

    private File videoFile(int slot) { return new File(new File(getFilesDir(), "transitions"), "t" + slot + ".mp4"); }

    private void cycleTransition() {
        int n = (AppSettings.transition(this) + 1) % TRANS.length;
        if (n >= 2 && !videoFile(n - 1).exists()) {
            Toast.makeText(this, TRANS[n] + " ainda não foi importado. Importe o vídeo primeiro.", Toast.LENGTH_SHORT).show();
            n = (n + 1) % TRANS.length; // pula os vídeos que não existem
            while (n >= 2 && !videoFile(n - 1).exists()) n = (n + 1) % TRANS.length;
        }
        AppSettings.p(this).edit().putInt("transition", n).apply();
        transBtn.setText(TRANS[n]);
    }

    private void pickVideo(int slot) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("video/mp4");
        startActivityForResult(i, 100 + slot);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req < 101 || req > 103 || res != RESULT_OK || data == null || data.getData() == null) return;
        final int slot = req - 100;
        final Uri uri = data.getData();
        new Thread(new Runnable() { @Override public void run() {
            String msg;
            try {
                MediaMetadataRetriever mr = new MediaMetadataRetriever();
                long dur = 0;
                try { mr.setDataSource(SettingsActivity.this, uri); String ds = mr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); dur = ds == null ? 0 : Long.parseLong(ds); }
                finally { try { mr.release(); } catch (Exception ignore) { } }
                if (dur <= 0) throw new Exception("não consegui ler o vídeo");
                if (dur > 5300) throw new Exception("o vídeo tem " + (dur / 1000) + " s e o limite é 5 s");
                File dir = new File(getFilesDir(), "transitions"); dir.mkdirs();
                InputStream in = getContentResolver().openInputStream(uri);
                FileOutputStream out = new FileOutputStream(videoFile(slot));
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                in.close(); out.close();
                msg = "Vídeo " + slot + " importado.";
            } catch (Exception e) { msg = "Falha: " + e.getMessage(); }
            final String m = msg;
            runOnUiThread(new Runnable() { @Override public void run() { Toast.makeText(SettingsActivity.this, m, Toast.LENGTH_LONG).show(); } });
        } }).start();
    }
}
