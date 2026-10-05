package com.disgusty.retroffice.sheet;

/** One spreadsheet cell. */
public final class Cell {
    public static final int EMPTY = 0, NUMBER = 1, STRING = 2, BOOL = 3, ERROR = 4;

    public int type;
    public double num;
    public String str = "";
    public boolean bool;
    /** Formula in Excel A1 syntax without the leading '=', or null. */
    public String formula;
    /** Text shown in the grid. */
    public String display = "";
    /** Part of a merged area (not the top-left cell): read-only. */
    public boolean covered;
    /** True once the user (or a recalculation caused by the user) changed this cell. */
    public boolean edited;
    /** Format-specific source information. */
    public Object src;

    /** Value as seen by formulas. */
    public Object value() {
        switch (type) {
            case NUMBER: return num;
            case STRING: return str;
            case BOOL: return bool;
            case ERROR: return new Formula.Err(str);
            default: return null;
        }
    }

    public void setValue(Object v) {
        if (v == null) {
            type = EMPTY;
            str = "";
        } else if (v instanceof Double) {
            type = NUMBER;
            num = (Double) v;
        } else if (v instanceof Boolean) {
            type = BOOL;
            bool = (Boolean) v;
        } else if (v instanceof Formula.Err) {
            type = ERROR;
            str = ((Formula.Err) v).code;
        } else {
            type = STRING;
            str = v.toString();
        }
    }

    /** What the formula bar shows for editing. */
    public String inputText() {
        if (formula != null) return "=" + formula;
        switch (type) {
            case NUMBER: return NumFormat.general(num);
            case BOOL: return bool ? "TRUE" : "FALSE";
            case STRING:
            case ERROR: return str;
            default: return "";
        }
    }

    boolean sameValue(Object v) {
        Object mine = value();
        if (mine == null || v == null) return mine == v;
        if (mine instanceof Double && v instanceof Double) {
            double a = (Double) mine, b = (Double) v;
            return a == b || Math.abs(a - b) <= 1e-12 * Math.max(1, Math.abs(a));
        }
        if (mine instanceof Formula.Err && v instanceof Formula.Err) {
            return ((Formula.Err) mine).code.equals(((Formula.Err) v).code);
        }
        return mine.equals(v);
    }
}
