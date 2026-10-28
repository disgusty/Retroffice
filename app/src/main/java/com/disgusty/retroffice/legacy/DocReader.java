package com.disgusty.retroffice.legacy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Reads Word 97–2003 (.doc) text with bold/italic/underline/strike, font size and paragraph
 * alignment. Word 6/95 files give plain text.
 *
 * Layout ([MS-DOC]): the FIB header points into a "table" stream holding the piece table
 * (where each stretch of text is stored, 8- or 16-bit) and the bin tables of the CHPX/PAPX
 * pages (512-byte pages with character and paragraph properties as "sprm" codes), plus the
 * style sheet (headings usually get their look from a style, not from direct formatting).
 */
public final class DocReader {
    private DocReader() {
    }

    static final int B = 1, I = 2, U = 4, S = 8;

    /** Properties from a sprm list: which flags are set and how. */
    static final class Props {
        int set, val;      // flags given explicitly, and their on/off values
        int asStyle;       // flags given as "same as the style" (operand 0x80)
        int opposite;      // flags given as "opposite of the style" (operand 0x81)
        int hps;           // font size in half-points, 0 = unset
        int jc = -1;       // paragraph alignment, -1 = unset
        boolean rowEnd;    // paragraph mark ends a table row

        Props copy() {
            Props p = new Props();
            p.set = set;
            p.val = val;
            p.asStyle = asStyle;
            p.opposite = opposite;
            p.hps = hps;
            p.jc = jc;
            p.rowEnd = rowEnd;
            return p;
        }

        /** Effective on/off flags. */
        int flags() {
            return val & set;
        }
    }

    public static ArrayList<OdfBuilder.Para> read(Cfb cfb) throws IOException {
        byte[] wd = cfb.stream("WordDocument");
        if (wd == null || wd.length < 0x200) throw new IOException("Not a Word document");
        int wIdent = Cfb.le16(wd, 0);
        if (wIdent != 0xA5EC && wIdent != 0xA5DC) throw new IOException("Not a Word document");
        int nFib = Cfb.le16(wd, 2);
        int flags = Cfb.le16(wd, 0x0A);
        if ((flags & 0x0100) != 0 || (flags & 0x8000) != 0) throw new LegacyFormatException("Encrypted document", true);
        if (nFib < 0x00C0) return word95(wd);

        // FIB: base (32 bytes), then counted arrays of shorts, ints and (fc, lcb) pairs.
        int csw = Cfb.le16(wd, 32);
        int lwPos = 34 + csw * 2;
        int cslw = Cfb.le16(wd, lwPos);
        int lw = lwPos + 2;
        int ccpText = Cfb.le32(wd, lw + 3 * 4);
        int blob = lw + cslw * 4 + 2;
        byte[] table = cfb.stream((flags & 0x0200) != 0 ? "1Table" : "0Table");
        if (table == null) throw new IOException("Damaged Word document: no table stream");

        // ---- piece table (Clx): CPs of each piece and where its characters are stored
        int fcClx = Cfb.le32(wd, blob + 33 * 8), lcbClx = Cfb.le32(wd, blob + 33 * 8 + 4);
        int[] cps = null, fcs = null;
        boolean[] compressed = null;
        int p = fcClx, end = Math.min(table.length, fcClx + lcbClx);
        while (p >= 0 && p < end) {
            int t = table[p] & 0xFF;
            if (t == 1) {                     // Prc: property modifiers, not needed
                p += 3 + Cfb.le16(table, p + 1);
            } else if (t == 2) {              // Pcdt: the piece table itself
                int lcb = Cfb.le32(table, p + 1);
                int plc = p + 5;
                int n = (lcb - 4) / 12;
                if (n <= 0 || plc + lcb > table.length) throw new IOException("Damaged piece table");
                cps = new int[n + 1];
                fcs = new int[n];
                compressed = new boolean[n];
                for (int i = 0; i <= n; i++) cps[i] = Cfb.le32(table, plc + i * 4);
                for (int i = 0; i < n; i++) {
                    int fc = Cfb.le32(table, plc + (n + 1) * 4 + i * 8 + 2);
                    compressed[i] = (fc & 0x40000000) != 0;
                    fcs[i] = compressed[i] ? (fc & 0x3FFFFFFF) / 2 : fc;
                }
                break;
            } else {
                break;
            }
        }
        if (cps == null) throw new IOException("Damaged Word document: no piece table");

        // ---- main text, with the stream offset of every character (formatting is keyed by offset)
        int total = Math.max(0, ccpText);
        char[] text = new char[total];
        int[] off = new int[total];
        int k = 0;
        for (int i = 0; i < fcs.length && k < total; i++) {
            int from = Math.max(cps[i], 0), to = Math.min(cps[i + 1], total);
            for (int cp = from; cp < to && k < total; cp++) {
                int idx = cp - cps[i];
                int o = compressed[i] ? fcs[i] + idx : fcs[i] + idx * 2;
                if (o < 0 || o + (compressed[i] ? 0 : 1) >= wd.length) break;
                text[k] = compressed[i] ? Codepages.decode(wd, o, 1, 1252).charAt(0) : (char) Cfb.le16(wd, o);
                off[k] = o;
                k++;
            }
        }
        total = k;

        HashMap<Integer, Props> styles = readStyles(table, Cfb.le32(wd, blob + 8), Cfb.le32(wd, blob + 8 + 4));
        Runs chpx = readFkps(wd, table, Cfb.le32(wd, blob + 12 * 8), Cfb.le32(wd, blob + 12 * 8 + 4), true);
        Runs papx = readFkps(wd, table, Cfb.le32(wd, blob + 13 * 8), Cfb.le32(wd, blob + 13 * 8 + 4), false);

        // ---- paragraphs
        ArrayList<OdfBuilder.Para> paras = new ArrayList<OdfBuilder.Para>();
        ArrayList<Integer> fieldStack = new ArrayList<Integer>(); // per open field: 0 instructions, 1 result
        StringBuilder chars = new StringBuilder();
        ArrayList<Props> charProps = new ArrayList<Props>();
        HashMap<Long, Props> merged = new HashMap<Long, Props>();
        for (int i = 0; i < total; i++) {
            char c = text[i];
            int pi = papx.find(off[i]);
            Props para = pi >= 0 ? papx.props.get(pi) : null;
            Props style = styleOf(styles, pi >= 0 ? papx.istd.get(pi) : 0);
            boolean paraEnd = c == 0x0D || c == 0x0B || c == 0x0C;
            if (c == 0x07) {                  // table cell mark; a row end mark ends the row
                paraEnd = para != null && para.rowEnd;
                if (!paraEnd) c = '\t';
            }
            if (c == 0x13) {                  // field: code, separator, result, end
                fieldStack.add(0);
                continue;
            }
            if (c == 0x14) {
                if (!fieldStack.isEmpty()) fieldStack.set(fieldStack.size() - 1, 1);
                continue;
            }
            if (c == 0x15) {
                if (!fieldStack.isEmpty()) fieldStack.remove(fieldStack.size() - 1);
                continue;
            }
            if (paraEnd) {
                // The last cell's mark became a tab just before the row end: drop it.
                if (c == 0x07 && chars.length() > 0 && chars.charAt(chars.length() - 1) == '\t') {
                    chars.setLength(chars.length() - 1);
                    charProps.remove(charProps.size() - 1);
                }
                int jc = para != null && para.jc >= 0 ? para.jc : style.jc;
                paras.add(makePara(chars, charProps, jc));
                chars.setLength(0);
                charProps.clear();
                continue;
            }
            boolean inCode = false;
            for (int f : fieldStack) if (f == 0) inCode = true;
            if (inCode) continue;
            if (c == 0x1E) c = '-';                              // non-breaking hyphen
            if (c < 0x20 && c != '\t') continue;                 // pictures, footnote refs, soft hyphens...
            int ci = chpx.find(off[i]);
            Props eff = style;
            if (ci >= 0) {
                // Same style + same run => same result: reuse it instead of a new object per character.
                long key = ((long) (pi >= 0 ? papx.istd.get(pi) : 0) << 32) | ci;
                eff = merged.get(key);
                if (eff == null) {
                    eff = overlay(style, chpx.props.get(ci));
                    merged.put(key, eff);
                }
            }
            charProps.add(eff);
            chars.append(c);
        }
        if (chars.length() > 0) paras.add(makePara(chars, charProps, -1));
        return paras;
    }

    private static OdfBuilder.Para makePara(StringBuilder chars, ArrayList<Props> props, int jc) {
        OdfBuilder.Para para = new OdfBuilder.Para();
        para.align = jc == 1 ? OdfBuilder.CENTER : jc == 2 ? OdfBuilder.RIGHT : (jc == 3 || jc == 4) ? OdfBuilder.JUSTIFY : OdfBuilder.LEFT;
        int start = 0;
        for (int i = 1; i <= chars.length(); i++) {
            if (i == chars.length() || !same(props.get(i), props.get(start))) {
                para.runs.add(run(chars.substring(start, i), props.get(start)));
                start = i;
            }
        }
        return para;
    }

    private static boolean same(Props a, Props b) {
        return a == b || (a.flags() == b.flags() && a.hps == b.hps);
    }

    private static OdfBuilder.Run run(String s, Props p) {
        OdfBuilder.Run r = new OdfBuilder.Run(s);
        int v = p.flags();
        r.bold = (v & B) != 0;
        r.italic = (v & I) != 0;
        r.underline = (v & U) != 0;
        r.strike = (v & S) != 0;
        r.halfPoints = p.hps == 24 ? 0 : p.hps; // 12 pt is the default size
        return r;
    }

    /** Direct properties on top of a style, resolving "same as style" / "opposite of style". */
    private static Props overlay(Props base, Props direct) {
        Props r = base.copy();
        for (int bit : new int[]{B, I, U, S}) {
            boolean styleOn = (base.flags() & bit) != 0;
            boolean on;
            if ((direct.opposite & bit) != 0) on = !styleOn;
            else if ((direct.asStyle & bit) != 0) on = styleOn;
            else if ((direct.set & bit) != 0) on = (direct.val & bit) != 0;
            else continue;
            r.set |= bit;
            r.val = on ? r.val | bit : r.val & ~bit;
        }
        if (direct.hps > 0) r.hps = direct.hps;
        if (direct.jc >= 0) r.jc = direct.jc;
        r.asStyle = r.opposite = 0;
        r.rowEnd = false;
        return r;
    }

    private static Props styleOf(HashMap<Integer, Props> styles, int istd) {
        Props p = styles.get(istd);
        return p != null ? p : EMPTY;
    }

    private static final Props EMPTY = new Props();

    /** Property runs of a bin table, sorted by stream offset. */
    static final class Runs {
        final ArrayList<int[]> ranges = new ArrayList<int[]>(); // {fcStart, fcEnd}
        final ArrayList<Props> props = new ArrayList<Props>();
        final ArrayList<Integer> istd = new ArrayList<Integer>();

        int find(int fc) {
            int lo = 0, hi = ranges.size() - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int[] r = ranges.get(mid);
                if (fc < r[0]) hi = mid - 1;
                else if (fc >= r[1]) lo = mid + 1;
                else return mid;
            }
            return -1;
        }
    }

    /** Reads a bin table and its FKP pages (CHPX when {@code chp}, PAPX otherwise). */
    private static Runs readFkps(byte[] wd, byte[] table, int fcPlc, int lcbPlc, boolean chp) {
        Runs out = new Runs();
        if (lcbPlc < 8 || fcPlc < 0 || fcPlc + lcbPlc > table.length) return out;
        int n = (lcbPlc - 4) / 8;
        for (int i = 0; i < n; i++) {
            int page = (Cfb.le32(table, fcPlc + (n + 1) * 4 + i * 4) & 0x3FFFFF) * 512;
            if (page < 0 || page + 512 > wd.length) continue;
            int crun = wd[page + 511] & 0xFF;
            if ((crun + 1) * 4 > 511) continue;
            for (int r = 0; r < crun; r++) {
                int fcStart = Cfb.le32(wd, page + r * 4), fcEnd = Cfb.le32(wd, page + (r + 1) * 4);
                Props pr = new Props();
                int istd = 0;
                if (chp) {
                    int b = (wd[page + (crun + 1) * 4 + r] & 0xFF) * 2;
                    if (b > 0 && b < 511) sprms(wd, page + b + 1, Math.min(wd[page + b] & 0xFF, 511 - b), pr);
                } else {
                    int bx = page + (crun + 1) * 4 + r * 13;
                    if (bx >= page + 511) break;
                    int b = (wd[bx] & 0xFF) * 2;
                    if (b > 0 && b < 510) {
                        int cb = wd[page + b] & 0xFF;
                        int len = cb == 0 ? (wd[page + b + 1] & 0xFF) * 2 : cb * 2 - 1;
                        int st = cb == 0 ? page + b + 2 : page + b + 1;
                        if (len >= 2 && st + len <= page + 512) {
                            istd = Cfb.le16(wd, st);
                            sprms(wd, st + 2, len - 2, pr);
                        }
                    }
                }
                out.ranges.add(new int[]{fcStart, fcEnd});
                out.props.add(pr);
                out.istd.add(istd);
            }
        }
        sortIfNeeded(out);
        return out;
    }

    private static void sortIfNeeded(Runs r) {
        boolean sorted = true;
        for (int i = 1; i < r.ranges.size() && sorted; i++) if (r.ranges.get(i)[0] < r.ranges.get(i - 1)[0]) sorted = false;
        if (sorted) return;
        Integer[] order = new Integer[r.ranges.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        final ArrayList<int[]> r0 = new ArrayList<int[]>(r.ranges);
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int x = r0.get(a)[0], y = r0.get(b)[0];
                return x < y ? -1 : (x == y ? 0 : 1);
            }
        });
        ArrayList<Props> p0 = new ArrayList<Props>(r.props);
        ArrayList<Integer> i0 = new ArrayList<Integer>(r.istd);
        for (int i = 0; i < order.length; i++) {
            r.ranges.set(i, r0.get(order[i]));
            r.props.set(i, p0.get(order[i]));
            r.istd.set(i, i0.get(order[i]));
        }
    }

    /** Applies the sprms we care about; the operand size comes from the sprm code itself. */
    static void sprms(byte[] g, int off, int len, Props p) {
        int i = off, end = Math.min(g.length, off + len);
        while (i + 2 <= end) {
            int sprm = Cfb.le16(g, i);
            i += 2;
            int size;
            switch (sprm >>> 13) {
                case 0:
                case 1: size = 1; break;
                case 2:
                case 4:
                case 5: size = 2; break;
                case 3: size = 4; break;
                case 7: size = 3; break;
                default:
                    if (sprm == 0xD608 || sprm == 0xD606) return; // table definitions: nothing we need after
                    if (i >= end) return;
                    size = (g[i] & 0xFF) + 1;
            }
            if (i + size > end) return;
            int v = g[i] & 0xFF;
            switch (sprm) {
                case 0x0835: toggle(p, B, v); break;                  // bold
                case 0x0836: toggle(p, I, v); break;                  // italic
                case 0x0837:                                          // strike
                case 0x2A53: toggle(p, S, v); break;                  // double strike
                case 0x2A3E: toggle(p, U, v == 0 ? 0 : 1); break;     // underline kind
                case 0x4A43: p.hps = Cfb.le16(g, i); break;           // font size
                case 0x2403:
                case 0x2461: p.jc = v; break;                         // alignment
                case 0x2417: p.rowEnd = v != 0; break;                // table row end
                default: break;
            }
            i += size;
        }
    }

    private static void toggle(Props p, int bit, int v) {
        if (v == 0x81) p.opposite |= bit;
        else if (v == 0x80) p.asStyle |= bit;
        else {
            p.set |= bit;
            p.val = v != 0 ? p.val | bit : p.val & ~bit;
        }
    }

    /** Style sheet: character properties and alignment of each style, with "based on" applied. */
    private static HashMap<Integer, Props> readStyles(byte[] table, int fc, int lcb) {
        HashMap<Integer, Props> out = new HashMap<Integer, Props>();
        if (lcb < 4 || fc < 0 || fc + lcb > table.length) return out;
        int cbStshi = Cfb.le16(table, fc);
        int stshi = fc + 2;
        int cstd = Cfb.le16(table, stshi);
        int cbBase = Cfb.le16(table, stshi + 2);
        int p = stshi + cbStshi;
        int end = fc + lcb;
        HashMap<Integer, Props> own = new HashMap<Integer, Props>();
        HashMap<Integer, Integer> baseOf = new HashMap<Integer, Integer>();
        for (int istd = 0; istd < cstd && p + 2 <= end; istd++) {
            int cbStd = Cfb.le16(table, p);
            int std = p + 2;
            p = std + cbStd;
            if (cbStd == 0 || p > end) continue;
            int stk = Cfb.le16(table, std + 2) & 0x0F;
            int base = Cfb.le16(table, std + 2) >>> 4;
            int cupx = Cfb.le16(table, std + 4) & 0x0F;
            int q = std + cbBase;
            if (q + 2 > p) continue;
            q += 2 + Cfb.le16(table, q) * 2 + 2;                     // style name (length, chars, terminator)
            if ((q - std) % 2 != 0) q++;
            Props pr = new Props();
            for (int u = 0; u < cupx && q + 2 <= p; u++) {
                int cbUpx = Cfb.le16(table, q);
                int data = q + 2;
                if (data + cbUpx > p) break;
                if (stk == 1 && u == 0) {
                    if (cbUpx >= 2) sprms(table, data + 2, cbUpx - 2, pr); // paragraph props: istd + sprms
                } else if ((stk == 1 && u == 1) || (stk == 2 && u == 0)) {
                    sprms(table, data, cbUpx, pr);                           // character props
                }
                q = data + cbUpx;
                if ((q - std) % 2 != 0) q++;
            }
            own.put(istd, pr);
            if (base != 0x0FFF) baseOf.put(istd, base);
        }
        for (Integer istd : own.keySet()) out.put(istd, resolve(istd, own, baseOf, 0));
        return out;
    }

    private static Props resolve(int istd, HashMap<Integer, Props> own, HashMap<Integer, Integer> baseOf, int depth) {
        Props self = own.get(istd);
        if (self == null) return EMPTY;
        Integer b = baseOf.get(istd);
        Props base = b == null || depth > 20 || b == istd ? EMPTY : resolve(b, own, baseOf, depth + 1);
        return overlay(base, self);
    }

    /** Word 6.0/95: plain text between fcMin and fcMac (no formatting). */
    private static ArrayList<OdfBuilder.Para> word95(byte[] wd) {
        int fcMin = Cfb.le32(wd, 0x18), fcMac = Cfb.le32(wd, 0x1C);
        ArrayList<OdfBuilder.Para> paras = new ArrayList<OdfBuilder.Para>();
        if (fcMin < 0 || fcMac > wd.length || fcMac <= fcMin) return paras;
        int high = 0, cyr = 0;
        for (int i = fcMin; i < fcMac; i++) {
            int v = wd[i] & 0xFF;
            if (v >= 0x80) high++;
            if (v >= 0xC0) cyr++;
        }
        String s = Codepages.decode(wd, fcMin, fcMac - fcMin, high > 0 && cyr * 2 >= high ? 1251 : 1252);
        StringBuilder sb = new StringBuilder();
        boolean code = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 0x13) { code = true; continue; }
            if (c == 0x14 || c == 0x15) { code = false; continue; }
            if (code) continue;
            if (c == 0x0D || c == 0x0B || c == 0x0C || c == 0x07) {
                OdfBuilder.Para p = new OdfBuilder.Para();
                p.runs.add(new OdfBuilder.Run(sb.toString()));
                paras.add(p);
                sb.setLength(0);
            } else if (c >= 0x20 || c == '\t') {
                sb.append(c);
            }
        }
        if (sb.length() > 0) {
            OdfBuilder.Para p = new OdfBuilder.Para();
            p.runs.add(new OdfBuilder.Run(sb.toString()));
            paras.add(p);
        }
        return paras;
    }
}
