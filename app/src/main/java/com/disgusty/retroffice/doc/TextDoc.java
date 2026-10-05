package com.disgusty.retroffice.doc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import com.disgusty.retroffice.pkg.OfficePackage;
import com.disgusty.retroffice.pkg.PackageVerifier;
import com.disgusty.retroffice.pkg.PackageWriter;
import com.disgusty.retroffice.text.DocxDialect;
import com.disgusty.retroffice.text.DocxStyles;
import com.disgusty.retroffice.text.EmitContext;
import com.disgusty.retroffice.text.ImageResolver;
import com.disgusty.retroffice.text.Line;
import com.disgusty.retroffice.text.OdfDialect;
import com.disgusty.retroffice.text.OdfStyles;
import com.disgusty.retroffice.text.TextDialect;
import com.disgusty.retroffice.text.TextSegment;
import com.disgusty.retroffice.xml.Patcher;
import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XNode;

/** A word-processing document (DOCX or ODT): editable paragraph segments between kept blocks. */
public final class TextDoc extends Doc {
    public static final int DOCX = 1, ODT = 2;

    /** One item of the document body as shown in the editor. */
    public static final class Block {
        public final TextSegment segment;   // editable paragraphs, or null
        public final XNode node;            // kept element (table, index, ...), or null
        public final String label;
        /** For tables: rows of cell texts, for display only. */
        public final List<List<String>> table;
        public final String preview;

        Block(TextSegment s) {
            segment = s;
            node = null;
            label = null;
            table = null;
            preview = null;
        }

        Block(XNode n, String label, List<List<String>> table, String preview) {
            segment = null;
            node = n;
            this.label = label;
            this.table = table;
            this.preview = preview;
        }
    }

    public final int format;
    private OfficePackage pkg;
    private String mainPart;
    private XDoc xdoc;
    private TextDialect dialect;
    public final ArrayList<Block> blocks = new ArrayList<Block>();
    private String wns;

    private TextDoc(int format) {
        this.format = format;
    }

    @Override
    public int kind() {
        return TEXT;
    }

    @Override
    public String formatName() {
        return format == DOCX ? "DOCX" : "ODT";
    }

    public static TextDoc openDocx(OfficePackage pkg, String mainPart, Images images) throws Exception {
        TextDoc d = new TextDoc(DOCX);
        d.pkg = pkg;
        d.mainPart = mainPart;
        d.xdoc = pkg.xml(mainPart);
        XNode root = d.xdoc.root;
        d.wns = Xml.W.equals(root.ns) ? Xml.W : Xml.W_STRICT.equals(root.ns) ? Xml.W_STRICT : null;
        if (d.wns == null || !"document".equals(root.local)) throw new IOException("Not a Word document");
        XNode body = root.child(d.wns, "body");
        if (body == null) throw new IOException("Document has no body");
        String stylesPart = Rels.byType(pkg, mainPart, "/styles");
        DocxStyles styles = new DocxStyles(d.wns, stylesPart == null ? null : pkg.xmlOrNull(stylesPart));
        ImageResolver img = images.forPart(pkg, mainPart, Rels.targets(pkg, mainPart));
        d.dialect = new DocxDialect(d.xdoc.src, d.wns, root.prefix, styles, img);
        d.walkDocx(body);
        d.finishBlocks();
        d.markLoaded();
        return d;
    }

    public static TextDoc openOdt(OfficePackage pkg, Images images) throws Exception {
        TextDoc d = new TextDoc(ODT);
        d.pkg = pkg;
        d.mainPart = "content.xml";
        d.xdoc = pkg.xml("content.xml");
        XNode root = d.xdoc.root;
        XNode body = root.child(Xml.OFFICE, "body");
        XNode text = body == null ? null : body.child(Xml.OFFICE, "text");
        if (text == null) throw new IOException("Not a text document");
        OdfStyles styles = new OdfStyles(pkg.xmlOrNull("styles.xml"), d.xdoc);
        d.dialect = new OdfDialect(d.xdoc.src, root, styles, images.forPart(pkg, "content.xml", null));
        d.walkOdt(text);
        d.finishBlocks();
        d.markLoaded();
        return d;
    }

    // Body walking -------------------------------------------------------------

    private final ArrayList<XNode> pending = new ArrayList<XNode>();
    private final ArrayList<XNode> keepParents = new ArrayList<XNode>();

    /**
     * Paragraphs per editable field. Long documents are split into several fields so that typing
     * re-lays out a small text instead of the whole document (which crawls on old phones).
     */
    static final int SEGMENT_PARAGRAPHS = 40;

    private void flush() {
        if (pending.isEmpty()) return;
        for (int from = 0; from < pending.size(); from += SEGMENT_PARAGRAPHS) {
            int to = Math.min(pending.size(), from + SEGMENT_PARAGRAPHS);
            TextSegment s = new TextSegment(dialect);
            for (XNode k : keepParents) s.requireParagraphIn(k);
            s.load(new ArrayList<XNode>(pending.subList(from, to)));
            blocks.add(new Block(s));
        }
        pending.clear();
    }

    /** All editable text in document order, one line per paragraph (object characters ignored). */
    static String allText(List<Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (b.segment == null) continue;
            sb.append(b.segment.text.toString()).append('\n');
        }
        return sb.toString().replace(String.valueOf((char) 0xFFFC), "");
    }

    private void opaque(XNode n, String label, List<List<String>> table) {
        flush();
        String preview = table == null ? n.textContent().trim() : null;
        if (preview != null && preview.length() > 2000) preview = preview.substring(0, 2000) + "…";
        blocks.add(new Block(n, label, table, preview));
    }

    private void finishBlocks() {
        flush();
        if (blocks.isEmpty() || blocks.get(blocks.size() - 1).segment == null) {
            // Nothing editable at the end (or at all): there's no paragraph to type into.
            // Leave as is; the editor shows the kept blocks read-only.
        }
    }

    private static final HashSet<String> DOCX_HIDDEN = new HashSet<String>(Arrays.asList("sectPr",
            "bookmarkStart", "bookmarkEnd", "proofErr", "permStart", "permEnd", "commentRangeStart",
            "commentRangeEnd", "moveFromRangeStart", "moveFromRangeEnd", "moveToRangeStart", "moveToRangeEnd",
            "customXmlInsRangeStart", "customXmlInsRangeEnd", "customXmlDelRangeStart", "customXmlDelRangeEnd",
            "del", "moveFrom", "sdtPr", "sdtEndPr"));

    private void walkDocx(XNode container) {
        for (XNode c : container.children) {
            if (c.type != XNode.ELEMENT) continue;
            boolean isW = wns.equals(c.ns);
            String l = c.local;
            if (isW && l.equals("p")) {
                pending.add(c);
            } else if (isW && l.equals("sdt")) {
                XNode content = c.child(wns, "sdtContent");
                if (content != null) {
                    keepParents.add(content);
                    walkDocx(content);
                }
            } else if (isW && (l.equals("customXml") || l.equals("ins") || l.equals("moveTo"))) {
                walkDocx(c);
            } else if (isW && l.equals("tbl")) {
                opaque(c, "table", docxTable(c));
            } else if (isW && DOCX_HIDDEN.contains(l)) {
                // not shown, never touched
            } else {
                opaque(c, l, null);
            }
        }
    }

    private List<List<String>> docxTable(XNode tbl) {
        ArrayList<List<String>> rows = new ArrayList<List<String>>();
        for (XNode tr : tbl.childrenNamed(wns, "tr")) {
            ArrayList<String> row = new ArrayList<String>();
            for (XNode tc : tr.childrenNamed(wns, "tc")) {
                StringBuilder sb = new StringBuilder();
                for (XNode p : tc.childrenNamed(wns, "p")) {
                    if (sb.length() > 0) sb.append('\n');
                    ArrayList<XNode> ts = new ArrayList<XNode>();
                    p.findAll(wns, "t", ts);
                    for (XNode t : ts) sb.append(t.textContent());
                }
                XNode inner = tc.child(wns, "tbl");
                if (inner != null) sb.append(sb.length() > 0 ? "\n" : "").append("[table]");
                row.add(sb.toString());
            }
            rows.add(row);
            if (rows.size() >= 500) break;
        }
        return rows;
    }

    private static final HashSet<String> ODT_HIDDEN = new HashSet<String>(Arrays.asList("sequence-decls",
            "variable-decls", "user-field-decls", "dde-connection-decls", "tracked-changes", "soft-page-break",
            "bookmark", "bookmark-start", "bookmark-end", "number"));

    private void walkOdt(XNode container) {
        for (XNode c : container.children) {
            if (c.type != XNode.ELEMENT) continue;
            String l = c.local;
            boolean isText = Xml.TEXT.equals(c.ns);
            if (isText && (l.equals("p") || l.equals("h"))) {
                pending.add(c);
            } else if (isText && (l.equals("list") || l.equals("list-item") || l.equals("list-header") || l.equals("section"))) {
                walkOdt(c);
            } else if (c.is(Xml.TABLE, "table")) {
                opaque(c, "table", odtTable(c));
            } else if ((isText && ODT_HIDDEN.contains(l)) || c.is(Xml.OFFICE, "forms")) {
                // not shown, never touched
            } else {
                opaque(c, l, null);
            }
        }
    }

    private List<List<String>> odtTable(XNode table) {
        ArrayList<List<String>> rows = new ArrayList<List<String>>();
        collectOdtRows(table, rows);
        return rows;
    }

    private void collectOdtRows(XNode n, ArrayList<List<String>> rows) {
        for (XNode c : n.children) {
            if (rows.size() >= 500) return;
            if (c.is(Xml.TABLE, "table-row")) {
                ArrayList<String> row = new ArrayList<String>();
                for (XNode cell : c.children) {
                    if (!cell.is(Xml.TABLE, "table-cell") && !cell.is(Xml.TABLE, "covered-table-cell")) continue;
                    int rep = 1;
                    String r = cell.attr(Xml.TABLE, "number-columns-repeated");
                    if (r != null) {
                        try {
                            rep = Math.min(50, Integer.parseInt(r));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    for (XNode p : cell.children) {
                        if (p.is(Xml.TEXT, "p") || p.is(Xml.TEXT, "h")) {
                            if (sb.length() > 0) sb.append('\n');
                            sb.append(p.textContent());
                        }
                    }
                    for (int i = 0; i < rep; i++) row.add(sb.toString());
                }
                // Trim trailing empty cells produced by repeated empty columns.
                while (!row.isEmpty() && row.get(row.size() - 1).length() == 0) row.remove(row.size() - 1);
                rows.add(row);
            } else if (c.is(Xml.TABLE, "table-header-rows") || c.is(Xml.TABLE, "table-rows") || c.is(Xml.TABLE, "table-row-group")) {
                collectOdtRows(c, rows);
            }
        }
        while (!rows.isEmpty() && rows.get(rows.size() - 1).isEmpty()) rows.remove(rows.size() - 1);
    }

    // State ---------------------------------------------------------------------

    @Override
    public String fingerprint() {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) if (b.segment != null) sb.append(b.segment.modCount).append(',');
        return sb.toString();
    }

    @Override
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        for (Block b : blocks) {
            if (sb.length() > 0) sb.append('\n');
            if (b.segment != null) sb.append(b.segment.plainText());
            else if (b.preview != null) sb.append(b.preview);
            else if (b.table != null) {
                for (List<String> r : b.table) {
                    for (int i = 0; i < r.size(); i++) {
                        if (i > 0) sb.append('\t');
                        sb.append(r.get(i));
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    // Saving ---------------------------------------------------------------------

    @Override
    public SaveJob prepareSave() {
        final String fp = fingerprint();
        final ArrayList<TextSegment> segs = new ArrayList<TextSegment>();
        final ArrayList<ArrayList<Line>> lines = new ArrayList<ArrayList<Line>>();
        for (Block b : blocks) {
            if (b.segment == null) continue;
            segs.add(b.segment);
            lines.add(b.segment.extractLines());
        }
        final String expected = allText(blocks);
        final OfficePackage source = pkg;
        final String part = mainPart;
        final XDoc doc = xdoc;
        final int fmt = format;
        return new SaveJob() {
            @Override
            public String fingerprint() {
                return fp;
            }

            @Override
            public void write(File out) throws Exception {
                Patcher p = new Patcher(doc.src);
                EmitContext ctx = new EmitContext();
                for (int i = 0; i < segs.size(); i++) segs.get(i).emitPatches(lines.get(i), p, ctx);
                if (fmt == ODT && !ctx.newStyles.isEmpty()) addOdfStyles(doc, p, ctx);
                String result = p.apply();
                LinkedHashMap<String, byte[]> replaced = new LinkedHashMap<String, byte[]>();
                replaced.put(part, XDoc.encode(result));
                PackageWriter.write(source, replaced, null, out);
            }

            @Override
            public void verify(File written) throws Exception {
                ArrayList<String> req = new ArrayList<String>();
                req.add(part);
                if (source.has("mimetype")) req.add("mimetype");
                if (source.has("[Content_Types].xml")) req.add("[Content_Types].xml");
                PackageVerifier.verify(written, req, java.util.Collections.singletonList(part));
                OfficePackage again = OfficePackage.open(written);
                try {
                    TextDoc d = fmt == DOCX ? openDocx(again, part, Images.NONE) : openOdt(again, Images.NONE);
                    if (!allText(d.blocks).equals(expected)) {
                        throw new IOException("Verification failed: text read back differs from what was written");
                    }
                } finally {
                    again.close();
                }
            }
        };
    }

    public static void addOdfStyles(XDoc doc, Patcher p, EmitContext ctx) {
        StringBuilder styles = new StringBuilder();
        for (String s : ctx.newStyles.values()) styles.append(s);
        XNode root = doc.root;
        XNode auto = root.child(Xml.OFFICE, "automatic-styles");
        if (auto != null && !auto.selfClosing) {
            p.insert(auto.endTagStart, styles.toString());
        } else if (auto != null) {
            p.replace(auto, Xml.startTag(auto, null, false) + styles + "</" + auto.qname + ">");
        } else {
            XNode body = root.child(Xml.OFFICE, "body");
            String op = root.prefixFor(Xml.OFFICE);
            String q = Xml.q(op, "automatic-styles");
            p.insert(body.start, "<" + q + ">" + styles + "</" + q + ">");
        }
    }

    @Override
    public void close() {
        if (pkg != null) pkg.close();
    }
}
