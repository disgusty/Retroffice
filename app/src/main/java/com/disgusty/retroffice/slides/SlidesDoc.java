package com.disgusty.retroffice.slides;

import android.graphics.Bitmap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.disgusty.retroffice.doc.Doc;
import com.disgusty.retroffice.doc.Images;
import com.disgusty.retroffice.doc.Rels;
import com.disgusty.retroffice.doc.TextDoc;
import com.disgusty.retroffice.pkg.OfficePackage;
import com.disgusty.retroffice.pkg.PackageVerifier;
import com.disgusty.retroffice.pkg.PackageWriter;
import com.disgusty.retroffice.text.DmlDialect;
import com.disgusty.retroffice.text.EmitContext;
import com.disgusty.retroffice.text.ImageResolver;
import com.disgusty.retroffice.text.Line;
import com.disgusty.retroffice.text.OdfDialect;
import com.disgusty.retroffice.text.OdfStyles;
import com.disgusty.retroffice.text.TextSegment;
import com.disgusty.retroffice.text.Units;
import com.disgusty.retroffice.xml.Patcher;
import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XNode;

/** A presentation (PPTX or ODP): slides with positioned shapes whose text can be edited. */
public final class SlidesDoc extends Doc {
    public static final int PPTX = 1, ODP = 2;

    public static final class Shape {
        /** Position and size in points. */
        public float x, y, w, h;
        public TextSegment text;
        public Bitmap image;
        public int fill;
        public String label;
        /** Base font size (pt) of the text. */
        public float baseSize = 18;
        public boolean title;
    }

    public static final class Slide {
        String part;
        XDoc doc;
        public int background = 0xFFFFFFFF;
        public final ArrayList<Shape> shapes = new ArrayList<Shape>();
    }

    public final int format;
    public float width = 720, height = 540;
    public final ArrayList<Slide> slides = new ArrayList<Slide>();
    private OfficePackage pkg;
    private String presPart;
    private XDoc odpContent;
    private String ans, pns;

    private SlidesDoc(int format) {
        this.format = format;
    }

    @Override
    public int kind() {
        return SLIDES;
    }

    @Override
    public String formatName() {
        return format == PPTX ? "PPTX" : "ODP";
    }

    // ------------------------------------------------------------------ PPTX

    public static SlidesDoc openPptx(OfficePackage pkg, String presPart, Images images) throws Exception {
        SlidesDoc d = new SlidesDoc(PPTX);
        d.pkg = pkg;
        d.presPart = presPart;
        XDoc pres = pkg.xml(presPart);
        XNode root = pres.root;
        d.pns = Xml.P.equals(root.ns) ? Xml.P : Xml.P_STRICT.equals(root.ns) ? Xml.P_STRICT : null;
        if (d.pns == null) throw new IOException("Not a PowerPoint presentation");
        d.ans = d.pns.equals(Xml.P) ? Xml.A : Xml.A_STRICT;
        XNode sz = root.child(d.pns, "sldSz");
        if (sz != null) {
            try {
                d.width = Long.parseLong(sz.attr(null, "cx")) / 12700f;
                d.height = Long.parseLong(sz.attr(null, "cy")) / 12700f;
            } catch (Exception ignored) {
            }
        }
        HashMap<String, String> rels = Rels.targets(pkg, presPart);
        XNode lst = root.child(d.pns, "sldIdLst");
        if (lst != null) {
            for (XNode id : lst.childrenNamed(d.pns, "sldId")) {
                String rid = id.attr(Xml.R, "id");
                if (rid == null) rid = id.attr(Xml.R_STRICT, "id");
                String t = rid == null ? null : rels.get(rid);
                if (t == null) continue;
                String part = OfficePackage.resolve(presPart, t);
                if (!pkg.has(part)) continue;
                d.slides.add(d.readPptxSlide(part, images));
            }
        }
        d.markLoaded();
        return d;
    }

    private Slide readPptxSlide(String part, Images images) throws Exception {
        Slide s = new Slide();
        s.part = part;
        s.doc = pkg.xml(part);
        XNode cSld = s.doc.root.child(pns, "cSld");
        if (cSld == null) return s;
        HashMap<String, String> rels = Rels.targets(pkg, part);
        ImageResolver img = images.forPart(pkg, part, rels);
        String layout = Rels.byType(pkg, part, "/slideLayout");
        String master = layout == null ? null : Rels.byType(pkg, layout, "/slideMaster");
        Integer bg = background(cSld);
        if (bg == null && layout != null) bg = backgroundOf(layout);
        if (bg == null && master != null) bg = backgroundOf(master);
        if (bg != null) s.background = bg;
        XNode tree = cSld.child(pns, "spTree");
        if (tree != null) walkPptx(s, tree, new float[]{0, 0, 1, 1}, img, layout, master);
        return s;
    }

    private Integer backgroundOf(String part) {
        try {
            XNode c = pkg.xml(part).root.child(pns, "cSld");
            return c == null ? null : background(c);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer background(XNode cSld) {
        XNode bg = cSld.child(pns, "bg");
        XNode pr = bg == null ? null : bg.child(pns, "bgPr");
        XNode fill = pr == null ? null : pr.child(ans, "solidFill");
        XNode srgb = fill == null ? null : fill.child(ans, "srgbClr");
        if (srgb == null) return null;
        int c = color(srgb.attr(null, "val"));
        return c == 0 ? null : c;
    }

    private static int color(String hex) {
        if (hex == null || hex.length() != 6) return 0;
        try {
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** t = {offsetX, offsetY, scaleX, scaleY} mapping child coordinates (pt) to slide points. */
    private void walkPptx(Slide s, XNode tree, float[] t, ImageResolver img, String layout, String master) {
        for (XNode c : tree.children) {
            if (c.type != XNode.ELEMENT) continue;
            if (c.is(Xml.MC, "AlternateContent")) {
                XNode fb = c.child(Xml.MC, "Fallback");
                XNode ch = c.child(Xml.MC, "Choice");
                walkPptx(s, fb != null ? fb : ch, t, img, layout, master);
                continue;
            }
            if (!pns.equals(c.ns)) continue;
            String l = c.local;
            if (l.equals("grpSp")) {
                XNode gp = c.child(pns, "grpSpPr");
                XNode xf = gp == null ? null : gp.child(ans, "xfrm");
                float[] nt = t;
                if (xf != null) {
                    float[] off = pair(xf.child(ans, "off"), "x", "y"), ext = pair(xf.child(ans, "ext"), "cx", "cy");
                    float[] cho = pair(xf.child(ans, "chOff"), "x", "y"), che = pair(xf.child(ans, "chExt"), "cx", "cy");
                    if (off != null && ext != null && cho != null && che != null && che[0] > 0 && che[1] > 0) {
                        float sx = ext[0] / che[0], sy = ext[1] / che[1];
                        float ox = off[0] - cho[0] * sx, oy = off[1] - cho[1] * sy;
                        nt = new float[]{t[0] + ox * t[2], t[1] + oy * t[3], t[2] * sx, t[3] * sy};
                    }
                }
                walkPptx(s, c, nt, img, layout, master);
                continue;
            }
            if (!l.equals("sp") && !l.equals("pic") && !l.equals("graphicFrame") && !l.equals("cxnSp")) continue;
            if (l.equals("cxnSp")) continue;
            XNode xfrm;
            if (l.equals("graphicFrame")) xfrm = c.child(pns, "xfrm");
            else {
                XNode spPr = c.child(pns, "spPr");
                xfrm = spPr == null ? null : spPr.child(ans, "xfrm");
            }
            XNode ph = placeholder(c);
            if (xfrm == null && ph != null) xfrm = inheritedXfrm(ph, layout, master);
            float[] off = xfrm == null ? null : pair(xfrm.child(ans, "off"), "x", "y");
            float[] ext = xfrm == null ? null : pair(xfrm.child(ans, "ext"), "cx", "cy");
            Shape sh = new Shape();
            if (off != null && ext != null) {
                sh.x = t[0] + off[0] * t[2];
                sh.y = t[1] + off[1] * t[3];
                sh.w = ext[0] * t[2];
                sh.h = ext[1] * t[3];
            } else {
                sh.x = width * 0.05f;
                sh.y = height * 0.05f + s.shapes.size() * 40;
                sh.w = width * 0.9f;
                sh.h = 60;
            }
            String phType = ph == null ? null : ph.attr(null, "type");
            sh.title = "title".equals(phType) || "ctrTitle".equals(phType);
            sh.baseSize = sh.title ? 44 : ("subTitle".equals(phType) ? 32 : (ph != null ? 28 : 18));
            if (l.equals("sp")) {
                XNode spPr = c.child(pns, "spPr");
                XNode fill = spPr == null ? null : spPr.child(ans, "solidFill");
                XNode srgb = fill == null ? null : fill.child(ans, "srgbClr");
                if (srgb != null) sh.fill = color(srgb.attr(null, "val"));
                XNode body = c.child(pns, "txBody");
                if (body != null) {
                    XNode bodyPr = body.child(ans, "bodyPr");
                    XNode fit = bodyPr == null ? null : bodyPr.child(ans, "normAutofit");
                    if (fit != null && fit.attr(null, "fontScale") != null) {
                        try {
                            sh.baseSize *= Integer.parseInt(fit.attr(null, "fontScale")) / 100000f;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    ArrayList<XNode> paras = body.childrenNamed(ans, "p");
                    if (!paras.isEmpty()) {
                        TextSegment seg = new TextSegment(new DmlDialect(s.doc.src, ans, sh.baseSize));
                        seg.requireParagraphIn(body);
                        seg.load(paras);
                        sh.text = seg;
                    }
                }
                if (sh.text == null && sh.fill == 0) continue;
            } else if (l.equals("pic")) {
                XNode blip = c.find(ans, "blip");
                String rid = blip == null ? null : blip.attr(Xml.R, "embed");
                if (rid == null && blip != null) rid = blip.attr(Xml.R_STRICT, "embed");
                sh.image = rid == null ? null : img.load(rid, (int) (sh.w * 1.5f), (int) (sh.h * 1.5f));
                if (sh.image == null) sh.label = "[image]";
            } else {
                sh.label = c.find(ans, "tbl") != null ? "[table]" : "[chart]";
            }
            s.shapes.add(sh);
        }
    }

    private XNode placeholder(XNode shape) {
        for (String nv : new String[]{"nvSpPr", "nvPicPr", "nvGraphicFramePr"}) {
            XNode n = shape.child(pns, nv);
            if (n == null) continue;
            XNode nvPr = n.child(pns, "nvPr");
            if (nvPr == null) continue;
            return nvPr.child(pns, "ph");
        }
        return null;
    }

    private XNode inheritedXfrm(XNode ph, String layout, String master) {
        String idx = ph.attr(null, "idx"), type = ph.attr(null, "type");
        for (String part : new String[]{layout, master}) {
            if (part == null) continue;
            try {
                XNode cSld = pkg.xml(part).root.child(pns, "cSld");
                XNode tree = cSld == null ? null : cSld.child(pns, "spTree");
                if (tree == null) continue;
                XNode best = null;
                for (XNode sp : tree.childrenNamed(pns, "sp")) {
                    XNode p2 = placeholder(sp);
                    if (p2 == null) continue;
                    String i2 = p2.attr(null, "idx"), t2 = p2.attr(null, "type");
                    boolean match = (idx != null && idx.equals(i2) && part.equals(layout))
                            || (normType(type).equals(normType(t2)));
                    if (!match) continue;
                    XNode spPr = sp.child(pns, "spPr");
                    XNode x = spPr == null ? null : spPr.child(ans, "xfrm");
                    if (x != null) {
                        best = x;
                        break;
                    }
                }
                if (best != null) return best;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String normType(String t) {
        if (t == null || t.equals("obj") || t.equals("subTitle")) return "body";
        if (t.equals("ctrTitle")) return "title";
        return t;
    }

    private static float[] pair(XNode n, String a, String b) {
        if (n == null) return null;
        try {
            return new float[]{Long.parseLong(n.attr(null, a)) / 12700f, Long.parseLong(n.attr(null, b)) / 12700f};
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ ODP

    public static SlidesDoc openOdp(OfficePackage pkg, Images images) throws Exception {
        SlidesDoc d = new SlidesDoc(ODP);
        d.pkg = pkg;
        d.odpContent = pkg.xml("content.xml");
        XNode root = d.odpContent.root;
        XNode body = root.child(Xml.OFFICE, "body");
        XNode pres = body == null ? null : body.child(Xml.OFFICE, "presentation");
        if (pres == null) throw new IOException("Not a presentation");
        XDoc styles = pkg.xmlOrNull("styles.xml");
        OdfStyles st = new OdfStyles(styles, d.odpContent);
        ImageResolver img = images.forPart(pkg, "content.xml", null);
        boolean sized = false;
        for (XNode page : pres.childrenNamed(Xml.DRAW, "page")) {
            if (!sized) {
                d.pageSize(styles, page.attr(Xml.DRAW, "master-page-name"));
                sized = true;
            }
            Slide s = new Slide();
            s.doc = d.odpContent;
            d.walkOdp(s, page, st, img, 0);
            d.slides.add(s);
        }
        d.markLoaded();
        return d;
    }

    private void pageSize(XDoc styles, String master) {
        if (styles == null || master == null) return;
        XNode ms = styles.root.child(Xml.OFFICE, "master-styles");
        if (ms == null) return;
        String layout = null;
        for (XNode m : ms.childrenNamed(Xml.STYLE, "master-page")) {
            if (master.equals(m.attr(Xml.STYLE, "name"))) layout = m.attr(Xml.STYLE, "page-layout-name");
        }
        XNode auto = styles.root.child(Xml.OFFICE, "automatic-styles");
        if (layout == null || auto == null) return;
        for (XNode pl : auto.childrenNamed(Xml.STYLE, "page-layout")) {
            if (!layout.equals(pl.attr(Xml.STYLE, "name"))) continue;
            XNode pp = pl.child(Xml.STYLE, "page-layout-properties");
            if (pp == null) return;
            float w = Units.toPt(pp.attr(Xml.FO, "page-width")), h = Units.toPt(pp.attr(Xml.FO, "page-height"));
            if (w > 0 && h > 0) {
                width = w;
                height = h;
            }
        }
    }

    private void walkOdp(Slide s, XNode parent, OdfStyles st, ImageResolver img, int depth) {
        for (XNode c : parent.children) {
            if (c.type != XNode.ELEMENT || !Xml.DRAW.equals(c.ns)) continue;
            String l = c.local;
            if (l.equals("g") && depth < 8) {
                walkOdp(s, c, st, img, depth + 1);
                continue;
            }
            if (l.equals("line") || l.equals("connector")) continue;
            Shape sh = new Shape();
            sh.x = Units.toPt(c.attr(Xml.SVG, "x"));
            sh.y = Units.toPt(c.attr(Xml.SVG, "y"));
            sh.w = Units.toPt(c.attr(Xml.SVG, "width"));
            sh.h = Units.toPt(c.attr(Xml.SVG, "height"));
            String cls = c.attr(Xml.PRESENTATION, "class");
            sh.title = "title".equals(cls);
            sh.baseSize = sh.title ? 40 : ("subtitle".equals(cls) ? 32 : ("outline".equals(cls) ? 24 : 18));
            XNode textHolder = c;
            if (l.equals("frame")) {
                XNode tb = c.child(Xml.DRAW, "text-box");
                XNode image = c.child(Xml.DRAW, "image");
                if (tb != null) textHolder = tb;
                else if (image != null) {
                    String href = image.attr(Xml.XLINK, "href");
                    sh.image = img.load(href, (int) (sh.w * 1.5f), (int) (sh.h * 1.5f));
                    if (sh.image == null) sh.label = "[image]";
                    s.shapes.add(sh);
                    continue;
                } else {
                    sh.label = "[object]";
                    s.shapes.add(sh);
                    continue;
                }
            }
            ArrayList<XNode> paras = new ArrayList<XNode>();
            collectOdpParas(textHolder, paras);
            if (paras.isEmpty()) continue; // empty placeholders and plain shapes aren't shown
            OdfDialect dialect = new OdfDialect(odpContent.src, odpContent.root, st, img);
            dialect.setBaseSize(sh.baseSize);
            TextSegment seg = new TextSegment(dialect);
            seg.load(paras);
            sh.text = seg;
            s.shapes.add(sh);
        }
    }

    private static void collectOdpParas(XNode n, ArrayList<XNode> out) {
        for (XNode c : n.children) {
            if (c.is(Xml.TEXT, "p") || c.is(Xml.TEXT, "h")) out.add(c);
            else if (c.is(Xml.TEXT, "list") || c.is(Xml.TEXT, "list-item") || c.is(Xml.TEXT, "list-header")) collectOdpParas(c, out);
        }
    }

    // ------------------------------------------------------------------ state + saving

    @Override
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        for (Slide s : slides) {
            for (Shape sh : s.shapes) {
                if (sh.text != null) sb.append(sh.text.modCount).append(',');
            }
        }
        return sb.toString();
    }

    @Override
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slides.size(); i++) {
            for (Shape sh : slides.get(i).shapes) {
                if (sh.text != null) sb.append(sh.text.plainText()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** All shape text in order, one line per paragraph (object characters ignored). */
    static String allText(ArrayList<Slide> slides) {
        StringBuilder sb = new StringBuilder();
        for (Slide s : slides) {
            for (Shape sh : s.shapes) if (sh.text != null) sb.append(sh.text.text.toString()).append('\n');
        }
        return sb.toString().replace(String.valueOf((char) 0xFFFC), "");
    }

    @Override
    public SaveJob prepareSave() {
        final String fp = fingerprint();
        final ArrayList<Slide> snapSlides = new ArrayList<Slide>(slides);
        final HashMap<Shape, ArrayList<Line>> lines = new HashMap<Shape, ArrayList<Line>>();
        for (Slide s : slides) {
            for (Shape sh : s.shapes) if (sh.text != null) lines.put(sh, sh.text.extractLines());
        }
        final String expected = allText(slides);
        return new SaveJob() {
            final LinkedHashMap<String, byte[]> replaced = new LinkedHashMap<String, byte[]>();

            @Override
            public String fingerprint() {
                return fp;
            }

            @Override
            public void write(File out) throws Exception {
                if (format == PPTX) {
                    for (Slide s : snapSlides) {
                        Patcher p = new Patcher(s.doc.src);
                        EmitContext ctx = new EmitContext();
                        for (Shape sh : s.shapes) if (sh.text != null) sh.text.emitPatches(lines.get(sh), p, ctx);
                        if (!p.isEmpty()) {
                            String res = p.apply();
                            if (!res.equals(s.doc.src)) replaced.put(s.part, XDoc.encode(res));
                        }
                    }
                } else {
                    Patcher p = new Patcher(odpContent.src);
                    EmitContext ctx = new EmitContext();
                    for (Slide s : snapSlides)
                        for (Shape sh : s.shapes) if (sh.text != null) sh.text.emitPatches(lines.get(sh), p, ctx);
                    if (!ctx.newStyles.isEmpty()) TextDoc.addOdfStyles(odpContent, p, ctx);
                    replaced.put("content.xml", XDoc.encode(p.apply()));
                }
                PackageWriter.write(pkg, replaced, null, out);
            }

            @Override
            public void verify(File written) throws Exception {
                ArrayList<String> req = new ArrayList<String>();
                if (format == PPTX) {
                    req.add(presPart);
                    req.add("[Content_Types].xml");
                } else {
                    req.add("content.xml");
                    if (pkg.has("mimetype")) req.add("mimetype");
                }
                PackageVerifier.verify(written, req, new ArrayList<String>(replaced.keySet()));
                OfficePackage again = OfficePackage.open(written);
                try {
                    SlidesDoc d = format == PPTX ? openPptx(again, presPart, Images.NONE) : openOdp(again, Images.NONE);
                    if (!allText(d.slides).equals(expected)) {
                        throw new IOException("Verification failed: slide text reads back differently");
                    }
                } finally {
                    again.close();
                }
            }
        };
    }

    @Override
    public void close() {
        if (pkg != null) pkg.close();
    }
}
