package com.livemcq.web;

/** Small input-cleaning helpers shared by services. */
public final class Text {

    private Text() { }

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** Bengali digits (০-৯) become 0-9, so a roll typed on a Bengali keyboard still matches. */
    public static String asciiDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c >= '০' && c <= '৯' ? (char) ('0' + (c - '০')) : c);
        }
        return sb.toString();
    }
}
