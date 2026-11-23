package com.example.automath;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MathCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE="result_code";
    public static final String EXTRA_DATA="projection_data";
    private static volatile boolean alive;
    public static boolean isAlive() { return alive; }
    private static final int NOTIFY_ID=410;
    private static final String CHANNEL="math_projection";
    private final Handler main=new Handler(android.os.Looper.getMainLooper());
    private HandlerThread thread;
    private Handler cameraHandler;
    private WindowManager wm;
    private WindowManager.LayoutParams floatParams;
    private View floatView;
    private TextView status;
    private MediaProjection projection;
    private ImageReader reader;
    private VirtualDisplay virtualDisplay;
    private TextRecognizer textRecognizer;
    private final AtomicBoolean decoding=new AtomicBoolean(false);
    private volatile boolean running=false;
    private volatile int generation=0;
    private int displayWidth,displayHeight;
    private long lastFrameAt=0,lastAccessAt=0,lastTapAt=0;
    private String lastFingerprint="";
    private int solved=0;

    @Override public void onCreate() {
        super.onCreate(); alive=true;
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        thread=new HandlerThread("math-screenshot"); thread.start();
        cameraHandler=new Handler(thread.getLooper());
        textRecognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        if(Build.VERSION.SDK_INT>=26) {
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Auto Math: chia sẻ màn hình",NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    private void foreground() {
        Notification.Builder builder=Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this,CHANNEL) : new Notification.Builder(this);
        Notification notify=builder.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("Auto Math Solver")
                .setContentText("Đã cấp quyền màn hình — dùng ■ trên thanh nổi để dừng")
                .setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=29) startForeground(NOTIFY_ID,notify,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIFY_ID,notify);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null || !intent.hasExtra(EXTRA_DATA)) { stopSelf(); return START_NOT_STICKY; }
        try {
            foreground();
            shutdownProjection();
            int resultCode=intent.getIntExtra(EXTRA_RESULT_CODE,0);
            Intent data;
            if(Build.VERSION.SDK_INT>=33) data=intent.getParcelableExtra(EXTRA_DATA,Intent.class);
            else data=(Intent)intent.getParcelableExtra(EXTRA_DATA);
            if(data==null) throw new IllegalStateException("Empty MediaProjection token");
            MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            projection=mgr.getMediaProjection(resultCode,data);
            if(projection==null) throw new IllegalStateException("MediaProjection unavailable");
            final MediaProjection thisSession=projection;
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { main.post(()->{
                    if (projection==thisSession) stopSelf();
                }); }
            },main);
            DisplayMetrics metrics=new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(metrics);
            displayWidth=metrics.widthPixels; displayHeight=metrics.heightPixels;
            reader=ImageReader.newInstance(displayWidth,displayHeight,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(this::onImage,cameraHandler);
            virtualDisplay=projection.createVirtualDisplay("AutoMathCapture",displayWidth,displayHeight,metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,cameraHandler);
            if(floatView==null) showFloat();
            setStatus("SẴN SÀNG • ▶ để chạy");
        } catch(Exception e) {
            android.util.Log.e("AutoMath", "Cannot initiate projection",e);
            Toast.makeText(this,"Không khởi tạo được CAPTURE: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }
    private int dp(float size) { return (int)(getResources().getDisplayMetrics().density*size+.5f); }
    private GradientDrawable background(int color) {
        GradientDrawable result=new GradientDrawable(); result.setColor(color); result.setCornerRadius(dp(10));
        result.setStroke(dp(1),Color.rgb(93,113,144)); return result;
    }
    private TextView label(String text,int size,int color) {
        TextView tv=new TextView(this);tv.setText(text);tv.setTextColor(color);tv.setTextSize(size);
        tv.setGravity(Gravity.CENTER);tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return tv;
    }
    private void showFloat() {
        LinearLayout container=new LinearLayout(this);container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(6),dp(5),dp(6),dp(5));container.setBackground(background(Color.rgb(22,31,49)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView handle=label("⠿",21,Color.rgb(180,210,255));
        row.addView(handle,new LinearLayout.LayoutParams(dp(28),dp(40)));
        TextView start=label("▶",22,Color.rgb(91,235,145));
        TextView stop=label("■",20,Color.rgb(255,185,73));
        TextView close=label("×",26,Color.rgb(250,130,130));
        for(TextView v:new TextView[]{start,stop,close}) row.addView(v,new LinearLayout.LayoutParams(dp(42),dp(40)));
        container.addView(row);
        status=label("SẴN SÀNG",10,Color.WHITE);
        container.addView(status,new LinearLayout.LayoutParams(-1,dp(23)));
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        floatParams=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,PixelFormat.TRANSLUCENT);
        floatParams.gravity=Gravity.TOP|Gravity.LEFT;
        floatParams.x=Math.max(0,displayWidth-dp(190));floatParams.y=dp(22);
        wm.addView(container,floatParams);floatView=container;
        final float[] down=new float[4];
        handle.setOnTouchListener((v,event)->{
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0]=event.getRawX();down[1]=event.getRawY();down[2]=floatParams.x;down[3]=floatParams.y;return true;
                case MotionEvent.ACTION_MOVE:
                    floatParams.x=Math.max(0,Math.min(displayWidth-dp(170),(int)(down[2]+event.getRawX()-down[0])));
                    floatParams.y=Math.max(0,Math.min(displayHeight-dp(80),(int)(down[3]+event.getRawY()-down[1])));
                    if(floatView!=null) wm.updateViewLayout(floatView,floatParams);return true;
                case MotionEvent.ACTION_UP: return true;
                default:return false;
            }
        });
        start.setOnClickListener(v->{
            if(MathAccessibilityService.current()==null) {setStatus("CẦN QUYỀN CLICK");return;}
            generation++;running=true;lastFingerprint=""; lastTapAt=0;
            setStatus("ĐANG QUÉT • 0");
        });
        stop.setOnClickListener(v->stopSolving());
        close.setOnClickListener(v->{stopSolving();stopSelf();});
    }
    private void setStatus(String message) { if(status!=null) status.setText(message); }
    private void stopSolving() {
        running=false;generation++;lastFingerprint="";
        setStatus("ĐÃ DỪNG • ■");
    }
    private void onImage(ImageReader source) {
        Image image=null;
        try {
            image=source.acquireLatestImage();
            if(image==null) return;
            if(!running || decoding.get()) return;
            long now=SystemClock.uptimeMillis();
            if(now-lastFrameAt<90) return;
            lastFrameAt=now;
            if(!decoding.compareAndSet(false,true)) return;
            final int pass=generation;
            final Bitmap bitmap=copyImage(image);
            if(bitmap==null) {decoding.set(false);return;}
            // Fast path: text available from Accessibility nodes, checked at most 5x per sec.
            if(now-lastAccessAt>=200) {
                lastAccessAt=now;
                main.post(()->{
                    if(!running||generation!=pass) return;
                    MathAccessibilityService a=MathAccessibilityService.current();
                    if(a!=null) {
                        OcrLayout.Solution solution=OcrLayout.solve(a.readVisibleText(),displayWidth,displayHeight);
                        if(solution!=null) accept(solution,pass);
                    }
                });
            }
            Task<Text> task=textRecognizer.process(InputImage.fromBitmap(bitmap,0));
            task.addOnSuccessListener(text->{
                List<OcrLayout.Token> tokens=new ArrayList<>();
                for(Text.TextBlock block:text.getTextBlocks()) for(Text.Line line:block.getLines())
                    for(Text.Element element:line.getElements()) {
                        Rect rect=element.getBoundingBox();
                        if(rect!=null) tokens.add(new OcrLayout.Token(element.getText(),rect.exactCenterX(),rect.exactCenterY()));
                    }
                OcrLayout.Solution answer=OcrLayout.solve(tokens,displayWidth,displayHeight);
                if(answer!=null) main.post(()->accept(answer,pass));
            }).addOnFailureListener(e->android.util.Log.w("AutoMath","OCR skipped a frame",e))
              .addOnCompleteListener(t->{bitmap.recycle();decoding.set(false);});
        } catch(Exception e) {
            decoding.set(false);
            android.util.Log.e("AutoMath","Read frame failed",e);
        } finally {if(image!=null)image.close();}
    }
    private Bitmap copyImage(Image image) {
        Image.Plane[] planes=image.getPlanes();
        if(planes.length==0) return null;
        Image.Plane plane=planes[0];
        if(plane.getPixelStride()!=4) return null;
        int rowStride=plane.getRowStride();
        int bufferWidth=rowStride/4;
        Bitmap padded=Bitmap.createBitmap(bufferWidth,displayHeight,Bitmap.Config.ARGB_8888);
        ByteBuffer pixels=plane.getBuffer();pixels.rewind();
        padded.copyPixelsFromBuffer(pixels);
        if(bufferWidth==displayWidth) return padded;
        Bitmap cropped=Bitmap.createBitmap(padded,0,0,displayWidth,displayHeight);
        padded.recycle();return cropped;
    }
    /** This runs on main thread; Stop increments generation to invalidate in-flight OCR. */
    private void accept(OcrLayout.Solution solution,int pass) {
        if(!running||pass!=generation) return;
        MathAccessibilityService service=MathAccessibilityService.current();
        if(service==null) {setStatus("CẦN QUYỀN CLICK");return;}
        long now=SystemClock.uptimeMillis();
        if(solution.fingerprint.equals(lastFingerprint)&&now-lastTapAt<900) return;
        if(now-lastTapAt<125) return; // Prevent duplicate taps from two simultaneous paths.
        if(service.tap(solution.x,solution.y)) {
            lastTapAt=now;lastFingerprint=solution.fingerprint;solved++;
            setStatus("✓ "+solution.value+" • "+solved+" câu");
        }
    }
    private void shutdownProjection() {
        stopSolving();
        if(reader!=null) {reader.setOnImageAvailableListener(null,null);}
        if(virtualDisplay!=null) {virtualDisplay.release();virtualDisplay=null;}
        if(reader!=null) {reader.close();reader=null;}
        if(projection!=null) {
            MediaProjection toStop=projection;projection=null;toStop.stop();
        }
    }
    @Override public void onDestroy() {
        stopSolving();
        if(floatView!=null) {wm.removeView(floatView);floatView=null;status=null;}
        shutdownProjection();
        if(textRecognizer!=null) textRecognizer.close();
        if(thread!=null) thread.quitSafely();
        alive=false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) {return null;}
}
