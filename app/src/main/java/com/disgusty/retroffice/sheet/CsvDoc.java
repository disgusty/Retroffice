package com.disgusty.retroffice.sheet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.disgusty.retroffice.doc.FileUtil;
import com.disgusty.retroffice.doc.TextCodec;

/** CSV/TSV. Every field is kept as the exact text it had; saving rewrites the file. */
public final class CsvDoc extends SheetDoc {
    private char delim;
    private TextCodec codec;
    private boolean trailingNewline;
    private String ext;
    public boolean switchedToUtf8;

    @Override
    public String formatName() {
        return ext.toUpperCase(java.util.Locale.US);
    }

    @Override
    protected boolean supportsFormulas() {
        return false;
    }

    public static CsvDoc open(File f, String ext) throws IOException {
        byte[] b = FileUtil.readAll(f, 32L * 1024 * 1024);
        if (TextCodec.looksBinary(b)) throw new IOException("Not a text document");
        TextCodec.Decoded d = TextCodec.decode(b);
        CsvDoc doc = new CsvDoc();
        doc.ext = ext.length() == 0 ? "csv" : ext;
        doc.codec = d.codec;
        doc.delim = ext.equals("tsv") || ext.equals("tab") ? '\t' : detect(d.text);
        doc.trailingNewline = d.text.endsWith("\n");
        Sheet sheet = new Sheet(FileUtil.baseName(f.getName()));
        ArrayList<ArrayList<String>> rows = parse(d.text, doc.delim);
        doc.rememberWidths(rows);
        for (int r = 0; r < rows.size(); r++) {
            ArrayList<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                String v = row.get(c);
                if (v.length() == 0) continue;
                Cell cell = new Cell();
                cell.type = Cell.STRING;
                cell.str = v;
                cell.display = v;
                sheet.put(r, c, cell);
            }
        }
        // Keep the row/column extent even for empty trailing fields so the file round-trips.
        sheet.maxRow = Math.max(sheet.maxRow, rows.size() - 1);
        for (ArrayList<String> row : rows) sheet.maxCol = Math.max(sheet.maxCol, row.size() - 1);
        doc.sheets.add(sheet);
        doc.markLoaded();
        return doc;
    }

    private static char detect(String text) {
        int nl = text.indexOf('\n');
        String first = nl < 0 ? text : text.substring(0, nl);
        int commas = 0, semis = 0, tabs = 0;
        boolean q = false;
        for (int i = 0; i < first.length(); i++) {
            char c = first.charAt(i);
            if (c == '"') q = !q;
            if (q) continue;
            if (c == ',') commas++;
            else if (c == ';') semis++;
            else if (c == '\t') tabs++;
        }
        if (tabs > commas && tabs > semis) return '\t';
        if (semis > commas) return ';';
        return ',';
    }

    static ArrayList<ArrayList<String>> parse(String text, char delim) {
        ArrayList<ArrayList<String>> rows = new ArrayList<ArrayList<String>>();
        ArrayList<String> row = new ArrayList<String>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false, fieldStarted = false;
        int i = 0, n = text.length();
        if (n == 0) return rows;
        while (i < n) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    quoted = false;
                } else {
                    field.append(c);
                }
                i++;
                continue;
            }
            if (c == '"' && field.length() == 0 && !fieldStarted) {
                quoted = true;
                fieldStarted = true;
            } else if (c == delim) {
                row.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
            } else if (c == '\n') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<String>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
            i++;
        }
        if (field.length() > 0 || fieldStarted || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    String serialize() {
        Sheet sh = sheets.get(0);
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r <= sh.maxRow; r++) {
            int lastCol = -1;
            for (int c = 0; c <= sh.maxCol; c++) {
                Cell cell = sh.get(r, c);
                if (cell != null && cell.type != Cell.EMPTY) lastCol = c;
            }
            int cols = Math.max(lastCol, rowWidth(r)) + 1;
            for (int c = 0; c < cols; c++) {
                if (c > 0) sb.append(delim);
                Cell cell = sh.get(r, c);
                String v = cell == null || cell.type == Cell.EMPTY ? "" : cell.str;
                sb.append(quote(v));
            }
            if (r < sh.maxRow || trailingNewline) sb.append('\n');
        }
        return sb.toString();
    }

    /** Original field count per row, so trailing empty fields survive. */
    private final ArrayList<Integer> widths = new ArrayList<Integer>();

    private int rowWidth(int r) {
        return r < widths.size() ? widths.get(r) - 1 : -1;
    }

    private String quote(String v) {
        boolean need = v.indexOf(delim) >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!need) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    @Override
    public SaveJob prepareSave() {
        final String fp = fingerprint();
        final String content = serialize();
        TextCodec c = codec;
        if (!c.canEncode(content)) {
            c = c.asUtf8();
            switchedToUtf8 = true;
        }
        final TextCodec use = c;
        return new SaveJob() {
            @Override
            public String fingerprint() {
                return fp;
            }

            @Override
            public void write(File out) throws Exception {
                FileUtil.writeAll(out, use.encode(content));
            }

            @Override
            public void verify(File written) throws Exception {
                byte[] actual = FileUtil.readAll(written, Long.MAX_VALUE);
                if (!FileUtil.equal(use.encode(content), actual)) {
                    throw new IOException("Verification failed: file reads back differently");
                }
            }
        };
    }

    /** Called by open() to remember per-row field counts. */
    void rememberWidths(ArrayList<ArrayList<String>> rows) {
        widths.clear();
        for (ArrayList<String> r : rows) widths.add(r.size());
    }

    @Override
    public void close() {
    }
}
