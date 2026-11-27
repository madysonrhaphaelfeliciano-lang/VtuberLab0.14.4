package com.vtuber.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Grava a tela (com o overlay da Vtuber) + áudio do microfone. Salva em Filmes/VTuberLab. */
public class RecordService extends Service {

    static final String ACTION_STOP = "com.vtuber.overlay.REC_STOP";
    private static final String CHANNEL_ID = "vtuber_rec";
    private static final int NOTIF_ID = 2;

    private static volatile boolean recording = false;
    static boolean isRecording() { return recording; }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay display;
    private MediaRecorder recorder;
    private ParcelFileDescriptor pfd;
    private Uri outUri;
    private File outFile;
    private boolean stopping = false;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            if (recording) stopRecording(); else stopSelf();
            return START_NOT_STICKY;
        }
        if (intent == null || recording) return START_NOT_STICKY;

        int code = intent.getIntExtra("code", 0);
        Intent data = (Intent) intent.getParcelableExtra("data");
        boolean audio = intent.getBooleanExtra("audio", false);
        boolean landscape = intent.getBooleanExtra("landscape", true);

        goForeground(audio);
        try {
            startRecording(code, data, audio, landscape);
        } catch (Exception e) {
            Toast.makeText(this, "Não consegui gravar a tela: " + Friendly.why(e), Toast.LENGTH_LONG).show();
            cleanup(false);
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void goForeground(boolean audio) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Gravação de tela", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stopPi = PendingIntent.getService(this, 11, new Intent(this, RecordService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent openPi = PendingIntent.getActivity(this, 12, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stop = new Notification.Action.Builder(
                Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Parar", stopPi).build();
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Gravando a tela")
                .setContentText(audio ? "Com áudio do microfone. Use Parar para salvar." : "Sem áudio. Use Parar para salvar.")
                .setContentIntent(openPi)
                .addAction(stop)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            if (audio && Build.VERSION.SDK_INT >= 30) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(NOTIF_ID, n, type);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void startRecording(int code, Intent data, boolean audio, boolean landscape) throws Exception {
        if (data == null) throw new Exception("permissão de captura ausente");
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(code, data);
        if (projection == null) throw new Exception("captura não autorizada");
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                handler.post(new Runnable() { @Override public void run() { if (recording) stopRecording(); } });
            }
        }, handler);

        DisplayMetrics m = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(m);
        int a = Math.max(m.widthPixels, m.heightPixels), b = Math.min(m.widthPixels, m.heightPixels);
        float k = a > 1920 ? 1920f / a : 1f;
        a = ((int) (a * k)) & ~1;
        b = ((int) (b * k)) & ~1;
        int w = landscape ? a : b, h = landscape ? b : a;

        recorder = new MediaRecorder();
        if (audio) recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (audio) {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
        }
        recorder.setVideoSize(w, h);
        recorder.setVideoFrameRate(30);
        recorder.setVideoEncodingBitRate(Math.min(12000000, w * h * 5));

        String name = "VTuberLab_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Video.Media.DISPLAY_NAME, name);
            cv.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            cv.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VTuberLab");
            cv.put(MediaStore.Video.Media.IS_PENDING, 1);
            outUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (outUri == null) throw new Exception("não foi possível criar o arquivo");
            pfd = getContentResolver().openFileDescriptor(outUri, "w");
            recorder.setOutputFile(pfd.getFileDescriptor());
        } else {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VTuberLab");
            dir.mkdirs();
            outFile = new File(dir, name);
            recorder.setOutputFile(outFile.getAbsolutePath());
        }
        recorder.prepare();
        display = projection.createVirtualDisplay("VTuberRec", w, h, m.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.getSurface(), null, handler);
        recorder.start();
        recording = true;
    }

    private void stopRecording() {
        if (stopping) return;
        stopping = true;
        boolean ok = true;
        recording = false;
        try { recorder.stop(); } catch (Exception e) { ok = false; }
        cleanup(ok);
        if (ok) Toast.makeText(this, Build.VERSION.SDK_INT >= 29 ? "Gravação salva em Filmes/VTuberLab" : "Gravação salva (Android/data/com.vtuber.overlay/files/Movies)", Toast.LENGTH_LONG).show();
        else Toast.makeText(this, "Gravação muito curta, descartada.", Toast.LENGTH_SHORT).show();
        stopForeground(true);
        stopSelf();
    }

    private void cleanup(boolean keep) {
        recording = false;
        try { if (display != null) display.release(); } catch (Exception ignored) { }
        try { if (recorder != null) { recorder.reset(); recorder.release(); } } catch (Exception ignored) { }
        try { if (projection != null) projection.stop(); } catch (Exception ignored) { }
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
        display = null; recorder = null; projection = null; pfd = null;
        if (outUri != null) {
            try {
                if (keep) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Video.Media.IS_PENDING, 0);
                    getContentResolver().update(outUri, cv, null, null);
                } else getContentResolver().delete(outUri, null, null);
            } catch (Exception ignored) { }
            outUri = null;
        }
        if (outFile != null) { if (!keep) outFile.delete(); outFile = null; }
    }

    @Override
    public void onDestroy() {
        if (recording) { try { recorder.stop(); } catch (Exception ignored) { } cleanup(true); }
        super.onDestroy();
    }
}
