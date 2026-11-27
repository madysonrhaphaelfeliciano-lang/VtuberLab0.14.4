package com.vtuber.overlay;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Surface;

import com.pedro.encoder.input.sources.OrientationConfig;
import com.pedro.encoder.input.sources.OrientationForced;
import com.pedro.encoder.input.sources.video.VideoSource;

/**
 * Fonte de vídeo da "sala de espera" da Live: tela preta simples com um texto centralizado.
 * Segue a mesma convenção de orientação/tamanho do ScreenSource do RootEncoder, para a troca
 * para a captura real de tela ser direta (sem pulo de proporção).
 */
final class WaitingSource extends VideoSource {

    private final String text;
    private volatile boolean running = false;
    private Thread thread;
    private Surface surface;

    WaitingSource(String text) {
        this.text = text;
    }

    @Override
    protected boolean create(int width, int height, int fps, int rotation) {
        return true;
    }

    private boolean rotated() {
        return getRotation() == 90 || getRotation() == 270;
    }

    @Override
    public void start(SurfaceTexture surfaceTexture) {
        setSurfaceTexture(surfaceTexture);
        if (running) return;
        final int dw = rotated() ? getHeight() : getWidth();
        final int dh = rotated() ? getWidth() : getHeight();
        surfaceTexture.setDefaultBufferSize(dw, dh);
        final Surface s = new Surface(surfaceTexture);
        surface = s;
        final long frameMs = Math.max(1, 1000 / Math.max(1, getFps()));
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(Color.WHITE);
                p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextAlign(Paint.Align.CENTER);
                p.setTextSize(Math.min(dw * 0.06f, dh * 0.10f));
                float baseline = dh / 2f - (p.ascent() + p.descent()) / 2f;
                while (running) {
                    long t0 = SystemClock.uptimeMillis();
                    try {
                        Canvas c = s.lockCanvas(null);
                        try {
                            c.drawColor(Color.BLACK);
                            c.drawText(text, dw / 2f, baseline, p);
                        } finally {
                            s.unlockCanvasAndPost(c);
                        }
                    } catch (Exception ignored) {
                    }
                    long left = frameMs - (SystemClock.uptimeMillis() - t0);
                    if (left > 0) {
                        try { Thread.sleep(left); } catch (InterruptedException e) { return; }
                    }
                }
            }
        }, "VTuberWaitingSource");
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        try { if (t != null) t.join(300); } catch (InterruptedException ignored) { }
        Surface s = surface;
        surface = null;
        if (s != null) s.release();
        try { if (t != null && t.isAlive()) { t.interrupt(); t.join(300); } } catch (InterruptedException ignored) { }
    }

    @Override
    public void release() { }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public OrientationConfig getOrientationConfig() {
        boolean portrait = rotated() != (getHeight() > getWidth());
        return new OrientationConfig(0, portrait, OrientationForced.NONE);
    }
}
