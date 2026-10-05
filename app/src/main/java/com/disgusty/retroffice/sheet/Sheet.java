package com.disgusty.retroffice.sheet;

import java.util.HashMap;

/** One worksheet. Only non-empty (or styled) cells are stored. */
public final class Sheet {
    public String name;
    public final HashMap<Long, Cell> cells = new HashMap<Long, Cell>();
    public int maxRow = -1, maxCol = -1;
    /** Column widths in points, where known. */
    public final HashMap<Integer, Float> colWidthPt = new HashMap<Integer, Float>();
    /** Sheets we can show but not change (e.g. chart sheets). */
    public boolean readOnly;
    public Object src;

    public Sheet(String name) {
        this.name = name;
    }

    public Cell get(int row, int col) {
        return cells.get(CellRef.key(row, col));
    }

    public Cell getOrCreate(int row, int col) {
        long k = CellRef.key(row, col);
        Cell c = cells.get(k);
        if (c == null) {
            c = new Cell();
            cells.put(k, c);
        }
        if (row > maxRow) maxRow = row;
        if (col > maxCol) maxCol = col;
        return c;
    }

    public void put(int row, int col, Cell c) {
        cells.put(CellRef.key(row, col), c);
        if (c.type != Cell.EMPTY || c.formula != null) {
            if (row > maxRow) maxRow = row;
            if (col > maxCol) maxCol = col;
        }
    }
}
