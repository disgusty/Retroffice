package com.disgusty.retroffice.legacy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Reads PowerPoint 97–2003 (.ppt): the title and text of every slide.
 *
 * PowerPoint appends to the file on every save, so old copies of slides may still be inside.
 * The current version is found the official way: "Current User" points to the last edit, each
 * edit has a persist directory (object id -> offset), and the document lists its slides by id.
 * Records share one header format (version/instance, type, length); containers hold records.
 */
public final class PptReader {
    private static final int DOCUMENT = 0x03E8, SLIDE = 0x03EE, SLWT = 0x0FF0, SLIDE_PERSIST = 0x03F3,
            TEXT_HEADER = 0x0F9F, TEXT_CHARS = 0x0FA0, TEXT_BYTES = 0x0FA8, USER_EDIT = 0x0FF5,
            PERSIST_DIR = 0x1772;
    private static final int TITLE = 0, CENTER_TITLE = 6;

    private final byte[] d;

    private PptReader(byte[] d) {
        this.d = d;
    }

    public static ArrayList<OdfBuilder.SlideData> read(Cfb cfb) throws IOException {
        byte[] doc = cfb.stream("PowerPoint Document");
        if (doc == null) throw new IOException("Not a PowerPoint presentation");
        byte[] cu = cfb.stream("Current User");
        PptReader r = new PptReader(doc);
        if (cu != null && cu.length >= 20) {
            int token = Cfb.le32(cu, 12);
            if (token == 0xF3D1C4DF) throw new LegacyFormatException("Encrypted presentation", true);
            try {
                ArrayList<OdfBuilder.SlideData> s = r.viaPersist(Cfb.le32(cu, 16));
                if (s != null) return s;
            } catch (RuntimeException ignored) {
                // damaged edit chain: fall back to scanning below
            }
        }
        return r.scan();
    }

    private int verInst(int o) {
        return Cfb.le16(d, o);
    }

    private int type(int o) {
        return Cfb.le16(d, o + 2);
    }

    private int len(int o) {
        return Cfb.le32(d, o + 4);
    }

    private boolean valid(int o) {
        return o >= 0 && o + 8 <= d.length && len(o) >= 0 && o + 8L + len(o) <= d.length;
    }

    private boolean container(int o) {
        return (verInst(o) & 0x0F) == 0x0F;
    }

    private ArrayList<OdfBuilder.SlideData> viaPersist(int editOffset) {
        HashMap<Integer, Integer> persist = new HashMap<Integer, Integer>();
        int docRef = -1, o = editOffset, guard = 0;
        while (valid(o) && type(o) == USER_EDIT && guard++ < 1000) {
            int data = o + 8;
            if (docRef < 0) docRef = Cfb.le32(d, data + 16);
            int pd = Cfb.le32(d, data + 12);
            if (valid(pd) && type(pd) == PERSIST_DIR) {
                int p = pd + 8, end = pd + 8 + len(pd);
                while (p + 4 <= end) {
                    int head = Cfb.le32(d, p);
                    int id = head & 0xFFFFF, count = (head >>> 20) & 0xFFF;
                    p += 4;
                    for (int i = 0; i < count && p + 4 <= end; i++, p += 4) {
                        if (!persist.containsKey(id + i)) persist.put(id + i, Cfb.le32(d, p)); // newest edit wins
                    }
                }
            }
            int prev = Cfb.le32(d, data + 8);
            if (prev <= 0 || prev == o) break;
            o = prev;
        }
        Integer docOff = persist.get(docRef);
        if (docOff == null || !valid(docOff) || type(docOff) != DOCUMENT) return null;

        // Slide list (instance 0 = slides): ids of slides plus their placeholder texts.
        LinkedHashMap<Integer, OdfBuilder.SlideData> slides = new LinkedHashMap<Integer, OdfBuilder.SlideData>();
        int c = docOff + 8, end = docOff + 8 + len(docOff);
        while (c + 8 <= end && valid(c)) {
            if (type(c) == SLWT && (verInst(c) >> 4) == 0) readSlideList(c, slides);
            c += 8 + len(c);
        }
        for (java.util.Map.Entry<Integer, OdfBuilder.SlideData> e : slides.entrySet()) {
            Integer so = persist.get(e.getKey());
            if (so != null && valid(so) && type(so) == SLIDE) collectTexts(so, e.getValue(), 0);
        }
        return new ArrayList<OdfBuilder.SlideData>(slides.values());
    }

    /** Placeholder texts stored in a SlideListWithText container, keyed by slide persist id. */
    private void readSlideList(int c, LinkedHashMap<Integer, OdfBuilder.SlideData> slides) {
        OdfBuilder.SlideData cur = null;
        int textType = 4;
        int p = c + 8, end = c + 8 + len(c);
        while (p + 8 <= end && valid(p)) {
            int t = type(p);
            if (t == SLIDE_PERSIST) {
                cur = new OdfBuilder.SlideData();
                slides.put(Cfb.le32(d, p + 8), cur);
            } else if (t == TEXT_HEADER && len(p) >= 4) {
                textType = Cfb.le32(d, p + 8);
            } else if ((t == TEXT_CHARS || t == TEXT_BYTES) && cur != null) {
                add(cur, textType, text(p));
            }
            p += 8 + len(p);
        }
    }

    /** Text boxes drawn on the slide itself (inside its drawing records). */
    private void collectTexts(int c, OdfBuilder.SlideData s, int depth) {
        if (depth > 12) return;
        int textType = 4;
        int p = c + 8, end = c + 8 + len(c);
        while (p + 8 <= end && valid(p)) {
            int t = type(p);
            if (t == TEXT_HEADER && len(p) >= 4) textType = Cfb.le32(d, p + 8);
            else if (t == TEXT_CHARS || t == TEXT_BYTES) add(s, textType, text(p));
            else if (container(p)) collectTexts(p, s, depth + 1);
            p += 8 + len(p);
        }
    }

    private String text(int p) {
        int n = len(p), o = p + 8;
        StringBuilder sb = new StringBuilder();
        if (type(p) == TEXT_CHARS) for (int i = 0; i + 1 < n; i += 2) sb.append((char) Cfb.le16(d, o + i));
        else for (int i = 0; i < n; i++) sb.append((char) (d[o + i] & 0xFF)); // low bytes of UTF-16
        return sb.toString();
    }

    private static void add(OdfBuilder.SlideData s, int textType, String text) {
        String[] lines = text.replace('\u000B', '\r').split("\r", -1);
        if ((textType == TITLE || textType == CENTER_TITLE) && s.title.length() == 0) {
            StringBuilder t = new StringBuilder();
            for (String l : lines) {
                if (t.length() > 0) t.append(' ');
                t.append(l);
            }
            s.title = t.toString().trim();
            return;
        }
        for (String l : lines) if (l.trim().length() > 0) s.body.add(l);
    }

    /** Fallback: the first slide list found anywhere in the stream. */
    private ArrayList<OdfBuilder.SlideData> scan() {
        LinkedHashMap<Integer, OdfBuilder.SlideData> slides = new LinkedHashMap<Integer, OdfBuilder.SlideData>();
        scanFor(0, d.length, slides, 0);
        return new ArrayList<OdfBuilder.SlideData>(slides.values());
    }

    private void scanFor(int from, int to, LinkedHashMap<Integer, OdfBuilder.SlideData> slides, int depth) {
        int p = from;
        while (p + 8 <= to && valid(p) && slides.isEmpty()) {
            if (type(p) == SLWT && (verInst(p) >> 4) == 0) readSlideList(p, slides);
            else if (container(p) && depth < 6) scanFor(p + 8, p + 8 + len(p), slides, depth + 1);
            p += 8 + len(p);
        }
    }
}
