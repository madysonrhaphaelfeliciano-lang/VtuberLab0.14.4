package com.vtuber.overlay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Collab — janela (mesmo visual do diálogo "Configurações do modelo").
 * A conexão vive no CollabService: fechar esta janela NÃO derruba a conexão (use ENCERRAR).
 * Anfitrião: Criar sala > copiar o código > o amigo devolve a resposta > colar o código no campo e tocar na seta. Convidado: Entrar Sala > colar o código do anfitrião e tocar na seta > copiar a resposta e devolver.
 * Depois de conectar, o convidado envia o modelo; o anfitrião vê "Você aceita (nome do modelo) na colaboração?".
 */
public class CollabActivity extends Activity implements CollabService.Listener {
    private static final int PANEL = 0xFF15171B, CARD = 0xFF1E2126, BORDER = 0xFF343840, FIELD = 0xFF0C0D0F, FIELD2 = 0xFF1A1C20,
        YELLOW = 0xFFFFD43B, MUTED = 0xFFA6A8B3, HINT = 0xFF7A7D85, WHITE = 0xFFFFFFFF;
    private TextView status, codeField, pasteLabel, endBtn;
    private EditText pasteField;
    private ImageView wifi;
    private LinearLayout statusRow;
    private SharedPreferences prefs;
    private AlertDialog helloDialog;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("collab", MODE_PRIVATE);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 7);
        buildUi();
        CollabService.send(this, CollabService.A_INIT, null);
    }

    @Override protected void onResume() { super.onResume(); CollabService.listener = this; onChanged(); }
    @Override protected void onPause() { super.onPause(); if (CollabService.listener == this) CollabService.listener = null; }

    // ---------- Interface ----------
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radiusDp)); return g;
    }

    private GradientDrawable box(int fill, int radiusDp, int stroke) {
        GradientDrawable g = rounded(fill, radiusDp); if (stroke != 0) g.setStroke(dp(1), stroke); return g;
    }

    private void pressFx(View v) {
        v.setOnTouchListener((x, e) -> {
            int a = e.getAction();
            if (a == MotionEvent.ACTION_DOWN) x.setAlpha(0.7f);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) x.setAlpha(1f);
            return false;
        });
    }

    /** Botão grande amarelo com ícone + texto (Criar Sala / Entrar Sala). */
    private LinearLayout pill(int icon, String text, View.OnClickListener l) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL); p.setGravity(Gravity.CENTER);
        p.setBackground(rounded(YELLOW, 26)); p.setPadding(dp(10), 0, dp(10), 0);
        p.setClickable(true); p.setOnClickListener(l); pressFx(p);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        p.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView t = new TextView(this);
        t.setText(text); t.setTextColor(0xFF121215); t.setTextSize(16); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setSingleLine(true); t.setPadding(dp(8), 0, 0, 0);
        p.addView(t);
        return p;
    }

    private View divider() {
        View v = new View(this); v.setBackgroundColor(YELLOW);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(2)); lp.setMargins(0, dp(12), 0, dp(12));
        v.setLayoutParams(lp); return v;
    }

    /** Cartão escuro com cabeçalho (ícone + título); o chamador põe o resto dentro. */
    private LinearLayout card(int icon, String title, boolean isFriend) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL); c.setBackground(box(CARD, 22, BORDER));
        c.setPadding(dp(12), dp(10), dp(12), dp(12));
        LinearLayout h = new LinearLayout(this); h.setOrientation(LinearLayout.HORIZONTAL); h.setGravity(Gravity.CENTER_VERTICAL);
        ImageView iv = new ImageView(this); iv.setImageResource(icon); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        h.addView(iv, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView t = new TextView(this); t.setText(title); t.setTextColor(WHITE); t.setTextSize(15); t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setSingleLine(true); t.setPadding(dp(8), 0, 0, 0);
        h.addView(t);
        if (isFriend) pasteLabel = t;
        c.addView(h);
        return c;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x00000000);
        root.setOnClickListener(v -> finish());

        final int pw = Math.min((int) (getResources().getDisplayMetrics().widthPixels * 0.94f), dp(560));
        final boolean wide = pw >= dp(440);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(box(PANEL, 28, BORDER));
        panel.setElevation(dp(10));
        panel.setPadding(dp(16), dp(16), dp(16), dp(10));
        panel.setClickable(true);

        // Cabeçalho: pessoas + título + subtítulo
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL); head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView people = new ImageView(this); people.setImageResource(R.drawable.ui_cb_people); people.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int ps = dp(wide ? 76 : 60);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ps, ps); pp.rightMargin = dp(14);
        head.addView(people, pp);
        LinearLayout tx = new LinearLayout(this); tx.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Colabore com amigos!"); title.setTextColor(WHITE); title.setTextSize(wide ? 24 : 19); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        // Toque longo no título: liga/desliga o STUN. Fica LIGADO por padrão.
        title.setOnLongClickListener(v -> {
            boolean on = !AppSettings.stun(this);
            prefs.edit().putBoolean("stun", on).apply();
            Toast.makeText(this, on ? "STUN: ligado" : "STUN: desligado (só IPv6/mesma Wi-Fi)", Toast.LENGTH_SHORT).show();
            return true;
        });
        tx.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Crie ou entre em uma sala para se conectar."); sub.setTextColor(MUTED); sub.setTextSize(wide ? 14 : 12);
        sub.setPadding(0, dp(3), 0, 0);
        tx.addView(sub);
        head.addView(tx, new LinearLayout.LayoutParams(0, -2, 1f));
        panel.addView(head);
        panel.addView(divider());

        // Criar Sala / Entrar Sala
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout bHost = pill(R.drawable.ui_cb_plus, "Criar Sala", v -> startOrConfirm(CollabService.A_HOST));
        LinearLayout bJoin = pill(R.drawable.ui_cb_enter, "Entrar Sala", v -> startOrConfirm(CollabService.A_GUEST));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(52), 1f); l1.rightMargin = dp(6);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(52), 1f); l2.leftMargin = dp(6);
        top.addView(bHost, l1); top.addView(bJoin, l2);
        panel.addView(top);

        // Seu Código (+ copiar)
        LinearLayout cCode = card(R.drawable.ui_cb_ticket, "Seu Código", false);
        LinearLayout codeRow = new LinearLayout(this); codeRow.setOrientation(LinearLayout.HORIZONTAL); codeRow.setGravity(Gravity.CENTER_VERTICAL);
        codeField = new TextView(this);
        codeField.setText("---"); codeField.setTextColor(WHITE); codeField.setTextSize(14); codeField.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        codeField.setSingleLine(true); codeField.setEllipsize(TextUtils.TruncateAt.END); codeField.setGravity(Gravity.CENTER);
        codeField.setBackground(box(FIELD, 16, BORDER)); codeField.setPadding(dp(12), 0, dp(12), 0);
        codeRow.addView(codeField, new LinearLayout.LayoutParams(0, dp(44), 1f));
        ImageView copy = new ImageView(this); copy.setImageResource(R.drawable.ui_cb_copy); copy.setScaleType(ImageView.ScaleType.FIT_CENTER);
        copy.setPadding(dp(8), dp(8), dp(8), dp(8)); copy.setClickable(true); copy.setOnClickListener(v -> copyCode()); pressFx(copy);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(44), dp(44)); cp.leftMargin = dp(6);
        codeRow.addView(copy, cp);
        LinearLayout.LayoutParams rp1 = new LinearLayout.LayoutParams(-1, -2); rp1.topMargin = dp(8);
        cCode.addView(codeRow, rp1);

        // Código do Amigo (+ enviar)
        LinearLayout cFriend = card(R.drawable.ui_cb_useradd, "Código do Amigo", true);
        LinearLayout friendRow = new LinearLayout(this); friendRow.setOrientation(LinearLayout.HORIZONTAL); friendRow.setGravity(Gravity.CENTER_VERTICAL);
        pasteField = new EditText(this);
        pasteField.setHint("Digite o código..."); pasteField.setHintTextColor(HINT); pasteField.setTextColor(WHITE); pasteField.setTextSize(13);
        pasteField.setSingleLine(true); pasteField.setEllipsize(TextUtils.TruncateAt.END);
        pasteField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        pasteField.setBackground(box(FIELD2, 16, BORDER)); pasteField.setPadding(dp(14), 0, dp(14), 0);
        friendRow.addView(pasteField, new LinearLayout.LayoutParams(0, dp(44), 1f));
        ImageView go = new ImageView(this); go.setImageResource(R.drawable.ui_cb_go); go.setScaleType(ImageView.ScaleType.FIT_CENTER);
        go.setBackground(rounded(YELLOW, 14)); go.setPadding(dp(9), dp(9), dp(9), dp(9)); go.setClickable(true); go.setOnClickListener(v -> submitFriendCode()); pressFx(go);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(44), dp(44)); gp.leftMargin = dp(6);
        friendRow.addView(go, gp);
        LinearLayout.LayoutParams rp2 = new LinearLayout.LayoutParams(-1, -2); rp2.topMargin = dp(8);
        cFriend.addView(friendRow, rp2);

        // Lado a lado quando cabe; um embaixo do outro no retrato estreito
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        LinearLayout.LayoutParams c1 = wide ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-1, -2);
        LinearLayout.LayoutParams c2 = wide ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-1, -2);
        if (wide) { c1.rightMargin = dp(6); c2.leftMargin = dp(6); } else c2.topMargin = dp(10);
        cards.addView(cCode, c1); cards.addView(cFriend, c2);
        LinearLayout.LayoutParams cardsP = new LinearLayout.LayoutParams(-1, -2); cardsP.topMargin = dp(10);
        panel.addView(cards, cardsP);

        // Estado da conexão (aparece só quando há o que mostrar) + ENCERRAR
        statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL); statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(6), dp(10), dp(4), 0);
        wifi = new ImageView(this); wifi.setImageResource(R.drawable.ui_cb_wifi); wifi.setVisibility(View.GONE);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(dp(20), dp(20)); wp.rightMargin = dp(8);
        statusRow.addView(wifi, wp);
        status = new TextView(this); status.setTextSize(12); status.setTextColor(WHITE); status.setMaxLines(4);
        statusRow.addView(status, new LinearLayout.LayoutParams(0, -2, 1f));
        endBtn = new TextView(this);
        endBtn.setText("ENCERRAR"); endBtn.setTextColor(0xFFFF9F9F); endBtn.setTextSize(13); endBtn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        endBtn.setPadding(dp(12), dp(8), dp(8), dp(8));
        endBtn.setOnClickListener(v -> askLeave());
        statusRow.addView(endBtn);
        panel.addView(statusRow);

        panel.addView(divider());

        // Rodapé: ← Sair
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL); foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(dp(6), dp(2), dp(6), dp(2)); foot.setClickable(true); foot.setOnClickListener(v -> finish()); pressFx(foot);
        ImageView back = new ImageView(this); back.setImageResource(R.drawable.ui_cb_back); back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        foot.addView(back, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView exit = new TextView(this);
        exit.setText("Sair"); exit.setTextColor(WHITE); exit.setTextSize(17); exit.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        exit.setPadding(dp(12), dp(8), dp(8), dp(8));
        foot.addView(exit);
        panel.addView(foot, new LinearLayout.LayoutParams(-2, -2));

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(panel);
        root.addView(sv, new FrameLayout.LayoutParams(pw, -2, Gravity.CENTER));
        setContentView(root);
    }

    // ---------- Estado vindo do serviço ----------
    @Override public void onChanged() {
        if (status == null) return;
        status.setText(CollabService.status);
        status.setTextColor(CollabService.connectedOk ? YELLOW : WHITE);
        wifi.setVisibility(CollabService.connectedOk ? View.VISIBLE : View.GONE);
        codeField.setText(CollabService.code.isEmpty() ? "---" : CollabService.code);
        pasteLabel.setText(CollabService.role == 2 ? "Código do Anfitrião" : CollabService.role == 1 ? "Resposta do Amigo" : "Código do Amigo");
        endBtn.setVisibility(CollabService.role != 0 ? View.VISIBLE : View.GONE);
        boolean hasStatus = CollabService.status != null && !CollabService.status.isEmpty();
        // o aviso padrão ("Crie uma sala...") repete o subtítulo: só aparece quando há conexão em andamento ou uma mensagem nova
        statusRow.setVisibility(CollabService.role != 0 || (hasStatus && !CollabService.status.startsWith("Crie uma sala")) ? View.VISIBLE : View.GONE);
        if (CollabService.pendingHello != null && helloDialog == null) askAccept(CollabService.pendingHello);
    }

    private void confirm(String title, String msg, String yes, final Runnable go) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton(yes, (d, w) -> go.run())
            .setNegativeButton("Continuar aqui", null).show();
    }

    /** Criar/entrar numa sala nova enquanto já está conectado desfaz a conexão atual: pede confirmação. */
    private void startOrConfirm(final String action) {
        if (CollabService.role != 0 && CollabService.mode == 3)
            confirm("Sair da sala atual?", "Você já está conectado com um amigo. Começar outra sala desfaz essa conexão e o modelo dele sai da tela.", "Sair e continuar",
                () -> CollabService.send(this, action, null));
        else CollabService.send(this, action, null);
    }

    private void askLeave() {
        final Runnable close = () -> CollabService.send(this, CollabService.A_CLOSE, null);
        if (CollabService.mode == 3)
            confirm("Sair da sala?", "A conexão com o seu amigo será desfeita e o modelo dele sai da tela. Para voltar, vocês terão que trocar os códigos de novo.", "Sair da sala", close);
        else
            confirm("Sair da sala?", "A sala e os códigos que você já gerou serão descartados.", "Sair da sala", close);
    }

    private void askAccept(String name) {
        helloDialog = new AlertDialog.Builder(this)
            .setTitle("Colaboração")
            .setMessage("Você aceita \"" + name + "\" na colaboração?")
            .setCancelable(false)
            .setPositiveButton("Aceitar", (d, w) -> { helloDialog = null; CollabService.answer(this, true); })
            .setNegativeButton("Recusar", (d, w) -> { helloDialog = null; CollabService.answer(this, false); })
            .show();
    }

    private void copyCode() {
        if (CollabService.code.isEmpty()) { Toast.makeText(this, "Ainda não há código para copiar.", Toast.LENGTH_SHORT).show(); return; }
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("VTuber Lab", CollabService.code));
        Toast.makeText(this, "Código copiado. Mande para o seu amigo.", Toast.LENGTH_SHORT).show();
    }

    /** Seta: usa o que está digitado no campo; se estiver vazio, pega o código copiado (como o antigo "Colar"). */
    private void submitFriendCode() {
        String t = pasteField.getText().toString();
        if (t.trim().isEmpty()) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence c = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (c != null) t = c.toString();
            }
        }
        sendFriendCode(t);
    }

    private void sendFriendCode(String raw) {
        if (CollabService.role == 0) { Toast.makeText(this, "Toque em Criar Sala ou Entrar Sala primeiro.", Toast.LENGTH_SHORT).show(); return; }
        String t = raw == null ? "" : raw.trim().replaceAll("\\s+", "");
        int i = t.indexOf("VL1:");
        if (i < 0) { Toast.makeText(this, "Cole o código do VTuber Lab (começa com VL1:).", Toast.LENGTH_LONG).show(); return; }
        t = t.substring(i);
        pasteField.setText(t);
        CollabService.send(this, CollabService.A_PASTE, t);
    }

    @Override protected void onDestroy() {
        if (CollabService.listener == this) CollabService.listener = null;
        if (helloDialog != null) { try { helloDialog.dismiss(); } catch (Exception ignored) { } helloDialog = null; }
        super.onDestroy();
    }
}
