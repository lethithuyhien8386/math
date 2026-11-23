package com.example.automath;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArithmeticTest {
    @Test public void exampleFromScreenshot() {
        Arithmetic.Question q=Arithmetic.find("2 + 1 =");
        assertNotNull(q);assertEquals(3,q.answer);
    }
    @Test public void handlesMathOperators() {
        assertEquals(4,Arithmetic.find("7 - 3 =").answer);
        assertEquals(12,Arithmetic.find("3 × 4 =").answer);
        assertEquals(2,Arithmetic.find("8 ÷ 4 =").answer);
        assertEquals(-1,Arithmetic.find("2−3=").answer);
    }
    @Test public void declinesInvalidMath() {
        assertNull(Arithmetic.find("1 / 0 ="));
        assertNull(Arithmetic.find("4 / 3 ="));
        assertNull(Arithmetic.find("choose now"));
    }
    @Test public void parsesScreenCoordinatesForScreenshot() {
        java.util.List<OcrLayout.Token> t=new java.util.ArrayList<>();
        t.add(new OcrLayout.Token("2",80,312));
        t.add(new OcrLayout.Token("+",169,312));
        t.add(new OcrLayout.Token("1",246,312));
        t.add(new OcrLayout.Token("=",325,312));
        t.add(new OcrLayout.Token("3",126,479));
        t.add(new OcrLayout.Token("2",292,479));
        t.add(new OcrLayout.Token("4",126,591));
        t.add(new OcrLayout.Token("1",292,591));
        OcrLayout.Solution solution=OcrLayout.solve(t,417,695);
        assertNotNull(solution);assertEquals(3,solution.value);
        assertEquals(127,solution.x);assertEquals(476,solution.y);
    }
    @Test public void neverTapWhenNoMatchingAnswer() {
        java.util.List<OcrLayout.Token> t=new java.util.ArrayList<>();
        t.add(new OcrLayout.Token("9 + 1 =",200,312));
        t.add(new OcrLayout.Token("3",126,479));
        t.add(new OcrLayout.Token("2",292,479));
        assertNull(OcrLayout.solve(t,417,695));
    }
}
