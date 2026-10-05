package com.disgusty.retroffice.text;

import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XNode;
import com.disgusty.retroffice.xml.XmlException;
import com.disgusty.retroffice.xml.XmlParser;

/** DrawingML paragraphs (a:p) inside PowerPoint text bodies. */
public final class DmlDialect extends TextDialect {
    private final String a;
    private final float baseSizePt;

    public DmlDialect(String src, String aNs, float baseSizePt) {
        super(src);
        this.a = aNs;
        this.baseSizePt = baseSizePt;
    }

    static final class Run {
        final String rPr; // raw <a:rPr .../> or ""
        final int eff;

        Run(String rPr, int eff) {
            this.rPr = rPr;
            this.eff = eff;
        }
    }

    static final class Para {
        String startTag, startTagNew, endTag, pPr = "", endPara = "", prefix;
    }

    @Override
    public Object loadParagraph(XNode p, LoadSink sink) {
        Para info = new Para();
        info.prefix = p.prefix;
        info.startTag = p.selfClosing ? Xml.startTag(p, null, false) : p.rawStartTag(src);
        info.startTagNew = "<" + p.qname + ">";
        info.endTag = "</" + p.qname + ">";
        Spans.Frame[] none = TextSegment.NO_FRAMES;
        for (XNode c : p.children) {
            if (c.type != XNode.ELEMENT) continue;
            boolean isA = a.equals(c.ns);
            String l = c.local;
            if (isA && l.equals("pPr")) {
                info.pPr = c.raw(src);
            } else if (isA && l.equals("endParaRPr")) {
                info.endPara = c.raw(src);
            } else if (isA && l.equals("r")) {
                XNode rPr = c.child(a, "rPr");
                int eff = rPr == null ? 0 : fmtOf(rPr);
                float size = 0;
                int color = 0;
                if (rPr != null) {
                    String sz = rPr.attr(null, "sz");
                    if (sz != null) {
                        try {
                            size = DocxDialect.clampSize(Integer.parseInt(sz) / 100f / baseSizePt);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    XNode fill = rPr.child(a, "solidFill");
                    XNode srgb = fill == null ? null : fill.child(a, "srgbClr");
                    if (srgb != null) color = DocxStyles.parseColor(srgb.attr(null, "val"));
                }
                Run run = new Run(rPr == null ? "" : rPr.raw(src), eff);
                XNode t = c.child(a, "t");
                if (t != null) sink.text(DocxDialect.sanitize(t.textContent()), none, run, eff, size, color);
            } else if (isA && l.equals("br")) {
                sink.obj(new Spans.Obj(c.raw(src), false, "↵"), none, null, 0);
            } else if (isA && l.equals("fld")) {
                XNode t = c.child(a, "t");
                String label = t == null ? "[field]" : t.textContent();
                if (label.length() == 0) label = "[field]";
                sink.obj(new Spans.Obj(c.raw(src), false, label), none, null, 0);
            } else {
                sink.obj(new Spans.Obj(c.raw(src), false, "[" + l + "]"), none, null, 0);
            }
        }
        return info;
    }

    private static int fmtOf(XNode rPr) {
        int f = 0;
        if (isOn(rPr.attr(null, "b"))) f |= Spans.BOLD;
        if (isOn(rPr.attr(null, "i"))) f |= Spans.ITALIC;
        String u = rPr.attr(null, "u");
        if (u != null && !u.equals("none")) f |= Spans.UNDERLINE;
        String s = rPr.attr(null, "strike");
        if (s != null && !s.equals("noStrike")) f |= Spans.STRIKE;
        return f;
    }

    private static boolean isOn(String v) {
        return v != null && (v.equals("1") || v.equals("true"));
    }

    @Override
    public String emitParagraph(Object infoObj, Line line, boolean first, boolean last, EmitContext ctx) {
        Para info = (Para) infoObj;
        String r = Xml.q(info.prefix, "r"), t = Xml.q(info.prefix, "t");
        StringBuilder sb = new StringBuilder();
        sb.append(first ? info.startTag : info.startTagNew);
        sb.append(info.pPr);
        for (Atom at : line.atoms) {
            if (at.kind == Atom.TEXT) {
                sb.append('<').append(r).append('>');
                sb.append(rPrFor((Run) at.run, at.fmt, info.prefix));
                sb.append('<').append(t).append('>').append(Xml.escText(at.text)).append("</").append(t).append('>');
                sb.append("</").append(r).append('>');
            } else if (at.kind == Atom.OBJ) {
                sb.append(at.obj.xml);
            } else {
                sb.append(at.mark.xml);
            }
        }
        sb.append(info.endPara);
        sb.append(info.endTag);
        return sb.toString();
    }

    private String rPrFor(Run run, int fmt, String prefix) {
        String base = run == null ? "" : run.rPr;
        int orig = run == null ? 0 : run.eff;
        if (fmt == orig) return base;
        String[] set = new String[8];
        int diff = fmt ^ orig;
        int k = 0;
        if ((diff & Spans.BOLD) != 0) {
            set[k++] = "b";
            set[k++] = (fmt & Spans.BOLD) != 0 ? "1" : "0";
        }
        if ((diff & Spans.ITALIC) != 0) {
            set[k++] = "i";
            set[k++] = (fmt & Spans.ITALIC) != 0 ? "1" : "0";
        }
        if ((diff & Spans.UNDERLINE) != 0) {
            set[k++] = "u";
            set[k++] = (fmt & Spans.UNDERLINE) != 0 ? "sng" : "none";
        }
        if ((diff & Spans.STRIKE) != 0) {
            set[k++] = "strike";
            set[k++] = (fmt & Spans.STRIKE) != 0 ? "sngStrike" : "noStrike";
        }
        String[] pairs = new String[k];
        System.arraycopy(set, 0, pairs, 0, k);
        if (base.length() == 0) {
            StringBuilder sb = new StringBuilder("<").append(Xml.q(prefix, "rPr"));
            for (int i = 0; i < pairs.length; i += 2) {
                sb.append(' ').append(pairs[i]).append("=\"").append(pairs[i + 1]).append('"');
            }
            return sb.append("/>").toString();
        }
        try {
            XNode frag = XmlParser.parseFragment(base);
            XNode rPr = DocxDialect.firstElement(frag);
            if (rPr == null) return base;
            String tag = Xml.startTag(rPr, pairs, rPr.selfClosing);
            return tag + base.substring(rPr.startTagEnd);
        } catch (XmlException e) {
            return base;
        }
    }
}
