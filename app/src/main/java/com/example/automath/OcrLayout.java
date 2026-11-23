package com.example.automath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Layout calibrated from the supplied portrait screenshot (417x695).
 * Coordinates are relative to entire display; avoid acting if OCR is ambiguous.
 */
public final class OcrLayout {
    private OcrLayout() {}
    public static final class Token {
        public final String text;
        public final float centerX, centerY;
        public Token(String text, float x, float y) { this.text=text; centerX=x; centerY=y; }
    }
    public static final class Solution {
        public final String fingerprint;
        public final int x, y, value;
        Solution(String fingerprint, int x, int y, int value) {
            this.fingerprint=fingerprint; this.x=x; this.y=y; this.value=value;
        }
    }
    // The centers of the four colored buttons, from the image supplied by the user.
    private static final float[] BX = { .305f, .705f, .305f, .705f };
    private static final float[] BY = { .685f, .685f, .850f, .850f };

    public static Solution solve(List<Token> tokens, int width, int height) {
        if (width <= 0 || height <= 0 || width >= height || tokens == null) return null;
        List<Token> questionTokens = new ArrayList<>();
        List<Token> answerTokens = new ArrayList<>();
        for (Token t: tokens) {
            float x=t.centerX/width, y=t.centerY/height;
            if (x >= .10f && x <= .91f && y >= .385f && y <= .55f) questionTokens.add(t);
            if (x >= .12f && x <= .88f && y >= .61f && y <= .94f) answerTokens.add(t);
        }
        questionTokens.sort(Comparator.comparingDouble(o -> o.centerX));
        StringBuilder equation = new StringBuilder();
        for (Token token : questionTokens) equation.append(token.text).append(' ');
        Arithmetic.Question q=Arithmetic.find(equation.toString());
        if (q == null) return null;
        Integer[] values = new Integer[4];
        for (int slot=0; slot<4; slot++) {
            int index = slot;
            List<Token> inSlot = new ArrayList<>();
            for (Token token: answerTokens) {
                float x=token.centerX/width, y=token.centerY/height;
                if (Math.abs(x-BX[index]) < .16f && Math.abs(y-BY[index]) < .078f) inSlot.add(token);
            }
            inSlot.sort(Comparator.comparingDouble(o -> o.centerX));
            StringBuilder sb = new StringBuilder();
            for (Token token: inSlot) sb.append(token.text);
            values[slot]=Arithmetic.readNumber(sb.toString());
        }
        int match=-1;
        for(int i=0;i<4;i++) if(values[i] != null && values[i] == q.answer) {
            if (match!=-1) return null; // Never tap when two candidates look correct.
            match=i;
        }
        if(match<0) return null;
        String fingerprint=q.key()+":"+values[0]+","+values[1]+","+values[2]+","+values[3];
        return new Solution(fingerprint,Math.round(BX[match]*width),Math.round(BY[match]*height),q.answer);
    }
}
