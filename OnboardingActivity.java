package com.vtuber.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Guia da primeira vez: explica cada permissão e leva direto para a configuração certa. */
public class OnboardingActivity extends Activity {
    private static final int YELLOW = 0xFFFFCE31, PANEL = 0xFF424242;
    private TextView sOverlay, sCamera, sMic, sNotif;

    @Override protected void onCreate(Bundle b) { super.onCreate(b); build(); }
    @Override protected void onResume() { super.onResume(); refresh(); }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable rounded(int c, int r) { GradientDrawable g = new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp(r)); return g; }

    private TextView pill(String t, View.OnClickListener l) {
        TextView x = new TextView(this);
        x.setText(t); x.setTextColor(Color.BLACK); x.setTextSize(13); x.setTypeface(Typeface.DEFAULT, Typeface.BOLD); x.setGravity(Gravity.CENTER);
        x.setBackground(rounded(YELLOW, 20)); x.setPadding(dp(16), 0, dp(16), 0); x.setOnClickListener(l);
        return x;
    }

    private LinearLayout item(String title, String why, TextView status, View.OnClickListener allow) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout txt = new LinearLayout(this); txt.setOrientation(LinearLayout.VERTICAL);
        TextView a = new TextView(this); a.setText(title); a.setTextColor(Color.WHITE); a.setTextSize(14); a.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView c = new TextView(this); c.setText(why); c.setTextColor(0xFFC8C8C8); c.setTextSize(12);
        txt.addView(a); txt.addView(c);
        row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));
        status.setTextSize(18); status.setTextColor(YELLOW); status.setPadding(dp(10), 0, dp(10), 0);
        row.addView(status);
        row.addView(pill("Permitir", allow), new LinearLayout.LayoutParams(-2, dp(38)));
        return row;
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(248, 191, 79));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setBackgroundColor(PANEL); panel.setElevation(dp(10));
        panel.setPadding(dp(22), dp(16), dp(22), dp(14));

        TextView title = new TextView(this);
        title.setText("Bem-vindo ao VTuber Lab"); title.setTextColor(Color.WHITE); title.setTextSize(22); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Para tudo funcionar, o app precisa de quatro permissões. Toque em Permitir em cada uma.");
        sub.setTextColor(0xFFC8C8C8); sub.setTextSize(12); sub.setPadding(0, dp(4), 0, dp(6));
        panel.addView(sub);

        sOverlay = new TextView(this); sCamera = new TextView(this); sMic = new TextView(this); sNotif = new TextView(this);
        panel.addView(item("Exibir sobre outros apps", "Para o Vtuber, o chat e os atalhos aparecerem por cima de qualquer app.", sOverlay, v ->
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())))));
        panel.addView(item("Câmera", "Para o Vtuber acompanhar o movimento do seu rosto.", sCamera, v -> ask(Manifest.permission.CAMERA)));
        panel.addView(item("Microfone", "Para o Pngtuber falar, para a live e para a conversa no Collab.", sMic, v -> ask(Manifest.permission.RECORD_AUDIO)));
        panel.addView(item("Notificações", "Para os botões Parar e Mutar durante a live.", sNotif, v -> {
            if (Build.VERSION.SDK_INT >= 33) ask(Manifest.permission.POST_NOTIFICATIONS);
        }));

        LinearLayout foot = new LinearLayout(this); foot.setGravity(Gravity.END); foot.setPadding(0, dp(10), 0, 0);
        foot.addView(pill("Começar", v -> { AppSettings.p(this).edit().putBoolean("onboarding_done", true).apply(); finish(); }), new LinearLayout.LayoutParams(-2, dp(44)));
        panel.addView(foot);

        ScrollView sv = new ScrollView(this);
        sv.addView(panel);
        root.addView(sv, new FrameLayout.LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * 0.66f), -2, Gravity.CENTER));
        setContentView(root);
    }

    private void ask(String perm) { requestPermissions(new String[]{perm}, 21); }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) { super.onRequestPermissionsResult(code, p, r); refresh(); }

    private boolean has(String perm) { return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; }

    private void refresh() {
        sOverlay.setText(Settings.canDrawOverlays(this) ? "\u2713" : "\u2022");
        sCamera.setText(has(Manifest.permission.CAMERA) ? "\u2713" : "\u2022");
        sMic.setText(has(Manifest.permission.RECORD_AUDIO) ? "\u2713" : "\u2022");
        sNotif.setText(Build.VERSION.SDK_INT < 33 || has(Manifest.permission.POST_NOTIFICATIONS) ? "\u2713" : "\u2022");
    }
}
