package com.disgusty.retroffice.sheet;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Spreadsheet formulas in Excel A1 syntax: tokenizing, evaluating a common subset, shifting
 * relative references (shared formulas) and converting to/from ODF's OpenFormula notation.
 *
 * Anything outside the supported subset throws {@link Unsupported}; callers then keep the value
 * cached in the file instead of guessing.
 */
public final class Formula {
    private Formula() {
    }

    public static final class Unsupported extends Exception {
        Unsupported(String m) {
            super(m);
        }
    }

    /** An error value such as #DIV/0!. */
    public static final class Err {
        public final String code;

        public Err(String code) {
            this.code = code;
        }

        @Override
        public String toString() {
            return code;
        }
    }

    public static final Err DIV0 = new Err("#DIV/0!"), VALUE = new Err("#VALUE!"), REF = new Err("#REF!"),
            NAME = new Err("#NAME?"), NUM = new Err("#NUM!"), NA = new Err("#N/A");

    public interface Context {
        /** Cell value: Double, String, Boolean, Err, or null when empty. */
        Object value(int sheet, int row, int col) throws Unsupported;

        /** Sheet index by name, or -1. */
        int sheetIndex(String name);

        int currentSheet();
    }

    static final class Range {
        final int sheet, r1, c1, r2, c2;

        Range(int sheet, int r1, int c1, int r2, int c2) {
            this.sheet = sheet;
            this.r1 = Math.min(r1, r2);
            this.c1 = Math.min(c1, c2);
            this.r2 = Math.max(r1, r2);
            this.c2 = Math.max(c1, c2);
        }
    }

    // ------------------------------------------------------------------ tokens

    static final int NUM_T = 1, STR_T = 2, REF_T = 3, FUNC_T = 4, OP_T = 5, LP = 6, RP = 7, SEP = 8, BOOL_T = 9,
            ERR_T = 10, NAME_T = 11;

    static final class Tok {
        int type;
        String text;      // source text
        int start, end;
        // REF_T
        String sheet;     // as written (unquoted) or null
        int r1 = -1, c1 = -1, r2 = -1, c2 = -1;
        boolean absR1, absC1, absR2, absC2, isRange;
    }

    static ArrayList<Tok> tokenize(String f) throws Unsupported {
        ArrayList<Tok> out = new ArrayList<Tok>();
        int i = 0, n = f.length();
        while (i < n) {
            char c = f.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
                continue;
            }
            Tok t = new Tok();
            t.start = i;
            if (c == '"') {
                StringBuilder sb = new StringBuilder();
                i++;
                while (true) {
                    if (i >= n) throw new Unsupported("Unterminated string");
                    char d = f.charAt(i);
                    if (d == '"') {
                        if (i + 1 < n && f.charAt(i + 1) == '"') {
                            sb.append('"');
                            i += 2;
                            continue;
                        }
                        i++;
                        break;
                    }
                    sb.append(d);
                    i++;
                }
                t.type = STR_T;
                t.text = sb.toString();
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(f.charAt(i + 1)))) {
                int j = i;
                while (j < n && (Character.isDigit(f.charAt(j)) || f.charAt(j) == '.')) j++;
                if (j < n && (f.charAt(j) == 'e' || f.charAt(j) == 'E')) {
                    int k = j + 1;
                    if (k < n && (f.charAt(k) == '+' || f.charAt(k) == '-')) k++;
                    if (k < n && Character.isDigit(f.charAt(k))) {
                        j = k;
                        while (j < n && Character.isDigit(f.charAt(j))) j++;
                    }
                }
                // "1:3" style row ranges aren't supported
                if (j < n && f.charAt(j) == ':') throw new Unsupported("Row range");
                t.type = NUM_T;
                t.text = f.substring(i, j);
                i = j;
            } else if (c == '#') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(f.charAt(j)) || f.charAt(j) == '/' || f.charAt(j) == '!' || f.charAt(j) == '?'))
                    j++;
                t.type = ERR_T;
                t.text = f.substring(i, j);
                i = j;
            } else if (c == '(') {
                t.type = LP;
                t.text = "(";
                i++;
            } else if (c == ')') {
                t.type = RP;
                t.text = ")";
                i++;
            } else if (c == ',' || c == ';') {
                t.type = SEP;
                t.text = String.valueOf(c);
                i++;
            } else if ("+-*/^&=<>%".indexOf(c) >= 0) {
                String op = String.valueOf(c);
                if (i + 1 < n) {
                    String two = f.substring(i, i + 2);
                    if (two.equals("<=") || two.equals(">=") || two.equals("<>")) op = two;
                }
                t.type = OP_T;
                t.text = op;
                i += op.length();
            } else if (c == '\'' || c == '$' || Character.isLetter(c) || c == '_') {
                i = word(f, i, t);
            } else {
                throw new Unsupported("Unexpected character " + c);
            }
            t.end = i;
            if (t.type == STR_T || t.type == NUM_T || t.type == ERR_T || t.type == LP || t.type == RP
                    || t.type == SEP || t.type == OP_T) {
                // text already set
            }
            out.add(t);
        }
        return out;
    }

    /** Reads a reference, function name, boolean or defined name starting at i. */
    private static int word(String f, int i, Tok t) throws Unsupported {
        int n = f.length();
        String sheet = null;
        int p = i;
        if (f.charAt(p) == '\'') {
            StringBuilder sb = new StringBuilder();
            p++;
            while (true) {
                if (p >= n) throw new Unsupported("Unterminated sheet name");
                char d = f.charAt(p);
                if (d == '\'') {
                    if (p + 1 < n && f.charAt(p + 1) == '\'') {
                        sb.append('\'');
                        p += 2;
                        continue;
                    }
                    p++;
                    break;
                }
                sb.append(d);
                p++;
            }
            if (p >= n || f.charAt(p) != '!') throw new Unsupported("Quoted name without sheet reference");
            sheet = sb.toString();
            p++;
        } else {
            int j = p;
            while (j < n && (Character.isLetterOrDigit(f.charAt(j)) || f.charAt(j) == '_' || f.charAt(j) == '.' || f.charAt(j) == '$'))
                j++;
            if (j < n && f.charAt(j) == '!') {
                sheet = f.substring(p, j);
                p = j + 1;
            }
        }
        int[] a = cellAt(f, p);
        if (a != null) {
            t.type = REF_T;
            t.sheet = sheet;
            t.r1 = a[0];
            t.c1 = a[1];
            t.absR1 = a[2] == 1;
            t.absC1 = a[3] == 1;
            p = a[4];
            if (p < n && f.charAt(p) == ':') {
                int q = p + 1;
                // allow Sheet!A1:Sheet!B2 only when sheets match – rare; not supported
                int[] b = cellAt(f, q);
                if (b == null) throw new Unsupported("Bad range");
                t.isRange = true;
                t.r2 = b[0];
                t.c2 = b[1];
                t.absR2 = b[2] == 1;
                t.absC2 = b[3] == 1;
                p = b[4];
            }
            t.text = f.substring(i, p);
            return p;
        }
        if (sheet != null) throw new Unsupported("Unsupported reference");
        int j = p;
        while (j < n && (Character.isLetterOrDigit(f.charAt(j)) || f.charAt(j) == '_' || f.charAt(j) == '.')) j++;
        String w = f.substring(p, j);
        int k = j;
        while (k < n && f.charAt(k) == ' ') k++;
        if (k < n && f.charAt(k) == '(') {
            t.type = FUNC_T;
            t.text = w.toUpperCase(Locale.US);
            if (t.text.startsWith("_XLFN.")) t.text = t.text.substring(6);
            return j;
        }
        String up = w.toUpperCase(Locale.US);
        if (up.equals("TRUE") || up.equals("FALSE")) {
            t.type = BOOL_T;
            t.text = up;
            return j;
        }
        if (w.length() == 0) throw new Unsupported("Unexpected input");
        t.type = NAME_T;
        t.text = w;
        return j;
    }

    /** Parses "$A$1" at position p: {row, col, absRow, absCol, end} or null. */
    static int[] cellAt(String f, int p) {
        int n = f.length();
        int q = p;
        int absC = 0, absR = 0;
        if (q < n && f.charAt(q) == '$') {
            absC = 1;
            q++;
        }
        int cs = q;
        while (q < n && q - cs < 3 && Character.isLetter(f.charAt(q)) && f.charAt(q) < 128) q++;
        if (q == cs) return null;
        String letters = f.substring(cs, q);
        if (q < n && f.charAt(q) == '$') {
            absR = 1;
            q++;
        }
        int rs = q;
        while (q < n && Character.isDigit(f.charAt(q))) q++;
        if (q == rs) return null;
        if (q < n && (Character.isLetterOrDigit(f.charAt(q)) || f.charAt(q) == '_' || f.charAt(q) == '(')) return null;
        int col = CellRef.colIndex(letters);
        int row;
        try {
            row = Integer.parseInt(f.substring(rs, q)) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
        if (row < 0 || col < 0 || row > 1048575 || col > 16383) return null;
        return new int[]{row, col, absR, absC, q};
    }

    // ------------------------------------------------------------------ parse + eval

    /** Evaluates a formula (without the leading '='). */
    public static Object evaluate(String formula, Context ctx) throws Unsupported {
        Parser p = new Parser(tokenize(formula), ctx);
        Object v = p.expr(0);
        if (p.pos != p.toks.size()) throw new Unsupported("Trailing input");
        return scalar(v, ctx);
    }

    static final class Parser {
        final ArrayList<Tok> toks;
        final Context ctx;
        int pos;

        Parser(ArrayList<Tok> toks, Context ctx) {
            this.toks = toks;
            this.ctx = ctx;
        }

        Tok peek() {
            return pos < toks.size() ? toks.get(pos) : null;
        }

        static int prec(String op) {
            if (op.equals("=") || op.equals("<>") || op.equals("<") || op.equals(">") || op.equals("<=") || op.equals(">="))
                return 1;
            if (op.equals("&")) return 2;
            if (op.equals("+") || op.equals("-")) return 3;
            if (op.equals("*") || op.equals("/")) return 4;
            if (op.equals("^")) return 5;
            return -1;
        }

        Object expr(int minPrec) throws Unsupported {
            Object left = unary();
            while (true) {
                Tok t = peek();
                if (t == null || t.type != OP_T) break;
                int p = prec(t.text);
                if (p < 0 || p < minPrec) break;
                pos++;
                Object right = expr(t.text.equals("^") ? p : p + 1);
                left = binary(t.text, left, right);
            }
            return left;
        }

        Object binary(String op, Object l, Object r) throws Unsupported {
            Object a = scalar(l, ctx), b = scalar(r, ctx);
            if (a instanceof Err) return a;
            if (b instanceof Err) return b;
            if (op.equals("&")) return str(a) + str(b);
            if (prec(op) == 1) {
                int c = compare(a, b);
                if (op.equals("=")) return c == 0;
                if (op.equals("<>")) return c != 0;
                if (op.equals("<")) return c < 0;
                if (op.equals(">")) return c > 0;
                if (op.equals("<=")) return c <= 0;
                return c >= 0;
            }
            Double x = num(a), y = num(b);
            if (x == null || y == null) return VALUE;
            double v;
            if (op.equals("+")) v = x + y;
            else if (op.equals("-")) v = x - y;
            else if (op.equals("*")) v = x * y;
            else if (op.equals("/")) {
                if (y == 0) return DIV0;
                v = x / y;
            } else {
                v = Math.pow(x, y);
            }
            if (Double.isNaN(v) || Double.isInfinite(v)) return NUM;
            return v;
        }

        Object unary() throws Unsupported {
            Tok t = peek();
            if (t != null && t.type == OP_T && (t.text.equals("-") || t.text.equals("+"))) {
                pos++;
                Object v = unary();
                if (t.text.equals("+")) return v;
                Object s = scalar(v, ctx);
                if (s instanceof Err) return s;
                Double d = num(s);
                return d == null ? VALUE : -d;
            }
            Object v = primary();
            Tok pct = peek();
            while (pct != null && pct.type == OP_T && pct.text.equals("%")) {
                pos++;
                Object s = scalar(v, ctx);
                if (s instanceof Err) return s;
                Double d = num(s);
                v = d == null ? VALUE : d / 100.0;
                pct = peek();
            }
            return v;
        }

        Object primary() throws Unsupported {
            Tok t = peek();
            if (t == null) throw new Unsupported("Unexpected end");
            pos++;
            switch (t.type) {
                case NUM_T:
                    try {
                        return Double.parseDouble(t.text);
                    } catch (NumberFormatException e) {
                        throw new Unsupported("Bad number");
                    }
                case STR_T:
                    return t.text;
                case BOOL_T:
                    return t.text.equals("TRUE");
                case ERR_T:
                    return new Err(t.text.toUpperCase(Locale.US));
                case LP: {
                    Object v = expr(0);
                    Tok r = peek();
                    if (r == null || r.type != RP) throw new Unsupported("Missing )");
                    pos++;
                    return v;
                }
                case REF_T: {
                    int sheet = ctx.currentSheet();
                    if (t.sheet != null) {
                        sheet = ctx.sheetIndex(t.sheet);
                        if (sheet < 0) return REF;
                    }
                    if (t.isRange) return new Range(sheet, t.r1, t.c1, t.r2, t.c2);
                    return new Range(sheet, t.r1, t.c1, t.r1, t.c1);
                }
                case FUNC_T:
                    return call(t.text);
                default:
                    throw new Unsupported("Unsupported token " + t.text);
            }
        }

        Object call(String name) throws Unsupported {
            Tok lp = peek();
            if (lp == null || lp.type != LP) throw new Unsupported("Expected (");
            pos++;
            ArrayList<Object> args = new ArrayList<Object>();
            Tok t = peek();
            if (t != null && t.type == RP) {
                pos++;
            } else {
                while (true) {
                    Tok a = peek();
                    if (a != null && (a.type == SEP || a.type == RP)) {
                        args.add(null); // omitted argument
                    } else {
                        args.add(expr(0));
                    }
                    Tok s = peek();
                    if (s == null) throw new Unsupported("Missing )");
                    pos++;
                    if (s.type == RP) break;
                    if (s.type != SEP) throw new Unsupported("Expected separator");
                }
            }
            return Functions.call(name, args, ctx);
        }
    }

    /** Excel ordering: numbers < text < booleans; empty compares as 0 or "". */
    static int compare(Object a, Object b) {
        if (a == null) a = b instanceof String ? "" : (b instanceof Boolean ? Boolean.FALSE : (Object) 0.0);
        if (b == null) b = a instanceof String ? "" : (a instanceof Boolean ? Boolean.FALSE : (Object) 0.0);
        int ra = rank(a), rb = rank(b);
        if (ra != rb) return ra < rb ? -1 : 1;
        if (a instanceof Double) return Double.compare((Double) a, (Double) b);
        if (a instanceof Boolean) {
            boolean x = (Boolean) a, y = (Boolean) b;
            return x == y ? 0 : (x ? 1 : -1);
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }

    private static int rank(Object v) {
        if (v instanceof Double) return 0;
        if (v instanceof String) return 1;
        return 2;
    }

    static Object scalar(Object v, Context ctx) throws Unsupported {
        if (v instanceof Range) {
            Range r = (Range) v;
            if (r.r1 != r.r2 || r.c1 != r.c2) return VALUE;
            return ctx.value(r.sheet, r.r1, r.c1);
        }
        return v;
    }

    static Double num(Object s) {
        if (s == null) return 0.0;
        if (s instanceof Double) return (Double) s;
        if (s instanceof Boolean) return ((Boolean) s) ? 1.0 : 0.0;
        if (s instanceof String) {
            String t = ((String) s).trim();
            if (t.length() == 0) return 0.0;
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    static String str(Object s) {
        if (s == null) return "";
        if (s instanceof Double) return NumFormat.general((Double) s);
        if (s instanceof Boolean) return ((Boolean) s) ? "TRUE" : "FALSE";
        return s.toString();
    }

    // ------------------------------------------------------------------ rewriting

    /** Shifts relative references by (dr, dc) — used to expand shared formulas. */
    public static String shift(String formula, int dr, int dc) throws Unsupported {
        ArrayList<Tok> toks = tokenize(formula);
        StringBuilder sb = new StringBuilder();
        int cur = 0;
        for (Tok t : toks) {
            if (t.type != REF_T) continue;
            sb.append(formula, cur, t.start);
            String prefix = formula.substring(t.start, t.end);
            int bang = prefix.lastIndexOf('!');
            if (bang >= 0) sb.append(prefix, 0, bang + 1);
            int r1 = t.absR1 ? t.r1 : t.r1 + dr, c1 = t.absC1 ? t.c1 : t.c1 + dc;
            if (r1 < 0 || c1 < 0) return null;
            sb.append(CellRef.name(c1, r1, t.absC1, t.absR1));
            if (t.isRange) {
                int r2 = t.absR2 ? t.r2 : t.r2 + dr, c2 = t.absC2 ? t.c2 : t.c2 + dc;
                if (r2 < 0 || c2 < 0) return null;
                sb.append(':').append(CellRef.name(c2, r2, t.absC2, t.absR2));
            }
            cur = t.end;
        }
        sb.append(formula, cur, formula.length());
        return sb.toString();
    }

    /** Excel syntax ("SUM(A1:B2,Sheet2!C3)") to OpenFormula ("of:=SUM([.A1:.B2];[Sheet2.C3])"). */
    public static String toOpenFormula(String formula) throws Unsupported {
        ArrayList<Tok> toks = tokenize(formula);
        StringBuilder sb = new StringBuilder("of:=");
        int cur = 0;
        for (Tok t : toks) {
            if (t.type != REF_T && t.type != SEP) continue;
            sb.append(formula, cur, t.start);
            if (t.type == SEP) {
                sb.append(';');
            } else {
                String sheet = t.sheet == null ? "" : quoteOdfSheet(t.sheet);
                sb.append('[').append(sheet).append('.').append(CellRef.name(t.c1, t.r1, t.absC1, t.absR1));
                if (t.isRange) sb.append(":.").append(CellRef.name(t.c2, t.r2, t.absC2, t.absR2));
                sb.append(']');
            }
            cur = t.end;
        }
        sb.append(formula, cur, formula.length());
        return sb.toString();
    }

    private static String quoteOdfSheet(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') return "'" + s.replace("'", "''") + "'";
        }
        return s;
    }

    /** OpenFormula ("of:=SUM([.A1:.B2])") to Excel syntax ("SUM(A1:B2)"). */
    public static String fromOpenFormula(String of) throws Unsupported {
        String f = of;
        int colon = f.indexOf(':');
        if (colon > 0 && colon < 8 && f.substring(0, colon).matches("[a-zA-Z]+")) f = f.substring(colon + 1);
        if (f.startsWith("=")) f = f.substring(1);
        StringBuilder sb = new StringBuilder();
        int i = 0, n = f.length();
        while (i < n) {
            char c = f.charAt(i);
            if (c == '"') {
                int j = i + 1;
                while (j < n) {
                    if (f.charAt(j) == '"') {
                        if (j + 1 < n && f.charAt(j + 1) == '"') {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                if (j >= n) throw new Unsupported("Unterminated string");
                sb.append(f, i, j + 1);
                i = j + 1;
            } else if (c == '[') {
                int j = f.indexOf(']', i);
                if (j < 0) throw new Unsupported("Unterminated reference");
                sb.append(odfRef(f.substring(i + 1, j)));
                i = j + 1;
            } else if (c == ';') {
                sb.append(',');
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    private static String odfRef(String r) throws Unsupported {
        String[] parts = r.split(":");
        if (parts.length > 2) throw new Unsupported("Complex reference");
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < parts.length; k++) {
            String p = parts[k];
            int dot = p.lastIndexOf('.');
            if (dot < 0) throw new Unsupported("Bad reference");
            String sheet = p.substring(0, dot);
            String cell = p.substring(dot + 1);
            if (sheet.startsWith("$")) sheet = sheet.substring(1);
            if (k == 0 && sheet.length() > 0) {
                if (sheet.startsWith("'") && sheet.endsWith("'") && sheet.length() >= 2) {
                    sheet = sheet.substring(1, sheet.length() - 1).replace("''", "'");
                }
                sb.append('\'').append(sheet.replace("'", "''")).append("'!");
            } else if (k == 1 && sheet.length() > 0 && !parts[0].startsWith(sheet + ".") && !parts[0].startsWith("$" + sheet + ".")) {
                throw new Unsupported("3D reference");
            }
            if (k == 1) sb.append(':');
            if (cellAt(cell, 0) == null) throw new Unsupported("Bad cell reference");
            sb.append(cell);
        }
        return sb.toString();
    }
}
