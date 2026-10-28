package com.disgusty.retroffice.legacy;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Reads Rich Text Format into paragraphs with basic character formatting.
 *
 * RTF is plain text with commands: "{" / "}" open and close a group (formatting set inside a
 * group ends with it), "\b" turns bold on, "\par" ends a paragraph, "\'e0" is one byte in the
 * document's code page, "ၲ" a Unicode character. Groups holding metadata (fonts, colors,
 * pictures, headers...) are skipped.
 */
public final class RtfReader {
    private RtfReader() {
    }

    public static boolean looksLikeRtf(byte[] b) {
        int i = 0;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) i = 3;
        while (i < b.length && (b[i] == ' ' || b[i] == '\r' || b[i] == '\n' || b[i] == '\t')) i++;
        return b.length - i >= 5 && b[i] == '{' && b[i + 1] == '\\' && b[i + 2] == 'r' && b[i + 3] == 't' && b[i + 4] == 'f';
    }

    private static final HashSet<String> SKIP = new HashSet<String>(Arrays.asList("colortbl", "stylesheet", "info",
            "pict", "object", "header", "headerl", "headerr", "headerf", "footer", "footerl", "footerr", "footerf",
            "footnote", "fldinst", "themedata", "colorschememapping", "datastore", "latentstyles", "generator",
            "xmlnstbl", "listtable", "listoverridetable", "rsidtbl", "mmathPr", "pgdsctbl", "filetbl", "revtbl",
            "listtext", "pntext", "pntxtb", "pntxta", "bkmkstart", "bkmkend", "xe", "tc", "atnid", "atnauthor",
            "annotation", "nonshppict", "shp", "shpinst", "sp", "fonttbl_dummy", "userprops", "operator",
            "template", "docvar", "wgrffmtfilter", "passwordhash", "ftnsep", "ftnsepc", "aftnsep", "aftnsepc"));

    static final class State implements Cloneable {
        boolean bold, italic, underline, strike, skip, fontTable;
        int halfPoints, align, font = -1, uc = 1;

        State copy() {
            try {
                return (State) clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static ArrayList<OdfBuilder.Para> read(byte[] data) {
        return new Parser(data).run();
    }

    static final class Parser {
        final byte[] d;
        int pos;
        int defaultCp = 1252, defaultFont = -1;
        final HashMap<Integer, Integer> fontCp = new HashMap<Integer, Integer>();
        final ArrayList<OdfBuilder.Para> paras = new ArrayList<OdfBuilder.Para>();
        OdfBuilder.Para para = new OdfBuilder.Para();
        final StringBuilder text = new StringBuilder();
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        State st = new State();
        State runState = st.copy();
        final ArrayList<State> stack = new ArrayList<State>();
        int skipUnicode;
        // Font table parsing
        int curFontNo = -1;

        Parser(byte[] d) {
            this.d = d;
        }

        ArrayList<OdfBuilder.Para> run() {
            int n = d.length;
            while (pos < n) {
                int c = d[pos] & 0xFF;
                if (c == '{') {
                    flushBytes();
                    stack.add(st);
                    st = st.copy();
                    pos++;
                    // "{\*\dest ...}" marks a destination that may be ignored if unknown.
                    if (pos + 1 < n && d[pos] == '\\' && d[pos + 1] == '*') {
                        pos += 2;
                        String w = peekWord();
                        if (!w.equals("fldrslt")) st.skip = true;
                    }
                } else if (c == '}') {
                    flushBytes();
                    if (st.fontTable && curFontNo >= 0) curFontNo = -1;
                    if (!stack.isEmpty()) st = stack.remove(stack.size() - 1);
                    pos++;
                } else if (c == '\\') {
                    pos++;
                    control();
                } else if (c == '\r' || c == '\n') {
                    pos++;
                } else {
                    pos++;
                    if (skipUnicode > 0) {
                        skipUnicode--;
                        continue;
                    }
                    if (st.fontTable) continue;
                    if (st.skip) continue;
                    bytes.write(c);
                }
            }
            flushBytes();
            endPara(false);
            return paras;
        }

        private String peekWord() {
            int i = pos;
            if (i < d.length && d[i] == '\\') i++;
            int s = i;
            while (i < d.length && Character.isLetter((char) d[i])) i++;
            return new String(d, s, i - s);
        }

        private void control() {
            int n = d.length;
            if (pos >= n) return;
            char c = (char) (d[pos] & 0xFF);
            if (!isLetter(c)) {
                pos++;
                switch (c) {
                    case '\'': {
                        if (pos + 2 > n) return;
                        int v = hex(d[pos]) * 16 + hex(d[pos + 1]);
                        pos += 2;
                        if (skipUnicode > 0) {
                            skipUnicode--;
                            return;
                        }
                        if (!st.skip && !st.fontTable && v >= 0) bytes.write(v);
                        return;
                    }
                    case '\\': case '{': case '}':
                        literal(String.valueOf(c));
                        return;
                    case '~':
                        literal(" ");
                        return;
                    case '_':
                        literal("‑");
                        return;
                    case '-':
                        return; // optional hyphen
                    case '\r': case '\n':
                        flushBytes();
                        endPara(true);
                        return;
                    case '*':
                        st.skip = true;
                        return;
                    default:
                        return;
                }
            }
            int s = pos;
            while (pos < n && isLetter((char) d[pos])) pos++;
            String word = new String(d, s, pos - s);
            boolean hasParam = false;
            int param = 0;
            boolean neg = false;
            if (pos < n && d[pos] == '-') {
                neg = true;
                pos++;
            }
            int ps = pos;
            while (pos < n && d[pos] >= '0' && d[pos] <= '9' && pos - ps < 10) pos++;
            if (pos > ps) {
                hasParam = true;
                param = Integer.parseInt(new String(d, ps, pos - ps));
                if (neg) param = -param;
            }
            if (pos < n && d[pos] == ' ') pos++;
            word(word, hasParam, param);
        }

        private void word(String w, boolean has, int p) {
            if (w.equals("u")) {
                int cp = p < 0 ? p + 65536 : p;
                if (!st.skip && !st.fontTable) {
                    flushBytes();
                    literal(String.valueOf((char) cp));
                }
                skipUnicode = st.uc;
                return;
            }
            if (st.fontTable) {
                if (w.equals("f") && has) curFontNo = p;
                else if (w.equals("fcharset") && has && curFontNo >= 0) {
                    int cp = Codepages.fromCharset(p);
                    if (cp > 0) fontCp.put(curFontNo, cp);
                } else if (w.equals("cpg") && has && curFontNo >= 0 && p > 0) {
                    fontCp.put(curFontNo, p);
                }
                return;
            }
            if (w.equals("fonttbl")) {
                st.fontTable = true;
                return;
            }
            if (SKIP.contains(w)) {
                st.skip = true;
                return;
            }
            if (w.equals("ansicpg") && has) {
                defaultCp = p;
                return;
            }
            if (w.equals("deff") && has) {
                defaultFont = p;
                return;
            }
            if (w.equals("uc") && has) {
                st.uc = Math.max(0, p);
                return;
            }
            if (st.skip) return;
            flushBytes();
            if (w.equals("par") || w.equals("sect") || w.equals("page") || w.equals("row") || w.equals("line")) {
                endPara(true);
            } else if (w.equals("pard")) {
                st.align = OdfBuilder.LEFT;
            } else if (w.equals("plain")) {
                st.bold = st.italic = st.underline = st.strike = false;
                st.halfPoints = 0;
            } else if (w.equals("b")) {
                st.bold = !has || p != 0;
            } else if (w.equals("i")) {
                st.italic = !has || p != 0;
            } else if (w.equals("ul") || w.equals("uld") || w.equals("uldb") || w.equals("ulw") || w.equals("uldash")
                    || w.equals("ulth") || w.equals("ulwave")) {
                st.underline = !has || p != 0;
            } else if (w.equals("ulnone")) {
                st.underline = false;
            } else if (w.equals("strike") || w.equals("striked")) {
                st.strike = !has || p != 0;
            } else if (w.equals("fs") && has) {
                st.halfPoints = p == 24 ? 0 : p;
            } else if (w.equals("f") && has) {
                st.font = p;
            } else if (w.equals("qc")) {
                st.align = OdfBuilder.CENTER;
            } else if (w.equals("qr")) {
                st.align = OdfBuilder.RIGHT;
            } else if (w.equals("qj")) {
                st.align = OdfBuilder.JUSTIFY;
            } else if (w.equals("ql")) {
                st.align = OdfBuilder.LEFT;
            } else if (w.equals("tab") || w.equals("cell")) {
                literal("\t");
            } else if (w.equals("emdash")) {
                literal("—");
            } else if (w.equals("endash")) {
                literal("–");
            } else if (w.equals("bullet")) {
                literal("•");
            } else if (w.equals("lquote")) {
                literal("‘");
            } else if (w.equals("rquote")) {
                literal("’");
            } else if (w.equals("ldblquote")) {
                literal("“");
            } else if (w.equals("rdblquote")) {
                literal("”");
            } else if (w.equals("emspace") || w.equals("enspace") || w.equals("qmspace")) {
                literal(" ");
            }
        }

        private void literal(String s) {
            if (st.skip || st.fontTable) return;
            flushBytes();
            append(s);
        }

        /** Decodes buffered \'hh / raw bytes with the current font's code page. */
        private void flushBytes() {
            if (bytes.size() == 0) return;
            byte[] b = bytes.toByteArray();
            bytes.reset();
            int font = st.font >= 0 ? st.font : defaultFont;
            Integer cp = fontCp.get(font);
            append(Codepages.decode(b, 0, b.length, cp != null ? cp : defaultCp));
        }

        private void append(String s) {
            if (s.length() == 0) return;
            if (!sameFormat(runState, st) && text.length() > 0) closeRun();
            if (text.length() == 0) runState = st.copy();
            text.append(s);
        }

        private static boolean sameFormat(State a, State b) {
            return a.bold == b.bold && a.italic == b.italic && a.underline == b.underline && a.strike == b.strike
                    && a.halfPoints == b.halfPoints;
        }

        private void closeRun() {
            if (text.length() == 0) return;
            OdfBuilder.Run r = new OdfBuilder.Run(text.toString());
            r.bold = runState.bold;
            r.italic = runState.italic;
            r.underline = runState.underline;
            r.strike = runState.strike;
            r.halfPoints = runState.halfPoints;
            para.runs.add(r);
            text.setLength(0);
        }

        private void endPara(boolean force) {
            closeRun();
            if (force || !para.runs.isEmpty()) {
                para.align = st.align;
                paras.add(para);
            }
            para = new OdfBuilder.Para();
        }

        private static boolean isLetter(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private static int hex(byte b) {
            int c = b & 0xFF;
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'a' && c <= 'f') return c - 'a' + 10;
            if (c >= 'A' && c <= 'F') return c - 'A' + 10;
            return -100;
        }
    }
}
