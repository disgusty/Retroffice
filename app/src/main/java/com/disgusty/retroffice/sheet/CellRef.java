package com.disgusty.retroffice.sheet;

/** A1-style cell addresses. Rows and columns are 0-based internally. */
public final class CellRef {
    private CellRef() {
    }

    public static int colIndex(String letters) {
        int c = 0;
        for (int i = 0; i < letters.length(); i++) {
            char ch = Character.toUpperCase(letters.charAt(i));
            if (ch < 'A' || ch > 'Z') return -1;
            c = c * 26 + (ch - 'A' + 1);
        }
        return c - 1;
    }

    public static String colName(int col) {
        StringBuilder sb = new StringBuilder();
        int c = col + 1;
        while (c > 0) {
            int m = (c - 1) % 26;
            sb.insert(0, (char) ('A' + m));
            c = (c - 1) / 26;
        }
        return sb.toString();
    }

    public static String name(int col, int row) {
        return colName(col) + (row + 1);
    }

    public static String name(int col, int row, boolean absCol, boolean absRow) {
        return (absCol ? "$" : "") + colName(col) + (absRow ? "$" : "") + (row + 1);
    }

    /** Parses "B3" into {row, col}, or null. */
    public static int[] parse(String ref) {
        if (ref == null) return null;
        int i = 0, n = ref.length();
        while (i < n && Character.isLetter(ref.charAt(i))) i++;
        if (i == 0 || i == n) return null;
        int col = colIndex(ref.substring(0, i));
        try {
            int row = Integer.parseInt(ref.substring(i)) - 1;
            if (row < 0 || col < 0) return null;
            return new int[]{row, col};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static long key(int row, int col) {
        return ((long) row << 20) | col;
    }

    public static int keyRow(long key) {
        return (int) (key >> 20);
    }

    public static int keyCol(long key) {
        return (int) (key & 0xFFFFF);
    }
}
