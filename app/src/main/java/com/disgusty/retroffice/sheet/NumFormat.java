package com.disgusty.retroffice.sheet;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/** Number formatting for display and for writing values into files. */
public final class NumFormat {
    private NumFormat() {
    }

    /** "General" display: up to 10 significant digits, no trailing zeros. */
    public static String general(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "#NUM!";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        double a = Math.abs(v);
        if (a >= 1e11 || a < 1e-9) {
            String s = String.format(Locale.US, "%.5E", v);
            int e = s.indexOf('E');
            String mant = stripZeros(s.substring(0, e));
            String exp = s.substring(e + 1);
            int ex = Integer.parseInt(exp);
            return mant + "E" + (ex < 0 ? "-" : "+") + (Math.abs(ex) < 10 ? "0" : "") + Math.abs(ex);
        }
        BigDecimal bd = new BigDecimal(v).round(new java.math.MathContext(10));
        String s = bd.toPlainString();
        if (s.indexOf('.') >= 0) s = stripZeros(s);
        return s;
    }

    private static String stripZeros(String s) {
        if (s.indexOf('.') < 0) return s;
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '0') end--;
        if (end > 0 && s.charAt(end - 1) == '.') end--;
        return s.substring(0, end);
    }

    /** Exact value for storing in a file (xsd:double compatible, round-trips). */
    public static String store(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }

    /** Parses user input as a number ("1.5", "1,5", "-3", "12%", "1e3"); null if not a number. */
    public static Double parseInput(String s) {
        String t = s.trim();
        if (t.length() == 0) return null;
        boolean pct = t.endsWith("%");
        if (pct) t = t.substring(0, t.length() - 1).trim();
        // Accept a single decimal comma when there's no dot (common in many locales).
        if (t.indexOf(',') >= 0 && t.indexOf('.') < 0 && t.indexOf(',') == t.lastIndexOf(',')) t = t.replace(',', '.');
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!(Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E')) return null;
        }
        try {
            double d = Double.parseDouble(t);
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return pct ? d / 100.0 : d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Formats a number according to an Excel number format code (common cases only). */
    public static String excel(double v, int numFmtId, String code) {
        if (code == null) code = builtin(numFmtId);
        if (code == null || code.equalsIgnoreCase("General")) return general(v);
        String c = code;
        int semi = c.indexOf(';');
        if (semi >= 0) c = c.substring(0, semi);
        String plain = stripQuoted(c).toLowerCase(Locale.US);
        if (isDate(plain)) return date(v, plain);
        if (plain.indexOf('%') >= 0) return fixed(v * 100, decimals(plain)) + "%";
        if (plain.indexOf('0') >= 0 || plain.indexOf('#') >= 0) {
            String s = fixed(v, decimals(plain));
            if (plain.indexOf(',') >= 0) s = group(s);
            return s;
        }
        return general(v);
    }

    private static String builtin(int id) {
        switch (id) {
            case 0: return "General";
            case 1: return "0";
            case 2: return "0.00";
            case 3: return "#,##0";
            case 4: return "#,##0.00";
            case 9: return "0%";
            case 10: return "0.00%";
            case 11: return "0.00E+00";
            case 14: return "dd.mm.yyyy";
            case 15: return "d-mmm-yy";
            case 16: return "d-mmm";
            case 17: return "mmm-yy";
            case 18: return "h:mm AM/PM";
            case 19: return "h:mm:ss AM/PM";
            case 20: return "h:mm";
            case 21: return "h:mm:ss";
            case 22: return "dd.mm.yyyy h:mm";
            case 45: return "mm:ss";
            case 46: return "[h]:mm:ss";
            case 47: return "mm:ss.0";
            case 49: return "@";
            default: return null;
        }
    }

    private static String stripQuoted(String c) {
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (ch == '"') {
                q = !q;
                continue;
            }
            if (q) continue;
            if (ch == '\\' && i + 1 < c.length()) {
                i++;
                continue;
            }
            if (ch == '[') {
                int e = c.indexOf(']', i);
                if (e > i) {
                    String in = c.substring(i + 1, e).toLowerCase(Locale.US);
                    if (in.startsWith("h") || in.startsWith("m") || in.startsWith("s")) sb.append(in);
                    i = e;
                    continue;
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private static boolean isDate(String p) {
        return p.indexOf('y') >= 0 || p.indexOf('d') >= 0 || p.indexOf('h') >= 0
                || (p.indexOf('m') >= 0 && p.indexOf('0') < 0 && p.indexOf('#') < 0) || p.indexOf('s') >= 0 && p.indexOf(':') >= 0;
    }

    private static int decimals(String p) {
        int dot = p.indexOf('.');
        if (dot < 0) return 0;
        int n = 0;
        for (int i = dot + 1; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '0' || c == '#') n++;
            else break;
        }
        return n;
    }

    private static String fixed(double v, int dec) {
        return new BigDecimal(v).setScale(dec, BigDecimal.ROUND_HALF_UP).toPlainString();
    }

    private static String group(String s) {
        boolean neg = s.startsWith("-");
        if (neg) s = s.substring(1);
        int dot = s.indexOf('.');
        String ip = dot < 0 ? s : s.substring(0, dot);
        String fp = dot < 0 ? "" : s.substring(dot);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ip.length(); i++) {
            if (i > 0 && (ip.length() - i) % 3 == 0) sb.append(' ');
            sb.append(ip.charAt(i));
        }
        return (neg ? "-" : "") + sb + fp;
    }

    /** Excel serial date (1900 system) to text following the pattern's components. */
    static String date(double serial, String p) {
        boolean hasDate = p.indexOf('y') >= 0 || p.indexOf('d') >= 0;
        boolean hasTime = p.indexOf('h') >= 0 || p.indexOf('s') >= 0;
        long days = (long) Math.floor(serial);
        double frac = serial - days;
        long secs = Math.round(frac * 86400);
        if (secs >= 86400) {
            days++;
            secs -= 86400;
        }
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(1899, Calendar.DECEMBER, 30);
        // Excel's fictitious 29 Feb 1900: serials before 61 are off by one.
        if (days < 61) days += 1;
        cal.add(Calendar.DAY_OF_MONTH, (int) days);
        StringBuilder sb = new StringBuilder();
        if (hasDate || !hasTime) {
            sb.append(two(cal.get(Calendar.DAY_OF_MONTH))).append('.').append(two(cal.get(Calendar.MONTH) + 1))
                    .append('.').append(cal.get(Calendar.YEAR));
        }
        if (hasTime) {
            if (sb.length() > 0) sb.append(' ');
            long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
            sb.append(h).append(':').append(two((int) m));
            if (p.indexOf('s') >= 0) sb.append(':').append(two((int) s));
        }
        return sb.toString();
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
