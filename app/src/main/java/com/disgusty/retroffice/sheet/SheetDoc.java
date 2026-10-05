package com.disgusty.retroffice.sheet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import com.disgusty.retroffice.doc.Doc;

/** Common spreadsheet behaviour: editing cells and recalculating dependent formulas. */
public abstract class SheetDoc extends Doc {
    public final ArrayList<Sheet> sheets = new ArrayList<Sheet>();

    @Override
    public int kind() {
        return SHEET;
    }

    /** Result of an edit: null when accepted, otherwise a message key. */
    public static final String ERR_COVERED = "covered", ERR_READONLY = "readonly", ERR_FORMULA = "formula";

    /** Whether formulas can be stored (CSV can't). */
    protected boolean supportsFormulas() {
        return true;
    }

    /** Hook for formats that need to check a formula can be written (e.g. converted to ODF). */
    protected boolean canStoreFormula(String formula) {
        try {
            Formula.tokenize(formula);
            return true;
        } catch (Formula.Unsupported e) {
            return false;
        }
    }

    /** Formats a number for display in a given cell (number format, if any). */
    protected String displayNumber(Cell c, double v) {
        return NumFormat.general(v);
    }

    public String setInput(int sheetIdx, int row, int col, String text) {
        Sheet sh = sheets.get(sheetIdx);
        if (sh.readOnly) return ERR_READONLY;
        Cell c = sh.getOrCreate(row, col);
        if (c.covered) return ERR_COVERED;
        String t = text == null ? "" : text;
        if (t.equals(c.inputText())) return null;
        String newFormula = null;
        Object value;
        if (!supportsFormulas()) {
            // Plain-text formats keep exactly what was typed.
            value = t.length() == 0 ? null : t;
        } else if (t.length() > 1 && t.charAt(0) == '=') {
            newFormula = t.substring(1).trim();
            if (!canStoreFormula(newFormula)) return ERR_FORMULA;
            value = null;
        } else if (t.length() == 0) {
            value = null;
        } else if (t.charAt(0) == '\'') {
            value = t.substring(1);
        } else if (t.equalsIgnoreCase("TRUE") || t.equalsIgnoreCase("FALSE")) {
            value = t.equalsIgnoreCase("TRUE");
        } else {
            Double d = NumFormat.parseInput(t);
            value = d != null ? (Object) d : t;
        }
        c.formula = newFormula;
        c.edited = true;
        if (newFormula != null) {
            try {
                c.setValue(Formula.evaluate(newFormula, context(sheetIdx)));
            } catch (Formula.Unsupported e) {
                c.setValue(null);
                c.display = "";
            }
        } else {
            c.setValue(value);
        }
        refreshDisplay(c);
        if (row > sh.maxRow) sh.maxRow = row;
        if (col > sh.maxCol) sh.maxCol = col;
        recalcDependents(sheetIdx, row, col);
        return null;
    }

    protected void refreshDisplay(Cell c) {
        switch (c.type) {
            case Cell.NUMBER: c.display = displayNumber(c, c.num); break;
            case Cell.BOOL: c.display = c.bool ? "TRUE" : "FALSE"; break;
            case Cell.STRING:
            case Cell.ERROR: c.display = c.str; break;
            default: c.display = "";
        }
    }

    // ------------------------------------------------------------------ recalculation

    Formula.Context context(final int sheetIdx) {
        return new Formula.Context() {
            @Override
            public Object value(int sheet, int row, int col) {
                if (sheet < 0 || sheet >= sheets.size()) return Formula.REF;
                Cell c = sheets.get(sheet).get(row, col);
                return c == null ? null : c.value();
            }

            @Override
            public int sheetIndex(String name) {
                for (int i = 0; i < sheets.size(); i++) {
                    if (sheets.get(i).name.equalsIgnoreCase(name)) return i;
                }
                return -1;
            }

            @Override
            public int currentSheet() {
                return sheetIdx;
            }
        };
    }

    /**
     * Recomputes formulas that (directly or indirectly) read a changed cell. Formulas that don't
     * depend on any edit keep the value stored in the file, even if our evaluator would disagree.
     */
    private void recalcDependents(int sheetIdx, int row, int col) {
        HashSet<String> dirty = new HashSet<String>();
        dirty.add(sheetIdx + ":" + row + ":" + col);
        // Collect formula cells and their references once.
        ArrayList<Object[]> formulas = new ArrayList<Object[]>();
        for (int s = 0; s < sheets.size(); s++) {
            for (Map.Entry<Long, Cell> e : sheets.get(s).cells.entrySet()) {
                Cell c = e.getValue();
                if (c.formula == null) continue;
                int[][] refs = references(c.formula, s);
                if (refs == null) continue;
                formulas.add(new Object[]{s, e.getKey(), c, refs});
            }
        }
        boolean changed = true;
        int guard = 0;
        HashSet<Cell> done = new HashSet<Cell>();
        while (changed && guard++ < 100) {
            changed = false;
            for (Object[] f : formulas) {
                Cell c = (Cell) f[2];
                if (done.contains(c)) continue;
                int s = (Integer) f[0];
                long key = (Long) f[1];
                int r = CellRef.keyRow(key), cc = CellRef.keyCol(key);
                if (s == sheetIdx && r == row && cc == col) continue;
                if (!touches((int[][]) f[3], dirty)) continue;
                done.add(c);
                try {
                    Object v = Formula.evaluate(c.formula, context(s));
                    if (!c.sameValue(v)) {
                        c.setValue(v);
                        refreshDisplay(c);
                        c.edited = true;
                    }
                } catch (Formula.Unsupported e) {
                    // keep cached value
                }
                dirty.add(s + ":" + r + ":" + cc);
                changed = true;
            }
        }
    }

    private static boolean touches(int[][] refs, HashSet<String> dirty) {
        for (String d : dirty) {
            String[] p = d.split(":");
            int s = Integer.parseInt(p[0]), r = Integer.parseInt(p[1]), c = Integer.parseInt(p[2]);
            for (int[] ref : refs) {
                if (ref[0] == s && r >= ref[1] && r <= ref[3] && c >= ref[2] && c <= ref[4]) return true;
            }
        }
        return false;
    }

    /** {sheet, r1, c1, r2, c2} for every reference in a formula, or null if it can't be analysed. */
    private int[][] references(String formula, int sheetIdx) {
        try {
            ArrayList<Formula.Tok> toks = Formula.tokenize(formula);
            ArrayList<int[]> out = new ArrayList<int[]>();
            Formula.Context ctx = context(sheetIdx);
            for (Formula.Tok t : toks) {
                if (t.type == Formula.NAME_T) return null; // defined names: unknown dependencies
                if (t.type != Formula.REF_T) continue;
                int s = t.sheet == null ? sheetIdx : ctx.sheetIndex(t.sheet);
                if (t.isRange) {
                    out.add(new int[]{s, Math.min(t.r1, t.r2), Math.min(t.c1, t.c2), Math.max(t.r1, t.r2), Math.max(t.c1, t.c2)});
                } else {
                    out.add(new int[]{s, t.r1, t.c1, t.r1, t.c1});
                }
            }
            return out.toArray(new int[out.size()][]);
        } catch (Formula.Unsupported e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ state

    @Override
    public String fingerprint() {
        ArrayList<String> parts = new ArrayList<String>();
        for (int s = 0; s < sheets.size(); s++) {
            for (Map.Entry<Long, Cell> e : sheets.get(s).cells.entrySet()) {
                Cell c = e.getValue();
                if (!c.edited) continue;
                parts.add(s + ":" + e.getKey() + ":" + c.type + ":" + c.inputText() + ":" + c.display);
            }
        }
        Collections.sort(parts);
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(p).append('\n');
        return sb.toString();
    }

    @Override
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        for (Sheet sh : sheets) {
            if (sheets.size() > 1) sb.append(sh.name).append('\n');
            for (int r = 0; r <= sh.maxRow; r++) {
                for (int c = 0; c <= sh.maxCol; c++) {
                    if (c > 0) sb.append('\t');
                    Cell cell = sh.get(r, c);
                    if (cell != null) sb.append(cell.display);
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** Snapshot of an edited cell for a background save. */
    public static final class EditedCell {
        public int sheet, row, col, type;
        public double num;
        public String str, formula, display;
        public boolean bool;
        public Object src;

        static EditedCell of(int sheet, int row, int col, Cell c) {
            EditedCell e = new EditedCell();
            e.sheet = sheet;
            e.row = row;
            e.col = col;
            e.type = c.type;
            e.num = c.num;
            e.str = c.str;
            e.bool = c.bool;
            e.formula = c.formula;
            e.display = c.display;
            e.src = c.src;
            return e;
        }

        public String valueText() {
            switch (type) {
                case Cell.NUMBER: return NumFormat.store(num);
                case Cell.BOOL: return bool ? "1" : "0";
                default: return str;
            }
        }
    }

    /** Edited cells grouped by sheet, sorted by row then column. */
    protected HashMap<Integer, ArrayList<EditedCell>> editedCells() {
        HashMap<Integer, ArrayList<EditedCell>> out = new HashMap<Integer, ArrayList<EditedCell>>();
        for (int s = 0; s < sheets.size(); s++) {
            ArrayList<EditedCell> list = new ArrayList<EditedCell>();
            for (Map.Entry<Long, Cell> e : sheets.get(s).cells.entrySet()) {
                Cell c = e.getValue();
                if (c.edited) list.add(EditedCell.of(s, CellRef.keyRow(e.getKey()), CellRef.keyCol(e.getKey()), c));
            }
            if (list.isEmpty()) continue;
            Collections.sort(list, new java.util.Comparator<EditedCell>() {
                @Override
                public int compare(EditedCell a, EditedCell b) {
                    if (a.row != b.row) return a.row < b.row ? -1 : 1;
                    return a.col < b.col ? -1 : (a.col == b.col ? 0 : 1);
                }
            });
            out.put(s, list);
        }
        return out;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.US);
    }
}
