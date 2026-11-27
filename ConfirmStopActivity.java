package com.vtuber.overlay;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

/** Confirmação para o botão "Parar" da notificação da Live (evita encerrar a transmissão sem querer). */
public class ConfirmStopActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!LiveService.isLive()) { finish(); return; }
        new AlertDialog.Builder(this)
            .setTitle("Encerrar a live?")
            .setMessage("A transmissão para os espectadores vai parar agora. Para voltar ao ar, você precisará iniciar a live de novo.")
            .setPositiveButton("Encerrar live", (d, w) -> {
                startService(new Intent(this, LiveService.class).setAction(LiveService.ACTION_STOP));
                finish();
            })
            .setNegativeButton("Continuar no ar", (d, w) -> finish())
            .setOnCancelListener(d -> finish())
            .show();
    }
}
