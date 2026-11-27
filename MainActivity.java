package com.vtuber.overlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.graphics.PixelFormat;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import java.io.ByteArrayInputStream;
import android.widget.HorizontalScrollView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.view.ViewOutlineProvider;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import android.widget.ScrollView;
import android.media.projection.MediaProjectionManager;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int REQ_PERMISSIONS=10, REQ_OVERLAY=11, REQ_IMPORT=12, REQ_AUDIO=13, REQ_RECORD=14, REQ_RELOAD_PERM=15, REQ_EXPORT=16, REQ_ACCESSORY=17, REQ_SCENERY=18, REQ_LIVE=19, REQ_LIVE_PERM=20;
    private ImageView bgView;
    private android.view.TextureView bgTex;private android.media.MediaPlayer bgMp;private File bgVideoFile;
    private String pendingExportId=null;
    private static final int REQ_PNG_IDLE=30, REQ_PNG_TALK=31;
    private Uri pngIdleUri,pngTalkUri;private ImageView pngIdleThumb,pngTalkThumb;
    private FrameLayout pageRoot;private FrameLayout sideCol;private LinearLayout colCreate,colRec;private View dotA,dotB;private int tab=0;private boolean onboardingShown=false;private boolean transitioning=false;
    private android.view.TextureView trTex;private android.media.MediaPlayer trMp;private SharedPreferences.OnSharedPreferenceChangeListener roomListener;
    private boolean importPng=false;
    private TextView status;
    private View playBtn,recDot;private GradientDrawable recDotBg;private boolean pendingAccBack=false;
    private final ArrayList<WebView> previews=new ArrayList<WebView>();
    private final java.util.HashMap<AlertDialog,Runnable> closers=new java.util.HashMap<AlertDialog,Runnable>();
    private final android.os.Handler ui=new android.os.Handler();
    private LinearLayout root;
    private int yellow=0xFFFFD43B, panel=0xFF1B1B20, muted=0xFFA6A8B3;
    private final int cBg=0xFF121215, cCard=0xFF26262E, cBorder=0xFF34343F, cText=0xFFF8F9FA, cRec=0xFFFF4D5E, cDanger=0xFFFF5C6C;

    @Override protected void onCreate(Bundle b){super.onCreate(b);registerRoomListener();showLoading();root.postDelayed(new Runnable(){public void run(){showDashboard();}},1100);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void showLoading(){
        LinearLayout splash=new LinearLayout(this);splash.setOrientation(1);splash.setGravity(Gravity.CENTER);splash.setBackgroundColor(Color.rgb(8,8,9));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_launcher);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);splash.addView(logo,new LinearLayout.LayoutParams(dp(170),dp(170)));
        TextView name=text("VTuber Lab",29,Color.WHITE,true);name.setGravity(Gravity.CENTER);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.topMargin=dp(18);splash.addView(name,np);
        TextView loading=text("CARREGANDO ESTÚDIO...",12,yellow,true);loading.setGravity(Gravity.CENTER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(24);splash.addView(loading,lp);setContentView(splash);root=splash;
    }
    /** Nova GUI: fundo amarelo com a logo V e botões redondos na esquerda. A Vtuber real é só o overlay. */
    private void showDashboard(){
        int H=Math.min(getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);
        FrameLayout page=new FrameLayout(this);
        page.setBackgroundColor(cBg);
        hideSystemBars();
        liveSetupOpen=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);

        stopBgVideo();
        bgView=new ImageView(this);bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        page.addView(bgView,new FrameLayout.LayoutParams(-1,-1));
        bgTex=new android.view.TextureView(this);bgTex.setVisibility(View.GONE);
        bgTex.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st,int w,int h){startBgVideo();}
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st,int w,int h){try{if(bgMp!=null)fitBgVideo(bgMp.getVideoWidth(),bgMp.getVideoHeight());}catch(Exception ignore){}}
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st){releaseBgPlayer();return true;}
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st){}
        });
        page.addView(bgTex,new FrameLayout.LayoutParams(-1,-1));applyDashboardBg();

        int ih=(int)(H*0.125f),iw=(int)(ih*155f/134f),gap=(int)(H*0.01f);
        // Duas abas (deslize para o lado): criação (modelos, Collab, Comunidade) e gravação (Play/Live, acessório, cenários, chat).
        colCreate=new LinearLayout(this);colCreate.setOrientation(LinearLayout.VERTICAL);colCreate.setGravity(Gravity.CENTER_HORIZONTAL);
        colCreate.addView(iconButton(R.drawable.ui_layers,iw,ih,gap,v->showLayersWindow()));
        colCreate.addView(iconButton(R.drawable.ui_gear,iw,ih,gap,v->showModelSettings()));
        colCreate.addView(iconButton(R.drawable.ui_swap,iw,ih,gap,v->showModelPicker()));
        colCreate.addView(iconButton(R.drawable.ui_collab,iw,ih,gap,v->startActivity(new Intent(this,CollabActivity.class))));
        colCreate.addView(iconButton(R.drawable.ui_group,iw,ih,gap,v->showCommunity()));
        colRec=new LinearLayout(this);colRec.setOrientation(LinearLayout.VERTICAL);colRec.setGravity(Gravity.CENTER_HORIZONTAL);
        playBtn=recordButton(iw);attachLiveHold(playBtn);colRec.addView(playBtn);
        colCreate.setVisibility(tab==0?View.VISIBLE:View.GONE);colRec.setVisibility(tab==1?View.VISIBLE:View.GONE);
        FrameLayout side=new FrameLayout(this);side.addView(colCreate,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));side.addView(colRec,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,-2);cp.gravity=Gravity.CENTER_VERTICAL|Gravity.LEFT;cp.leftMargin=(int)(H*0.035f);
        page.addView(side,cp);
        pageRoot=page;sideCol=side;side.setVisibility(AppSettings.sideButtons(this)?View.VISIBLE:View.GONE);
        LinearLayout dots=new LinearLayout(this);dots.setOrientation(LinearLayout.HORIZONTAL);dotA=tabDot();dotB=tabDot();dots.addView(dotA);dots.addView(dotB);updateTabDots();
        FrameLayout.LayoutParams dp0=new FrameLayout.LayoutParams(-2,-2);dp0.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;dp0.bottomMargin=dp(14);
        page.addView(dots,dp0);
        final float[] dn=new float[2];
        // Dois toques numa área vazia da tela escondem ou mostram os botões laterais (como no VTube Studio).
        final android.view.GestureDetector dbl=new android.view.GestureDetector(this,new android.view.GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDoubleTap(MotionEvent e){toggleSideButtons();return true;}
        });
        page.setOnTouchListener((v,e)->{
            dbl.onTouchEvent(e);
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){dn[0]=e.getX();dn[1]=e.getY();}
            else if(e.getActionMasked()==MotionEvent.ACTION_UP){float dx=e.getX()-dn[0],dy=e.getY()-dn[1];
                if(Math.abs(dx)>dp(70)&&Math.abs(dx)>Math.abs(dy)*1.5f){setTab(dx<0?1:0);return true;}}
            return false;});
        page.setOnLongClickListener(v->{if(sideCol!=null&&sideCol.getVisibility()!=View.VISIBLE){sideCol.setVisibility(View.VISIBLE);Toast.makeText(this,"Botões laterais visíveis.",Toast.LENGTH_SHORT).show();}return true;});

        status=text("",11,cText,true);status.setBackground(bg(0xBF121215,16));status.setPadding(dp(10),dp(5),dp(10),dp(5));status.setVisibility(View.GONE);
        status.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence a,int b,int c,int d){}
            public void onTextChanged(CharSequence a,int b,int c,int d){}
            public void afterTextChanged(android.text.Editable e){status.setVisibility(e.length()==0?View.GONE:View.VISIBLE);}
        });
        FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2);sp.gravity=Gravity.BOTTOM|Gravity.RIGHT;sp.rightMargin=dp(16);sp.bottomMargin=dp(10);
        page.addView(status,sp);

        setContentView(page);root=null;refreshStatus();
        if(!AppSettings.onboardingDone(this)&&!onboardingShown){onboardingShown=true;startActivity(new Intent(this,OnboardingActivity.class));}
    }
    private void toggleSideButtons(){
        if(sideCol==null)return;
        boolean show=sideCol.getVisibility()!=View.VISIBLE;
        sideCol.setVisibility(show?View.VISIBLE:View.GONE);
        getSharedPreferences("settings",MODE_PRIVATE).edit().putBoolean("side_buttons",show).apply();
        Toast.makeText(this,show?"Botões laterais visíveis.":"Botões escondidos. Toque duas vezes na tela para mostrar.",Toast.LENGTH_SHORT).show();
    }

    // ---------- Visual "Estúdio Kawaii Pro": ícones, Play e fundo desenhados por código ----------
    /** Ícones de traço simples (fechar e lupa) usados nas janelas. */
    private static class LineIcon extends android.graphics.drawable.Drawable{
        private final int kind,size;private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        LineIcon(int k,int color,int sz){kind=k;size=sz;p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeCap(android.graphics.Paint.Cap.ROUND);p.setStrokeJoin(android.graphics.Paint.Join.ROUND);p.setColor(color);}
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void draw(android.graphics.Canvas c){
            android.graphics.Rect b=getBounds();float s=Math.min(b.width(),b.height())/24f;
            c.save();c.translate(b.left+(b.width()-24*s)/2f,b.top+(b.height()-24*s)/2f);c.scale(s,s);
            p.setStrokeWidth(2.6f);
            android.graphics.Path q=new android.graphics.Path();
            switch(kind){
                case 0:c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);break;
                case 1:c.drawCircle(10.5f,10.5f,6.5f,p);c.drawLine(15.3f,15.3f,20,20,p);break;
                case 2:q.moveTo(6,9);q.lineTo(12,15);q.lineTo(18,9);c.drawPath(q,p);break;
                case 3:c.drawLine(12,5,12,19,p);c.drawLine(5,12,19,12,p);break;
                case 4:q.moveTo(12,3);q.quadTo(12,12,21,12);q.quadTo(12,12,12,21);q.quadTo(12,12,3,12);q.quadTo(12,12,12,3);c.drawPath(q,p);break;
                case 5:c.drawCircle(12,8,3.6f,p);q.moveTo(5,20);q.quadTo(5,13,12,13);q.quadTo(19,13,19,20);c.drawPath(q,p);break;
                case 6:c.drawRoundRect(new android.graphics.RectF(4,9,15,20),3,3,p);c.drawRoundRect(new android.graphics.RectF(9,4,20,15),3,3,p);break;
                case 7:q.moveTo(3,19);q.lineTo(9,10);q.lineTo(13,15);q.lineTo(16,12);q.lineTo(21,19);c.drawPath(q,p);c.drawCircle(17,6,2f,p);break;
                default:p.setStrokeWidth(3.8f);c.drawPoint(6,12,p);c.drawPoint(12,12,p);c.drawPoint(18,12,p);
            }
            c.restore();
        }
        @Override public void setAlpha(int al){p.setAlpha(al);}
        @Override public void setColorFilter(android.graphics.ColorFilter cf){p.setColorFilter(cf);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    private static class PlayDrawable extends android.graphics.drawable.Drawable{
        private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(android.graphics.Canvas c){
            android.graphics.Rect b=getBounds();float w=Math.min(b.width(),b.height());float x=b.left+(b.width()-w)/2f,y=b.top+(b.height()-w)/2f;
            p.setColor(0xFF121215);p.setStyle(android.graphics.Paint.Style.FILL_AND_STROKE);p.setStrokeJoin(android.graphics.Paint.Join.ROUND);p.setStrokeWidth(w*0.07f);
            android.graphics.Path t=new android.graphics.Path();
            t.moveTo(x+w*0.40f,y+w*0.30f);t.lineTo(x+w*0.72f,y+w*0.50f);t.lineTo(x+w*0.40f,y+w*0.70f);t.close();
            c.drawPath(t,p);
        }
        @Override public void setAlpha(int al){p.setAlpha(al);}
        @Override public void setColorFilter(android.graphics.ColorFilter cf){p.setColorFilter(cf);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    /** Fundo padrão: estúdio minimalista em preto carvão, grade sutil e luz amarela suave no centro. */
    private static class StudioBg extends android.graphics.drawable.Drawable{
        private final float dens;private final android.graphics.Paint p=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        StudioBg(float d){dens=d;}
        @Override public void draw(android.graphics.Canvas c){
            android.graphics.Rect b=getBounds();
            p.setShader(null);p.setStyle(android.graphics.Paint.Style.FILL);p.setColor(0xFF121215);c.drawRect(b,p);
            float cx=b.exactCenterX(),cy=b.top+b.height()*0.45f,r=Math.max(b.width(),b.height())*0.7f;
            p.setShader(new android.graphics.RadialGradient(cx,cy,r,new int[]{0x2EFFD43B,0x00FFD43B},null,android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(b,p);p.setShader(null);
            p.setColor(0x0DFFFFFF);p.setStrokeWidth(Math.max(1f,dens*0.7f));
            float step=48*dens;
            for(float x=b.left;x<=b.right;x+=step)c.drawLine(x,b.top,x,b.bottom,p);
            for(float y=b.top;y<=b.bottom;y+=step)c.drawLine(b.left,y,b.right,y,p);
        }
        @Override public void setAlpha(int al){p.setAlpha(al);}
        @Override public void setColorFilter(android.graphics.ColorFilter cf){p.setColorFilter(cf);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
    }
    private ImageView iconButton(int res,int w,int h,int gap,View.OnClickListener click){
        ImageView v=new ImageView(this);v.setImageResource(res);v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(h,h);lp.topMargin=gap;lp.bottomMargin=gap;v.setLayoutParams(lp);
        if(click!=null)v.setOnClickListener(click);
        v.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View view,MotionEvent e){
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_DOWN)view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(150).start();
            else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL)view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
            return false;}});
        return v;
    }
    // ---------- Janelas por cima do modelo ----------
    private View scrim;
    private final ArrayList<AlertDialog> layered=new ArrayList<AlertDialog>();
    /** Janela do app: com o overlay ligado, o modelo e os acessórios ficam ATRÁS dela, escurecidos junto com o fundo. */
    private AlertDialog.Builder newDialog(){
        return new AlertDialog.Builder(this,android.R.style.Theme_Material_Dialog_Alert){
            @Override public AlertDialog show(){AlertDialog d=create();present(d);return d;}
        };
    }
    private void present(final AlertDialog d){
        boolean over=false;
        try{
            if(OverlayService.isRunning()&&Settings.canDrawOverlays(this)){
                WindowManager wm=(WindowManager)getApplicationContext().getSystemService(WINDOW_SERVICE);
                if(scrim==null){
                    View s=new View(this);s.setBackgroundColor(Color.argb(153,0,0,0));
                    s.setOnClickListener(v->{if(!layered.isEmpty())layered.get(layered.size()-1).cancel();});
                    WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS|WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,PixelFormat.TRANSLUCENT);
                    wm.addView(s,p);scrim=s;
                }
                d.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                d.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                layered.add(d);
                over=true;
            }
        }catch(Exception ignored){}
        try{d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);}catch(Exception ignored){}
        final boolean ov=over;
        d.setOnDismissListener(x->{if(ov){layered.remove(d);if(layered.isEmpty())removeScrim();}runClose(d);});
        styleWindow(d);
        d.show();
        styleButtons(d);
    }
    private void styleWindow(AlertDialog d){
        try{if("side".equals(d.getWindow().getDecorView().getTag()))return;GradientDrawable g=bg(panel,28);g.setStroke(dp(2),cBorder);d.getWindow().setBackgroundDrawable(g);}catch(Exception ignored){}
    }
    private void styleButtons(AlertDialog d){
        try{
            int[] ids={AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL};
            for(int id:ids){Button b=d.getButton(id);if(b!=null){b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(id==AlertDialog.BUTTON_NEGATIVE?muted:yellow);}}
        }catch(Exception ignored){}
    }
    private void runClose(AlertDialog d){Runnable r=closers.remove(d);if(r!=null)r.run();}
    private void removeScrim(){
        if(scrim==null)return;
        try{((WindowManager)getApplicationContext().getSystemService(WINDOW_SERVICE)).removeView(scrim);}catch(Exception ignored){}
        scrim=null;
    }
        private void hideSystemBars(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);}
    @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f&&status!=null)hideSystemBars();}
    private SharedPreferences prefs(){return getSharedPreferences("vtuber",MODE_PRIVATE);}
    // Salas do cenário: cada sala tem o seu próprio cenário (imagem ou MP4 de até 5 s em loop).
    private static final String[] ROOM_IDS={"wait","talk","normal","end"};
    private static final String[] ROOM_NAMES={"Sala de espera","Sala de conversa","Sala normal","Sala de terminar"};
    private String curRoom(){String r=prefs().getString("room","normal");for(String x:ROOM_IDS)if(x.equals(r))return r;return "normal";}
    private String roomName(String room){for(int i=0;i<ROOM_IDS.length;i++)if(ROOM_IDS[i].equals(room))return ROOM_NAMES[i];return ROOM_NAMES[2];}
    private String sceneryOf(String room){String v=prefs().getString("scenery_"+room,room.equals("normal")?prefs().getString("scenery","builtin:yellow"):"builtin:yellow");return v.equals("none")?"builtin:yellow":v;}
    private String curScenery(){return sceneryOf(curRoom());}
    private boolean hasMic(){return checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED;}

    /** Play: grava a tela com áudio; clicar de novo para. */
    private void toggleRecording(){
        if(LiveService.isLive()){confirmStopLive();return;}
        if(RecordService.isRecording()){
            startService(new Intent(this,RecordService.class).setAction(RecordService.ACTION_STOP));
            if(status!=null)status.setText("Salvando gravação...");
            ui.postDelayed(new Runnable(){public void run(){updateRecState();}},700);
            return;
        }
        if(!hasMic()){requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS}:new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);return;}
        requestProjection();
    }
    private void requestProjection(){
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(),REQ_RECORD);
    }

    // ---------- Live no YouTube (RTMPS): deslizar o botão ▶ para a direita ----------
    private boolean liveSetupOpen=false;

    /** Play: toque rápido grava; segurar por 10 segundos abre a Live (evita abrir a Live sem querer). */
    private void attachLiveHold(final View btn){
        final android.os.Handler h=new android.os.Handler(android.os.Looper.getMainLooper());
        final long[] t0=new long[1];final boolean[] fired=new boolean[1];
        final Runnable go=new Runnable(){public void run(){fired[0]=true;btn.setPressed(false);try{btn.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);}catch(Exception ignore){}showLiveConfirm();}};
        final Runnable hint=new Runnable(){public void run(){Toast.makeText(MainActivity.this,"Continue segurando para abrir a Live...",Toast.LENGTH_SHORT).show();}};
        btn.setOnTouchListener(new View.OnTouchListener(){public boolean onTouch(View v,MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    fired[0]=false;t0[0]=e.getEventTime();
                    v.animate().cancel();v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).start();
                    h.postDelayed(hint,1500);h.postDelayed(go,10000);
                    return false;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    h.removeCallbacks(hint);h.removeCallbacks(go);
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    if(fired[0])return true; // a Live já abriu: não conta como toque
                    if(e.getActionMasked()==MotionEvent.ACTION_UP&&e.getEventTime()-t0[0]>1500){
                        Toast.makeText(MainActivity.this,"Toque rápido para gravar. Segure 10 segundos para abrir a Live.",Toast.LENGTH_SHORT).show();
                        return true;}
                    return false;
            }
            return false;}});
    }
    // ---------- Abas: criação e gravação ----------
    private View tabDot(){
        View v=new View(this);android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);g.setColor(Color.rgb(20,20,20));v.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(8),dp(8));lp.leftMargin=dp(4);lp.rightMargin=dp(4);v.setLayoutParams(lp);
        return v;
    }
    private void paintDot(View v,boolean on){
        GradientDrawable g=new GradientDrawable();g.setShape(GradientDrawable.RECTANGLE);g.setCornerRadius(dp(8));g.setColor(on?yellow:0x66FFFFFF);v.setBackground(g);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(on?20:8),dp(8));lp.leftMargin=dp(4);lp.rightMargin=dp(4);v.setLayoutParams(lp);
    }
    private void updateTabDots(){if(dotA!=null)paintDot(dotA,tab==0);if(dotB!=null)paintDot(dotB,tab==1);}
    private void setTab(int t){
        if(tab==t||colCreate==null||colRec==null)return;
        tab=t;updateTabDots();
        final View out=t==1?colCreate:colRec,in=t==1?colRec:colCreate;
        final View keep=in;float dir=t==1?-1f:1f;int d=dp(60);
        out.animate().cancel();in.animate().cancel();
        out.animate().alpha(0f).translationX(dir*d).setDuration(150).withEndAction(new Runnable(){public void run(){if(out!=keep)out.setVisibility(View.GONE);}}).start();
        in.setTranslationX(-dir*d);in.setAlpha(0f);in.setVisibility(View.VISIBLE);
        in.animate().alpha(1f).translationX(0f).setDuration(200).start();
    }
    private void showCommunity(){
        newDialog().setTitle("Comunidade")
            .setMessage("Em breve: modelos compartilhados pela comunidade, direto de um repositório público no GitHub.")
            .setPositiveButton("Fechar",null).show();
    }
    private void showLiveConfirm(){
        if(LiveService.isLive()){Toast.makeText(this,"Já existe uma Live em andamento.",Toast.LENGTH_SHORT).show();return;}
        if(RecordService.isRecording()){Toast.makeText(this,"Pare a gravação antes de abrir uma Live.",Toast.LENGTH_SHORT).show();return;}
        newDialog().setMessage("Deseja abrir Live?")
            .setPositiveButton("Abrir",(d,w)->showLiveSetup())
            .setNegativeButton("Cancelar",null).show();
    }
    private void confirmStopLive(){
        newDialog().setMessage("Encerrar a Live?")
            .setPositiveButton("Encerrar",(d,w)->startService(new Intent(this,LiveService.class).setAction(LiveService.ACTION_STOP)))
            .setNegativeButton("Cancelar",null).show();
    }
    private EditText liveField(String hint,String value,boolean secret){
        EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setHint(hint);
        e.setTextColor(Color.WHITE);e.setHintTextColor(Color.rgb(120,120,128));e.setTextSize(15);
        e.setBackground(bg(Color.rgb(44,44,50),12));e.setPadding(dp(12),dp(10),dp(12),dp(10));
        e.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_ACTION_DONE);
        e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_VARIATION_URI));
        if(secret)e.setTransformationMethod(PasswordTransformationMethod.getInstance());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);lp.bottomMargin=dp(10);e.setLayoutParams(lp);
        return e;
    }
    /** Tela de configuração da Live. A janela fica protegida (FLAG_SECURE) enquanto ela estiver aberta. */
    private void showLiveSetup(){
        liveSetupOpen=true;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        FrameLayout page=new FrameLayout(this);page.setBackgroundColor(Color.rgb(8,8,9));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(16),dp(20),dp(20));
        box.addView(text("Transmitir no YouTube",20,yellow,true));
        TextView info=text("No YouTube Studio: Transmitir ao vivo > Transmissão por software. Copie o URL do servidor (RTMPS) e a chave de transmissão.",12,muted,false);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.topMargin=dp(4);ip.bottomMargin=dp(12);box.addView(info,ip);

        box.addView(text("Endereço do servidor (RTMPS)",12,muted,true));
        final EditText fServer=liveField(SecureStore.DEFAULT_SERVER,SecureStore.server(this),false);box.addView(fServer);
        box.addView(text("Chave de transmissão",12,muted,true));
        final boolean saved=SecureStore.hasKey(this);
        final EditText fKey=liveField(saved?"Chave salva neste aparelho (deixe em branco para usá-la)":"Cole a chave aqui","",true);box.addView(fKey);
        final TextView show=text("Mostrar chave",12,yellow,true);show.setPadding(0,0,0,dp(10));
        show.setOnClickListener(v->{boolean hidden=fKey.getTransformationMethod()!=null;fKey.setTransformationMethod(hidden?null:PasswordTransformationMethod.getInstance());show.setText(hidden?"Ocultar chave":"Mostrar chave");fKey.setSelection(fKey.length());});
        box.addView(show);

        TextView note=text("720p · 30 FPS · tela inteira (inclui o Vtuber, o app e tudo que aparecer). Ao conectar, a tela entra no ar na sala de espera, sem limite de tempo; troque para a sala normal quando estiver pronto. Se a tela do celular apagar, a transmissão mostra a sala de espera. A chave fica cifrada no aparelho. Tudo que aparecer na tela vai ao ar: feche conversas, senhas e notificações privadas. Para encerrar, use Parar na notificação.",11,muted,false);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);np.bottomMargin=dp(10);box.addView(note,np);

        Button cancel=pill("Cancelar",v->showDashboard());
        Button go=pill("Iniciar Live",v->{
            String srv=normalizeServer(fServer.getText().toString());
            if(srv==null){Toast.makeText(this,"Use o endereço RTMPS do YouTube (começa com rtmps://).",Toast.LENGTH_LONG).show();return;}
            String key=fKey.getText().toString().trim();
            if(key.isEmpty()&&!SecureStore.hasKey(this)){Toast.makeText(this,"Cole a chave de transmissão do YouTube Studio.",Toast.LENGTH_LONG).show();return;}
            if(key.matches(".*\\s.*")){Toast.makeText(this,"A chave não pode ter espaços.",Toast.LENGTH_LONG).show();return;}
            try{if(!key.isEmpty())SecureStore.saveKey(this,key);}
            catch(Exception ex){Toast.makeText(this,"Este aparelho não conseguiu guardar a chave com segurança. A Live não foi iniciada.",Toast.LENGTH_LONG).show();return;}
            SecureStore.saveServer(this,srv);
            fKey.setText("");
            beginLive();
        });
        box.addView(row(cancel,go));
        if(saved){
            TextView del=text("Apagar chave salva",12,Color.rgb(255,120,110),true);del.setGravity(Gravity.CENTER);del.setPadding(0,dp(12),0,dp(4));
            del.setOnClickListener(v->{SecureStore.clearKey(this);Toast.makeText(this,"Chave apagada.",Toast.LENGTH_SHORT).show();showLiveSetup();});
            box.addView(del);
        }
        LinearLayout holder=new LinearLayout(this);holder.setGravity(Gravity.CENTER_HORIZONTAL);
        holder.addView(box,new LinearLayout.LayoutParams(Math.min(dp(560),getResources().getDisplayMetrics().widthPixels),-2));
        ScrollView sv=new ScrollView(this);sv.setFillViewport(true);sv.addView(holder,new FrameLayout.LayoutParams(-1,-2));
        page.addView(sv,new FrameLayout.LayoutParams(-1,-1));
        setContentView(page);hideSystemBars();
    }
    /** Aceita só rtmps:// sem espaços; remove "/" no fim. */
    private String normalizeServer(String raw){
        String s=raw==null?"":raw.trim();
        while(s.endsWith("/"))s=s.substring(0,s.length()-1);
        if(!s.toLowerCase(java.util.Locale.ROOT).startsWith("rtmps://")||s.length()<=9||s.matches(".*\\s.*"))return null;
        return s;
    }
    private void beginLive(){
        boolean notif=Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
        if(!hasMic()||!notif){
            requestPermissions(Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.POST_NOTIFICATIONS}:new String[]{Manifest.permission.RECORD_AUDIO},REQ_LIVE_PERM);
            return;
        }
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(m.createScreenCaptureIntent(),REQ_LIVE);
    }
    /** Botão de gravar estilo rede social: anel branco e bolinha vermelha (vira quadradinho enquanto grava). */
    private GradientDrawable recRing;
    private android.graphics.drawable.Drawable playTri;
    private View recordButton(int d){
        FrameLayout f=new FrameLayout(this);
        recRing=new GradientDrawable();recRing.setShape(GradientDrawable.OVAL);
        f.setBackground(recRing);
        recDot=new View(this);recDotBg=new GradientDrawable();recDotBg.setColor(cRec);
        playTri=new PlayDrawable();
        int m=dp(11);
        f.addView(recDot,new FrameLayout.LayoutParams(d-2*m,d-2*m,Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(d,d));
        f.setOnClickListener(v->toggleRecording());
        paintRecDot(false);
        return f;
    }
    private void paintRecDot(boolean rec){
        if(recDot==null||recDotBg==null)return;
        recDotBg.setShape(GradientDrawable.RECTANGLE);recDotBg.setCornerRadius(dp(8));
        recDot.setBackground(rec?recDotBg:playTri);
        recDot.setScaleX(rec?0.55f:1f);recDot.setScaleY(rec?0.55f:1f);
        if(recRing!=null){
            if(rec){recRing.setColor(panel);recRing.setStroke(dp(4),cRec);}
            else{recRing.setColor(yellow);recRing.setStroke(dp(3),0xFFFFE885);}
        }
    }
    private void updateRecState(){
        if(recDot==null)return;
        recDot.animate().cancel();
        if(RecordService.isRecording()){
            paintRecDot(true);recDot.setAlpha(1f);
            recDot.animate().alpha(0.35f).setDuration(600).withEndAction(new Runnable(){public void run(){if(RecordService.isRecording())pulseUp();else{if(recDot!=null){recDot.setAlpha(1f);paintRecDot(false);}}}}).start();
            if(status!=null)status.setText("● Gravando  ·  toque no botão vermelho para parar");
        }else{recDot.setAlpha(1f);paintRecDot(false);refreshStatus();}
    }
    private void pulseUp(){
        if(recDot==null)return;
        recDot.animate().alpha(1f).setDuration(600).withEndAction(new Runnable(){public void run(){if(RecordService.isRecording())updateRecState();else{if(recDot!=null){recDot.setAlpha(1f);paintRecDot(false);}}}}).start();
    }

    /** Camadas: liga/desliga o overlay da Vtuber. */
    private void toggleOverlay(){
        if(OverlayService.isRunning()){stopService(new Intent(this,OverlayService.class));if(status!=null)status.setText("Overlay desligado.");}
        else startVtuber();
    }

    private Button pill(String label,View.OnClickListener l){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(cBg);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(bg(yellow,999));b.setOnClickListener(l);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(4),dp(4),dp(4),dp(4));b.setLayoutParams(lp);return b;
    }
    private LinearLayout row(Button... bs){LinearLayout r=new LinearLayout(this);for(Button b:bs)r.addView(b);return r;}
    private void remote(String cmd){if(!OverlayService.remote(cmd))Toast.makeText(this,"Ligue o overlay primeiro (Camadas > Ligar).",Toast.LENGTH_SHORT).show();}

    /** Engrenagem: configurações do modelo (as mesmas do painel do overlay) + voltar à Verônica. */
    private void showModelSettings(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(8),dp(16),dp(8));
        box.addView(row(pill("Calibrar",v->remote("calibrate")),pill("Verde (chroma)",v->remote("green"))));
        final Button orient=pill(orientLabel(),null);
        orient.setOnClickListener(v->{String m=prefs().getString("rec_orient","auto");String n=m.equals("auto")?"land":m.equals("land")?"port":"auto";prefs().edit().putString("rec_orient",n).apply();orient.setText(orientLabel());});
        box.addView(row(orient));
        if(isPng())box.addView(row(pill("Pngtuber: trocar parado/falando",v->swapPng())));
        box.addView(row(pill("Voltar à Verônica (padrão)",v->restoreDefaultCharacter())));
        box.addView(row(pill("Configurações do app",v->startActivity(new Intent(this,SettingsActivity.class)))));
        ScrollView sv=new ScrollView(this);sv.addView(box);
        newDialog().setTitle("Configurações do modelo").setView(sv).setNegativeButton("Fechar",null).show();
    }
    private void restoreDefaultCharacter(){
        newDialog().setTitle("Personagem padrão")
            .setMessage("Reiniciar o modelo e voltar para a Verônica? Os modelos importados continuam na lista.")
            .setPositiveButton("Voltar",(d,w)->selectModel("default"))
            .setNegativeButton("Cancelar",null).show();
    }

    // ---------- Biblioteca de modelos ----------
    /** Botão +: lista de funções extras. */
    /** Gravação e Live: "auto" segue a posição do app na hora de começar; também dá para fixar paisagem ou retrato. */
    private boolean recLandscape(){
        String m=prefs().getString("rec_orient","auto");
        if(m.equals("land"))return true;
        if(m.equals("port"))return false;
        return getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_PORTRAIT;
    }
    private String orientLabel(){String m=prefs().getString("rec_orient","auto");return "Gravação e Live: "+(m.equals("land")?"Paisagem":m.equals("port")?"Retrato":"Automático (como o app)");}
    // ---------- Camadas: uma janela só (na frente / Vtuber / atrás / fundo) ----------
    private TextView chip(String label,int fill,int fg,View.OnClickListener l){
        TextView t=text(label,13,fg,true);t.setGravity(Gravity.CENTER);t.setBackground(bg(fill,999));t.setPadding(dp(14),dp(8),dp(14),dp(8));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(6);t.setLayoutParams(lp);t.setOnClickListener(l);return t;
    }
    private LinearLayout layerRow(boolean highlight){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g=bg(cCard,16);if(highlight)g.setStroke(dp(2),yellow);r.setBackground(g);
        r.setPadding(dp(12),dp(8),dp(8),dp(8));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(3),0,dp(3));r.setLayoutParams(lp);return r;
    }
    private LinearLayout labels(String title,String sub){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.addView(text(title,15,Color.WHITE,true));
        if(sub!=null)c.addView(text(sub,11,muted,false));
        c.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));return c;
    }
    private String currentModelName(){String id=selectedId();if(id.equals("default"))return "Verônica";if(id.equals("default_png"))return "Verônica (Pngtuber)";return modelName(new File(modelsDir(),id));}
    private String sceneryLabel(String v){return v.equals("builtin:green")?"Sem fundo (verde)":v.startsWith("file:")?(v.endsWith(".mp4")?"Vídeo importado":"Imagem importada"):"Estúdio";}
    private Bitmap thumb(File f,int max){
        try{
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),o);
            int sm=1;while(Math.max(o.outWidth,o.outHeight)/sm>max)sm*=2;
            BitmapFactory.Options o2=new BitmapFactory.Options();o2.inSampleSize=sm;return BitmapFactory.decodeFile(f.getAbsolutePath(),o2);
        }catch(Throwable t){return null;}
    }
    private Bitmap thumbAsset(String name,int max){
        try{
            InputStream in=getAssets().open(name);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,o);in.close();
            int sm=1;while(Math.max(o.outWidth,o.outHeight)/sm>max)sm*=2;
            BitmapFactory.Options o2=new BitmapFactory.Options();o2.inSampleSize=sm;in=getAssets().open(name);Bitmap b=BitmapFactory.decodeStream(in,null,o2);in.close();return b;
        }catch(Throwable t){return null;}
    }
    // ---------- Camadas: lista de camadas (cada linha é UMA camada, não uma pasta) ----------
    // De cima para baixo: acessórios (na frente do modelo) > o modelo (Vtuber ou PNGtuber, conforme o escolhido) > objetos (atrás) > paisagem.
    // Acessório e Objeto não vêm de fábrica: a pessoa adiciona pelo "+".
    private void reopenLayers(){ui.post(new Runnable(){public void run(){showLayersWindow(false);}});}
    private void showLayersWindow(){showLayersWindow(true);}
    private boolean modelIsPng(){return isPngId(selectedId());}
    private String modelLabel(){return modelIsPng()?"PNGtuber":"Vtuber";}
    private void startAddObject(boolean back,AlertDialog[] dlg){
        if(!OverlayService.isRunning()){Toast.makeText(this,"Ligue o "+modelLabel()+" primeiro (toque na linha dele na lista).",Toast.LENGTH_SHORT).show();return;}
        dlg[0].dismiss();pendingAccBack=back;pickImage(REQ_ACCESSORY);
    }
    private void showAddMenu(final AlertDialog[] dlg){
        final String lab=modelLabel();
        newDialog().setTitle("Nova camada").setItems(new String[]{"Acessório (gruda no "+lab+")","Objeto (fica fixo na tela)"},(d,w)->startAddObject(w==1,dlg)).show();
    }
    private void showModelMenu(final boolean on,final AlertDialog[] dlg){
        newDialog().setTitle(modelLabel()+" · "+currentModelName()).setItems(new String[]{on?"Desligar":"Ligar"},(d,w)->{dlg[0].dismiss();toggleOverlay();}).show();
    }
    private void showSceneryRowMenu(final AlertDialog[] dlg){
        newDialog().setTitle("paisagem").setItems(new String[]{"Trocar fundo","Chat da live"},(d,w)->{
            dlg[0].dismiss();
            if(w==0)showSceneryMenu();else startActivity(new Intent(this,ChatActivity.class));
        }).show();
    }
    private void closeLayers(final AlertDialog d,View panelV,int pw){
        if(d==null)return;
        panelV.animate().translationX(pw).setDuration(160).withEndAction(new Runnable(){public void run(){try{d.dismiss();}catch(Exception ignored){}}}).start();
    }
    private LinearLayout rowsBox(){LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);return b;}
    private ImageView thumbImg(Bitmap b,ImageView.ScaleType st){ImageView v=new ImageView(this);v.setScaleType(st);if(b!=null)v.setImageBitmap(b);return v;}
    /** Miniatura do modelo escolhido: a própria imagem (PNGtuber = imagem parado; Vtuber = o preview dele), a mesma do seletor de modelos. */
    private View modelThumb(){String id=selectedId();return modelPreview(id,isPngId(id),currentModelName());}
    /** Miniatura da paisagem: a imagem do cenário atual (ou um quadro do vídeo; o estúdio e o verde são desenhados). */
    private View sceneryThumb(){
        String v=curScenery();ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try{
            if(v.equals("builtin:green"))iv.setBackgroundColor(Color.rgb(0,177,64));
            else if(v.startsWith("file:")){
                File f=new File(sceneriesDir(),v.substring(5));
                if(v.endsWith(".mp4")){
                    android.media.MediaMetadataRetriever mr=new android.media.MediaMetadataRetriever();
                    try{mr.setDataSource(f.getAbsolutePath());Bitmap fb=mr.getFrameAtTime(0);if(fb!=null)iv.setImageBitmap(fb);else iv.setBackgroundColor(Color.BLACK);}
                    finally{try{mr.release();}catch(Throwable ignored){}}
                }else{Bitmap b=thumb(f,128);if(b!=null)iv.setImageBitmap(b);else iv.setImageDrawable(new StudioBg(getResources().getDisplayMetrics().density));}
            }else iv.setImageDrawable(new StudioBg(getResources().getDisplayMetrics().density));
        }catch(Throwable t){iv.setImageDrawable(new StudioBg(getResources().getDisplayMetrics().density));}
        return iv;
    }
    /** Linha de camada: quadrado com a imagem + nome. Toque abre as opções; segurar e arrastar (nos acessórios/objetos) muda a ordem. */
    private LinearLayout layerItem(View thumbView,String name,boolean dim){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g=bg(cCard,18);g.setStroke(dp(1),cBorder);r.setBackground(g);r.setPadding(dp(8),0,dp(12),0);
        // a miniatura nunca "engole" o toque (um WebView de preview engoliria): o toque vai para a linha
        FrameLayout tf=new FrameLayout(this){@Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}};
        tf.setBackground(bg(0xFF101013,12));tf.setClipToOutline(true);
        tf.addView(thumbView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(dp(42),dp(42));tp.rightMargin=dp(12);r.addView(tf,tp);
        r.addView(text(name,17,cText,true),new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(3),0,dp(3));r.setLayoutParams(lp);
        if(dim)r.setAlpha(0.55f);
        return r;
    }
    private View accessoryItem(final OverlayService.AccInfo a,final String name,final LinearLayout box,final int pos,final int cnt,final int modelPos,final Runnable rebuild){
        LinearLayout r=layerItem(thumbImg(thumb(new File(a.path),96),ImageView.ScaleType.FIT_CENTER),name,false);
        r.setOnClickListener(v->showLayerMenu(a,name,rebuild));
        enableDrag(r,box,pos,cnt,modelPos,a,rebuild);
        return r;
    }
    private void showLayerMenu(final OverlayService.AccInfo a,String name,final Runnable rebuild){
        newDialog().setTitle(name).setItems(new String[]{"Remover"},(d,w)->{OverlayService.removeAccessoryById(a.id);ui.post(rebuild);}).show();
    }
    /**
     * Segurar a linha e arrastar: muda a ordem e, se passar pela linha do modelo, troca a camada
     * (acima do modelo = Acessório, gruda nele; abaixo = Objeto, fixo). A paisagem fica sempre no fim e não entra.
     */
    private void enableDrag(final View row,final LinearLayout box,final int pos,final int cnt,final int modelPos,final OverlayService.AccInfo acc,final Runnable rebuild){
        final float[] startY={0};final boolean[] drag={false};final int[] target={pos};
        row.setOnLongClickListener(v->{
            drag[0]=true;target[0]=pos;
            row.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if(row.getParent()!=null)row.getParent().requestDisallowInterceptTouchEvent(true);
            row.setElevation(dp(8));row.animate().scaleX(1.03f).scaleY(1.03f).setDuration(90).start();
            return true;
        });
        row.setOnTouchListener((v,e)->{
            int act=e.getActionMasked();
            if(act==MotionEvent.ACTION_DOWN){startY[0]=e.getRawY();return false;}
            if(!drag[0])return false;
            int pitch=row.getHeight()+dp(6);
            if(act==MotionEvent.ACTION_MOVE){
                float dy=e.getRawY()-startY[0];
                dy=Math.max(-pos*pitch,Math.min((cnt-1-pos)*pitch,dy));
                row.setTranslationY(dy);
                int t=Math.max(0,Math.min(cnt-1,pos+Math.round(dy/pitch)));
                if(t!=target[0]){
                    target[0]=t;
                    for(int i=0;i<cnt;i++){
                        View c=box.getChildAt(i);if(c==row)continue;
                        float ty=0;
                        if(t>pos&&i>pos&&i<=t)ty=-pitch;else if(t<pos&&i>=t&&i<pos)ty=pitch;
                        c.animate().translationY(ty).setDuration(120).start();
                    }
                }
                return true;
            }
            if(act==MotionEvent.ACTION_UP||act==MotionEvent.ACTION_CANCEL){
                drag[0]=false;
                row.setTranslationY(0);row.setElevation(0);row.setScaleX(1f);row.setScaleY(1f);
                for(int i=0;i<cnt;i++){View c=box.getChildAt(i);c.animate().cancel();c.setTranslationY(0);}
                if(act==MotionEvent.ACTION_UP&&target[0]!=pos){
                    // nova ordem das linhas: onde fica a linha do modelo decide o lado (frente/atrás)
                    ArrayList<Integer> ord=new ArrayList<Integer>();
                    for(int i=0;i<cnt;i++)ord.add(i);
                    ord.remove(Integer.valueOf(pos));ord.add(target[0],pos);
                    int newModel=ord.indexOf(modelPos);
                    boolean back=target[0]>newModel;
                    int fromTop=back?target[0]-newModel-1:target[0];
                    OverlayService.placeAccessory(acc.id,back,fromTop);
                    ui.post(rebuild);
                }
                return true;
            }
            return true;
        });
    }
    private void showLayersWindow(boolean animate){
        final AlertDialog[] dlg=new AlertDialog[1];
        final android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        final int pw=Math.min((int)(dm.widthPixels*0.9f),dp(340));
        final FrameLayout root=new FrameLayout(this);
        final LinearLayout panelV=new LinearLayout(this);panelV.setOrientation(LinearLayout.VERTICAL);panelV.setClickable(true);
        GradientDrawable pg=bg(cBg,0);pg.setCornerRadii(new float[]{dp(28),dp(28),0,0,0,0,dp(28),dp(28)});pg.setStroke(dp(2),cBorder);panelV.setBackground(pg);
        panelV.setPadding(dp(14),dp(16),dp(14),dp(10));
        root.addView(panelV,new FrameLayout.LayoutParams(pw,-1,Gravity.END));
        root.setOnClickListener(v->closeLayers(dlg[0],panelV,pw));

        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Camadas",24,cText,true),new LinearLayout.LayoutParams(0,-2,1));
        ImageView add=new ImageView(this);add.setImageDrawable(new LineIcon(3,cBg,dp(22)));add.setScaleType(ImageView.ScaleType.CENTER);add.setBackground(bg(yellow,999));
        add.setOnClickListener(v->showAddMenu(dlg));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(42),dp(42));ap.rightMargin=dp(8);head.addView(add,ap);
        ImageView close=new ImageView(this);close.setImageDrawable(new LineIcon(0,cBg,dp(20)));close.setScaleType(ImageView.ScaleType.CENTER);close.setBackground(bg(yellow,999));
        close.setOnClickListener(v->closeLayers(dlg[0],panelV,pw));
        head.addView(close,new LinearLayout.LayoutParams(dp(42),dp(42)));
        panelV.addView(head,new LinearLayout.LayoutParams(-1,-2));

        final LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(0,dp(12),0,dp(4));
        ScrollView sv=new ScrollView(this);sv.setVerticalScrollBarEnabled(false);sv.addView(list);
        panelV.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        final TextView hint=text("Segure um objeto e arraste para mudar a ordem",11,muted,false);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(6),0,0);
        panelV.addView(hint,new LinearLayout.LayoutParams(-1,-2));

        final Runnable[] rebuild=new Runnable[1];
        rebuild[0]=new Runnable(){public void run(){
            destroyPreviews();
            list.removeAllViews();
            boolean on=OverlayService.isRunning();
            ArrayList<OverlayService.AccInfo> accs=OverlayService.accessoryInfo();
            ArrayList<OverlayService.AccInfo> fr=new ArrayList<OverlayService.AccInfo>(),bk=new ArrayList<OverlayService.AccInfo>();
            for(int i=accs.size()-1;i>=0;i--){OverlayService.AccInfo a=accs.get(i);if(a.back)bk.add(a);else fr.add(a);}
            // nome de cada camada adicionada: "Acessório" / "Objeto" (com número só quando há mais de um do mesmo tipo)
            java.util.HashMap<Integer,String> names=new java.util.HashMap<Integer,String>();
            int cf=0,cb=0;for(OverlayService.AccInfo a:accs){if(a.back)cb++;else cf++;}
            int nf=0,nb=0;
            for(OverlayService.AccInfo a:accs){
                if(a.back){nb++;names.put(a.id,"Objeto"+(cb>1?" "+nb:""));}
                else{nf++;names.put(a.id,"Acessório"+(cf>1?" "+nf:""));}
            }
            // uma lista só: acessórios (frente) > modelo > objetos (atrás) > paisagem
            final int cnt=fr.size()+1+bk.size(),modelPos=fr.size();
            int pos=0;
            for(int i=0;i<fr.size();i++)list.addView(accessoryItem(fr.get(i),names.get(fr.get(i).id),list,pos++,cnt,modelPos,rebuild[0]));
            final boolean onF=on;
            LinearLayout mr=layerItem(modelThumb(),modelLabel(),!on);
            mr.setOnClickListener(v->showModelMenu(onF,dlg));
            list.addView(mr);pos++;
            for(int i=0;i<bk.size();i++)list.addView(accessoryItem(bk.get(i),names.get(bk.get(i).id),list,pos++,cnt,modelPos,rebuild[0]));
            LinearLayout sr=layerItem(sceneryThumb(),"paisagem",false);
            sr.setOnClickListener(v->showSceneryRowMenu(dlg));
            list.addView(sr);
            hint.setText(accs.isEmpty()?"Toque no + para adicionar acessórios e objetos":"Segure um objeto e arraste para mudar a ordem");
        }};

        dlg[0]=newDialog().create();
        closers.put(dlg[0],new Runnable(){public void run(){destroyPreviews();}});
        dlg[0].setView(root,0,0,0,0);
        try{
            android.view.Window w=dlg[0].getWindow();
            w.getDecorView().setTag("side");
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
        }catch(Exception ignored){}
        rebuild[0].run();
        if(animate)panelV.setTranslationX(pw);
        present(dlg[0]);
        if(animate)panelV.post(new Runnable(){public void run(){panelV.animate().translationX(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}});
    }
    private void pickImage(int req){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,req);}
    private Bitmap decodeUri(Uri u,int max)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
        InputStream in=getContentResolver().openInputStream(u);if(in==null)return null;BitmapFactory.decodeStream(in,null,o);in.close();
        int sm=1;while(Math.max(o.outWidth,o.outHeight)/sm>max)sm*=2;
        BitmapFactory.Options o2=new BitmapFactory.Options();o2.inSampleSize=sm;
        in=getContentResolver().openInputStream(u);if(in==null)return null;Bitmap b=BitmapFactory.decodeStream(in,null,o2);in.close();return b;
    }
    private void saveAccessory(final Uri uri){
        new Thread(new Runnable(){public void run(){
            String path=null;String err=null;
            try{
                String amime=getContentResolver().getType(uri);
                File dir=new File(getFilesDir(),"accessories");dir.mkdirs();
                if(android.os.Build.VERSION.SDK_INT>=28&&amime!=null&&(amime.equals("image/gif")||amime.equals("image/webp"))){
                    File f=new File(dir,System.currentTimeMillis()+(amime.equals("image/gif")?".gif":".webp"));
                    InputStream ain=getContentResolver().openInputStream(uri);FileOutputStream afo=new FileOutputStream(f);
                    byte[] abuf=new byte[65536];int an;long atot=0;
                    while((an=ain.read(abuf))>0){atot+=an;if(atot>20L*1024*1024)throw new Exception("arquivo maior que 20 MB");afo.write(abuf,0,an);}
                    ain.close();afo.close();path=f.getAbsolutePath();
                }else{
                    Bitmap b=decodeUri(uri,1024);if(b==null)throw new Exception("imagem inválida");
                    File f=new File(dir,System.currentTimeMillis()+".png");
                    FileOutputStream fos=new FileOutputStream(f);b.compress(Bitmap.CompressFormat.PNG,100,fos);fos.close();path=f.getAbsolutePath();
                }
            }catch(Exception ex){err=Friendly.why(ex);}
            final String fp=path,fe=err;
            runOnUiThread(new Runnable(){public void run(){
                if(fp==null){Toast.makeText(MainActivity.this,"Não consegui abrir a imagem: "+fe,Toast.LENGTH_SHORT).show();return;}
                if(!OverlayService.addAccessory(fp,pendingAccBack))Toast.makeText(MainActivity.this,"Ligue o Vtuber primeiro (Camadas > Ligar).",Toast.LENGTH_SHORT).show();else reopenLayers();
            }});
        }}).start();
    }

    // ---------- Cenário: do app e importados ----------
    private File sceneriesDir(){return new File(getFilesDir(),"sceneries");}
    private void setScenery(String v){
        prefs().edit().putString("scenery_"+curRoom(),v).apply();
        applyDashboardBg();
    }
    // ---------- Transição ao trocar de sala (nenhuma, suave ou vídeo de até 5 s) ----------
    private void registerRoomListener(){
        roomListener=new SharedPreferences.OnSharedPreferenceChangeListener(){public void onSharedPreferenceChanged(SharedPreferences sp,String key){
            if("room".equals(key))runOnUiThread(new Runnable(){public void run(){playTransition();}});}};
        AppSettings.main(this).registerOnSharedPreferenceChangeListener(roomListener);
    }
    private void playTransition(){
        if(pageRoot==null||bgView==null||transitioning){applyDashboardBg();return;}
        int mode=AppSettings.transition(this);
        if(mode>=2){File f=new File(new File(getFilesDir(),"transitions"),"t"+(mode-1)+".mp4");if(f.exists()){playVideoTransition(f);return;}mode=1;}
        if(mode==0){applyDashboardBg();return;}
        transitioning=true;
        final View cover=new View(this);cover.setBackgroundColor(Color.BLACK);cover.setAlpha(0f);
        pageRoot.addView(cover,new FrameLayout.LayoutParams(-1,-1));
        cover.animate().alpha(1f).setDuration(220).withEndAction(new Runnable(){public void run(){
            applyDashboardBg();
            cover.animate().alpha(0f).setDuration(260).withEndAction(new Runnable(){public void run(){if(pageRoot!=null)pageRoot.removeView(cover);transitioning=false;}}).start();
        }}).start();
    }
    private void playVideoTransition(final File f){
        transitioning=true;
        final android.view.TextureView tex=new android.view.TextureView(this);trTex=tex;
        pageRoot.addView(tex,new FrameLayout.LayoutParams(-1,-1));
        tex.setSurfaceTextureListener(new android.view.TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st,int w,int h){
                try{
                    trMp=new android.media.MediaPlayer();
                    trMp.setSurface(new android.view.Surface(st));
                    trMp.setDataSource(f.getAbsolutePath());trMp.setVolume(0f,0f);
                    trMp.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener(){public void onPrepared(android.media.MediaPlayer mp){
                        fitTr(mp.getVideoWidth(),mp.getVideoHeight());mp.start();
                        int half=Math.max(150,mp.getDuration()/2);
                        if(pageRoot!=null)pageRoot.postDelayed(new Runnable(){public void run(){applyDashboardBg();}},half);
                    }});
                    trMp.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener(){public void onCompletion(android.media.MediaPlayer mp){endVideoTransition();}});
                    trMp.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener(){public boolean onError(android.media.MediaPlayer mp,int what,int extra){applyDashboardBg();endVideoTransition();return true;}});
                    trMp.prepareAsync();
                }catch(Exception e){applyDashboardBg();endVideoTransition();}
            }
            public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st,int w,int h){}
            public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st){return true;}
            public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st){}
        });
        pageRoot.postDelayed(new Runnable(){public void run(){if(transitioning&&trTex==tex)endVideoTransition();}},6500);
    }
    private void fitTr(int vw,int vh){
        if(trTex==null||vw<=0||vh<=0)return;
        float W=trTex.getWidth(),H=trTex.getHeight();if(W<=0||H<=0)return;
        float sc=Math.max(W/vw,H/vh);
        android.graphics.Matrix m=new android.graphics.Matrix();
        m.setScale(sc*vw/W,sc*vh/H,W/2f,H/2f);trTex.setTransform(m);
    }
    private void endVideoTransition(){
        if(trMp!=null){try{trMp.release();}catch(Exception ignore){}trMp=null;}
        if(trTex!=null){android.view.ViewParent pr=trTex.getParent();if(pr instanceof android.view.ViewGroup)((android.view.ViewGroup)pr).removeView(trTex);trTex=null;}
        transitioning=false;
    }
    // Cenário em vídeo: MP4 mudo em loop, desenhado para cobrir a tela (como o CENTER_CROP da imagem).
    private void playBgVideo(File f){
        releaseBgPlayer();bgVideoFile=f;
        if(bgTex==null)return;
        bgTex.setVisibility(View.VISIBLE);
        if(bgTex.isAvailable())startBgVideo();
    }
    private void startBgVideo(){
        if(bgVideoFile==null||bgTex==null||!bgTex.isAvailable()||bgMp!=null)return;
        try{
            bgMp=new android.media.MediaPlayer();
            bgMp.setSurface(new android.view.Surface(bgTex.getSurfaceTexture()));
            bgMp.setDataSource(bgVideoFile.getAbsolutePath());
            bgMp.setLooping(true);bgMp.setVolume(0f,0f);
            bgMp.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener(){public void onPrepared(android.media.MediaPlayer mp){fitBgVideo(mp.getVideoWidth(),mp.getVideoHeight());mp.start();}});
            bgMp.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener(){public boolean onError(android.media.MediaPlayer mp,int what,int extra){releaseBgPlayer();return true;}});
            bgMp.prepareAsync();
        }catch(Exception e){releaseBgPlayer();}
    }
    private void fitBgVideo(int vw,int vh){
        if(bgTex==null||vw<=0||vh<=0)return;
        float W=bgTex.getWidth(),H=bgTex.getHeight();if(W<=0||H<=0)return;
        float sc=Math.max(W/vw,H/vh);
        android.graphics.Matrix m=new android.graphics.Matrix();
        m.setScale(sc*vw/W,sc*vh/H,W/2f,H/2f);bgTex.setTransform(m);
    }
    private void releaseBgPlayer(){if(bgMp!=null){try{bgMp.release();}catch(Exception ignore){}bgMp=null;}}
    private void stopBgVideo(){releaseBgPlayer();bgVideoFile=null;if(bgTex!=null)bgTex.setVisibility(View.GONE);}
    private void pickSceneryMedia(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/mp4"});startActivityForResult(i,REQ_SCENERY);}
    private void applyDashboardBg(){
        if(bgView==null)return;
        String v=curScenery();
        stopBgVideo();
        if(v.startsWith("file:")&&v.endsWith(".mp4")){File vf=new File(sceneriesDir(),v.substring(5));if(vf.exists()){bgView.setImageDrawable(null);bgView.setBackgroundColor(Color.BLACK);playBgVideo(vf);return;}}
        bgView.setBackgroundColor(Color.TRANSPARENT);
        if(v.equals("builtin:green")){bgView.setImageDrawable(null);bgView.setBackgroundColor(Color.rgb(0,177,64));}
        else if(v.startsWith("file:")){
            File f=new File(sceneriesDir(),v.substring(5));Bitmap b=null;
            if(f.exists()){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getAbsolutePath(),o);int sm=1;while(Math.max(o.outWidth,o.outHeight)/sm>2048)sm*=2;BitmapFactory.Options o2=new BitmapFactory.Options();o2.inSampleSize=sm;b=BitmapFactory.decodeFile(f.getAbsolutePath(),o2);}
            if(b!=null)bgView.setImageBitmap(b);else bgView.setImageDrawable(new StudioBg(getResources().getDisplayMetrics().density));
        }else bgView.setImageDrawable(new StudioBg(getResources().getDisplayMetrics().density));
    }
    private TextView sceneRow(String label,boolean selected,View.OnClickListener click,View.OnLongClickListener lc){
        TextView t=text(label,15,selected?cBg:cText,true);t.setGravity(Gravity.CENTER);
        t.setBackground(bg(selected?yellow:cCard,16));t.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(4));t.setLayoutParams(lp);
        t.setOnClickListener(click);if(lc!=null)t.setOnLongClickListener(lc);return t;
    }
    private void showSceneryMenu(){
        final AlertDialog[] dlg=new AlertDialog[1];
        String cur=curScenery();
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(8),dp(16),dp(8));
        box.addView(text("Sala",12,muted,true));
        for(int i=0;i<4;i+=2){
            LinearLayout row2=new LinearLayout(this);row2.setOrientation(LinearLayout.HORIZONTAL);
            for(int j=i;j<i+2;j++){
                final String rid=ROOM_IDS[j];
                TextView t=sceneRow(ROOM_NAMES[j],rid.equals(curRoom()),v->{dlg[0].dismiss();prefs().edit().putString("room",rid).apply();showSceneryMenu();},null);
                t.setTextSize(13);
                LinearLayout.LayoutParams lp2=new LinearLayout.LayoutParams(0,-2,1);lp2.setMargins(dp(3),dp(3),dp(3),dp(3));t.setLayoutParams(lp2);
                row2.addView(t);
            }
            box.addView(row2);
        }
        box.addView(text("Cenário da "+roomName(curRoom()).toLowerCase(),12,muted,true));
        box.addView(text("Do app",12,muted,true));
        final String[][] builtin={{"Estúdio (padrão)","builtin:yellow"},{"Sem fundo (verde)","builtin:green"}};
        for(final String[] b:builtin)box.addView(sceneRow(b[0],cur.equals(b[1]),v->{dlg[0].dismiss();setScenery(b[1]);},null));
        box.addView(text("Importados",12,muted,true));
        File[] fs=sceneriesDir().listFiles();
        if(fs!=null){Arrays.sort(fs);int n=0;for(final File f:fs){n++;final String name=f.getName();final String label=(name.endsWith(".mp4")?"Vídeo ":"Cenário ")+n;
            box.addView(sceneRow(label,cur.equals("file:"+name),v->{dlg[0].dismiss();setScenery("file:"+name);},v->{dlg[0].dismiss();confirmDeleteScenery(f,label);return true;}));}}
        box.addView(sceneRow("+ Importar imagem ou MP4 (até 5 s)",false,v->{dlg[0].dismiss();pickSceneryMedia();},null));
        ScrollView sv=new ScrollView(this);sv.addView(box);
        dlg[0]=newDialog().setTitle("Cenários").setView(sv).setNegativeButton("Fechar",null).create();
        present(dlg[0]);
    }
    private void confirmDeleteScenery(final File f,String label){
        newDialog().setTitle("Remover cenário").setMessage("Remover \""+label+"\"?")
            .setPositiveButton("Remover",(d,w)->{String key="file:"+f.getName();f.delete();for(String rm:ROOM_IDS)if(sceneryOf(rm).equals(key))prefs().edit().putString("scenery_"+rm,"builtin:yellow").apply();applyDashboardBg();})
            .setNegativeButton("Cancelar",null).show();
    }
    private void saveScenery(final Uri uri){
        new Thread(new Runnable(){public void run(){
            String name=null;String err=null;
            try{
                String mime=getContentResolver().getType(uri);
                if(mime!=null&&mime.startsWith("video/")){
                    android.media.MediaMetadataRetriever mr=new android.media.MediaMetadataRetriever();long dur=0;
                    try{mr.setDataSource(MainActivity.this,uri);String ds=mr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);dur=ds==null?0:Long.parseLong(ds);}
                    finally{try{mr.release();}catch(Exception ignore){}}
                    if(dur<=0)throw new Exception("não consegui ler o vídeo");
                    if(dur>5300)throw new Exception("o vídeo tem "+(dur/1000)+" s e o limite é 5 s");
                    File vdir=sceneriesDir();vdir.mkdirs();name=System.currentTimeMillis()+".mp4";
                    InputStream vin=getContentResolver().openInputStream(uri);FileOutputStream vfo=new FileOutputStream(new File(vdir,name));
                    byte[] vbuf=new byte[65536];int vn;while((vn=vin.read(vbuf))>0)vfo.write(vbuf,0,vn);vin.close();vfo.close();
                }else{
                    Bitmap b=decodeUri(uri,2048);if(b==null)throw new Exception("imagem inválida");
                    File dir=sceneriesDir();dir.mkdirs();name=System.currentTimeMillis()+".jpg";
                    FileOutputStream fos=new FileOutputStream(new File(dir,name));b.compress(Bitmap.CompressFormat.JPEG,92,fos);fos.close();
                }
            }catch(Exception ex){err=Friendly.why(ex);name=null;}
            final String fn=name,fe=err;
            runOnUiThread(new Runnable(){public void run(){
                if(fn==null)Toast.makeText(MainActivity.this,"Não consegui importar: "+fe,Toast.LENGTH_SHORT).show();
                else setScenery("file:"+fn);
            }});
        }}).start();
    }
    private File modelsDir(){return new File(getFilesDir(),"models");}
    private String selectedId(){return prefs().getString("selected_model","default");}
    private String modelName(File dir){try{java.io.BufferedReader r=new java.io.BufferedReader(new java.io.FileReader(new File(dir,"name.txt")));String n=r.readLine();r.close();return n==null||n.trim().isEmpty()?"Modelo":n.trim();}catch(Exception e){return "Modelo";}}

    /** Setas: escolher entre os modelos já importados (com ícone), por categoria (Vtuber / Pngtuber), ou importar um novo. */
    private String norm(String t){return java.text.Normalizer.normalize(t==null?"":t,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase().trim();}
    private View tagged(View tile,String nm){tile.setTag(norm(nm));return tile;}
    private void showModelPicker(){
        final AlertDialog[] dlg=new AlertDialog[1];
        final boolean[] png={isPngId(selectedId())};
        final android.util.DisplayMetrics dm=getResources().getDisplayMetrics();
        final boolean land=dm.widthPixels>dm.heightPixels;
        final int dw=Math.min((int)(dm.widthPixels*0.94f),dp(760)),pad=dp(18),gc=dp(10);
        final int cols=Math.max(2,(dw-2*pad+gc)/(dp(104)+gc));
        final int cw=(dw-2*pad-(cols-1)*gc)/cols,ph=(int)(cw*0.82f),chh=ph+dp(28);
        final int chrome=dp(land?196:262);
        final int maxScroll=Math.max(chh+gc,(int)(dm.heightPixels*0.9f)-chrome);

        // Cabeçalho: título + botão fechar amarelo
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("Modelos",22,cText,true);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageView close=new ImageView(this);close.setImageDrawable(new LineIcon(0,cBg,dp(20)));close.setScaleType(ImageView.ScaleType.CENTER);close.setBackground(bg(yellow,999));
        close.setOnClickListener(v->dlg[0].dismiss());
        head.addView(close,new LinearLayout.LayoutParams(dp(38),dp(38)));

        // Busca com lupa
        final EditText filter=new EditText(this);
        filter.setHint("Filtrar modelos...");filter.setSingleLine(true);filter.setTextSize(14);filter.setTextColor(cText);filter.setHintTextColor(muted);
        GradientDrawable fg=bg(cBg,16);fg.setStroke(dp(2),cBorder);filter.setBackground(fg);
        filter.setPadding(dp(14),0,dp(14),0);filter.setCompoundDrawablePadding(dp(10));
        filter.setCompoundDrawablesRelativeWithIntrinsicBounds(new LineIcon(1,muted,dp(20)),null,null,null);

        // Abas Vtuber / Pngtuber
        LinearLayout seg=new LinearLayout(this);seg.setOrientation(LinearLayout.HORIZONTAL);seg.setBackground(bg(cCard,999));seg.setPadding(dp(4),dp(4),dp(4),dp(4));
        final TextView tv=text("Vtuber",14,cText,true),tp=text("Pngtuber",14,cText,true);
        tv.setGravity(Gravity.CENTER);tp.setGravity(Gravity.CENTER);
        seg.addView(tv,new LinearLayout.LayoutParams(0,dp(36),1));seg.addView(tp,new LinearLayout.LayoutParams(0,dp(36),1));

        // Grade de modelos
        final android.widget.GridLayout grid=new android.widget.GridLayout(this);grid.setColumnCount(cols);
        final ScrollView sv=new ScrollView(this);sv.setVerticalScrollBarEnabled(false);sv.addView(grid);
        final Runnable applyFilter=new Runnable(){public void run(){
            String q=norm(filter.getText().toString());int shown=0;
            for(int i=0;i<grid.getChildCount();i++){
                View t=grid.getChildAt(i);Object tag=t.getTag();
                boolean vis=tag==null||q.isEmpty()||((String)tag).contains(q);
                t.setVisibility(vis?View.VISIBLE:View.GONE);if(vis)shown++;
            }
            int rows=Math.max(1,(shown+cols-1)/cols);
            sv.getLayoutParams().height=Math.min(rows*(chh+gc),maxScroll);sv.requestLayout();
        }};
        filter.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence a,int b,int c,int d){}
            public void onTextChanged(CharSequence a,int b,int c,int d){}
            public void afterTextChanged(android.text.Editable e){applyFilter.run();}
        });

        // Botões de baixo
        final LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.HORIZONTAL);bottom.setGravity(Gravity.CENTER_VERTICAL);

        final Runnable[] fill=new Runnable[1];
        fill[0]=new Runnable(){public void run(){
            destroyPreviews();
            grid.removeAllViews();
            String sel=selectedId();
            if(!png[0])grid.addView(tagged(modelCard("Verônica",modelPreview("default",false,"Verônica"),sel.equals("default"),cw,ph,chh,gc,v->{dlg[0].dismiss();selectModel("default");},v->{dlg[0].dismiss();showModelOptions("default","Verônica",false);return true;}),"Verônica"));
            else grid.addView(tagged(modelCard("Verônica",modelPreview("default_png",true,"Verônica"),sel.equals("default_png"),cw,ph,chh,gc,v->{dlg[0].dismiss();selectModel("default_png");},v->{dlg[0].dismiss();showModelOptions("default_png","Verônica (Pngtuber)",false);return true;}),"Verônica"));
            File[] ds=modelsDir().listFiles();
            if(ds!=null){Arrays.sort(ds);for(final File d:ds){
                if(!d.isDirectory()||!new File(d,"content").exists())continue;
                if(new File(d,"kind.txt").exists()!=png[0])continue;
                final String id=d.getName(),nm=modelName(d);
                grid.addView(tagged(modelCard(nm,modelPreview(id,png[0],nm),sel.equals(id),cw,ph,chh,gc,v->{dlg[0].dismiss();selectModel(id);},v->{dlg[0].dismiss();showModelOptions(id,nm,true);return true;}),nm));
            }}
            segStyle(tv,!png[0]);segStyle(tp,png[0]);
            bottom.removeAllViews();
            if(png[0]){
                bottom.addView(softBtn("Criar novo",true,v->{dlg[0].dismiss();showCreatePngtuber();}));
                bottom.addView(softBtn("Importar ZIP",false,v->{dlg[0].dismiss();importPng=true;chooseCharacterZip();}));
            }else bottom.addView(softBtn("Importar",true,v->{dlg[0].dismiss();importPng=false;chooseCharacterZip();}));
            applyFilter.run();
        }};
        tv.setOnClickListener(v->{png[0]=false;fill[0].run();});
        tp.setOnClickListener(v->{png[0]=true;fill[0].run();});

        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(pad,dp(16),pad,dp(14));
        box.addView(head,new LinearLayout.LayoutParams(-1,-2));
        if(land){
            LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams f1=new LinearLayout.LayoutParams(0,dp(44),1);f1.rightMargin=gc;
            r.addView(filter,f1);r.addView(seg,new LinearLayout.LayoutParams(0,dp(44),1));
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(10);box.addView(r,rp);
        }else{
            LinearLayout.LayoutParams f1=new LinearLayout.LayoutParams(-1,dp(46));f1.topMargin=dp(12);box.addView(filter,f1);
            LinearLayout.LayoutParams s1=new LinearLayout.LayoutParams(-1,dp(44));s1.topMargin=dp(10);box.addView(seg,s1);
        }
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,maxScroll);sp.topMargin=dp(12);box.addView(sv,sp);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(8);box.addView(bottom,bp);
        box.setLayoutParams(new FrameLayout.LayoutParams(dw,-2));
        fill[0].run();
        dlg[0]=newDialog().create();
        dlg[0].setView(box,0,0,0,0);
        closers.put(dlg[0],new Runnable(){public void run(){destroyPreviews();}});
        present(dlg[0]);
        try{dlg[0].getWindow().setLayout(dw,WindowManager.LayoutParams.WRAP_CONTENT);}catch(Exception ignored){}
    }
    private void segStyle(TextView t,boolean on){t.setTextColor(on?cBg:muted);t.setBackground(on?bg(yellow,999):null);}
    /** Botão arredondado das janelas: primário (amarelo) ou secundário (escuro com borda). */
    private Button softBtn(String label,boolean primary,View.OnClickListener l){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setStateListAnimator(null);b.setMinWidth(0);b.setMinHeight(0);b.setMinimumWidth(0);b.setMinimumHeight(0);b.setOnClickListener(l);
        if(primary){b.setTextColor(cBg);b.setBackground(bg(yellow,999));}
        else{b.setTextColor(cText);GradientDrawable g=bg(cCard,999);g.setStroke(dp(2),cBorder);b.setBackground(g);}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(46));lp.rightMargin=dp(10);b.setPadding(dp(22),0,dp(22),0);b.setLayoutParams(lp);return b;
    }
    /** Cartão do modelo: preview em fundo claro + nome embaixo. Selecionado = borda amarela. */
    private View modelCard(String name,View preview,boolean selected,int cw,int ph,int chh,int gc,View.OnClickListener click,View.OnLongClickListener lc){
        LinearLayout t=new LinearLayout(this);t.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g=bg(cCard,16);g.setStroke(dp(selected?3:1),selected?yellow:cBorder);t.setBackground(g);
        t.setOutlineProvider(new ViewOutlineProvider(){public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(16));}});t.setClipToOutline(true);
        t.setPadding(dp(selected?3:1),dp(selected?3:1),dp(selected?3:1),dp(selected?3:1));
        FrameLayout pv=new FrameLayout(this){@Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}};
        pv.setBackgroundColor(0xFFDCDDE4);
        pv.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        t.addView(pv,new LinearLayout.LayoutParams(-1,ph-dp(selected?5:1)));
        TextView n=text(name,12,selected?yellow:cText,selected);n.setSingleLine();n.setEllipsize(TextUtils.TruncateAt.END);n.setGravity(Gravity.CENTER);n.setPadding(dp(6),0,dp(6),0);
        t.addView(n,new LinearLayout.LayoutParams(-1,dp(28)));
        android.widget.GridLayout.LayoutParams lp=new android.widget.GridLayout.LayoutParams();lp.width=cw;lp.height=chh;lp.setMargins(0,0,gc,gc);t.setLayoutParams(lp);
        t.setOnClickListener(click);if(lc!=null)t.setOnLongClickListener(lc);
        return t;
    }
    // ---------- Preview do modelo no lugar de imagem de ícone ----------
    private File findHtml(File dir){
        File a=new File(dir,"vtuber.html");if(a.exists())return a;
        File b=new File(dir,"index.html");if(b.exists())return b;
        File[] fs=dir.listFiles();
        if(fs!=null)for(File f:fs)if(f.isDirectory()){File x=findHtml(f);if(x!=null)return x;}
        return null;
    }
    private void destroyPreviews(){
        for(WebView w:new ArrayList<WebView>(previews)){
            try{w.stopLoading();android.view.ViewParent par=w.getParent();if(par instanceof android.view.ViewGroup)((android.view.ViewGroup)par).removeView(w);w.destroy();}catch(Throwable ignored){}
        }
        previews.clear();
    }
    private View modelPreview(String id,boolean png,String name){
        try{return png?pngPreview(id):webPreview(id);}
        catch(Throwable t){TextView l=text(name.substring(0,1).toUpperCase(),26,yellow,true);l.setGravity(Gravity.CENTER);return l;}
    }
    /** Pngtuber: o preview é a própria imagem "parado" do personagem. */
    private View pngPreview(String id)throws Exception{
        Bitmap b;boolean swap=prefs().getBoolean("png_swap_"+id,false);
        if(id.equals("default_png"))b=thumbAsset(swap?"png_talk.png":"png_idle.png",256);
        else{
            File content=new File(new File(modelsDir(),id),"content");
            String[] pr=pngPair(content);
            b=thumb(new File(content,swap?pr[1]:pr[0]),256);
        }
        if(b==null)throw new Exception("sem imagem");
        ImageView iv=new ImageView(this);iv.setScaleType(ImageView.ScaleType.CENTER_CROP);iv.setImageBitmap(waistUp(b));return iv;
    }
    /** Recorte quadrado do Pngtuber: do topo do personagem até mais ou menos a cintura. */
    private Bitmap waistUp(Bitmap b){
        try{
            int w=b.getWidth(),h=b.getHeight();int[] px=new int[w*h];b.getPixels(px,0,w,0,0,w,h);
            int x0=w,x1=-1,y0=h,y1=-1;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int c=px[y*w+x];int a=c>>>24,r=(c>>16)&255,g=(c>>8)&255,bl=c&255;
                boolean empty=a<10||(a>=250&&r>=232&&g>=232&&bl>=232);
                if(!empty){if(x<x0)x0=x;if(x>x1)x1=x;if(y<y0)y0=y;if(y>y1)y1=y;}
            }
            if(x1<0)return b;
            int bh=y1-y0+1;
            int side=Math.max(1,(int)(bh*0.55f));
            if(side>w)side=w;
            if(y0+side>h)side=h-y0;
            if(side<1)return b;
            int left=(x0+x1)/2-side/2;if(left<0)left=0;if(left+side>w)left=w-side;
            return Bitmap.createBitmap(b,left,y0,side,side);
        }catch(Throwable t){return b;}
    }
    /** Preview parado: sem câmera o modelo faria "olhar aleatório" e piscar sozinho. Desliga isso, espera o modelo aparecer e congela o quadro. */
    private void freezePreview(final WebView wv){
        final int[] n={0};
        final Runnable[] r=new Runnable[1];
        r[0]=new Runnable(){public void run(){
            if(!previews.contains(wv))return;
            n[0]++;
            String js="(function(){try{window.idleLook=function(){};var b=document.getElementById('blink');if(b)b.checked=false;if(typeof T!=='undefined'){T.x=0;T.y=0;T.z=0}if(typeof crop==='undefined')return 2;return crop?1:0}catch(e){return 2}})()";
            wv.evaluateJavascript(js,new ValueCallback<String>(){public void onReceiveValue(String v){
                if(!previews.contains(wv))return;
                boolean done=("1".equals(v)&&n[0]>=5)||("2".equals(v)&&n[0]>=10)||n[0]>=40;
                if(done)wv.evaluateJavascript("window.requestAnimationFrame=function(){return 0};",null);
                else ui.postDelayed(r[0],300);
            }});
        }};
        ui.postDelayed(r[0],300);
    }
    /** Vtuber: o modelo roda de verdade numa mini janela (parado, sem câmera e sem internet). */
    private View webPreview(String id)throws Exception{
        String url;
        WebViewAssetLoader.Builder lb=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this));
        if(id.equals("default"))url="https://appassets.androidplatform.net/assets/vtuber.html";
        else{
            File content=new File(new File(modelsDir(),id),"content");
            File html=findHtml(content);if(html==null)throw new Exception("sem página");
            lb.addPathHandler("/imported/",new WebViewAssetLoader.InternalStoragePathHandler(this,content));
            url="https://appassets.androidplatform.net/imported/"+Uri.encode(relPath(content,html),"/");
        }
        final WebViewAssetLoader loader=lb.build();
        WebView wv=new WebView(this);
        wv.setBackgroundColor(Color.TRANSPARENT);
        WebSettings ws=wv.getSettings();ws.setJavaScriptEnabled(true);ws.setDomStorageEnabled(true);ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);
        wv.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                WebResourceResponse r=loader.shouldInterceptRequest(request.getUrl());
                if(r!=null)return r;
                return new WebResourceResponse("text/plain","utf-8",404,"Not Found",null,new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView view,String url){freezePreview(view);}
        });
        previews.add(wv);
        wv.loadUrl(url);
        FrameLayout fl=new FrameLayout(this);fl.setClipChildren(true);
        wv.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
        wv.setScaleX(1.9f);wv.setScaleY(1.9f);
        wv.addOnLayoutChangeListener((v,a,b,c,d,e,f,g,h)->{v.setPivotX((c-a)/2f);v.setPivotY(0f);});
        fl.addView(wv);
        return fl;
    }
    /** Toque longo num modelo: perguntar se quer exportar (ZIP) ou deletar. */
    private void showModelOptions(final String id,final String nm,boolean deletable){
        final boolean editable=deletable&&isPngId(id);
        final ArrayList<String> items=new ArrayList<String>();
        if(editable)items.add("Editar");
        items.add("Exportar");
        if(deletable)items.add("Deletar");
        newDialog().setTitle(nm)
            .setItems(items.toArray(new String[0]),(d,i)->{String it=items.get(i);if(it.equals("Editar"))showPngEditor(id);else if(it.equals("Exportar"))startExport(id,nm);else confirmDeleteModel(id,nm);})
            .setNegativeButton("Cancelar",null).show();
    }
    private void startExport(String id,String nm){
        pendingExportId=id;
        String safe=nm.replaceAll("[^A-Za-z0-9À-ÿ _().-]","").trim();if(safe.isEmpty())safe="modelo";
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_TITLE,safe+".zip");
        startActivityForResult(i,REQ_EXPORT);
    }
    private void zipPut(java.util.zip.ZipOutputStream z,String name,InputStream in)throws Exception{
        z.putNextEntry(new java.util.zip.ZipEntry(name));byte[] buf=new byte[8192];int r;while((r=in.read(buf))!=-1)z.write(buf,0,r);in.close();z.closeEntry();
    }
    private void zipTree(java.util.zip.ZipOutputStream z,File base,File f)throws Exception{
        if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)zipTree(z,base,x);}
        else zipPut(z,relPath(base,f),new java.io.FileInputStream(f));
    }
    /** Grava o ZIP do modelo no local escolhido (o mesmo formato que o app importa). */
    private void writeExport(final Uri uri,final String id){
        if(status!=null)status.setText("Exportando modelo...");
        new Thread(new Runnable(){public void run(){
            String msg;
            try{
                java.io.OutputStream os=getContentResolver().openOutputStream(uri);if(os==null)throw new Exception("Não foi possível criar o arquivo.");
                java.util.zip.ZipOutputStream z=new java.util.zip.ZipOutputStream(new java.io.BufferedOutputStream(os));
                if(id.equals("default")){zipPut(z,"vtuber.html",getAssets().open("vtuber.html"));zipPut(z,"Atalhos/atalhos.json",getAssets().open("default_atalhos.json"));zipPut(z,"Atalhos/careca.png",getAssets().open("default_careca.png"));zipPut(z,"Atalhos/energetico.png",getAssets().open("default_energetico.png"));}
                else if(id.equals("default_png")){zipPut(z,"parado.png",getAssets().open("png_idle.png"));zipPut(z,"falando.png",getAssets().open("png_talk.png"));}
                else{File c=new File(new File(modelsDir(),id),"content");zipTree(z,c,c);}
                z.close();
                msg="Modelo exportado.";
            }catch(Exception ex){msg="Não consegui exportar: "+Friendly.why(ex);}
            final String m=msg;
            runOnUiThread(new Runnable(){public void run(){if(status!=null)status.setText(m);}});
        }}).start();
    }
    private void confirmDeleteModel(final String id,final String nm){
        newDialog().setTitle("Remover modelo").setMessage("Remover \""+nm+"\" da lista?")
            .setPositiveButton("Remover",(d,w)->{deleteTree(new File(modelsDir(),id));if(selectedId().equals(id))selectModel("default");else{if(status!=null)status.setText("Modelo removido.");}})
            .setNegativeButton("Cancelar",null).show();
    }
    private boolean isPng(){return new File(getFilesDir(),"characters/current/pngtuber.flag").exists();}
    private boolean isPngId(String id){return id.equals("default_png")||(!id.equals("default")&&new File(new File(modelsDir(),id),"kind.txt").exists());}
    private void swapPng(){String id=selectedId();prefs().edit().putBoolean("png_swap_"+id,!prefs().getBoolean("png_swap_"+id,false)).apply();selectModel(id);}
    private void copyAsset(String name,File dst)throws Exception{
        InputStream in=getAssets().open(name);FileOutputStream out=new FileOutputStream(dst);byte[] buf=new byte[8192];int r;while((r=in.read(buf))!=-1)out.write(buf,0,r);out.close();in.close();
    }
    private boolean hasToken(String name,String[] keys){
        String[] toks=name.split("[^a-z0-9]+");
        for(String k:keys){if(k.length()>=4){if(name.contains(k))return true;}else for(String t:toks)if(t.equals(k))return true;}
        return false;
    }
    private String relPath(File base,File f){return f.getAbsolutePath().substring(base.getAbsolutePath().length()+1).replace('\\','/');}
    /** Descobre quais das imagens do ZIP é "parado" e qual é "falando" (pelo nome; sem pista, a 1ª em ordem alfabética é parada e a 2ª é falando). */
    private String[] pngPair(File dir)throws Exception{
        ArrayList<File> all=new ArrayList<File>();collectImages(dir,all);
        if(all.size()<2)throw new Exception("O ZIP do Pngtuber precisa ter 2 imagens (parado e falando).");
        java.util.Collections.sort(all);
        String[] talkK={"fala","falando","talk","speak","open","abert","grito","boca","mouth","on"};
        String[] idleK={"calad","idle","closed","fechad","silen","mute","quiet","parad","rest","neutral","off"};
        File idle=null,talk=null;
        for(File f:all){String n=f.getName().toLowerCase();boolean t=hasToken(n,talkK),i=hasToken(n,idleK);
            if(t&&!i&&talk==null)talk=f;else if(i&&!t&&idle==null)idle=f;}
        for(File f:all){if(idle==null&&f!=talk)idle=f;}
        for(File f:all){if(talk==null&&f!=idle)talk=f;}
        return new String[]{relPath(dir,idle),relPath(dir,talk)};
    }
    /** Gera a página do Pngtuber (index.html) na pasta do modelo atual a partir do modelo em assets/pngtuber.html. */
    private void buildPngtuberPage(File cur,String idle,String talk,String id)throws Exception{
        if(prefs().getBoolean("png_swap_"+id,false)){String t=idle;idle=talk;talk=t;}
        InputStream in=getAssets().open("pngtuber.html");java.io.ByteArrayOutputStream bo=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int r;while((r=in.read(buf))!=-1)bo.write(buf,0,r);in.close();
        String v="?v="+System.currentTimeMillis();
        String html=bo.toString("UTF-8").replace("__IDLE__",Uri.encode(idle,"/")+v).replace("__TALK__",Uri.encode(talk,"/")+v);
        new File(cur,"vtuber.html").delete();
        FileOutputStream fo=new FileOutputStream(new File(cur,"index.html"));fo.write(html.getBytes("UTF-8"));fo.close();
        new File(cur,"pngtuber.flag").createNewFile();
    }
    /** Ativa um modelo: copia para characters/current (o que o overlay lê) e recarrega o overlay se estiver ligado. */
    private void selectModel(final String id){
        if(status!=null)status.setText("Carregando modelo...");
        new Thread(new Runnable(){public void run(){
            String msg;boolean pg=false;
            try{
                File cur=new File(getFilesDir(),"characters/current");
                deleteTree(cur);
                String nm="Verônica";
                if(id.equals("default_png")){
                    pg=true;cur.mkdirs();copyAsset("png_idle.png",new File(cur,"idle.png"));copyAsset("png_talk.png",new File(cur,"talk.png"));
                    buildPngtuberPage(cur,"idle.png","talk.png",id);nm="Verônica (Pngtuber)";
                }else if(!id.equals("default")){
                    File base=new File(modelsDir(),id);copyTree(new File(base,"content"),cur);nm=modelName(base);
                    if(new File(base,"kind.txt").exists()){pg=true;String[] pr=pngPair(cur);buildPngtuberPage(cur,pr[0],pr[1],id);}
                }
                prefs().edit().putString("selected_model",id).apply();
                msg=nm+" selecionada.";
            }catch(Exception ex){msg="Não consegui trocar de personagem: "+Friendly.why(ex);}
            final String m=msg;
            runOnUiThread(new Runnable(){public void run(){
                if(status!=null)status.setText(m);
                if(OverlayService.isRunning()){
                    boolean ok=isPng()?hasMic():hasCamera();
                    if(ok)startService(new Intent(MainActivity.this,OverlayService.class).setAction(OverlayService.ACTION_RELOAD_CHARACTER));
                    else requestPermissions(isPng()?new String[]{Manifest.permission.RECORD_AUDIO}:new String[]{Manifest.permission.CAMERA},REQ_RELOAD_PERM);
                }
            }});
        }}).start();
    }
    private void copyTree(File src,File dst)throws Exception{
        if(src.isDirectory()){dst.mkdirs();File[] a=src.listFiles();if(a!=null)for(File f:a)copyTree(f,new File(dst,f.getName()));}
        else{File p=dst.getParentFile();if(p!=null)p.mkdirs();InputStream in=new java.io.FileInputStream(src);FileOutputStream out=new FileOutputStream(dst);byte[] buf=new byte[8192];int r;while((r=in.read(buf))!=-1)out.write(buf,0,r);out.close();in.close();}
    }
    private String displayName(Uri uri){
        String n="Modelo";android.database.Cursor c=null;
        try{c=getContentResolver().query(uri,null,null,null,null);if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);if(i>=0&&c.getString(i)!=null)n=c.getString(i);}}catch(Exception ignored){}finally{if(c!=null)c.close();}
        if(n.toLowerCase().endsWith(".zip"))n=n.substring(0,n.length()-4);
        return n;
    }
    private void collectImages(File d,ArrayList<File> out){File[] a=d.listFiles();if(a==null)return;for(File f:a){if(f.isDirectory()){if(!f.getName().equalsIgnoreCase("atalhos"))collectImages(f,out);}else{String l=f.getName().toLowerCase();if(l.endsWith(".png")||l.endsWith(".jpg")||l.endsWith(".jpeg")||l.endsWith(".webp"))out.add(f);}}}
    /** Importa um ZIP para a biblioteca (models/<id>/content) e já seleciona o modelo. */
    private void importCharacter(final Uri uri,final boolean png){
        if(status!=null)status.setText("Importando modelo...");
        new Thread(new Runnable(){public void run(){
            final String id=String.valueOf(System.currentTimeMillis());
            File base=new File(modelsDir(),id),content=new File(base,"content");
            try{
                content.mkdirs();boolean found=false;long total=0;
                InputStream raw=getContentResolver().openInputStream(uri);if(raw==null)throw new Exception("Não foi possível abrir o ZIP.");
                ZipInputStream zis=new ZipInputStream(new BufferedInputStream(raw));ZipEntry e;byte[] buf=new byte[8192];
                while((e=zis.getNextEntry())!=null){
                    String n=e.getName().replace('\\','/');
                    if(n.startsWith("/")||n.contains("../")||n.equals(".."))throw new Exception("Caminho inválido no ZIP.");
                    File out=new File(content,n);
                    if(!out.getCanonicalPath().startsWith(content.getCanonicalPath()+File.separator))throw new Exception("Arquivo fora da pasta permitida.");
                    if(e.isDirectory())out.mkdirs();
                    else{
                        File par=out.getParentFile();if(par!=null)par.mkdirs();
                        FileOutputStream fos=new FileOutputStream(out);int r;
                        while((r=zis.read(buf))!=-1){total+=r;if(total>80L*1024*1024){fos.close();throw new Exception("ZIP maior que 80 MB.");}fos.write(buf,0,r);}
                        fos.close();
                        String low=n.toLowerCase();
                        if(low.endsWith("/vtuber.html")||low.equals("vtuber.html")||low.endsWith("/index.html")||low.equals("index.html"))found=true;
                    }
                    zis.closeEntry();
                }
                zis.close();
                if(png){pngPair(content);}
                else if(!found)throw new Exception("ZIP sem vtuber.html ou index.html compatível.");
                java.io.FileWriter fw=new java.io.FileWriter(new File(base,"name.txt"));fw.write(displayName(uri));fw.close();
                if(png){java.io.FileWriter kw=new java.io.FileWriter(new File(base,"kind.txt"));kw.write("pngtuber");kw.close();}
                runOnUiThread(new Runnable(){public void run(){selectModel(id);}});
            }catch(final Exception ex){
                deleteTree(base);
                runOnUiThread(new Runnable(){public void run(){if(status!=null)status.setText("Não consegui concluir: "+Friendly.why(ex));}});
            }
        }}).start();
    }
    @Override public void onBackPressed(){if(liveSetupOpen){showDashboard();return;}super.onBackPressed();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){
        super.onConfigurationChanged(c);
        // girou a tela: refaz a tela inicial com as medidas novas (só se ela estiver na tela)
        if(pageRoot!=null&&pageRoot.isAttachedToWindow())showDashboard();
    }
    @Override protected void onResume(){super.onResume();OverlayService.setAppVisible(true);if(status!=null)updateRecState();applyDashboardBg();if(sideCol!=null)sideCol.setVisibility(AppSettings.sideButtons(this)?View.VISIBLE:View.GONE);}
    @Override protected void onPause(){super.onPause();for(AlertDialog d:new ArrayList<AlertDialog>(layered))d.dismiss();removeScrim();OverlayService.setAppVisible(false);}
    @Override protected void onDestroy(){super.onDestroy();destroyPreviews();try{AppSettings.main(this).unregisterOnSharedPreferenceChangeListener(roomListener);}catch(Exception ignored){}stopBgVideo();endVideoTransition();}
    private boolean hasCamera(){return checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;}
    private void refreshStatus(){if(status==null)return;if(isPng()&&!hasMic())status.setText("Permita o microfone (Camadas > Ligar) para o Pngtuber falar.");else if(!isPng()&&!hasCamera())status.setText("Permita a câmera (Camadas > Ligar) para o Vtuber seguir seu rosto.");else if(!Settings.canDrawOverlays(this))status.setText("Permita \"aparecer sobre outros apps\" (Camadas > Ligar).");else status.setText(new File(getFilesDir(),"characters/current").exists()?"Tudo pronto. Personagem importada.":"Tudo pronto. Verônica selecionada.");}
    // ---------- Criar Pngtuber a partir de 2 imagens (o ZIP fica só para exportar/importar entre amigos) ----------
    private void showCreatePngtuber(){showPngEditor(null);}
    private View pngRow(ImageView thumb,String label,final int req){
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(0,dp(8),0,dp(4));
        thumb.setBackground(bg(cCard,14));thumb.setScaleType(ImageView.ScaleType.FIT_CENTER);thumb.setPadding(dp(6),dp(6),dp(6),dp(6));
        row.addView(thumb,new LinearLayout.LayoutParams(dp(64),dp(64)));
        TextView t=text(label,14,Color.WHITE,false);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(0,-2,1);tl.leftMargin=dp(12);row.addView(t,tl);
        Button b=pill("Escolher",v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,req);});
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(110),dp(44)));row.addView(b);
        return row;
    }
    private void onPngPicked(boolean idle,Uri u){
        try{
            Bitmap b=decodeUri(u,256);if(b==null)throw new Exception("imagem inválida");
            if(idle){pngIdleUri=u;pngIdleThumb.setImageBitmap(b);}else{pngTalkUri=u;pngTalkThumb.setImageBitmap(b);}
        }catch(Exception e){Toast.makeText(this,"Não consegui abrir essa imagem.",Toast.LENGTH_SHORT).show();}
    }
    private void savePngImage(Uri u,File out)throws Exception{
        Bitmap b=decodeUri(u,2048);if(b==null)throw new Exception("imagem inválida");
        FileOutputStream fos=new FileOutputStream(out);b.compress(Bitmap.CompressFormat.PNG,100,fos);fos.close();
    }
    // ---------- Criar / editar Pngtuber: 2 imagens + até 3 atalhos opcionais ----------
    private static final int REQ_PNG_SC=40;
    // Cada atalho tem 2 imagens: parado (posição i) e falando (posição i+3).
    private final Uri[] scUri=new Uri[6];
    private final String[] scFile=new String[6],scOld=new String[6],scNm=new String[3];
    private final ImageView[] scThumb=new ImageView[6];
    private final EditText[] scNameEd=new EditText[3];

    private File atalhosDir(File content){
        File[] fs=content.listFiles();
        if(fs!=null)for(File f:fs)if(f.isDirectory()&&f.getName().equalsIgnoreCase("atalhos"))return f;
        return null;
    }
    private boolean scHas(int i){return scUri[i]!=null||scFile[i]!=null||scUri[i+3]!=null||scFile[i+3]!=null;}
    private int scCount(){int n=0;for(int i=0;i<3;i++)if(scHas(i))n++;return n;}
    private void scClear(int i){for(int k=i;k<6;k+=3){scUri[k]=null;scFile[k]=null;if(scThumb[k]!=null)scThumb[k].setImageDrawable(null);}if(scNameEd[i]!=null)scNameEd[i].setText("");}
    /** Lê os atalhos já salvos do Pngtuber (Atalhos/atalhos.json), no máximo 3 com imagem. */
    private void loadPngShortcuts(File content){
        for(int i=0;i<6;i++){scUri[i]=null;scFile[i]=null;scOld[i]=null;}
        for(int i=0;i<3;i++)scNm[i]=null;
        try{
            File dir=atalhosDir(content);if(dir==null)return;
            File js=new File(dir,"atalhos.json");if(!js.exists())return;
            java.io.BufferedReader r=new java.io.BufferedReader(new java.io.FileReader(js));StringBuilder sb=new StringBuilder();String ln;
            while((ln=r.readLine())!=null)sb.append(ln).append('\n');r.close();
            String txt=sb.toString().trim();
            org.json.JSONArray arr=txt.startsWith("[")?new org.json.JSONArray(txt):new org.json.JSONObject(txt).optJSONArray("atalhos");
            if(arr==null)return;
            int k=0;
            for(int i=0;i<arr.length()&&k<3;i++){
                org.json.JSONObject o=arr.optJSONObject(i);if(o==null)continue;
                String im=o.optString("imagem",""),it=o.optString("imagemFalando","");
                if(!im.isEmpty()&&!new File(dir,im).exists())im="";
                if(!it.isEmpty()&&!new File(dir,it).exists())it="";
                if(im.isEmpty()&&it.isEmpty())continue;
                if(!im.isEmpty()){scFile[k]=im;scOld[k]=im;}
                if(!it.isEmpty()&&!it.equals(im)){scFile[k+3]=it;scOld[k+3]=it;}
                scNm[k]=o.optString("nome","");k++;
            }
        }catch(Exception ignored){}
    }
    private View scThumbBox(final int k,String label,File dir){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView th=new ImageView(this);scThumb[k]=th;
        th.setBackground(bg(cCard,14));th.setScaleType(ImageView.ScaleType.FIT_CENTER);th.setPadding(dp(6),dp(6),dp(6),dp(6));
        if(scFile[k]!=null&&dir!=null){Bitmap b=thumb(new File(dir,scFile[k]),256);if(b!=null)th.setImageBitmap(b);}
        c.addView(th,new LinearLayout.LayoutParams(dp(72),dp(72)));
        Button ch=new Button(this);ch.setText(label);ch.setAllCaps(false);ch.setTextColor(cBg);ch.setTextSize(12);ch.setBackground(bg(yellow,999));
        ch.setOnClickListener(v->{Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);in.addCategory(Intent.CATEGORY_OPENABLE);in.setType("image/*");startActivityForResult(in,REQ_PNG_SC+k);});
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(72),dp(36));bp.topMargin=dp(4);
        c.addView(ch,bp);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=dp(10);c.setLayoutParams(cp);
        return c;
    }
    private View scRow(final int i,File dir){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(0,dp(10),0,dp(4));
        EditText ne=new EditText(this);scNameEd[i]=ne;
        ne.setHint("Nome do atalho "+(i+1));ne.setSingleLine(true);ne.setTextColor(Color.WHITE);ne.setHintTextColor(0xFF9A9AA5);
        ne.setBackground(bg(cCard,14));ne.setPadding(dp(12),dp(6),dp(12),dp(6));
        if(scNm[i]!=null)ne.setText(scNm[i]);
        box.addView(ne,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(6),0,0);
        row.addView(scThumbBox(i,"Parado",dir));
        row.addView(scThumbBox(i+3,"Falando",dir));
        Button cl=pill("Limpar",v->scClear(i));
        LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,dp(44),1);clp.leftMargin=dp(4);cl.setLayoutParams(clp);
        row.addView(cl);
        box.addView(row,new LinearLayout.LayoutParams(-1,-2));
        return box;
    }
    private void onScPicked(int k,Uri u){
        try{
            Bitmap b=decodeUri(u,256);if(b==null)throw new Exception("imagem inválida");
            scUri[k]=u;if(scThumb[k]!=null)scThumb[k].setImageBitmap(b);
        }catch(Exception e){Toast.makeText(this,"Não consegui abrir essa imagem.",Toast.LENGTH_SHORT).show();}
    }
    /** editId == null: cria um Pngtuber novo. Senão, edita o Pngtuber já criado. */
    private void showPngEditor(final String editId){
        final boolean edit=editId!=null;
        pngIdleUri=null;pngTalkUri=null;
        pngIdleThumb=new ImageView(this);pngTalkThumb=new ImageView(this);
        for(int i=0;i<6;i++){scUri[i]=null;scFile[i]=null;scOld[i]=null;scThumb[i]=null;}
        for(int i=0;i<3;i++){scNm[i]=null;scNameEd[i]=null;}
        String curName="";File scDir=null;
        if(edit){
            File base=new File(modelsDir(),editId),content=new File(base,"content");
            curName=modelName(base);
            try{
                String[] pr=pngPair(content);
                Bitmap a=thumb(new File(content,pr[0]),256),b=thumb(new File(content,pr[1]),256);
                if(a!=null)pngIdleThumb.setImageBitmap(a);if(b!=null)pngTalkThumb.setImageBitmap(b);
            }catch(Exception ignored){}
            loadPngShortcuts(content);
            scDir=atalhosDir(content);
        }
        final boolean[] touched={false};
        final LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(8),dp(16),dp(8));
        final LinearLayout p1=new LinearLayout(this);p1.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout p2=new LinearLayout(this);p2.setOrientation(LinearLayout.VERTICAL);p2.setVisibility(View.GONE);
        root.addView(p1);root.addView(p2);

        // ---- página 1: nome + 2 imagens + pergunta dos atalhos ----
        final EditText name=new EditText(this);
        name.setHint("Nome do Pngtuber (obrigatório)");name.setSingleLine(true);name.setTextColor(Color.WHITE);name.setHintTextColor(0xFF9A9AA5);
        name.setBackground(bg(cCard,14));name.setPadding(dp(16),dp(10),dp(16),dp(10));
        if(edit)name.setText(curName);
        p1.addView(name,new LinearLayout.LayoutParams(-1,-2));
        TextView hint=text(edit?"Troque só o que quiser mudar: o que você não escolher de novo continua como está.":"São só 2 imagens: uma com a boca fechada (parado) e outra com a boca aberta (falando). Fundo branco ou transparente.",12,muted,false);
        hint.setPadding(0,dp(8),0,dp(4));p1.addView(hint);
        p1.addView(pngRow(pngIdleThumb,"Parado (boca fechada)",REQ_PNG_IDLE));
        p1.addView(pngRow(pngTalkThumb,"Falando (boca aberta)",REQ_PNG_TALK));
        TextView qt=text(edit?"Atalhos do modelo":"Deseja criar atalhos?",15,Color.WHITE,true);qt.setPadding(0,dp(14),0,dp(2));p1.addView(qt);
        final TextView st=text("",12,muted,false);p1.addView(st);
        final Runnable refresh=new Runnable(){public void run(){int n=scCount();st.setText(n==0?"Sem atalhos":(n==1?"1 atalho com imagem":n+" atalhos com imagem"));}};
        LinearLayout qrow=new LinearLayout(this);qrow.setOrientation(LinearLayout.HORIZONTAL);
        Button yes=pill(edit?"Editar atalhos":"Sim",v->{touched[0]=true;p1.setVisibility(View.GONE);p2.setVisibility(View.VISIBLE);});
        qrow.addView(yes);
        if(!edit){Button no=pill("Não",v->{for(int i=0;i<3;i++)scClear(i);refresh.run();});qrow.addView(no);}
        p1.addView(qrow,new LinearLayout.LayoutParams(-1,-2));

        // ---- página 2: até 3 atalhos (slot sem imagem não conta) ----
        TextView t2=text("Atalhos (até 3)",16,Color.WHITE,true);t2.setPadding(0,dp(4),0,dp(2));p2.addView(t2);
        TextView h2=text("Cada atalho tem 2 imagens: parado (boca fechada) e falando (boca aberta). Pode deixar um atalho vazio: só contam os que têm imagem. Se escolher só uma das duas, ela vale para as duas situações. Tocar no atalho troca o visual do Pngtuber; tocar de novo volta ao normal.",12,muted,false);
        h2.setPadding(0,dp(4),0,dp(4));p2.addView(h2);
        for(int i=0;i<3;i++)p2.addView(scRow(i,scDir));
        LinearLayout brow=new LinearLayout(this);brow.setOrientation(LinearLayout.HORIZONTAL);
        brow.addView(pill("Voltar",v->{p2.setVisibility(View.GONE);p1.setVisibility(View.VISIBLE);refresh.run();}));
        p2.addView(brow,new LinearLayout.LayoutParams(-1,-2));
        refresh.run();

        ScrollView sv=new ScrollView(this);sv.addView(root);
        final AlertDialog dlg=newDialog().setTitle(edit?"Editar Pngtuber":"Criar Pngtuber").setView(sv).setPositiveButton(edit?"Salvar":"Criar",null).setNegativeButton(edit?"Cancelar edição":"Cancelar",null).create();
        dlg.setCanceledOnTouchOutside(false);
        styleWindow(dlg);
        dlg.show();
        styleButtons(dlg);
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String nm=name.getText().toString().trim();
            if(nm.isEmpty()){
                p2.setVisibility(View.GONE);p1.setVisibility(View.VISIBLE);
                name.setError("Dê um nome ao Pngtuber");name.requestFocus();
                Toast.makeText(this,"Dê um nome ao Pngtuber.",Toast.LENGTH_SHORT).show();return;
            }
            if(!edit&&(pngIdleUri==null||pngTalkUri==null)){
                p2.setVisibility(View.GONE);p1.setVisibility(View.VISIBLE);
                Toast.makeText(this,"Escolha as duas imagens.",Toast.LENGTH_SHORT).show();return;
            }
            String[] names=new String[3];
            for(int i=0;i<3;i++){String x=scNameEd[i]==null?"":scNameEd[i].getText().toString().trim();names[i]=x.isEmpty()?"Atalho "+(i+1):x;}
            dlg.dismiss();
            savePngtuber(editId,nm,pngIdleUri,pngTalkUri,names,edit?touched[0]:scCount()>0);
        });
    }
    /** Salva o Pngtuber (novo ou editado). writeSc: reescreve a pasta Atalhos; senão deixa como está. */
    private void savePngtuber(final String editId,final String nm,final Uri idle,final Uri talk,final String[] names,final boolean writeSc){
        final boolean edit=editId!=null;
        if(status!=null)status.setText(edit?"Salvando alterações...":"Criando Pngtuber...");
        final Uri[] su=scUri.clone();final String[] sfile=scFile.clone(),sold=scOld.clone();
        new Thread(new Runnable(){public void run(){
            final String id=edit?editId:String.valueOf(System.currentTimeMillis());
            File base=new File(modelsDir(),id),content=new File(base,"content");
            try{
                content.mkdirs();
                if(edit){
                    String[] pr=pngPair(content);
                    if(idle!=null)new File(content,pr[0]).delete();
                    if(talk!=null)new File(content,pr[1]).delete();
                }
                if(idle!=null)savePngImage(idle,new File(content,"parado.png"));
                if(talk!=null)savePngImage(talk,new File(content,"falando.png"));
                if(writeSc){
                    File dir=atalhosDir(content);
                    org.json.JSONArray arr=new org.json.JSONArray();
                    if(dir==null)dir=new File(content,"Atalhos");
                    dir.mkdirs();
                    for(int i=0;i<3;i++){
                        String[] fn=new String[2];
                        for(int j=0;j<2;j++){
                            int k=i+3*j;
                            if(su[k]!=null){
                                fn[j]="atalho"+(i+1)+(j==0?"_parado":"_falando")+".png";
                                if(sold[k]!=null&&!sold[k].equals(fn[j]))new File(dir,sold[k]).delete();
                                savePngImage(su[k],new File(dir,fn[j]));
                            }else if(sfile[k]!=null)fn[j]=sfile[k];
                            else if(sold[k]!=null)new File(dir,sold[k]).delete();
                        }
                        if(fn[0]==null&&fn[1]==null)continue;
                        String idleF=fn[0]!=null?fn[0]:fn[1],talkF=fn[1]!=null?fn[1]:fn[0];
                        org.json.JSONObject o=new org.json.JSONObject();
                        o.put("nome",names[i]);o.put("imagem",idleF);o.put("imagemFalando",talkF);
                        o.put("acao","setExpr('"+dir.getName()+"/"+Uri.encode(idleF,"/")+"','"+dir.getName()+"/"+Uri.encode(talkF,"/")+"')");
                        o.put("desligar","setExpr(null)");
                        arr.put(o);
                    }
                    if(arr.length()==0)deleteTree(dir);
                    else{
                        org.json.JSONObject root=new org.json.JSONObject();root.put("atalhos",arr);
                        java.io.FileWriter jw=new java.io.FileWriter(new File(dir,"atalhos.json"));jw.write(root.toString());jw.close();
                    }
                }
                java.io.FileWriter fw=new java.io.FileWriter(new File(base,"name.txt"));fw.write(nm);fw.close();
                if(!edit){java.io.FileWriter kw=new java.io.FileWriter(new File(base,"kind.txt"));kw.write("pngtuber");kw.close();}
                runOnUiThread(new Runnable(){public void run(){
                    if(!edit||selectedId().equals(id))selectModel(id);
                    else if(status!=null)status.setText("Pngtuber atualizado.");
                }});
            }catch(final Exception ex){
                if(!edit)deleteTree(base);
                runOnUiThread(new Runnable(){public void run(){if(status!=null)status.setText("Não consegui concluir: "+Friendly.why(ex));}});
            }
        }}).start();
    }
    private void chooseCharacterZip(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/zip");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/zip","application/x-zip-compressed","application/octet-stream"});startActivityForResult(i,REQ_IMPORT);}
    private void deleteTree(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)deleteTree(x);}f.delete();}
    private void startVtuber(){boolean pg=isPng();if(pg?!hasMic():!hasCamera()){String perm=pg?Manifest.permission.RECORD_AUDIO:Manifest.permission.CAMERA;String[] p=Build.VERSION.SDK_INT>=33?new String[]{perm,Manifest.permission.POST_NOTIFICATIONS}:new String[]{perm};requestPermissions(p,REQ_PERMISSIONS);return;}if(!Settings.canDrawOverlays(this)){startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),REQ_OVERLAY);return;}startForegroundService(new Intent(this,OverlayService.class));moveTaskToBack(true);}
    @Override public void onRequestPermissionsResult(int c,String[] p,int[] r){super.onRequestPermissionsResult(c,p,r);if(c==REQ_PERMISSIONS){if(isPng()?hasMic():hasCamera())startVtuber();else refreshStatus();}else if(c==REQ_RELOAD_PERM){if((isPng()?hasMic():hasCamera())&&OverlayService.isRunning())startService(new Intent(this,OverlayService.class).setAction(OverlayService.ACTION_RELOAD_CHARACTER));else refreshStatus();}else if(c==REQ_AUDIO){requestProjection();}else if(c==REQ_LIVE_PERM){if(hasMic())beginLive();else Toast.makeText(this,"A Live precisa da permissão do microfone.",Toast.LENGTH_LONG).show();}}
    @Override protected void onActivityResult(int c,int r,Intent d){super.onActivityResult(c,r,d);if(c==REQ_PNG_IDLE||c==REQ_PNG_TALK){if(r==RESULT_OK&&d!=null&&d.getData()!=null)onPngPicked(c==REQ_PNG_IDLE,d.getData());return;}if(c>=REQ_PNG_SC&&c<REQ_PNG_SC+6){if(r==RESULT_OK&&d!=null&&d.getData()!=null)onScPicked(c-REQ_PNG_SC,d.getData());return;}if(c==REQ_ACCESSORY){if(r==RESULT_OK&&d!=null&&d.getData()!=null)saveAccessory(d.getData());return;}if(c==REQ_SCENERY){if(r==RESULT_OK&&d!=null&&d.getData()!=null)saveScenery(d.getData());return;}if(c==REQ_EXPORT){if(r==RESULT_OK&&d!=null&&d.getData()!=null&&pendingExportId!=null)writeExport(d.getData(),pendingExportId);return;}if(c==REQ_IMPORT&&r==RESULT_OK&&d!=null){importCharacter(d.getData(),importPng);return;}
        if(c==REQ_LIVE){if(r==RESULT_OK&&d!=null){Intent li=new Intent(this,LiveService.class).setAction(LiveService.ACTION_START).putExtra("code",r).putExtra("data",d).putExtra("landscape",recLandscape());showDashboard();startForegroundService(li);ui.postDelayed(new Runnable(){public void run(){moveTaskToBack(true);}},300);}else Toast.makeText(this,"Live cancelada.",Toast.LENGTH_SHORT).show();return;}
        if(c==REQ_RECORD){if(r==RESULT_OK&&d!=null){Intent si=new Intent(this,RecordService.class).putExtra("code",r).putExtra("data",d).putExtra("audio",hasMic()).putExtra("landscape",recLandscape());startForegroundService(si);ui.postDelayed(new Runnable(){public void run(){moveTaskToBack(true);}},300);}else if(status!=null)status.setText("Gravação cancelada.");return;}if(c==REQ_OVERLAY&&Settings.canDrawOverlays(this))startVtuber();}
}
