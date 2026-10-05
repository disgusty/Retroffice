package com.disgusty.retroffice.text;

import java.util.HashMap;

import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XNode;

/** Minimal WordprocessingML style resolution: bold/italic/underline/strike, size, color, lists. */
public final class DocxStyles {
    public static final class Props {
        /** Which of BOLD/ITALIC/UNDERLINE/STRIKE are specified, and their values. */
        public int set, val;
        public int sz;       // half-points, 0 = unset
        public int color;    // ARGB, 0 = unset
        public int numbered = -1; // -1 unset, 0 no, 1 yes
        public String jc;

        Props copy() {
            Props p = new Props();
            p.set = set;
            p.val = val;
            p.sz = sz;
            p.color = color;
            p.numbered = numbered;
            p.jc = jc;
            return p;
        }

        void overlay(Props o) {
            if (o == null) return;
            set |= o.set;
            val = (val & ~o.set) | (o.val & o.set);
            if (o.sz > 0) sz = o.sz;
            if (o.color != 0) color = o.color;
            if (o.numbered >= 0) numbered = o.numbered;
            if (o.jc != null) jc = o.jc;
        }

        public int fmt() {
            return val & set;
        }
    }

    static final class Def {
        String type, basedOn;
        Props p = new Props();
    }

    private final String w;
    private final HashMap<String, Def> styles = new HashMap<String, Def>();
    private final Props defaults = new Props();
    private String defaultPara, defaultChar;

    public DocxStyles(String wns, XDoc stylesXml) {
        this.w = wns;
        if (stylesXml == null) return;
        XNode root = stylesXml.root;
        XNode dd = root.child(w, "docDefaults");
        if (dd != null) {
            XNode rd = dd.child(w, "rPrDefault");
            if (rd != null) readRPr(rd.child(w, "rPr"), defaults);
            XNode pd = dd.child(w, "pPrDefault");
            if (pd != null) readPPr(pd.child(w, "pPr"), defaults);
        }
        for (XNode s : root.childrenNamed(w, "style")) {
            String id = s.attr(w, "styleId");
            if (id == null) continue;
            Def d = new Def();
            d.type = s.attr(w, "type");
            XNode b = s.child(w, "basedOn");
            if (b != null) d.basedOn = b.attr(w, "val");
            readPPr(s.child(w, "pPr"), d.p);
            readRPr(s.child(w, "rPr"), d.p);
            styles.put(id, d);
            String def = s.attr(w, "default");
            if ("1".equals(def) || "true".equals(def)) {
                if ("paragraph".equals(d.type)) defaultPara = id;
                else if ("character".equals(d.type)) defaultChar = id;
            }
        }
    }

    /** Effective properties of a paragraph style (including document defaults). */
    public Props paragraph(String styleId) {
        Props p = defaults.copy();
        p.overlay(chain(styleId != null ? styleId : defaultPara, 0));
        return p;
    }

    /** Effective run properties before direct formatting: paragraph style + character style. */
    public Props run(Props para, String rStyleId) {
        Props p = para.copy();
        p.overlay(chain(rStyleId != null ? rStyleId : defaultChar, 0));
        return p;
    }

    private Props chain(String id, int depth) {
        if (id == null || depth > 20) return null;
        Def d = styles.get(id);
        if (d == null) return null;
        Props base = chain(d.basedOn, depth + 1);
        Props p = base == null ? new Props() : base.copy();
        p.overlay(d.p);
        return p;
    }

    public int bodySize() {
        Props p = paragraph(null);
        return p.sz > 0 ? p.sz : 22;
    }

    void readRPr(XNode rPr, Props p) {
        if (rPr == null) return;
        onOff(rPr, "b", Spans.BOLD, p);
        onOff(rPr, "i", Spans.ITALIC, p);
        XNode u = rPr.child(w, "u");
        if (u != null) {
            p.set |= Spans.UNDERLINE;
            String v = u.attr(w, "val");
            if (v == null || !v.equals("none")) p.val |= Spans.UNDERLINE;
            else p.val &= ~Spans.UNDERLINE;
        }
        onOff(rPr, "strike", Spans.STRIKE, p);
        if ((p.set & Spans.STRIKE) == 0 || (p.val & Spans.STRIKE) == 0) {
            XNode d = rPr.child(w, "dstrike");
            if (d != null && on(d)) {
                p.set |= Spans.STRIKE;
                p.val |= Spans.STRIKE;
            }
        }
        XNode sz = rPr.child(w, "sz");
        if (sz != null) {
            try {
                p.sz = Integer.parseInt(sz.attr(w, "val"));
            } catch (Exception ignored) {
            }
        }
        XNode c = rPr.child(w, "color");
        if (c != null) p.color = parseColor(c.attr(w, "val"));
    }

    void readPPr(XNode pPr, Props p) {
        if (pPr == null) return;
        XNode num = pPr.child(w, "numPr");
        if (num != null) {
            XNode id = num.child(w, "numId");
            String v = id == null ? null : id.attr(w, "val");
            p.numbered = v != null && !v.equals("0") ? 1 : 0;
        }
        XNode jc = pPr.child(w, "jc");
        if (jc != null) p.jc = jc.attr(w, "val");
    }

    private void onOff(XNode rPr, String name, int bit, Props p) {
        XNode e = rPr.child(w, name);
        if (e == null) return;
        p.set |= bit;
        if (on(e)) p.val |= bit;
        else p.val &= ~bit;
    }

    private boolean on(XNode e) {
        String v = e.attr(w, "val");
        return v == null || !(v.equals("0") || v.equals("false") || v.equals("off"));
    }

    static int parseColor(String v) {
        if (v == null || v.equals("auto") || v.length() != 6) return 0;
        try {
            return 0xFF000000 | Integer.parseInt(v, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
