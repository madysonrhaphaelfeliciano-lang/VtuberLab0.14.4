package com.vtuber.overlay;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Janela de configuração do chat da live (mesmo visual de "Configurações do modelo"):
 * link da live, cor do nome, cor da letra, tamanho, fundo, mostrar/esconder e mover.
 */
public class ChatActivity extends Activity {
    private static final int PANEL = 0xFF424242, YELLOW = 0xFFFFCE31, FIELD = 0xFF242424, TEAL = 0xFF7FD3CC, WHITE = 0xFFFFFFFF;
    private static final int[] COLORS = {0xFFFFFFFF, 0xFFFFCE31, 0xFFFF9F43, 0xFFFF6B6B, 0xFFFF8AD8, 0xFFB28DFF, 0xFF6EC6FF, 0xFF7CE38B, 0xFF141414};
    private SharedPreferences prefs;
    private EditText linkField;
    private Button bShow;
    private LinearLayout nameRow, textRow;
    private TextView sizeLabel, bgLabel;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = ChatService.prefs(this);
        buildUi();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }

    private Button pill(String t, int hDp, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t); x.setAllCaps(false); x.setTextColor(Color.BLACK); x.setTextSize(13); x.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        x.setBackground(rounded(YELLOW, hDp / 2 + 2)); x.setMinHeight(0); x.setMinimumHeight(0); x.setPadding(dp(14), 0, dp(14), 0);
        x.setOnClickListener(l);
        return x;
    }

    private TextView label(String t) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextColor(WHITE); v.setTextSize(12); v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        v.setPadding(dp(4), dp(8), 0, dp(3));
        return v;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> finish());

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setBackgroundColor(PANEL); panel.setElevation(dp(10));
        panel.setPadding(dp(20), dp(14), dp(20), dp(10)); panel.setClickable(true);

        TextView title = new TextView(this);
        title.setText("Chat da live"); title.setTextColor(WHITE); title.setTextSize(22); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        panel.addView(title);

        panel.addView(label("Link ou ID da live (YouTube)"));
        LinearLayout lr = new LinearLayout(this); lr.setOrientation(LinearLayout.HORIZONTAL); lr.setGravity(Gravity.CENTER_VERTICAL);
        linkField = new EditText(this);
        linkField.setBackground(rounded(FIELD, 20)); linkField.setTextColor(WHITE); linkField.setHintTextColor(0xFF888888);
        linkField.setHint("cole aqui"); linkField.setTextSize(12); linkField.setSingleLine(true); linkField.setEllipsize(TextUtils.TruncateAt.END);
        linkField.setPadding(dp(16), 0, dp(16), 0);
        linkField.setText(prefs.getString("video", ""));
        lr.addView(linkField, new LinearLayout.LayoutParams(0, dp(38), 1f));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(34)); bp.leftMargin = dp(8);
        lr.addView(pill("Colar", 34, v -> pasteLink()), bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(-2, dp(34)); bp2.leftMargin = dp(6);
        lr.addView(pill("\u2713 Aplicar", 34, v -> applyLink()), bp2);
        panel.addView(lr);

        panel.addView(label("Cor do nome"));
        nameRow = colorRow("name", 0xFFFFCE31);
        panel.addView(nameRow);
        panel.addView(label("Cor da letra"));
        textRow = colorRow("text", 0xFFFFFFFF);
        panel.addView(textRow);

        LinearLayout sliders = new LinearLayout(this); sliders.setOrientation(LinearLayout.HORIZONTAL);
        sliders.addView(sliderBox(true), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(0, -2, 1f); sp2.leftMargin = dp(12);
        sliders.addView(sliderBox(false), sp2);
        panel.addView(sliders);

        LinearLayout act = new LinearLayout(this); act.setOrientation(LinearLayout.HORIZONTAL); act.setGravity(Gravity.CENTER);
        act.setPadding(0, dp(12), 0, 0);
        bShow = pill(ChatService.isRunning() ? "Esconder chat" : "Mostrar chat", 40, v -> toggleChat());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, dp(40)); ap.setMargins(dp(5), 0, dp(5), 0);
        act.addView(bShow, ap);
        act.addView(pill("Mover chat", 40, v -> moveChat()), new LinearLayout.LayoutParams(ap));
        panel.addView(act);

        LinearLayout foot = new LinearLayout(this); foot.setGravity(Gravity.END);
        TextView close = new TextView(this);
        close.setText("FECHAR"); close.setTextColor(TEAL); close.setTextSize(15); close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setPadding(dp(14), dp(10), dp(4), dp(8)); close.setOnClickListener(v -> finish());
        foot.addView(close);
        panel.addView(foot);

        ScrollView sv = new ScrollView(this);
        sv.addView(panel);
        root.addView(sv, new FrameLayout.LayoutParams((int) (getResources().getDisplayMetrics().widthPixels * 0.58f), -2, Gravity.CENTER));
        setContentView(root);
    }

    private LinearLayout colorRow(final String key, int def) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL);
        paintColors(r, key, def);
        return r;
    }

    private void paintColors(final LinearLayout r, final String key, final int def) {
        r.removeAllViews();
        int cur = prefs.getInt(key, def);
        for (final int c : COLORS) {
            View dot = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL); g.setColor(c);
            boolean sel = (c & 0xFFFFFF) == (cur & 0xFFFFFF);
            g.setStroke(dp(sel ? 3 : 1), sel ? TEAL : 0xFF8A8A8A);
            dot.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(28), dp(28)); lp.rightMargin = dp(10);
            dot.setLayoutParams(lp);
            dot.setOnClickListener(v -> { prefs.edit().putInt(key, c).apply(); paintColors(r, key, def); restyle(); });
            r.addView(dot);
        }
    }

    private LinearLayout sliderBox(final boolean isSize) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        TextView l = label(isSize ? "Tamanho da letra: " + ChatService.textSize(this) : "Fundo do chat: " + ChatService.bgPercent(this) + "%");
        box.addView(l);
        SeekBar sb = new SeekBar(this);
        if (isSize) { sizeLabel = l; sb.setMax(22); sb.setProgress(ChatService.textSize(this) - 10); }
        else { bgLabel = l; sb.setMax(100); sb.setProgress(ChatService.bgPercent(this)); }
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (isSize) { prefs.edit().putInt("size", 10 + p).apply(); sizeLabel.setText("Tamanho da letra: " + (10 + p)); }
                else { prefs.edit().putInt("bg", p).apply(); bgLabel.setText("Fundo do chat: " + p + "%"); }
                restyle();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(sb);
        return box;
    }

    private void restyle() {
        if (ChatService.isRunning()) startService(new Intent(this, ChatService.class).setAction(ChatService.ACTION_STYLE));
    }

    private void pasteLink() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        String t = "";
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence c = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (c != null) t = c.toString().trim();
        }
        if (t.isEmpty()) { Toast.makeText(this, "Copie primeiro o link da live.", Toast.LENGTH_SHORT).show(); return; }
        linkField.setText(t);
        applyLink();
    }

    private void applyLink() {
        String id = ChatService.parseVideoId(linkField.getText().toString());
        if (id.isEmpty()) { Toast.makeText(this, "Link inválido. Use o link da live ou o ID de 11 caracteres.", Toast.LENGTH_LONG).show(); return; }
        prefs.edit().putString("video", id).apply();
        Toast.makeText(this, "Live definida.", Toast.LENGTH_SHORT).show();
        if (ChatService.isRunning()) startService(new Intent(this, ChatService.class).setAction(ChatService.ACTION_SHOW));
    }

    private boolean ensureOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return true;
        Toast.makeText(this, "Permita \"exibir sobre outros apps\" para o VTuber Lab.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        return false;
    }

    private void toggleChat() {
        if (ChatService.isRunning()) {
            startService(new Intent(this, ChatService.class).setAction(ChatService.ACTION_HIDE));
            bShow.setText("Mostrar chat");
        } else {
            if (!ensureOverlayPermission()) return;
            applyLinkQuiet();
            startService(new Intent(this, ChatService.class).setAction(ChatService.ACTION_SHOW));
            bShow.setText("Esconder chat");
        }
    }

    private void applyLinkQuiet() {
        String id = ChatService.parseVideoId(linkField.getText().toString());
        if (!id.isEmpty()) prefs.edit().putString("video", id).apply();
    }

    private void moveChat() {
        if (!ensureOverlayPermission()) return;
        applyLinkQuiet();
        if (!ChatService.isRunning()) startService(new Intent(this, ChatService.class).setAction(ChatService.ACTION_SHOW));
        startService(new Intent(this, ChatService.class).setAction(ChatService.ACTION_EDIT));
        Toast.makeText(this, "Arraste pela barra amarela e toque em Pronto.", Toast.LENGTH_LONG).show();
        finish();
    }
}
