package com.example.automath;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE=18;
    private TextView clickStatus, floatStatus, captureStatus;
    private int dp(float v) { return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22),dp(30),dp(22),dp(30));
        root.setBackgroundColor(Color.rgb(15,21,34));
        scroll.addView(root);
        addLabel(root,"AUTO MATH SOLVER",26,true,Color.WHITE);
        addLabel(root,"Nhận diện toán trên màn hình • OCR offline • tự chạm đáp án",14,false,Color.rgb(185,198,218));
        addGap(root,22);
        addLabel(root,"1. CẤP QUYỀN",17,true,Color.rgb(119,201,255));
        clickStatus=addLabel(root,"",15,false,Color.WHITE);
        addButton(root,"CLICK — Bật Accessibility",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),Color.rgb(30,95,164));
        addGap(root,8);
        floatStatus=addLabel(root,"",15,false,Color.WHITE);
        addButton(root,"FLOAT — Cho phép hiển thị nổi",()->{
            Intent intent=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()));
            try { startActivity(intent); } catch(Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
        },Color.rgb(30,95,164));
        addGap(root,8);
        captureStatus=addLabel(root,"",15,false,Color.WHITE);
        addLabel(root,"CAPTURE — Android sẽ hỏi quyền chụp màn hình mỗi khi mở phiên. Chọn toàn bộ màn hình khi được hỏi.",13,false,Color.rgb(190,198,209));
        addGap(root,24);
        addLabel(root,"2. ĐIỀU KHIỂN",17,true,Color.rgb(119,201,255));
        addButton(root,"START — Mở thanh nổi",this::requestCapture,Color.rgb(37,143,88));
        addButton(root,"TEST ẢNH MẪU — Kiểm tra OCR",this::testSample,Color.rgb(82,80,149));
        addButton(root,"STOP — Dừng và đóng thanh nổi",()->{
            Intent intent=new Intent(this,MathCaptureService.class);
            stopService(intent); refresh();
        },Color.rgb(156,55,64));
        addGap(root,15);
        addLabel(root,"Cách dùng: Cấp CLICK và FLOAT → nhấn START → đồng ý chụp màn hình → chuyển sang trò chơi → kéo thanh nổi lên khu vực trống và bấm ▶. Bấm ■ để dừng ngay; × để đóng.",14,false,Color.rgb(230,233,241));
        addGap(root,16);
        addLabel(root,"Tối ưu cho ảnh mẫu: phép cộng/trừ/nhân/chia số nguyên, 4 ô đáp án vị trí 2×2. Không sử dụng API key hoặc internet. Nếu OCR không đủ rõ, app bỏ qua thay vì bấm ngẫu nhiên.",13,false,Color.rgb(155,179,204));
        setContentView(scroll);
        if (Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},44);
        }
        refresh();
    }
    private TextView addLabel(LinearLayout root,String message,int sp,boolean bold,int color) {
        TextView view=new TextView(this);
        view.setText(message); view.setTextColor(color); view.setTextSize(sp);
        if(bold) view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        view.setPadding(0,dp(6),0,dp(6));
        root.addView(view); return view;
    }
    private void addGap(LinearLayout root,int height) {
        View v=new View(this); root.addView(v,new LinearLayout.LayoutParams(1,dp(height)));
    }
    private void addButton(LinearLayout root,String text,Runnable click,int bg) {
        Button b=new Button(this); b.setText(text); b.setTextAllCaps(false); b.setTextColor(Color.WHITE);
        GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(bg); drawable.setCornerRadius(dp(13)); b.setBackground(drawable);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(54)); p.topMargin=dp(6);
        root.addView(b,p); b.setOnClickListener(v->click.run());
    }
    private boolean accessibilityEnabled() {
        String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        String name=new ComponentName(this,MathAccessibilityService.class).flattenToString();
        if(enabled == null) return false;
        for(String service:enabled.split(":")) if(name.equalsIgnoreCase(service)) return true;
        return false;
    }
    private void refresh() {
        if(clickStatus == null) return;
        clickStatus.setText("CLICK: "+(accessibilityEnabled()?"✓ Đã cấp":"✕ Chưa cấp"));
        floatStatus.setText("FLOAT: "+(Settings.canDrawOverlays(this)?"✓ Đã cấp":"✕ Chưa cấp"));
        captureStatus.setText("CAPTURE: "+(MathCaptureService.isAlive()?"✓ Phiên đang mở":"Cần đồng ý khi START"));
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    private void testSample() {
        try {
            Bitmap bitmap=BitmapFactory.decodeStream(getAssets().open("sample.png"));
            if(bitmap==null) throw new IllegalStateException("Image not found");
            TextRecognizer recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(InputImage.fromBitmap(bitmap,0))
                    .addOnSuccessListener(result->{
                        List<OcrLayout.Token> tokens=new ArrayList<>();
                        for(Text.TextBlock block:result.getTextBlocks())
                            for(Text.Line line:block.getLines())
                                for(Text.Element element:line.getElements()) {
                                    android.graphics.Rect box=element.getBoundingBox();
                                    if(box!=null) tokens.add(new OcrLayout.Token(element.getText(),box.exactCenterX(),box.exactCenterY()));
                                }
                        OcrLayout.Solution answer=OcrLayout.solve(tokens,bitmap.getWidth(),bitmap.getHeight());
                        String status=answer==null ? "OCR chưa nhận dạng đủ để tự chạm." :
                                "THÀNH CÔNG — Đáp án "+answer.value+", chạm tại ("+answer.x+", "+answer.y+")";
                        new AlertDialog.Builder(this).setTitle("Kết quả TEST ẢNH MẪU")
                                .setMessage(status+"\n\nText đọc được:\n"+result.getText())
                                .setPositiveButton("Đóng",null).show();
                    }).addOnFailureListener(error->new AlertDialog.Builder(this)
                            .setTitle("Lỗi nhận diện").setMessage(error.toString())
                            .setPositiveButton("Đóng",null).show())
                    .addOnCompleteListener(done->{bitmap.recycle();recognizer.close();});
        } catch(Exception error) {
            Toast.makeText(this,"Không mở được ảnh mẫu: "+error.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    private void requestCapture() {
        if(!accessibilityEnabled()) {
            Toast.makeText(this,"Hãy cấp quyền CLICK trước",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return;
        }
        if(!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,"Hãy cấp quyền FLOAT trước",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))); return;
        }
        MediaProjectionManager projection=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent request;
        if(Build.VERSION.SDK_INT>=34) request=projection.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay());
        else request=projection.createScreenCaptureIntent();
        startActivityForResult(request,REQUEST_CAPTURE);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_CAPTURE) {
            if(resultCode!=RESULT_OK || data==null) {
                Toast.makeText(this,"Bạn chưa cấp quyền CAPTURE",Toast.LENGTH_LONG).show(); return;
            }
            Intent service=new Intent(this,MathCaptureService.class);
            service.putExtra(MathCaptureService.EXTRA_RESULT_CODE,resultCode);
            service.putExtra(MathCaptureService.EXTRA_DATA,data);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(service); else startService(service);
            Toast.makeText(this,"Thanh nổi đã mở. Chuyển sang trò chơi rồi bấm ▶",Toast.LENGTH_LONG).show();
            refresh();
        }
    }
}
