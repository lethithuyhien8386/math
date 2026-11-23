package com.example.automath;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public final class MathAccessibilityService extends AccessibilityService {
    private static volatile MathAccessibilityService active;
    public static MathAccessibilityService current() { return active; }
    @Override public void onServiceConnected() { super.onServiceConnected(); active=this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { /* Frame processor checks text when active. */ }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if(active==this) active=null; super.onDestroy(); }

    public boolean tap(int x, int y) {
        if (android.os.Build.VERSION.SDK_INT < 24) return false;
        Path path=new Path(); path.moveTo(x,y);
        GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(path,0,35);
        return dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),null,null);
    }

    /** Accessibility path is fast when a game exposes real TextViews.
     * Canvas/OpenGL games usually expose no such nodes, so screenshot OCR is used.
     */
    public List<OcrLayout.Token> readVisibleText() {
        ArrayList<OcrLayout.Token> out = new ArrayList<>();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null) return out;
        try { gather(root,out,0); }
        finally { root.recycle(); }
        return out;
    }
    private void gather(AccessibilityNodeInfo node, List<OcrLayout.Token> out, int depth) {
        if (depth>12 || out.size()>100) return;
        CharSequence text=node.getText();
        if (text!=null && text.length()>0) {
            Rect rect=new Rect(); node.getBoundsInScreen(rect);
            if (!rect.isEmpty()) out.add(new OcrLayout.Token(text.toString(),rect.exactCenterX(),rect.exactCenterY()));
        }
        for(int i=0;i<node.getChildCount() && out.size()<100;i++) {
            AccessibilityNodeInfo child=node.getChild(i);
            if(child!=null) { try { gather(child,out,depth+1); } finally { child.recycle(); } }
        }
    }
}
