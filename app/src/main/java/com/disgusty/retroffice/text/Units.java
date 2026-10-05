package com.disgusty.retroffice.text;

/** Length parsing for ODF/OOXML measurements. */
public final class Units {
    private Units() {
    }

    /** Converts "2.5cm", "12pt", "1in", "10mm", "3pc", "20px" to points. Returns 0 if unknown. */
    public static float toPt(String v) {
        if (v == null) return 0;
        v = v.trim();
        int i = 0;
        while (i < v.length() && (Character.isDigit(v.charAt(i)) || v.charAt(i) == '.' || v.charAt(i) == '-' || v.charAt(i) == '+'))
            i++;
        if (i == 0) return 0;
        float n;
        try {
            n = Float.parseFloat(v.substring(0, i));
        } catch (NumberFormatException e) {
            return 0;
        }
        String u = v.substring(i).trim().toLowerCase(java.util.Locale.US);
        if (u.equals("pt") || u.length() == 0) return n;
        if (u.equals("cm")) return n * 72f / 2.54f;
        if (u.equals("mm")) return n * 72f / 25.4f;
        if (u.equals("in") || u.equals("inch")) return n * 72f;
        if (u.equals("pc")) return n * 12f;
        if (u.equals("px")) return n * 0.75f;
        return 0;
    }
}
