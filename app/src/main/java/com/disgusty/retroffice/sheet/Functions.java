package com.disgusty.retroffice.sheet;

import java.util.ArrayList;
import java.util.Locale;

/** Built-in spreadsheet functions supported by {@link Formula}. */
final class Functions {
    private Functions() {
    }

    /** Max cells a single range argument may cover, to keep evaluation bounded. */
    private static final long MAX_CELLS = 200000;

    static Object call(String name, ArrayList<Object> args, Formula.Context ctx) throws Formula.Unsupported {
        if (name.equals("SUM")) return aggregate(args, ctx, 0);
        if (name.equals("AVERAGE")) return aggregate(args, ctx, 1);
        if (name.equals("MIN")) return aggregate(args, ctx, 2);
        if (name.equals("MAX")) return aggregate(args, ctx, 3);
        if (name.equals("COUNT")) return aggregate(args, ctx, 4);
        if (name.equals("PRODUCT")) return aggregate(args, ctx, 5);
        if (name.equals("COUNTA")) return countA(args, ctx, false);
        if (name.equals("COUNTBLANK")) return countA(args, ctx, true);
        if (name.equals("IF")) {
            need(args, 2, 3);
            Object c = Formula.scalar(args.get(0), ctx);
            if (c instanceof Formula.Err) return c;
            Double d = Formula.num(c);
            if (d == null) return Formula.VALUE;
            Object v = d != 0 ? args.get(1) : (args.size() > 2 ? args.get(2) : Boolean.FALSE);
            v = Formula.scalar(v, ctx);
            return v == null ? (Object) 0.0 : v;
        }
        if (name.equals("IFERROR")) {
            need(args, 2, 2);
            Object v = Formula.scalar(args.get(0), ctx);
            if (v instanceof Formula.Err) v = Formula.scalar(args.get(1), ctx);
            return v == null ? (Object) 0.0 : v;
        }
        if (name.equals("AND") || name.equals("OR")) {
            if (args.isEmpty()) throw new Formula.Unsupported("AND/OR needs arguments");
            boolean and = name.equals("AND");
            boolean acc = and;
            for (Object a : args) {
                for (Object v : values(a, ctx)) {
                    if (v instanceof Formula.Err) return v;
                    if (v == null || v instanceof String) continue;
                    Double d = Formula.num(v);
                    if (d == null) continue;
                    acc = and ? (acc && d != 0) : (acc || d != 0);
                }
            }
            return acc;
        }
        if (name.equals("NOT")) {
            need(args, 1, 1);
            Object v = Formula.scalar(args.get(0), ctx);
            if (v instanceof Formula.Err) return v;
            Double d = Formula.num(v);
            return d == null ? Formula.VALUE : (Object) (d == 0);
        }
        if (name.equals("PI")) {
            need(args, 0, 0);
            return Math.PI;
        }
        if (name.equals("ABS") || name.equals("INT") || name.equals("SQRT") || name.equals("SIGN")
                || name.equals("EXP") || name.equals("LN") || name.equals("LOG10")) {
            need(args, 1, 1);
            Object v = Formula.scalar(args.get(0), ctx);
            if (v instanceof Formula.Err) return v;
            Double d = Formula.num(v);
            if (d == null) return Formula.VALUE;
            double r;
            if (name.equals("ABS")) r = Math.abs(d);
            else if (name.equals("INT")) r = Math.floor(d);
            else if (name.equals("SQRT")) {
                if (d < 0) return Formula.NUM;
                r = Math.sqrt(d);
            } else if (name.equals("SIGN")) r = Math.signum(d);
            else if (name.equals("EXP")) r = Math.exp(d);
            else {
                if (d <= 0) return Formula.NUM;
                r = name.equals("LN") ? Math.log(d) : Math.log(d) / Math.log(10);
            }
            return check(r);
        }
        if (name.equals("ROUND") || name.equals("ROUNDUP") || name.equals("ROUNDDOWN")) {
            need(args, 1, 2);
            Double x = numArg(args, 0, ctx);
            Double digits = args.size() > 1 ? numArg(args, 1, ctx) : 0.0;
            if (x == null || digits == null) return Formula.VALUE;
            double f = Math.pow(10, Math.floor(digits));
            double v = x * f;
            double r;
            if (name.equals("ROUND")) r = Math.signum(v) * Math.floor(Math.abs(v) + 0.5 + 1e-9);
            else if (name.equals("ROUNDUP")) r = Math.signum(v) * Math.ceil(Math.abs(v) - 1e-9);
            else r = Math.signum(v) * Math.floor(Math.abs(v) + 1e-9);
            return check(r / f);
        }
        if (name.equals("MOD")) {
            need(args, 2, 2);
            Double a = numArg(args, 0, ctx), b = numArg(args, 1, ctx);
            if (a == null || b == null) return Formula.VALUE;
            if (b == 0) return Formula.DIV0;
            return check(a - b * Math.floor(a / b));
        }
        if (name.equals("POWER")) {
            need(args, 2, 2);
            Double a = numArg(args, 0, ctx), b = numArg(args, 1, ctx);
            if (a == null || b == null) return Formula.VALUE;
            return check(Math.pow(a, b));
        }
        if (name.equals("CONCATENATE") || name.equals("CONCAT")) {
            StringBuilder sb = new StringBuilder();
            for (Object a : args) {
                for (Object v : values(a, ctx)) {
                    if (v instanceof Formula.Err) return v;
                    sb.append(Formula.str(v));
                }
            }
            return sb.toString();
        }
        if (name.equals("LEN") || name.equals("UPPER") || name.equals("LOWER") || name.equals("TRIM")) {
            need(args, 1, 1);
            Object v = Formula.scalar(args.get(0), ctx);
            if (v instanceof Formula.Err) return v;
            String s = Formula.str(v);
            if (name.equals("LEN")) return (double) s.length();
            if (name.equals("UPPER")) return s.toUpperCase(Locale.getDefault());
            if (name.equals("LOWER")) return s.toLowerCase(Locale.getDefault());
            return s.trim().replaceAll(" +", " ");
        }
        if (name.equals("LEFT") || name.equals("RIGHT")) {
            need(args, 1, 2);
            Object v = Formula.scalar(args.get(0), ctx);
            if (v instanceof Formula.Err) return v;
            String s = Formula.str(v);
            Double n = args.size() > 1 ? numArg(args, 1, ctx) : 1.0;
            if (n == null || n < 0) return Formula.VALUE;
            int k = (int) Math.min(s.length(), Math.floor(n));
            return name.equals("LEFT") ? s.substring(0, k) : s.substring(s.length() - k);
        }
        if (name.equals("MID")) {
            need(args, 3, 3);
            Object v = Formula.scalar(args.get(0), ctx);
            if (v instanceof Formula.Err) return v;
            String s = Formula.str(v);
            Double st = numArg(args, 1, ctx), n = numArg(args, 2, ctx);
            if (st == null || n == null || st < 1 || n < 0) return Formula.VALUE;
            int a = (int) Math.min(s.length(), Math.floor(st) - 1);
            int b = (int) Math.min(s.length(), a + Math.floor(n));
            return s.substring(a, b);
        }
        if (name.equals("ISBLANK") || name.equals("ISNUMBER") || name.equals("ISTEXT") || name.equals("ISERROR")) {
            need(args, 1, 1);
            Object v = Formula.scalar(args.get(0), ctx);
            if (name.equals("ISBLANK")) return v == null;
            if (name.equals("ISNUMBER")) return v instanceof Double;
            if (name.equals("ISTEXT")) return v instanceof String;
            return v instanceof Formula.Err;
        }
        if (name.equals("COUNTIF") || name.equals("SUMIF")) {
            need(args, 2, name.equals("SUMIF") ? 3 : 2);
            Object crit = Formula.scalar(args.get(1), ctx);
            if (crit instanceof Formula.Err) return crit;
            if (!(args.get(0) instanceof Formula.Range)) return Formula.VALUE;
            Formula.Range r = (Formula.Range) args.get(0);
            Formula.Range sumR = args.size() > 2 && args.get(2) instanceof Formula.Range ? (Formula.Range) args.get(2) : r;
            checkSize(r);
            double sum = 0;
            int count = 0;
            for (int row = r.r1; row <= r.r2; row++) {
                for (int col = r.c1; col <= r.c2; col++) {
                    Object v = ctx.value(r.sheet, row, col);
                    if (matches(v, crit)) {
                        count++;
                        if (name.equals("SUMIF")) {
                            Object s = ctx.value(sumR.sheet, sumR.r1 + row - r.r1, sumR.c1 + col - r.c1);
                            if (s instanceof Double) sum += (Double) s;
                        }
                    }
                }
            }
            return name.equals("COUNTIF") ? (double) count : sum;
        }
        throw new Formula.Unsupported("Function " + name);
    }

    private static boolean matches(Object v, Object crit) {
        String c = Formula.str(crit);
        String op = "=";
        for (String o : new String[]{"<=", ">=", "<>", "<", ">", "="}) {
            if (c.startsWith(o)) {
                op = o;
                c = c.substring(o.length());
                break;
            }
        }
        Object target;
        Double d = null;
        try {
            d = Double.parseDouble(c.trim());
        } catch (NumberFormatException ignored) {
        }
        target = d != null ? (Object) d : c;
        if (op.equals("=") && target instanceof String) {
            return v != null && Formula.str(v).equalsIgnoreCase(c);
        }
        if (v == null) return op.equals("<>");
        if (target instanceof Double && !(v instanceof Double)) return op.equals("<>");
        int cmp = Formula.compare(v, target);
        if (op.equals("=")) return cmp == 0;
        if (op.equals("<>")) return cmp != 0;
        if (op.equals("<")) return cmp < 0;
        if (op.equals(">")) return cmp > 0;
        if (op.equals("<=")) return cmp <= 0;
        return cmp >= 0;
    }

    private static void need(ArrayList<Object> args, int min, int max) throws Formula.Unsupported {
        if (args.size() < min || args.size() > max) throw new Formula.Unsupported("Wrong argument count");
    }

    private static Double numArg(ArrayList<Object> args, int i, Formula.Context ctx) throws Formula.Unsupported {
        Object v = Formula.scalar(args.get(i), ctx);
        if (v instanceof Formula.Err) return null;
        return Formula.num(v);
    }

    private static Object check(double r) {
        if (Double.isNaN(r) || Double.isInfinite(r)) return Formula.NUM;
        return r;
    }

    private static void checkSize(Formula.Range r) throws Formula.Unsupported {
        long cells = (long) (r.r2 - r.r1 + 1) * (r.c2 - r.c1 + 1);
        if (cells > MAX_CELLS) throw new Formula.Unsupported("Range too large");
    }

    /** Values of an argument: every cell of a range (empties as null) or the scalar itself. */
    private static ArrayList<Object> values(Object a, Formula.Context ctx) throws Formula.Unsupported {
        ArrayList<Object> out = new ArrayList<Object>();
        if (a instanceof Formula.Range) {
            Formula.Range r = (Formula.Range) a;
            checkSize(r);
            for (int row = r.r1; row <= r.r2; row++)
                for (int col = r.c1; col <= r.c2; col++) out.add(ctx.value(r.sheet, row, col));
        } else {
            out.add(a);
        }
        return out;
    }

    /** SUM/AVERAGE/MIN/MAX/COUNT/PRODUCT: ranges skip text and empties, direct arguments convert. */
    private static Object aggregate(ArrayList<Object> args, Formula.Context ctx, int kind) throws Formula.Unsupported {
        double sum = 0, prod = 1, min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int count = 0;
        for (Object a : args) {
            if (a == null) continue;
            boolean range = a instanceof Formula.Range;
            for (Object v : values(a, ctx)) {
                if (v instanceof Formula.Err) {
                    if (kind == 4) continue;
                    return v;
                }
                Double d;
                if (range) {
                    if (!(v instanceof Double)) continue;
                    d = (Double) v;
                } else {
                    d = Formula.num(v);
                    if (d == null) {
                        if (kind == 4) continue;
                        return Formula.VALUE;
                    }
                }
                sum += d;
                prod *= d;
                min = Math.min(min, d);
                max = Math.max(max, d);
                count++;
            }
        }
        switch (kind) {
            case 0: return sum;
            case 1: return count == 0 ? Formula.DIV0 : (Object) (sum / count);
            case 2: return count == 0 ? 0.0 : min;
            case 3: return count == 0 ? 0.0 : max;
            case 4: return (double) count;
            default: return count == 0 ? 0.0 : prod;
        }
    }

    private static Object countA(ArrayList<Object> args, Formula.Context ctx, boolean blanks) throws Formula.Unsupported {
        int n = 0;
        for (Object a : args) {
            for (Object v : values(a, ctx)) {
                boolean empty = v == null || (v instanceof String && ((String) v).length() == 0 && blanks);
                if (blanks ? empty : v != null) n++;
            }
        }
        return (double) n;
    }
}
