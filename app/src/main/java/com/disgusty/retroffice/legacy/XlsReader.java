package com.disgusty.retroffice.legacy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Reads Excel 97–2003 (BIFF8) and Excel 5.0/95 (BIFF5) workbooks: every worksheet with its
 * text, numbers, dates, booleans, errors and the cached results of formulas.
 *
 * The "Workbook" stream is a sequence of records (type, length, data). The globals part lists
 * the sheets and holds the shared string table (SST, which may spill into CONTINUE records) and
 * number formats; each sheet then has its own records, one per cell or group of cells.
 */
public final class XlsReader {
    private static final int BOF = 0x0809, EOF = 0x000A, CONTINUE = 0x003C, FILEPASS = 0x002F, CODEPAGE = 0x0042,
            BOUNDSHEET = 0x0085, SST = 0x00FC, FORMAT = 0x041E, XF = 0x00E0, LABELSST = 0x00FD, NUMBER = 0x0203,
            RK = 0x027E, MULRK = 0x00BD, LABEL = 0x0204, RSTRING = 0x00D6, BOOLERR = 0x0205, FORMULA = 0x0006,
            STRING = 0x0207, DATEMODE = 0x0022;

    private final byte[] w;
    private boolean biff8 = true;
    private int codepage = 1252;
    private boolean date1904;
    private final ArrayList<String> sst = new ArrayList<String>();
    private final ArrayList<Integer> xfFormat = new ArrayList<Integer>();
    private final HashMap<Integer, String> formats = new HashMap<Integer, String>();

    private XlsReader(byte[] w) {
        this.w = w;
    }

    public static ArrayList<OdfBuilder.SheetData> read(Cfb cfb) throws IOException {
        byte[] w = cfb.stream("Workbook");
        if (w == null) w = cfb.stream("Book");
        if (w == null) throw new IOException("Not an Excel workbook");
        return new XlsReader(w).run();
    }

    private int type(int o) {
        return Cfb.le16(w, o);
    }

    private int len(int o) {
        return Cfb.le16(w, o + 2);
    }

    private ArrayList<OdfBuilder.SheetData> run() throws IOException {
        ArrayList<Object[]> sheets = new ArrayList<Object[]>(); // {name, offset}
        int o = 0;
        boolean first = true;
        while (o + 4 <= w.length) {
            int t = type(o), l = len(o), dat = o + 4;
            if (dat + l > w.length) break;
            if (first) {
                if (t != BOF && t != 0x0409 && t != 0x0209) throw new IOException("Not an Excel workbook");
                int ver = Cfb.le16(w, dat);
                biff8 = ver == 0x0600 || l >= 16;
                if (ver == 0x0500) biff8 = false;
                first = false;
            } else if (t == FILEPASS) {
                throw new LegacyFormatException("Encrypted workbook", true);
            } else if (t == CODEPAGE && l >= 2) {
                codepage = Cfb.le16(w, dat);
            } else if (t == DATEMODE && l >= 2) {
                date1904 = Cfb.le16(w, dat) == 1;
            } else if (t == BOUNDSHEET && l >= 7) {
                int pos = Cfb.le32(w, dat);
                int kind = w[dat + 5] & 0xFF;
                String name = biff8 ? shortUnicode(dat + 6) : byteString(dat + 7, w[dat + 6] & 0xFF);
                if (kind == 0) sheets.add(new Object[]{name, pos});
            } else if (t == SST) {
                readSst(o);
            } else if (t == FORMAT && l >= 3) {
                int id = Cfb.le16(w, dat);
                String code = biff8 ? unicode(dat + 2) : byteString(dat + 3, w[dat + 2] & 0xFF);
                formats.put(id, code);
            } else if (t == XF && l >= 4) {
                xfFormat.add(Cfb.le16(w, dat + 2));
            } else if (t == EOF) {
                break;
            }
            o = dat + l;
        }
        ArrayList<OdfBuilder.SheetData> out = new ArrayList<OdfBuilder.SheetData>();
        for (Object[] s : sheets) {
            OdfBuilder.SheetData sd = new OdfBuilder.SheetData((String) s[0]);
            int pos = (Integer) s[1];
            if (pos >= 0 && pos + 4 <= w.length) readSheet(pos, sd);
            out.add(sd);
        }
        return out;
    }

    private void readSheet(int o, OdfBuilder.SheetData sd) {
        int pendingRow = -1, pendingCol = -1;
        boolean started = false;
        while (o + 4 <= w.length) {
            int t = type(o), l = len(o), d = o + 4;
            if (d + l > w.length) break;
            if (t == BOF) {
                if (started) { // embedded substream (chart): skip to its EOF
                    o = skipSubstream(o);
                    continue;
                }
                started = true;
            } else if (t == EOF) {
                break;
            } else if (t == LABELSST && l >= 10) {
                int isst = Cfb.le32(w, d + 6);
                text(sd, row(d), col(d), isst >= 0 && isst < sst.size() ? sst.get(isst) : "");
            } else if (t == NUMBER && l >= 14) {
                number(sd, row(d), col(d), Cfb.le16(w, d + 4), Double.longBitsToDouble(le64(d + 6)));
            } else if (t == RK && l >= 10) {
                number(sd, row(d), col(d), Cfb.le16(w, d + 4), rk(Cfb.le32(w, d + 6)));
            } else if (t == MULRK && l >= 6) {
                int r = row(d), c = col(d);
                int n = (l - 6) / 6;
                for (int i = 0; i < n; i++) {
                    int e = d + 4 + i * 6;
                    number(sd, r, c + i, Cfb.le16(w, e), rk(Cfb.le32(w, e + 2)));
                }
            } else if ((t == LABEL || t == RSTRING) && l >= 8) {
                String s = biff8 ? unicode(d + 6) : byteString(d + 8, Cfb.le16(w, d + 6));
                text(sd, row(d), col(d), s);
            } else if (t == BOOLERR && l >= 8) {
                int v = w[d + 6] & 0xFF;
                if (w[d + 7] == 0) {
                    OdfBuilder.Cell c = new OdfBuilder.Cell();
                    c.type = OdfBuilder.Cell.BOOL;
                    c.bool = v != 0;
                    sd.put(row(d), col(d), c);
                } else {
                    text(sd, row(d), col(d), error(v));
                }
            } else if (t == FORMULA && l >= 14) {
                int r = row(d), c = col(d);
                if (Cfb.le16(w, d + 12) == 0xFFFF) {
                    int kind = w[d + 6] & 0xFF;
                    if (kind == 0) {        // string result follows in a STRING record
                        pendingRow = r;
                        pendingCol = c;
                    } else if (kind == 1) {
                        OdfBuilder.Cell cell = new OdfBuilder.Cell();
                        cell.type = OdfBuilder.Cell.BOOL;
                        cell.bool = w[d + 8] != 0;
                        sd.put(r, c, cell);
                    } else if (kind == 2) {
                        text(sd, r, c, error(w[d + 8] & 0xFF));
                    }
                } else {
                    number(sd, r, c, Cfb.le16(w, d + 4), Double.longBitsToDouble(le64(d + 6)));
                }
            } else if (t == STRING && pendingRow >= 0) {
                String s = biff8 ? unicode(d) : byteString(d + 2, Cfb.le16(w, d));
                text(sd, pendingRow, pendingCol, s);
                pendingRow = -1;
            }
            o = d + l;
        }
    }

    private int skipSubstream(int o) {
        int depth = 0;
        while (o + 4 <= w.length) {
            int t = type(o), l = len(o);
            if (t == BOF) depth++;
            else if (t == EOF && --depth == 0) return o + 4 + l;
            o += 4 + l;
        }
        return w.length;
    }


    private int row(int d) {
        return Cfb.le16(w, d);
    }

    private int col(int d) {
        return Cfb.le16(w, d + 2);
    }

    private long le64(int o) {
        return (Cfb.le32(w, o) & 0xFFFFFFFFL) | ((long) Cfb.le32(w, o + 4) << 32);
    }

    /** RK: a compressed number (30-bit integer or the top bits of a double, optionally /100). */
    static double rk(int v) {
        double d;
        if ((v & 0x02) != 0) d = v >> 2;
        else d = Double.longBitsToDouble(((long) (v & 0xFFFFFFFC)) << 32);
        return (v & 0x01) != 0 ? d / 100.0 : d;
    }

    private static String error(int code) {
        switch (code) {
            case 0x00: return "#NULL!";
            case 0x07: return "#DIV/0!";
            case 0x0F: return "#VALUE!";
            case 0x17: return "#REF!";
            case 0x1D: return "#NAME?";
            case 0x24: return "#NUM!";
            default: return "#N/A";
        }
    }

    private void text(OdfBuilder.SheetData sd, int r, int c, String s) {
        OdfBuilder.Cell cell = new OdfBuilder.Cell();
        cell.type = OdfBuilder.Cell.STRING;
        cell.str = s;
        sd.put(r, c, cell);
    }

    private void number(OdfBuilder.SheetData sd, int r, int c, int xf, double v) {
        OdfBuilder.Cell cell = new OdfBuilder.Cell();
        boolean date = isDate(xf) && v >= 0 && v < 2958466;
        cell.type = date ? OdfBuilder.Cell.DATE : OdfBuilder.Cell.NUMBER;
        cell.num = date && date1904 ? v + 1462 : v; // 1904 date system starts 1462 days later
        sd.put(r, c, cell);
    }

    private boolean isDate(int xf) {
        if (xf < 0 || xf >= xfFormat.size()) return false;
        int id = xfFormat.get(xf);
        if ((id >= 14 && id <= 22) || (id >= 45 && id <= 47)) return true;
        String code = formats.get(id);
        if (code == null) return false;
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < code.length(); i++) {
            char ch = code.charAt(i);
            if (ch == '"') q = !q;
            else if (!q && ch == '\\') i++;
            else if (!q && ch == '[') {
                int e = code.indexOf(']', i);
                if (e < 0) break;
                i = e;
            } else if (!q) sb.append(ch);
        }
        String p = sb.toString().toLowerCase(Locale.US);
        int semi = p.indexOf(';');
        if (semi >= 0) p = p.substring(0, semi);
        return p.indexOf('y') >= 0 || p.indexOf('d') >= 0 || (p.indexOf('h') >= 0 && p.indexOf(':') >= 0);
    }

    // ------------------------------------------------------------------ strings

    private String byteString(int off, int cch) {
        if (off + cch > w.length) cch = Math.max(0, w.length - off);
        return Codepages.decode(w, off, cch, codepage == 1200 ? 1252 : codepage);
    }

    /** ShortXLUnicodeString: 1-byte length, flags, characters. */
    private String shortUnicode(int off) {
        int cch = w[off] & 0xFF;
        int flags = w[off + 1] & 0xFF;
        return chars(off + 2, cch, (flags & 1) != 0);
    }

    /** XLUnicodeString: 2-byte length, flags, characters (may continue in a CONTINUE record). */
    private String unicode(int off) {
        int cch = Cfb.le16(w, off);
        int flags = w[off + 2] & 0xFF;
        return chars(off + 3, cch, (flags & 1) != 0);
    }

    private String chars(int off, int cch, boolean wide) {
        StringBuilder sb = new StringBuilder(cch);
        for (int i = 0; i < cch; i++) {
            int p = off + (wide ? i * 2 : i);
            if (p + (wide ? 1 : 0) >= w.length) break;
            sb.append(wide ? (char) Cfb.le16(w, p) : (char) (w[p] & 0xFF));
        }
        return sb.toString();
    }

    /** Reads the shared string table, following CONTINUE records. */
    private void readSst(int o) {
        Cursor c = new Cursor(o);
        c.skip(8); // total count, unique count
        int unique = Cfb.le32(w, o + 4 + 4);
        for (int i = 0; i < unique && !c.done(); i++) {
            int cch = c.u16();
            int flags = c.u8();
            boolean wide = (flags & 1) != 0;
            int runs = (flags & 8) != 0 ? c.u16() : 0;
            int ext = (flags & 4) != 0 ? c.u32() : 0;
            StringBuilder sb = new StringBuilder(Math.max(0, Math.min(cch, 32767)));
            for (int k = 0; k < cch && !c.done(); k++) {
                if (c.atSegmentEnd()) {   // characters continue in the next record with new flags
                    c.nextSegment();
                    wide = (c.u8() & 1) != 0;
                }
                sb.append(wide ? (char) c.u16() : (char) c.u8());
            }
            c.skip(runs * 4 + ext);
            sst.add(sb.toString());
        }
    }

    /** Reads across an SST record and its CONTINUE records. */
    private final class Cursor {
        final ArrayList<int[]> segs = new ArrayList<int[]>();
        int seg, pos;

        Cursor(int o) {
            segs.add(new int[]{o + 4, o + 4 + len(o)});
            int n = o + 4 + len(o);
            while (n + 4 <= w.length && type(n) == CONTINUE) {
                segs.add(new int[]{n + 4, n + 4 + len(n)});
                n += 4 + len(n);
            }
            pos = segs.get(0)[0];
        }

        boolean done() {
            return seg >= segs.size() || (seg == segs.size() - 1 && pos >= segs.get(seg)[1]);
        }

        boolean atSegmentEnd() {
            return pos >= segs.get(seg)[1] && seg + 1 < segs.size();
        }

        void nextSegment() {
            seg++;
            pos = segs.get(seg)[0];
        }

        int u8() {
            if (seg < segs.size() && pos >= segs.get(seg)[1] && seg + 1 < segs.size()) nextSegment();
            if (pos >= w.length) return 0;
            return w[pos++] & 0xFF;
        }

        int u16() {
            return u8() | (u8() << 8);
        }

        int u32() {
            return u16() | (u16() << 16);
        }

        void skip(int n) {
            for (int i = 0; i < n && !done(); i++) u8();
        }
    }
}
