package com.disgusty.retroffice.text;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.HashSet;

import com.disgusty.retroffice.compat.Compat;
import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XNode;

/** ODF paragraphs (text:p / text:h) used by text documents and presentations. */
public final class OdfDialect extends TextDialect {
    private final OdfStyles styles;
    private final ImageResolver images;
    private final XNode partRoot;

    private static final HashSet<String> MARKERS = DocxDialect.set("soft-page-break", "bookmark", "bookmark-start",
            "bookmark-end", "reference-mark", "reference-mark-start", "reference-mark-end", "toc-mark",
            "toc-mark-start", "toc-mark-end", "alphabetical-index-mark", "alphabetical-index-mark-start",
            "alphabetical-index-mark-end", "user-index-mark", "user-index-mark-start", "user-index-mark-end",
            "change", "change-start", "change-end");

    /** Font size (pt) that on-screen relative sizes are computed against. */
    private float baseSizePt;

    public OdfDialect(String src, XNode partRoot, OdfStyles styles, ImageResolver images) {
        super(src);
        this.partRoot = partRoot;
        this.styles = styles;
        this.images = images;
        this.baseSizePt = styles.defaultSize();
    }

    /** Sets the size relative sizes refer to (slides use one per text frame). */
    public void setBaseSize(float pt) {
        baseSizePt = pt > 0 ? pt : styles.defaultSize();
    }

    static final class Para {
        String startTag, startTagNew, endTag, textPrefix;
        OdfStyles.Props props;
        String styleName;
    }

    private boolean lastSpace;

    @Override
    public Object loadParagraph(XNode p, LoadSink sink) {
        Para info = new Para();
        info.startTag = p.selfClosing ? Xml.startTag(p, null, false) : p.rawStartTag(src);
        info.startTagNew = Xml.startTag(p, new String[]{"xml:id", null, Xml.q(p.prefix, "id"), null}, false);
        info.endTag = "</" + p.qname + ">";
        info.textPrefix = p.prefix;
        info.styleName = p.attr(Xml.TEXT, "style-name");
        info.props = styles.paragraph(info.styleName);

        sink.setParagraphDisplay(relSize(info.props), info.props.color);
        int s = sink.length();
        lastSpace = true;
        load(p, sink, new ArrayList<Spans.Frame>(), info.props, 0);
        int e = sink.length();

        int level = 0;
        for (XNode a = p.parent; a != null; a = a.parent) if (a.is(Xml.TEXT, "list")) level++;
        if (level > 0) {
            sink.span(new Spans.DispBullet((int) (24 * images.density()), level - 1), s, e,
                    android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        String al = info.props.align;
        if (al != null) {
            int a = al.equals("center") ? Compat.ALIGN_CENTER
                    : (al.equals("end") || al.equals("right")) ? Compat.ALIGN_OPPOSITE : Compat.ALIGN_NORMAL;
            Object span = Compat.alignmentSpan(a);
            if (span != null) sink.span(span, s, e, android.text.Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        }
        return info;
    }

    private void load(XNode parent, LoadSink sink, ArrayList<Spans.Frame> frames, OdfStyles.Props props, int depth) {
        Spans.Frame[] fr = frames.toArray(new Spans.Frame[frames.size()]);
        float rel = relSize(props);
        int fmt = props.fmt();
        for (XNode c : parent.children) {
            if (c.type == XNode.TEXT || c.type == XNode.CDATA) {
                StringBuilder sb = new StringBuilder();
                String t = c.text;
                for (int i = 0; i < t.length(); i++) {
                    char ch = t.charAt(i);
                    if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                        if (!lastSpace) {
                            sb.append(' ');
                            lastSpace = true;
                        }
                    } else {
                        sb.append(ch == '￼' ? ' ' : ch);
                        lastSpace = false;
                    }
                }
                sink.text(sb.toString(), fr, null, fmt, rel, props.color);
                continue;
            }
            if (c.type != XNode.ELEMENT) continue;
            String l = c.local;
            if (Xml.TEXT.equals(c.ns)) {
                if ((l.equals("span") || l.equals("a") || l.equals("meta")) && !c.selfClosing && depth < 6) {
                    String sn = c.attr(Xml.TEXT, "style-name");
                    Spans.Frame f = new Spans.Frame(c.rawStartTag(src), c.rawEndTag(src), depth, l.equals("span"), sn);
                    frames.add(f);
                    load(c, sink, frames, sn != null ? styles.text(props, sn) : props, depth + 1);
                    frames.remove(frames.size() - 1);
                } else if (l.equals("span") || l.equals("a") || l.equals("meta") || MARKERS.contains(l)) {
                    sink.mark(c.raw(src), fr);
                } else if (l.equals("s")) {
                    int n = 1;
                    String cnt = c.attr(Xml.TEXT, "c");
                    if (cnt != null) {
                        try {
                            n = Math.max(1, Math.min(10000, Integer.parseInt(cnt)));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    StringBuilder sp = new StringBuilder();
                    for (int i = 0; i < n; i++) sp.append(' ');
                    sink.text(sp.toString(), fr, null, fmt, rel, props.color);
                    lastSpace = true;
                } else if (l.equals("tab")) {
                    sink.text("\t", fr, null, fmt, rel, props.color);
                    lastSpace = true;
                } else if (l.equals("line-break")) {
                    sink.obj(new Spans.Obj(c.raw(src), false, "↵"), fr, null, fmt);
                    lastSpace = true;
                } else if (l.equals("note")) {
                    sink.obj(new Spans.Obj(c.raw(src), false, "[note]"), fr, null, fmt);
                    lastSpace = false;
                } else {
                    String label = c.textContent().trim();
                    if (label.length() == 0 || label.length() > 40) label = "[" + l + "]";
                    sink.obj(new Spans.Obj(c.raw(src), false, label), fr, null, fmt);
                    lastSpace = false;
                }
            } else if (c.is(Xml.OFFICE, "annotation-end")) {
                sink.mark(c.raw(src), fr);
            } else if (c.is(Xml.OFFICE, "annotation")) {
                sink.obj(new Spans.Obj(c.raw(src), false, "[comment]"), fr, null, fmt);
                lastSpace = false;
            } else {
                Spans.Obj o = new Spans.Obj(c.raw(src), false, "[" + l + "]");
                if (c.is(Xml.DRAW, "frame")) {
                    o = new Spans.Obj(c.raw(src), false, "[image]");
                    attachImage(c, o);
                }
                sink.obj(o, fr, null, fmt);
                lastSpace = false;
            }
        }
    }

    /**
     * On-screen size relative to the base. In text documents every size counts; on slides only
     * sizes set by a style do (the document default would otherwise shrink titles).
     */
    private float relSize(OdfStyles.Props p) {
        boolean textDoc = Math.abs(baseSizePt - styles.defaultSize()) < 0.01f;
        if (p.sizePt <= 0 || (!textDoc && !p.sizeExplicit)) return 1f;
        return DocxDialect.clampSize(p.sizePt / baseSizePt);
    }

    private void attachImage(XNode frame, Spans.Obj o) {
        XNode img = frame.child(Xml.DRAW, "image");
        if (img == null) return;
        String href = img.attr(Xml.XLINK, "href");
        if (href == null) return;
        float d = images.density();
        int w = (int) (Units.toPt(frame.attr(Xml.SVG, "width")) * 96f / 72f * d);
        int h = (int) (Units.toPt(frame.attr(Xml.SVG, "height")) * 96f / 72f * d);
        Bitmap b = images.load(href, w, h);
        if (b != null) {
            o.bitmap = b;
            o.width = w;
            o.height = h;
        }
    }

    // ------------------------------------------------------------------ writing

    @Override
    public String emitParagraph(Object infoObj, Line line, boolean first, boolean last, EmitContext ctx) {
        Para info = (Para) infoObj;
        String tp = info.textPrefix;
        StringBuilder sb = new StringBuilder();
        sb.append(first ? info.startTag : info.startTagNew);
        Spans.Frame[] open = TextSegment.NO_FRAMES;
        boolean prevSpace = true;
        // Index of the last atom with visible content: spaces after it must be explicit.
        int lastContent = -1;
        for (int i = 0; i < line.atoms.size(); i++) {
            Atom a = line.atoms.get(i);
            if (a.kind == Atom.OBJ || (a.kind == Atom.TEXT && a.text.trim().length() > 0)) lastContent = i;
        }
        for (int i = 0; i < line.atoms.size(); i++) {
            Atom a = line.atoms.get(i);
            Spans.Frame[] want = a.frames;
            if (a.kind != Atom.MARK) {
                int baseline = baselineFmt(info, a.frames);
                if (a.fmt != baseline) want = withOverride(a.frames, a.fmt, baseline, ctx, tp);
            }
            open = DocxDialect.transition(sb, open, want);
            if (a.kind == Atom.TEXT) {
                String t = a.text;
                boolean trailing = i >= lastContent;
                int n = t.length();
                int k = 0;
                while (k < n) {
                    char c = t.charAt(k);
                    if (c == ' ') {
                        int j = k;
                        while (j < n && t.charAt(j) == ' ') j++;
                        int count = j - k;
                        boolean atEnd = trailing && j == n;
                        if (!prevSpace && !atEnd) {
                            sb.append(' ');
                            count--;
                        }
                        if (count > 0) {
                            sb.append('<').append(Xml.q(tp, "s"));
                            if (count > 1) sb.append(' ').append(Xml.q(tp, "c")).append("=\"").append(count).append('"');
                            sb.append("/>");
                        }
                        prevSpace = true;
                        k = j;
                    } else if (c == '\t') {
                        sb.append('<').append(Xml.q(tp, "tab")).append("/>");
                        prevSpace = true;
                        k++;
                    } else {
                        int j = k;
                        while (j < n && t.charAt(j) != ' ' && t.charAt(j) != '\t') j++;
                        sb.append(Xml.escText(t.substring(k, j)));
                        prevSpace = false;
                        k = j;
                    }
                }
            } else if (a.kind == Atom.OBJ) {
                sb.append(a.obj.xml);
                prevSpace = "↵".equals(a.obj.label);
            } else {
                sb.append(a.mark.xml);
            }
        }
        DocxDialect.transition(sb, open, TextSegment.NO_FRAMES);
        sb.append(info.endTag);
        return sb.toString();
    }

    private int baselineFmt(Para info, Spans.Frame[] frames) {
        OdfStyles.Props p = info.props;
        for (Spans.Frame f : frames) if (f.styleName != null) p = styles.text(p, f.styleName);
        return p.fmt();
    }

    private Spans.Frame[] withOverride(Spans.Frame[] frames, int fmt, int baseline, EmitContext ctx, String tp) {
        int diff = fmt ^ baseline;
        String key = "odf:" + diff + ":" + (fmt & diff);
        String name = ctx.styleByKey.get(key);
        if (name == null) {
            int n = 1;
            do {
                name = "MAU" + n++;
            } while (styles.allNames.contains(name) || ctx.newStyles.containsKey(name));
            ctx.styleByKey.put(key, name);
            ctx.newStyles.put(name, styleXml(name, diff, fmt));
        }
        String fkey = "frame:" + name;
        Spans.Frame f = (Spans.Frame) ctx.frameCache.get(fkey);
        if (f == null) {
            f = new Spans.Frame("<" + Xml.q(tp, "span") + " " + Xml.q(tp, "style-name") + "=\"" + name + "\">",
                    "</" + Xml.q(tp, "span") + ">", 1000, true, null);
            ctx.frameCache.put(fkey, f);
        }
        Spans.Frame[] out = new Spans.Frame[frames.length + 1];
        System.arraycopy(frames, 0, out, 0, frames.length);
        out[frames.length] = f;
        return out;
    }

    private String styleXml(String name, int diff, int fmt) {
        String sp = partRoot.prefixFor(Xml.STYLE);
        String fo = partRoot.prefixFor(Xml.FO);
        String extraNs = "";
        if (sp == null) {
            sp = "style";
            extraNs += " xmlns:style=\"" + Xml.STYLE + "\"";
        }
        if (fo == null) {
            fo = "fo";
            extraNs += " xmlns:fo=\"" + Xml.FO + "\"";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('<').append(Xml.q(sp, "style")).append(extraNs).append(' ')
                .append(Xml.q(sp, "name")).append("=\"").append(name).append("\" ")
                .append(Xml.q(sp, "family")).append("=\"text\">");
        sb.append('<').append(Xml.q(sp, "text-properties"));
        if ((diff & Spans.BOLD) != 0) {
            String v = (fmt & Spans.BOLD) != 0 ? "bold" : "normal";
            sb.append(' ').append(Xml.q(fo, "font-weight")).append("=\"").append(v).append('"');
            sb.append(' ').append(Xml.q(sp, "font-weight-asian")).append("=\"").append(v).append('"');
            sb.append(' ').append(Xml.q(sp, "font-weight-complex")).append("=\"").append(v).append('"');
        }
        if ((diff & Spans.ITALIC) != 0) {
            String v = (fmt & Spans.ITALIC) != 0 ? "italic" : "normal";
            sb.append(' ').append(Xml.q(fo, "font-style")).append("=\"").append(v).append('"');
            sb.append(' ').append(Xml.q(sp, "font-style-asian")).append("=\"").append(v).append('"');
            sb.append(' ').append(Xml.q(sp, "font-style-complex")).append("=\"").append(v).append('"');
        }
        if ((diff & Spans.UNDERLINE) != 0) {
            if ((fmt & Spans.UNDERLINE) != 0) {
                sb.append(' ').append(Xml.q(sp, "text-underline-style")).append("=\"solid\"");
                sb.append(' ').append(Xml.q(sp, "text-underline-width")).append("=\"auto\"");
                sb.append(' ').append(Xml.q(sp, "text-underline-color")).append("=\"font-color\"");
            } else {
                sb.append(' ').append(Xml.q(sp, "text-underline-style")).append("=\"none\"");
            }
        }
        if ((diff & Spans.STRIKE) != 0) {
            if ((fmt & Spans.STRIKE) != 0) {
                sb.append(' ').append(Xml.q(sp, "text-line-through-style")).append("=\"solid\"");
                sb.append(' ').append(Xml.q(sp, "text-line-through-type")).append("=\"single\"");
            } else {
                sb.append(' ').append(Xml.q(sp, "text-line-through-style")).append("=\"none\"");
            }
        }
        sb.append("/></").append(Xml.q(sp, "style")).append('>');
        return sb.toString();
    }
}
