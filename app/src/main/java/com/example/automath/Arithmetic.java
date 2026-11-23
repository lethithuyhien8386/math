package com.example.automath;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure Java, safe arithmetic; never evaluates untrusted strings as code. */
public final class Arithmetic {
    private static final Pattern EQUATION = Pattern.compile("(-?\\d{1,3})\\s*([+\\-*/x])\\s*(-?\\d{1,3})\\s*(?:=|\\?|$)");
    private static final Pattern INTEGER = Pattern.compile("^-?\\d{1,4}$");
    private Arithmetic() {}

    public static final class Question {
        public final int a, b, answer;
        public final char operator;
        Question(int a, int b, char operator, int answer) {
            this.a=a; this.b=b; this.operator=operator; this.answer=answer;
        }
        public String key() { return a+""+operator+b; }
    }

    public static String normalize(String value) {
        if (value == null) return "";
        return value.replace('×','x').replace('X','x').replace('÷','/')
                .replace('−','-').replace('–','-').replace('—','-')
                .replace('＋','+').replace('＝','=')
                .replace('O','0').replace('o','0')
                .replaceAll("[\\r\\n]+", " ").trim();
    }

    public static Question find(String input) {
        Matcher matcher = EQUATION.matcher(normalize(input));
        if (!matcher.find()) return null;
        try {
            int a = Integer.parseInt(matcher.group(1));
            int b = Integer.parseInt(matcher.group(3));
            char op = matcher.group(2).charAt(0);
            long answer;
            switch (op) {
                case '+': answer = (long)a + b; break;
                case '-': answer = (long)a - b; break;
                case '*': case 'x': answer = (long)a * b; break;
                case '/':
                    if (b == 0 || a % b != 0) return null;
                    answer = a / b; break;
                default: return null;
            }
            if (answer < -100000 || answer > 100000) return null;
            return new Question(a,b,op,(int)answer);
        } catch (NumberFormatException e) { return null; }
    }

    public static Integer readNumber(String input) {
        String clean = normalize(input).replaceAll("\\s+", "");
        if (!INTEGER.matcher(clean).matches()) return null;
        try { return Integer.valueOf(clean); } catch (NumberFormatException e) { return null; }
    }
}
