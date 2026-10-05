package com.disgusty.retroffice.text;

import java.util.HashMap;
import java.util.HashSet;

import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XNode;

/** ODF style lookup (named styles from styles.xml, automatic styles from content.xml). */
public final class OdfStyles {
    public static final class Props {
        public int set, val;
        public float sizePt;      // absolute size, 0 = unset
        public float sizePct;     // relative size, 0 = unset
        public int color;
        public String align;
        /** True when a style (not just the document default) set the size. */
        public boolean sizeExplicit;

        Props copy() {
            Props p = new Props();
            p.set = set;
            p.val = val;
            p.sizePt = sizePt;
            p.sizePct = sizePct;
            p.color = color;
            p.align = align;
            p.sizeExplicit = sizeExplicit;
            return p;
        }

        void overlay(Props o) {
            if (o == null) return;
            set |= o.set;
            val = (val & ~o.set) | (o.val & o.set);
            if (o.sizePt > 0) {
                sizePt = o.sizePt;
                sizeExplicit = true;
            } else if (o.sizePct > 0 && sizePt > 0) {
                sizePt = sizePt * o.sizePct / 100f;
                sizeExplicit = true;
            }
            if (o.sizeExplicit) sizeExplicit = true;
            if (o.color != 0) color = o.color;
            if (o.align != null) align = o.align;
        }

        public int fmt() {
            return val & set;
        }
    }

    static final class Def {
        String family, parent;
        Props p = new Props();
    }

    private final HashMap<String, Def> named = new HashMap<String, Def>();
    private final HashMap<String, Def> auto = new HashMap<String, Def>();
    private final Props paraDefault = new Props();
    /** Every style name in use anywhere (so generated names never collide). */
    public final HashSet<String> allNames = new HashSet<String>();

    public OdfStyles(XDoc stylesXml, XDoc contentXml) {
        if (stylesXml != null) {
            XNode os = stylesXml.root.child(Xml.OFFICE, "styles");
            if (os != null) {
                for (XNode s : os.children) {
                    if (s.is(Xml.STYLE, "style")) put(named, s);
                    else if (s.is(Xml.STYLE, "default-style") && "paragraph".equals(s.attr(Xml.STYLE, "family"))) {
                        readProps(s, paraDefault);
                    }
                }
            }
            XNode as = stylesXml.root.child(Xml.OFFICE, "automatic-styles");
            if (as != null) for (XNode s : as.children) if (s.is(Xml.STYLE, "style")) noteName(s);
        }
        if (contentXml != null) {
            XNode as = contentXml.root.child(Xml.OFFICE, "automatic-styles");
            if (as != null) for (XNode s : as.children) if (s.is(Xml.STYLE, "style")) put(auto, s);
        }
        if (paraDefault.sizePt <= 0) paraDefault.sizePt = 12f;
    }

    private void noteName(XNode s) {
        String n = s.attr(Xml.STYLE, "name");
        if (n != null) allNames.add(n);
    }

    private void put(HashMap<String, Def> map, XNode s) {
        String name = s.attr(Xml.STYLE, "name");
        if (name == null) return;
        allNames.add(name);
        Def d = new Def();
        d.family = s.attr(Xml.STYLE, "family");
        d.parent = s.attr(Xml.STYLE, "parent-style-name");
        readProps(s, d.p);
        map.put(name, d);
    }

    private static void readProps(XNode s, Props p) {
        XNode tp = s.child(Xml.STYLE, "text-properties");
        if (tp != null) {
            String fw = tp.attr(Xml.FO, "font-weight");
            if (fw != null) {
                p.set |= Spans.BOLD;
                if (fw.equals("bold") || (isDigits(fw) && Integer.parseInt(fw) >= 600)) p.val |= Spans.BOLD;
                else p.val &= ~Spans.BOLD;
            }
            String fs = tp.attr(Xml.FO, "font-style");
            if (fs != null) {
                p.set |= Spans.ITALIC;
                if (fs.equals("italic") || fs.equals("oblique")) p.val |= Spans.ITALIC;
                else p.val &= ~Spans.ITALIC;
            }
            String us = tp.attr(Xml.STYLE, "text-underline-style");
            if (us != null) {
                p.set |= Spans.UNDERLINE;
                if (!us.equals("none")) p.val |= Spans.UNDERLINE;
                else p.val &= ~Spans.UNDERLINE;
            }
            String ls = tp.attr(Xml.STYLE, "text-line-through-style");
            if (ls != null) {
                p.set |= Spans.STRIKE;
                if (!ls.equals("none")) p.val |= Spans.STRIKE;
                else p.val &= ~Spans.STRIKE;
            }
            String size = tp.attr(Xml.FO, "font-size");
            if (size != null) {
                if (size.endsWith("%")) p.sizePct = parseF(size.substring(0, size.length() - 1));
                else p.sizePt = Units.toPt(size);
            }
            String col = tp.attr(Xml.FO, "color");
            if (col != null && col.length() == 7 && col.charAt(0) == '#') {
                try {
                    p.color = 0xFF000000 | Integer.parseInt(col.substring(1), 16);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        XNode pp = s.child(Xml.STYLE, "paragraph-properties");
        if (pp != null) {
            String a = pp.attr(Xml.FO, "text-align");
            if (a != null) p.align = a;
        }
    }

    static boolean isDigits(String s) {
        if (s.length() == 0) return false;
        for (int i = 0; i < s.length(); i++) if (!Character.isDigit(s.charAt(i))) return false;
        return true;
    }

    static float parseF(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Effective properties of a paragraph style. */
    public Props paragraph(String name) {
        Props p = paraDefault.copy();
        p.overlay(chain(name, 0, true));
        return p;
    }

    /** Applies a text (span) style on top of {@code base}. */
    public Props text(Props base, String name) {
        Props p = base.copy();
        p.overlay(chain(name, 0, true));
        return p;
    }

    private Props chain(String name, int depth, boolean preferAuto) {
        if (name == null || depth > 20) return null;
        Def d = preferAuto && auto.containsKey(name) ? auto.get(name) : named.get(name);
        if (d == null) return null;
        Props base = chain(d.parent, depth + 1, false);
        Props p = base == null ? new Props() : base.copy();
        p.overlay(d.p);
        return p;
    }

    public float defaultSize() {
        return paraDefault.sizePt;
    }
}
