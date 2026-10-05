package com.disgusty.retroffice.text;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.disgusty.retroffice.compat.Compat;
import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XNode;
import com.disgusty.retroffice.xml.XmlException;
import com.disgusty.retroffice.xml.XmlParser;

/** WordprocessingML paragraphs (w:p). */
public final class DocxDialect extends TextDialect {
    private final String w;      // namespace URI (transitional or strict)
    private final String wp;     // prefix used in this part
    private final DocxStyles styles;
    private final ImageResolver images;
    private final int bodySz;

    private static final HashSet<String> WRAPPERS = set("hyperlink", "ins", "smartTag", "customXml",
            "fldSimple", "moveTo", "dir", "bdo");
    private static final HashSet<String> P_MARKERS = set("bookmarkStart", "bookmarkEnd", "proofErr",
            "commentRangeStart", "commentRangeEnd", "permStart", "permEnd", "del", "moveFrom",
            "moveFromRangeStart", "moveFromRangeEnd", "moveToRangeStart", "moveToRangeEnd",
            "customXmlInsRangeStart", "customXmlInsRangeEnd", "customXmlDelRangeStart", "customXmlDelRangeEnd",
            "customXmlMoveFromRangeStart", "customXmlMoveFromRangeEnd", "customXmlMoveToRangeStart",
            "customXmlMoveToRangeEnd");
    private static final HashSet<String> R_MARKERS = set("lastRenderedPageBreak", "fldChar", "instrText",
            "delText", "delInstrText", "annotationRef", "footnoteRef", "endnoteRef");

    /** Schema order of w:rPr children (CT_RPr). Unknown children sort last. */
    private static final List<String> RPR_ORDER = Arrays.asList("rStyle", "rFonts", "b", "bCs", "i", "iCs",
            "caps", "smallCaps", "strike", "dstrike", "outline", "shadow", "emboss", "imprint", "noProof",
            "snapToGrid", "vanish", "webHidden", "color", "spacing", "w", "kern", "position", "sz", "szCs",
            "highlight", "u", "effect", "bdr", "shd", "fitText", "vertAlign", "rtl", "cs", "em", "lang",
            "eastAsianLayout", "specVanish", "oMath");

    static HashSet<String> set(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    public DocxDialect(String src, String wns, String wPrefix, DocxStyles styles, ImageResolver images) {
        super(src);
        this.w = wns;
        this.wp = wPrefix;
        this.styles = styles;
        this.images = images;
        this.bodySz = styles.bodySize();
    }

    /** Run properties captured at load. */
    static final class Run {
        final String rPr;        // raw <w:rPr>...</w:rPr> or ""
        final int eff;           // effective formatting including direct properties
        final int style;         // formatting from styles only
        final float size;
        final int color;

        Run(String rPr, int eff, int style, float size, int color) {
            this.rPr = rPr;
            this.eff = eff;
            this.style = style;
            this.size = size;
            this.color = color;
        }
    }

    static final class Para {
        String startTag, startTagNew, endTag;
        String pPr = "", pPrNoSect = "";
        String markRPr = "";     // paragraph mark properties usable as a run base
        DocxStyles.Props props;  // paragraph style props (no direct run formatting)
    }

    private String q(String local) {
        return Xml.q(wp, local);
    }

    @Override
    public Object loadParagraph(XNode p, LoadSink sink) {
        Para info = new Para();
        info.startTag = p.selfClosing ? Xml.startTag(p, null, false) : p.rawStartTag(src);
        info.startTagNew = "<" + p.qname + ">";
        info.endTag = "</" + p.qname + ">";
        XNode pPr = p.child(w, "pPr");
        String pStyle = null;
        if (pPr != null) {
            info.pPr = pPr.raw(src);
            info.pPrNoSect = without(pPr, "sectPr");
            XNode ps = pPr.child(w, "pStyle");
            if (ps != null) pStyle = ps.attr(w, "val");
            XNode mr = pPr.child(w, "rPr");
            if (mr != null) info.markRPr = withoutChildren(mr, set("ins", "del", "moveFrom", "moveTo", "rPrChange"));
        }
        DocxStyles.Props pp = styles.paragraph(pStyle);
        if (pPr != null) styles.readPPr(pPr, pp);
        info.props = pp;

        int s = sink.length();
        float paraSize = pp.sz > 0 ? clampSize(pp.sz / (float) bodySz) : 1f;
        sink.setParagraphDisplay(paraSize, pp.color);
        loadChildren(p, sink, new ArrayList<Spans.Frame>(), pp, 0);
        int e = sink.length();

        if (pp.numbered == 1) {
            int lvl = 0;
            XNode num = pPr == null ? null : pPr.child(w, "numPr");
            XNode il = num == null ? null : num.child(w, "ilvl");
            if (il != null) {
                try {
                    lvl = Math.min(8, Integer.parseInt(il.attr(w, "val")));
                } catch (Exception ignored) {
                }
            }
            sink.span(new Spans.DispBullet((int) (24 * images.density()), lvl), s, e, android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        if (pp.jc != null) {
            int a = pp.jc.equals("center") ? Compat.ALIGN_CENTER
                    : (pp.jc.equals("right") || pp.jc.equals("end")) ? Compat.ALIGN_OPPOSITE : Compat.ALIGN_NORMAL;
            Object span = Compat.alignmentSpan(a);
            if (span != null) sink.span(span, s, e, android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        return info;
    }

    private void loadChildren(XNode parent, LoadSink sink, ArrayList<Spans.Frame> frames, DocxStyles.Props pp, int depth) {
        Spans.Frame[] fr = frames.toArray(new Spans.Frame[frames.size()]);
        for (XNode c : parent.children) {
            if (c.type != XNode.ELEMENT) continue;
            boolean isW = w.equals(c.ns);
            String l = c.local;
            if (isW && l.equals("pPr")) continue;
            if (isW && l.equals("r")) {
                loadRun(c, sink, fr, pp);
            } else if (isW && WRAPPERS.contains(l) && !c.selfClosing && depth < 4) {
                Spans.Frame f = new Spans.Frame(c.rawStartTag(src), c.rawEndTag(src), depth, false, null);
                frames.add(f);
                loadChildren(c, sink, frames, pp, depth + 1);
                frames.remove(frames.size() - 1);
            } else if (isW && (P_MARKERS.contains(l) || WRAPPERS.contains(l))) {
                sink.mark(c.raw(src), fr);
            } else {
                String label = "[" + l + "]";
                if (!isW && l.startsWith("oMath")) label = "[formula]";
                sink.obj(new Spans.Obj(c.raw(src), false, label), fr, null, 0);
            }
        }
    }

    private void loadRun(XNode r, LoadSink sink, Spans.Frame[] fr, DocxStyles.Props pp) {
        XNode rPr = r.child(w, "rPr");
        String rStyle = null;
        if (rPr != null) {
            XNode rs = rPr.child(w, "rStyle");
            if (rs != null) rStyle = rs.attr(w, "val");
        }
        DocxStyles.Props base = styles.run(pp, rStyle);
        DocxStyles.Props eff = base.copy();
        styles.readRPr(rPr, eff);
        float size = eff.sz > 0 ? clampSize(eff.sz / (float) bodySz) : 1f;
        Run run = new Run(rPr == null ? "" : rPr.raw(src), eff.fmt(), base.fmt(), size, eff.color);
        int fmt = run.eff;
        for (XNode c : r.children) {
            if (c.type != XNode.ELEMENT) continue;
            String l = c.local;
            boolean isW = w.equals(c.ns);
            if (isW && l.equals("rPr")) continue;
            if (isW && l.equals("t")) {
                sink.text(sanitize(c.textContent()), fr, run, fmt, size, eff.color);
            } else if (isW && l.equals("tab")) {
                sink.text("\t", fr, run, fmt, size, eff.color);
            } else if (isW && l.equals("noBreakHyphen")) {
                sink.text("‑", fr, run, fmt, size, eff.color);
            } else if (isW && l.equals("softHyphen")) {
                sink.text("­", fr, run, fmt, size, eff.color);
            } else if (isW && R_MARKERS.contains(l)) {
                sink.mark("<" + r.qname + ">" + run.rPr + c.raw(src) + "</" + r.qname + ">", fr);
            } else {
                Spans.Obj o = new Spans.Obj(c.raw(src), true, labelFor(c));
                if (isW && l.equals("drawing")) attachImage(c, o);
                sink.obj(o, fr, run, fmt);
            }
        }
    }

    private String labelFor(XNode c) {
        String l = c.local;
        if (l.equals("br")) {
            String t = c.attr(w, "type");
            if ("page".equals(t)) return "[page break]";
            if ("column".equals(t)) return "[column break]";
            return "↵";
        }
        if (l.equals("cr")) return "↵";
        if (l.equals("drawing") || l.equals("pict") || l.equals("object")) return "[image]";
        if (l.equals("footnoteReference") || l.equals("endnoteReference")) return "[note]";
        if (l.equals("commentReference")) return "[comment]";
        if (l.equals("sym")) return "[symbol]";
        return "[" + l + "]";
    }

    private void attachImage(XNode drawing, Spans.Obj o) {
        XNode blip = drawing.find(Xml.A, "blip");
        if (blip == null) blip = drawing.find(Xml.A_STRICT, "blip");
        if (blip == null) return;
        String rid = blip.attr(Xml.R, "embed");
        if (rid == null) rid = blip.attr(Xml.R_STRICT, "embed");
        if (rid == null) return;
        int wpx = 0, hpx = 0;
        XNode ext = drawing.find(Xml.WP, "extent");
        if (ext != null) {
            try {
                wpx = (int) (Long.parseLong(ext.attr(null, "cx")) / 9525 * images.density());
                hpx = (int) (Long.parseLong(ext.attr(null, "cy")) / 9525 * images.density());
            } catch (Exception ignored) {
            }
        }
        Bitmap b = images.load(rid, wpx, hpx);
        if (b != null) {
            o.bitmap = b;
            o.width = wpx;
            o.height = hpx;
        }
    }

    static String sanitize(String t) {
        if (t.indexOf('\n') < 0 && t.indexOf('\r') < 0 && t.indexOf('￼') < 0) return t;
        return t.replace('\n', ' ').replace('\r', ' ').replace('￼', ' ');
    }

    static float clampSize(float f) {
        return Math.max(0.5f, Math.min(3f, f));
    }

    // ------------------------------------------------------------------ writing

    @Override
    public String emitParagraph(Object infoObj, Line line, boolean first, boolean last, EmitContext ctx) {
        Para info = (Para) infoObj;
        StringBuilder sb = new StringBuilder();
        sb.append(first ? info.startTag : info.startTagNew);
        sb.append(last ? info.pPr : info.pPrNoSect);
        Spans.Frame[] open = TextSegment.NO_FRAMES;
        for (Atom a : line.atoms) {
            open = transition(sb, open, a.frames);
            switch (a.kind) {
                case Atom.TEXT:
                    sb.append('<').append(q("r")).append('>');
                    sb.append(rPrFor((Run) a.run, a.fmt, info));
                    appendText(sb, a.text);
                    sb.append("</").append(q("r")).append('>');
                    break;
                case Atom.OBJ:
                    if (a.obj.runChild) {
                        sb.append('<').append(q("r")).append('>');
                        sb.append(rPrFor((Run) a.run, a.fmt, info));
                        sb.append(a.obj.xml);
                        sb.append("</").append(q("r")).append('>');
                    } else {
                        sb.append(a.obj.xml);
                    }
                    break;
                default:
                    sb.append(a.mark.xml);
            }
        }
        transition(sb, open, TextSegment.NO_FRAMES);
        sb.append(info.endTag);
        return sb.toString();
    }

    static Spans.Frame[] transition(StringBuilder sb, Spans.Frame[] open, Spans.Frame[] want) {
        int common = 0;
        while (common < open.length && common < want.length && open[common] == want[common]) common++;
        for (int i = open.length - 1; i >= common; i--) sb.append(open[i].close);
        for (int i = common; i < want.length; i++) sb.append(want[i].open);
        return want;
    }

    private void appendText(StringBuilder sb, String text) {
        int i = 0, n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\t') {
                sb.append('<').append(q("tab")).append("/>");
                i++;
            } else if (c == '‑') {
                sb.append('<').append(q("noBreakHyphen")).append("/>");
                i++;
            } else if (c == '­') {
                sb.append('<').append(q("softHyphen")).append("/>");
                i++;
            } else {
                int j = i;
                while (j < n) {
                    char d = text.charAt(j);
                    if (d == '\t' || d == '‑' || d == '­') break;
                    j++;
                }
                String chunk = text.substring(i, j);
                boolean preserve = chunk.startsWith(" ") || chunk.endsWith(" ") || chunk.contains("  ");
                sb.append('<').append(q("t"));
                if (preserve) sb.append(" xml:space=\"preserve\"");
                sb.append('>').append(Xml.escText(chunk)).append("</").append(q("t")).append('>');
                i = j;
            }
        }
    }

    private static final String[][] PROP_NAMES = {{"b"}, {"i"}, {"u"}, {"strike", "dstrike"}};
    private static final int[] BITS = {Spans.BOLD, Spans.ITALIC, Spans.UNDERLINE, Spans.STRIKE};

    /** Run properties for text with formatting {@code fmt}, changing only what the user changed. */
    String rPrFor(Run run, int fmt, Para info) {
        String base;
        int origEff, styleFmt;
        if (run != null) {
            base = run.rPr;
            origEff = run.eff;
            styleFmt = run.style;
        } else {
            base = info.markRPr;
            styleFmt = info.props.fmt();
            DocxStyles.Props tmp = info.props.copy();
            if (base.length() > 0) {
                try {
                    XNode frag = XmlParser.parseFragment(base);
                    readDirect(frag, tmp);
                } catch (XmlException ignored) {
                }
            }
            origEff = tmp.fmt();
        }
        if (fmt == origEff) return base;

        ArrayList<String[]> kids = new ArrayList<String[]>(); // {local, raw}
        String open = "<" + q("rPr") + ">";
        String close = "</" + q("rPr") + ">";
        if (base.length() > 0) {
            try {
                XNode frag = XmlParser.parseFragment(base);
                XNode rPr = firstElement(frag);
                if (rPr != null) {
                    if (!rPr.selfClosing) {
                        open = rPr.rawStartTag(base);
                        close = rPr.rawEndTag(base);
                    } else {
                        open = Xml.startTag(rPr, null, false);
                        close = "</" + rPr.qname + ">";
                    }
                    for (XNode c : rPr.children) {
                        if (c.type == XNode.ELEMENT) kids.add(new String[]{c.local, c.raw(base)});
                    }
                }
            } catch (XmlException e) {
                // Unparseable fragment can't happen for markup we read ourselves; fall back to fresh props.
                kids.clear();
            }
        }
        for (int k = 0; k < 4; k++) {
            int bit = BITS[k];
            if ((fmt & bit) == (origEff & bit)) continue;
            boolean want = (fmt & bit) != 0;
            for (int i = kids.size() - 1; i >= 0; i--) {
                for (String nm : PROP_NAMES[k]) if (kids.get(i)[0].equals(nm)) kids.remove(i);
            }
            boolean fromStyle = (styleFmt & bit) != 0;
            if (want == fromStyle) continue; // the style already gives the wanted value
            String name = PROP_NAMES[k][0];
            String el;
            if (bit == Spans.UNDERLINE) {
                el = "<" + q("u") + " " + q("val") + "=\"" + (want ? "single" : "none") + "\"/>";
            } else {
                el = want ? "<" + q(name) + "/>" : "<" + q(name) + " " + q("val") + "=\"0\"/>";
            }
            kids.add(new String[]{name, el});
        }
        // Stable sort into schema order.
        ArrayList<String[]> sorted = new ArrayList<String[]>();
        for (String name : RPR_ORDER) {
            for (String[] k : kids) if (k[0].equals(name)) sorted.add(k);
        }
        for (String[] k : kids) if (!RPR_ORDER.contains(k[0])) sorted.add(k);
        if (sorted.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(open);
        for (String[] k : sorted) sb.append(k[1]);
        sb.append(close);
        return sb.toString();
    }

    private void readDirect(XNode frag, DocxStyles.Props p) {
        XNode rPr = firstElement(frag);
        if (rPr == null) return;
        // Fragment nodes carry no namespaces; match by prefix + local name instead.
        DocxStyles.Props d = new DocxStyles.Props();
        for (XNode c : rPr.children) {
            if (c.type != XNode.ELEMENT) continue;
            String v = c.attrRaw(q("val"));
            boolean on = v == null || !(v.equals("0") || v.equals("false") || v.equals("off"));
            int bit = 0;
            if (c.local.equals("b")) bit = Spans.BOLD;
            else if (c.local.equals("i")) bit = Spans.ITALIC;
            else if (c.local.equals("strike") || c.local.equals("dstrike")) bit = Spans.STRIKE;
            else if (c.local.equals("u")) {
                bit = Spans.UNDERLINE;
                on = v == null || !v.equals("none");
            }
            if (bit != 0) {
                d.set |= bit;
                if (on) d.val |= bit;
                else d.val &= ~bit;
            }
        }
        p.overlay(d);
    }

    static XNode firstElement(XNode holder) {
        for (XNode c : holder.children) if (c.type == XNode.ELEMENT) return c;
        return null;
    }

    private String without(XNode e, String childLocal) {
        XNode skip = e.child(w, childLocal);
        if (skip == null) return e.raw(src);
        return src.substring(e.start, skip.start) + src.substring(skip.end, e.end);
    }

    private String withoutChildren(XNode e, HashSet<String> locals) {
        StringBuilder sb = new StringBuilder();
        int cur = e.start;
        for (XNode c : e.children) {
            if (c.type == XNode.ELEMENT && w.equals(c.ns) && locals.contains(c.local)) {
                sb.append(src, cur, c.start);
                cur = c.end;
            }
        }
        sb.append(src, cur, e.end);
        String out = sb.toString();
        // An rPr that ends up empty is just noise.
        String emptyA = "<" + e.qname + "></" + e.qname + ">";
        if (out.equals(emptyA) || e.selfClosing) return "";
        return out;
    }
}
