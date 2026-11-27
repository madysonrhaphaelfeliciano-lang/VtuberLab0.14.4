package com.vtuber.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.sources.audio.MicrophoneSource;
import com.pedro.encoder.input.sources.video.ScreenSource;
import com.pedro.library.rtmp.RtmpStream;

/**
 * Live no YouTube (RTMPS) com a tela inteira do celular (MediaProjection) + microfone.
 *
 * Ciclo: serviço em primeiro plano -> MediaProjection -> conecta enviando uma tela preta
 * "Esperando conexão..." -> ao conectar entra a captura real da tela, sem espera (o app mostra a sala de espera sem limite de tempo).
 * Tela do celular apagada = a transmissão mostra a sala de espera; ao acender, volta a captura da tela.
 * Limites: no máximo 1280x720, 30 FPS, H.264 + AAC. Parar: ação "Parar" da notificação.
 *
 * A chave de transmissão é lida do SecureStore aqui dentro e nunca é registrada em log nem passada por Intent.
 */
public class LiveService extends Service implements ConnectChecker {

    static final String ACTION_START = "com.vtuber.overlay.LIVE_START";
    static final String ACTION_STOP = "com.vtuber.overlay.LIVE_STOP";
    static final String ACTION_MUTE = "com.vtuber.overlay.LIVE_MUTE";
    static final String ACTION_ROOM = "com.vtuber.overlay.LIVE_ROOM";

    private static final String CHANNEL_ID = "vtuber_live";
    private static final int NOTIF_ID = 3;

    private static final int MAX_LONG = 1280;        // 720p: 1280x720
    private static final int MAX_SHORT = 720;
    private static final int FPS = 30;
    private static final int VIDEO_BITRATE = 4_000_000; // recomendado pelo YouTube para 720p30 (H.264)
    private static final int AUDIO_BITRATE = 128_000;
    private static final int AUDIO_RATE = 44_100;
    private static final int RETRIES = 5;

    private static volatile boolean live = false;
    static boolean isLive() { return live; }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private RtmpStream stream;
    private MediaProjection projection;
    private boolean stopping = false;
    private boolean onScreen = false;
    private boolean connected = false;
    private boolean screenOff = false;
    private boolean muted = false;
    private String lastText = "";
    private PowerManager.WakeLock wakeLock;
    private BroadcastReceiver screenReceiver;

    private final Runnable switchToScreen = new Runnable() {
        @Override
        public void run() {
            if (stopping || stream == null || projection == null || onScreen || screenOff) return;
            try {
                stream.changeVideoSource(new ScreenSource(getApplicationContext(), projection));
                onScreen = true;
                notifyText("Transmitindo a tela. Use Parar para encerrar.");
            } catch (Exception e) {
                stopLive("Não consegui capturar a tela: " + Friendly.why(e));
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_STOP.equals(intent.getAction())) {
            if (live) stopLive(null); else stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_MUTE.equals(intent.getAction())) {
            if (live && stream != null) toggleMute(); else if (!live) stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_ROOM.equals(intent.getAction())) {
            if (live) cycleRoom(); else stopSelf();
            return START_NOT_STICKY;
        }
        if (live) return START_NOT_STICKY;

        int code = intent.getIntExtra("code", 0);
        Intent data = (Intent) intent.getParcelableExtra("data");
        boolean landscape = intent.getBooleanExtra("landscape", true);

        live = true;
        stopping = false;
        onScreen = false;
        connected = false;
        muted = false;
        goForeground();   // precisa vir ANTES de getMediaProjection (Android 14+)
        try {
            begin(code, data, landscape);
        } catch (Throwable t) {
            stopLive("Não foi possível iniciar a Live. " + Friendly.liveStart(t));
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String text) {
        // "Parar" da notificação abre uma confirmação antes de encerrar a transmissão
        PendingIntent stopPi = PendingIntent.getActivity(this, 35, new Intent(this, ConfirmStopActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent openPi = PendingIntent.getActivity(this, 32, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stop = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Parar", stopPi).build();
        PendingIntent mutePi = PendingIntent.getService(this, 33, new Intent(this, LiveService.class).setAction(ACTION_MUTE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action mute = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_lock_silent_mode), muted ? "Ativar mic" : "Mutar mic", mutePi).build();
        PendingIntent roomPi = PendingIntent.getService(this, 34, new Intent(this, LiveService.class).setAction(ACTION_ROOM),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action roomA = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_next), "Sala \u25B6", roomPi).build();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("VTuber Lab")
                .setContentText(text)
                .setContentIntent(openPi)
                .addAction(stop)
                .addAction(mute)
                .addAction(roomA)
                .setOngoing(true)
                .build();
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Transmissão ao vivo", NotificationManager.IMPORTANCE_LOW));
        Notification n = buildNotification("Conectando ao YouTube...");
        if (Build.VERSION.SDK_INT >= 29) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (Build.VERSION.SDK_INT >= 30) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(NOTIF_ID, n, type);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void notifyText(String text) {
        lastText = text;
        try {
            getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(text));
        } catch (Exception ignored) { }
    }

    private void begin(int code, Intent data, boolean landscape) throws Exception {
        if (data == null) throw new Exception("permissão de captura ausente");
        String endpoint = SecureStore.endpoint(this);
        if (endpoint == null) throw new Exception("configure o servidor e a chave de transmissão");

        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(code, data);
        if (projection == null) throw new Exception("captura de tela não autorizada");
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                handler.post(new Runnable() {
                    @Override public void run() { if (!stopping) stopLive("A captura de tela foi encerrada."); }
                });
            }
        }, handler);

        // Tamanho: o mesmo formato da tela, dentro de 1280x720 (nunca acima de 720p), múltiplo de 8.
        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
        int longSide = Math.max(m.widthPixels, m.heightPixels), shortSide = Math.min(m.widthPixels, m.heightPixels);
        float k = Math.min(1f, Math.min(MAX_LONG / (float) longSide, MAX_SHORT / (float) shortSide));
        int a = Math.max(16, ((int) (longSide * k)) & ~7), b = Math.max(16, ((int) (shortSide * k)) & ~7);
        int rotation = landscape ? 0 : 90; // (a x b) com rotação 90 = retrato b x a

        stream = new RtmpStream(getApplicationContext(), this, new WaitingSource("Esperando conexão..."),
                new MicrophoneSource(MediaRecorder.AudioSource.MIC));
        // A captura de tela só gera quadros quando a imagem muda; isto garante um mínimo de quadros constantes.
        stream.getGlInterface().setForceRender(true, 15);

        boolean ok = stream.prepareVideo(a, b, VIDEO_BITRATE, FPS, 2, rotation)
                && stream.prepareAudio(AUDIO_RATE, true, AUDIO_BITRATE, false, false);
        if (!ok) throw new Exception("este aparelho não conseguiu iniciar o codificador H.264/AAC");

        stream.getStreamClient().setReTries(RETRIES);
        stream.startStream(endpoint);
        startScreenWatch();
        acquireWake();
        // Ao começar a Live, o app mostra a sala de espera (sem limite de tempo). Ao terminar, volta a sala anterior.
        if (AppSettings.waitingOnLive(this)) {
            SharedPreferences sp = getSharedPreferences("vtuber", MODE_PRIVATE);
            sp.edit().putString("room_before_live", sp.getString("room", "normal")).putString("room", "wait").apply();
        }
    }

    // ---------- Tela apagada = sala de espera ----------
    private void startScreenWatch() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        screenOff = pm != null && !pm.isInteractive();
        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(a)) onScreenOff();
                else if (Intent.ACTION_SCREEN_ON.equals(a)) onScreenOn();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenReceiver, f);
    }

    private void onScreenOff() {
        screenOff = true;
        handler.post(new Runnable() {
            @Override public void run() {
                if (stopping || stream == null) return;
                try {
                    stream.changeVideoSource(new WaitingSource("Sala de espera"));
                    onScreen = false;
                    notifyText("Tela apagada: transmitindo a sala de espera.");
                } catch (Exception ignored) { }
            }
        });
    }

    private void onScreenOn() {
        screenOff = false;
        handler.post(new Runnable() {
            @Override public void run() {
                if (stopping || stream == null || projection == null || !connected) return;
                try {
                    stream.changeVideoSource(new ScreenSource(getApplicationContext(), projection));
                    onScreen = true;
                    notifyText("Transmitindo a tela. Use Parar para encerrar.");
                } catch (Exception e) {
                    stopLive("Não consegui voltar a captura da tela: " + Friendly.why(e));
                }
            }
        });
    }

    private void acquireWake() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vtuber:live");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(6 * 60 * 60 * 1000L);   // no máximo 6 h
        } catch (Exception ignored) { }
    }

    // ---------- Mutar o microfone (chamada por reflexão: não depende dos nomes da biblioteca) ----------
    private boolean setMicMuted(boolean m) {
        try {
            Object src = stream.getClass().getMethod("getAudioSource").invoke(stream);
            src.getClass().getMethod(m ? "mute" : "unMute").invoke(src);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Atalho da notificação: passa para a próxima sala e traz o app para a frente. */
    private void cycleRoom() {
        String cur = AppSettings.room(this);
        int idx = 0;
        for (int i = 0; i < AppSettings.ROOM_IDS.length; i++) if (AppSettings.ROOM_IDS[i].equals(cur)) idx = i;
        String next = AppSettings.ROOM_IDS[(idx + 1) % AppSettings.ROOM_IDS.length];
        AppSettings.setRoom(this, next);
        Toast.makeText(this, AppSettings.roomName(next), Toast.LENGTH_SHORT).show();
        try {
            startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (Exception ignored) { }
    }

    private void toggleMute() {
        boolean want = !muted;
        if (setMicMuted(want)) {
            muted = want;
            Toast.makeText(this, want ? "Microfone mutado." : "Microfone ativado.", Toast.LENGTH_SHORT).show();
            notifyText(lastText);
        } else {
            Toast.makeText(this, "Não foi possível mutar o microfone neste aparelho.", Toast.LENGTH_LONG).show();
        }
    }

    /** Encerra tudo (idempotente). msg != null mostra o motivo ao usuário. */
    private void stopLive(String msg) {
        if (stopping) return;
        stopping = true;
        live = false;
        cleanup();
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        stopForeground(true);
        stopSelf();
    }

    private void cleanup() {
        handler.removeCallbacksAndMessages(null);
        if (screenReceiver != null) {
            try { unregisterReceiver(screenReceiver); } catch (Exception ignored) { }
            screenReceiver = null;
        }
        if (wakeLock != null) {
            try { if (wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
            wakeLock = null;
        }
        try {
            SharedPreferences sp = getSharedPreferences("vtuber", MODE_PRIVATE);
            String old = sp.getString("room_before_live", null);
            if (old != null) sp.edit().putString("room", old).remove("room_before_live").apply();
        } catch (Exception ignored) { }
        RtmpStream s = stream;
        stream = null;
        if (s != null) {
            try { s.release(); } catch (Exception ignored) { }   // para a transmissão, as fontes e os encoders
        }
        MediaProjection p = projection;
        projection = null;
        if (p != null) {
            try { p.stop(); } catch (Exception ignored) { }
        }
    }

    // ---------- ConnectChecker (nunca registrar a URL: ela contém a chave) ----------
    @Override public void onConnectionStarted(String url) { }

    @Override
    public void onConnectionSuccess() {
        handler.post(new Runnable() {
            @Override public void run() {
                connected = true;
                if (stopping || onScreen || screenOff) return;
                switchToScreen.run();
            }
        });
    }

    @Override
    public void onConnectionFailed(final String reason) {
        handler.post(new Runnable() {
            @Override public void run() {
                if (stopping || stream == null) return;
                handler.removeCallbacks(switchToScreen);
                boolean retrying = false;
                try { retrying = stream.getStreamClient().reTry(3000L, reason, null); } catch (Exception ignored) { }
                if (retrying) notifyText("Conexão perdida. Reconectando...");
                else stopLive(reason != null && reason.contains("Endpoint malformed")
                        ? "O endereço do servidor da live está inválido. Confira o endereço."
                        : Friendly.live(reason));
            }
        });
    }

    @Override
    public void onAuthError() {
        handler.post(new Runnable() {
            @Override public void run() { stopLive("O servidor recusou a autenticação. Confira o endereço e a chave."); }
        });
    }

    @Override public void onAuthSuccess() { }
    @Override public void onDisconnect() { }

    @Override
    public void onDestroy() {
        stopping = true;
        live = false;
        cleanup();
        super.onDestroy();
    }
}
