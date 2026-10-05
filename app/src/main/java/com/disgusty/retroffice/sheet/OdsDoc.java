package com.disgusty.retroffice.sheet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.disgusty.retroffice.pkg.OfficePackage;
import com.disgusty.retroffice.pkg.PackageVerifier;
import com.disgusty.retroffice.pkg.PackageWriter;
import com.disgusty.retroffice.text.OdfText;
import com.disgusty.retroffice.xml.Patcher;
import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XNode;

/** OpenDocument spreadsheet (.ods). Repeated rows/cells are split only where a cell changed. */
public final class OdsDoc extends SheetDoc {
    /** Rows/columns materialized from a repeated element (more is treated as empty). */
    private static final int MAX_ROW_EXPAND = 2000, MAX_COL_EXPAND = 512;

    private OfficePackage pkg;
    private XDoc content;
    private String tp, op, xp, cp; // table, office, text, calcext prefixes (cp may be null)

    static final class RowRec {
        XNode node;
        int r0, repeat;
        final ArrayList<CellRec> cells = new ArrayList<CellRec>();
    }

    static final class CellRec {
        XNode node;
        int c0, repeat;
        boolean covered;
    }

    static final class OS {
        XNode table;
        final ArrayList<RowRec> rows = new ArrayList<RowRec>();
    }

    static final class OC {
        RowRec row;
        CellRec cell;
        String valueType, currency;
    }

    @Override
    public String formatName() {
        return "ODS";
    }

    public static OdsDoc open(OfficePackage pkg) throws Exception {
        OdsDoc d = new OdsDoc();
        d.pkg = pkg;
        d.content = pkg.xml("content.xml");
        XNode root = d.content.root;
        XNode body = root.child(Xml.OFFICE, "body");
        XNode ss = body == null ? null : body.child(Xml.OFFICE, "spreadsheet");
        if (ss == null) throw new IOException("Not a spreadsheet");
        d.tp = root.prefixFor(Xml.TABLE);
        d.op = root.prefixFor(Xml.OFFICE);
        d.xp = root.prefixFor(Xml.TEXT);
        d.cp = root.prefixFor(Xml.CALCEXT);
        if (d.tp == null) d.tp = "table";
        if (d.xp == null) d.xp = "text";
        for (XNode t : ss.childrenNamed(Xml.TABLE, "table")) {
            String name = t.attr(Xml.TABLE, "name");
            Sheet sheet = new Sheet(name == null ? "Sheet" + (d.sheets.size() + 1) : name);
            d.readTable(sheet, t);
            d.sheets.add(sheet);
        }
        if (d.sheets.isEmpty()) throw new IOException("Spreadsheet has no sheets");
        d.markLoaded();
        return d;
    }

    private static int intAttr(XNode n, String local, int def) {
        String v = n.attr(Xml.TABLE, local);
        if (v == null) return def;
        try {
            int i = Integer.parseInt(v.trim());
            return i > 0 ? i : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void readTable(Sheet sheet, XNode table) {
        OS os = new OS();
        os.table = table;
        sheet.src = os;
        int[] rowCounter = {0};
        readRows(sheet, os, table, rowCounter);
    }

    private void readRows(Sheet sheet, OS os, XNode parent, int[] rowCounter) {
        for (XNode c : parent.children) {
            if (c.is(Xml.TABLE, "table-row")) {
                RowRec rr = new RowRec();
                rr.node = c;
                rr.r0 = rowCounter[0];
                rr.repeat = intAttr(c, "number-rows-repeated", 1);
                rowCounter[0] += rr.repeat;
                os.rows.add(rr);
                int colIdx = 0;
                for (XNode cell : c.children) {
                    boolean covered = cell.is(Xml.TABLE, "covered-table-cell");
                    if (!covered && !cell.is(Xml.TABLE, "table-cell")) continue;
                    CellRec cr = new CellRec();
                    cr.node = cell;
                    cr.c0 = colIdx;
                    cr.repeat = intAttr(cell, "number-columns-repeated", 1);
                    cr.covered = covered;
                    colIdx += cr.repeat;
                    rr.cells.add(cr);
                    materialize(sheet, rr, cr);
                }
            } else if (c.is(Xml.TABLE, "table-header-rows") || c.is(Xml.TABLE, "table-rows")
                    || c.is(Xml.TABLE, "table-row-group")) {
                readRows(sheet, os, c, rowCounter);
            }
        }
    }

    private void materialize(Sheet sheet, RowRec rr, CellRec cr) {
        XNode n = cr.node;
        String vt = n.attr(Xml.OFFICE, "value-type");
        String formula = n.attr(Xml.TABLE, "formula");
        StringBuilder text = new StringBuilder();
        boolean hasText = false;
        for (XNode p : n.children) {
            if (p.is(Xml.TEXT, "p") || p.is(Xml.TEXT, "h")) {
                if (hasText) text.append('\n');
                text.append(odfParagraphText(p));
                hasText = true;
            }
        }
        boolean empty = vt == null && formula == null && (!hasText || text.length() == 0);
        if (empty && !cr.covered) return;
        int rows = Math.min(rr.repeat, MAX_ROW_EXPAND), cols = Math.min(cr.repeat, MAX_COL_EXPAND);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = new Cell();
                OC oc = new OC();
                oc.row = rr;
                oc.cell = cr;
                oc.valueType = vt;
                oc.currency = n.attr(Xml.OFFICE, "currency");
                cell.src = oc;
                cell.covered = cr.covered;
                if (!empty) fill(cell, n, vt, formula, text.toString());
                sheet.put(rr.r0 + r, cr.c0 + c, cell);
            }
        }
    }

    private void fill(Cell cell, XNode n, String vt, String formula, String text) {
        if (formula != null) {
            try {
                cell.formula = Formula.fromOpenFormula(formula);
            } catch (Formula.Unsupported e) {
                cell.formula = null;
            }
        }
        String calcType = n.attr(Xml.CALCEXT, "value-type");
        if ("error".equals(calcType)) {
            cell.type = Cell.ERROR;
            cell.str = text;
        } else if (vt == null) {
            if (text.length() > 0) {
                cell.type = Cell.STRING;
                cell.str = text;
            }
        } else if (vt.equals("float") || vt.equals("percentage") || vt.equals("currency")) {
            try {
                cell.type = Cell.NUMBER;
                cell.num = Double.parseDouble(n.attr(Xml.OFFICE, "value"));
            } catch (Exception e) {
                cell.type = Cell.STRING;
                cell.str = text;
            }
        } else if (vt.equals("boolean")) {
            cell.type = Cell.BOOL;
            cell.bool = "true".equals(n.attr(Xml.OFFICE, "boolean-value"));
        } else if (vt.equals("string")) {
            cell.type = Cell.STRING;
            String sv = n.attr(Xml.OFFICE, "string-value");
            cell.str = sv != null ? sv : text;
        } else {
            // date, time: keep as text (its display form)
            cell.type = Cell.STRING;
            cell.str = text;
        }
        cell.display = text.length() > 0 ? text : null;
        if (cell.display == null) refreshDisplay(cell);
    }

    /** Text of an ODF paragraph with whitespace rules applied (spaces collapse, text:s expands). */
    static String odfParagraphText(XNode p) {
        StringBuilder sb = new StringBuilder();
        boolean[] lastSpace = {true};
        appendOdf(p, sb, lastSpace);
        return sb.toString();
    }

    private static void appendOdf(XNode n, StringBuilder sb, boolean[] lastSpace) {
        for (XNode c : n.children) {
            if (c.type == XNode.TEXT || c.type == XNode.CDATA) {
                for (int i = 0; i < c.text.length(); i++) {
                    char ch = c.text.charAt(i);
                    if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r') {
                        if (!lastSpace[0]) {
                            sb.append(' ');
                            lastSpace[0] = true;
                        }
                    } else {
                        sb.append(ch);
                        lastSpace[0] = false;
                    }
                }
            } else if (c.is(Xml.TEXT, "s")) {
                int k = 1;
                try {
                    String v = c.attr(Xml.TEXT, "c");
                    if (v != null) k = Math.max(1, Math.min(1000, Integer.parseInt(v)));
                } catch (NumberFormatException ignored) {
                }
                for (int i = 0; i < k; i++) sb.append(' ');
                lastSpace[0] = true;
            } else if (c.is(Xml.TEXT, "tab")) {
                sb.append('\t');
                lastSpace[0] = true;
            } else if (c.is(Xml.TEXT, "line-break")) {
                sb.append('\n');
                lastSpace[0] = true;
            } else if (c.is(Xml.OFFICE, "annotation") || c.is(Xml.TEXT, "note")) {
                // not part of the cell text
            } else if (c.type == XNode.ELEMENT) {
                appendOdf(c, sb, lastSpace);
            }
        }
    }

    @Override
    protected boolean canStoreFormula(String formula) {
        try {
            Formula.toOpenFormula(formula);
            return true;
        } catch (Formula.Unsupported e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    public SaveJob prepareSave() {
        final String fp = fingerprint();
        final HashMap<Integer, ArrayList<EditedCell>> edits = editedCells();
        final ArrayList<OS> srcs = new ArrayList<OS>();
        for (Sheet s : sheets) srcs.add((OS) s.src);
        return new SaveJob() {
            @Override
            public String fingerprint() {
                return fp;
            }

            @Override
            public void write(File out) throws Exception {
                Patcher p = new Patcher(content.src);
                for (Map.Entry<Integer, ArrayList<EditedCell>> en : edits.entrySet()) {
                    patchTable(p, srcs.get(en.getKey()), en.getValue());
                }
                LinkedHashMap<String, byte[]> replaced = new LinkedHashMap<String, byte[]>();
                replaced.put("content.xml", XDoc.encode(p.apply()));
                PackageWriter.write(pkg, replaced, null, out);
            }

            @Override
            public void verify(File written) throws Exception {
                ArrayList<String> req = new ArrayList<String>(Arrays.asList("content.xml"));
                if (pkg.has("mimetype")) req.add("mimetype");
                PackageVerifier.verify(written, req, Arrays.asList("content.xml"));
                OfficePackage again = OfficePackage.open(written);
                try {
                    XlsxDoc.verifyCells(open(again), edits);
                } finally {
                    again.close();
                }
            }
        };
    }

    private void patchTable(Patcher p, OS os, ArrayList<EditedCell> list) {
        // Group edits: row record -> row -> edits
        LinkedHashMap<RowRec, TreeMap<Integer, ArrayList<EditedCell>>> byRec =
                new LinkedHashMap<RowRec, TreeMap<Integer, ArrayList<EditedCell>>>();
        TreeMap<Integer, ArrayList<EditedCell>> tail = new TreeMap<Integer, ArrayList<EditedCell>>();
        for (EditedCell e : list) {
            RowRec rec = null;
            for (RowRec rr : os.rows) {
                if (e.row >= rr.r0 && e.row < rr.r0 + rr.repeat) {
                    rec = rr;
                    break;
                }
            }
            TreeMap<Integer, ArrayList<EditedCell>> m;
            if (rec == null) m = tail;
            else {
                m = byRec.get(rec);
                if (m == null) {
                    m = new TreeMap<Integer, ArrayList<EditedCell>>();
                    byRec.put(rec, m);
                }
            }
            ArrayList<EditedCell> l = m.get(e.row);
            if (l == null) {
                l = new ArrayList<EditedCell>();
                m.put(e.row, l);
            }
            l.add(e);
        }
        for (Map.Entry<RowRec, TreeMap<Integer, ArrayList<EditedCell>>> en : byRec.entrySet()) {
            RowRec rr = en.getKey();
            StringBuilder sb = new StringBuilder();
            int cur = rr.r0;
            for (Map.Entry<Integer, ArrayList<EditedCell>> re : en.getValue().entrySet()) {
                int row = re.getKey();
                if (row > cur) sb.append(rowWithRepeat(rr, row - cur));
                sb.append(rebuiltRow(rr, re.getValue()));
                cur = row + 1;
            }
            int end = rr.r0 + rr.repeat;
            if (cur < end) sb.append(rowWithRepeat(rr, end - cur));
            p.replace(rr.node, sb.toString());
        }
        if (!tail.isEmpty()) {
            int next = 0;
            int insertAt;
            if (os.rows.isEmpty()) {
                insertAt = os.table.endTagStart;
            } else {
                RowRec last = os.rows.get(os.rows.size() - 1);
                next = last.r0 + last.repeat;
                insertAt = last.node.end;
            }
            StringBuilder sb = new StringBuilder();
            String row = Xml.q(tp, "table-row"), cell = Xml.q(tp, "table-cell");
            for (Map.Entry<Integer, ArrayList<EditedCell>> re : tail.entrySet()) {
                int r = re.getKey();
                if (r > next) {
                    sb.append('<').append(row).append(' ').append(Xml.q(tp, "number-rows-repeated")).append("=\"")
                            .append(r - next).append("\"><").append(cell).append("/></").append(row).append('>');
                }
                sb.append('<').append(row).append('>');
                appendNewCells(sb, 0, re.getValue());
                sb.append("</").append(row).append('>');
                next = r + 1;
            }
            p.insert(insertAt, sb.toString());
        }
    }

    private String rowWithRepeat(RowRec rr, int n) {
        String src = content.src;
        XNode r = rr.node;
        String tag = Xml.startTag(r, new String[]{Xml.q(tp, "number-rows-repeated"), n > 1 ? String.valueOf(n) : null}, r.selfClosing);
        return tag + (r.selfClosing ? "" : src.substring(r.startTagEnd, r.end));
    }

    private String rebuiltRow(RowRec rr, ArrayList<EditedCell> edits) {
        XNode r = rr.node;
        StringBuilder sb = new StringBuilder();
        sb.append(Xml.startTag(r, new String[]{Xml.q(tp, "number-rows-repeated"), null}, false));
        TreeMap<Integer, EditedCell> byCol = new TreeMap<Integer, EditedCell>();
        for (EditedCell e : edits) byCol.put(e.col, e);
        int lastEnd = 0;
        // Non-cell children are kept in place.
        int ci = 0;
        for (XNode ch : r.children) {
            if (ch.type != XNode.ELEMENT) continue;
            boolean isCell = ch.is(Xml.TABLE, "table-cell") || ch.is(Xml.TABLE, "covered-table-cell");
            if (!isCell) {
                sb.append(ch.raw(content.src));
                continue;
            }
            CellRec cr = rr.cells.get(ci++);
            int cur = cr.c0, end = cr.c0 + cr.repeat;
            for (Map.Entry<Integer, EditedCell> e : byCol.subMap(cr.c0, end).entrySet()) {
                int col = e.getKey();
                if (col > cur) sb.append(cellWithRepeat(cr, col - cur));
                sb.append(newCell(cr, e.getValue()));
                cur = col + 1;
            }
            if (cur < end) sb.append(cellWithRepeat(cr, end - cur));
            lastEnd = end;
        }
        ArrayList<EditedCell> beyond = new ArrayList<EditedCell>(byCol.tailMap(lastEnd).values());
        if (!beyond.isEmpty()) appendNewCells(sb, lastEnd, beyond);
        sb.append("</").append(r.qname).append('>');
        return sb.toString();
    }

    private void appendNewCells(StringBuilder sb, int startCol, ArrayList<EditedCell> cells) {
        int next = startCol;
        String cell = Xml.q(tp, "table-cell");
        for (EditedCell e : cells) {
            if (e.col > next) {
                sb.append('<').append(cell).append(' ').append(Xml.q(tp, "number-columns-repeated")).append("=\"")
                        .append(e.col - next).append("\"/>");
            }
            sb.append(newCell(null, e));
            next = e.col + 1;
        }
    }

    private String cellWithRepeat(CellRec cr, int n) {
        XNode c = cr.node;
        String tag = Xml.startTag(c, new String[]{Xml.q(tp, "number-columns-repeated"), n > 1 ? String.valueOf(n) : null}, c.selfClosing);
        return tag + (c.selfClosing ? "" : content.src.substring(c.startTagEnd, c.end));
    }

    private static final HashSet<String> VALUE_ATTRS = new HashSet<String>(Arrays.asList("value-type", "value",
            "date-value", "time-value", "boolean-value", "string-value", "currency", "formula",
            "number-columns-repeated"));

    private String newCell(CellRec cr, EditedCell e) {
        String cell = Xml.q(tp, "table-cell");
        StringBuilder sb = new StringBuilder("<").append(cell);
        String origType = null, currency = null;
        StringBuilder kept = new StringBuilder();
        if (cr != null) {
            XNode n = cr.node;
            origType = n.attr(Xml.OFFICE, "value-type");
            currency = n.attr(Xml.OFFICE, "currency");
            for (int i = 0; i < n.attrNames.size(); i++) {
                String an = n.attrNames.get(i);
                int colon = an.indexOf(':');
                String local = colon < 0 ? an : an.substring(colon + 1);
                String pre = colon < 0 ? "" : an.substring(0, colon);
                String ns = pre.length() == 0 ? null : n.resolvePrefix(pre);
                boolean valueAttr = VALUE_ATTRS.contains(local)
                        && (Xml.OFFICE.equals(ns) || Xml.TABLE.equals(ns) || Xml.CALCEXT.equals(ns));
                if (valueAttr) continue;
                sb.append(' ').append(an).append("=\"").append(Xml.escAttr(n.attrValues.get(i))).append('"');
            }
            for (XNode ch : n.children) {
                if (ch.type != XNode.ELEMENT) continue;
                if (ch.is(Xml.TEXT, "p") || ch.is(Xml.TEXT, "h")) continue;
                kept.append(ch.raw(content.src));
            }
        }
        String o = op == null ? "office" : op;
        String vt = Xml.q(o, "value-type");
        String cvt = cp == null ? null : Xml.q(cp, "value-type");
        if (cvt == null && e.type == Cell.ERROR) {
            // Errors are only representable with calcext; declare it on the cell when the file doesn't.
            sb.append(" xmlns:calcext=\"").append(Xml.CALCEXT).append('"');
            cvt = "calcext:value-type";
        }
        if (e.formula != null) {
            try {
                // The "of:" prefix names the formula syntax and must be bound to the OpenFormula namespace.
                String f = Formula.toOpenFormula(e.formula);
                XNode scopeNode = cr != null ? cr.node : content.root;
                String ofp = scopeNode.prefixFor(Xml.OF);
                if (ofp == null || ofp.length() == 0) {
                    sb.append(" xmlns:of=\"").append(Xml.OF).append('"');
                } else if (!ofp.equals("of")) {
                    f = ofp + f.substring(2);
                }
                sb.append(' ').append(Xml.q(tp, "formula")).append("=\"").append(Xml.escAttr(f)).append('"');
            } catch (Formula.Unsupported ex) {
                // canStoreFormula() rejects these up front
            }
        }
        String text = null;
        switch (e.type) {
            case Cell.NUMBER: {
                String type = "percentage".equals(origType) || ("currency".equals(origType) && currency != null) ? origType : "float";
                sb.append(' ').append(vt).append("=\"").append(type).append('"');
                if (type.equals("currency")) sb.append(' ').append(Xml.q(o, "currency")).append("=\"").append(Xml.escAttr(currency)).append('"');
                sb.append(' ').append(Xml.q(o, "value")).append("=\"").append(NumFormat.store(e.num)).append('"');
                if (cvt != null) sb.append(' ').append(cvt).append("=\"").append(type).append('"');
                text = e.display != null && e.display.length() > 0 ? e.display : NumFormat.general(e.num);
                break;
            }
            case Cell.STRING:
                sb.append(' ').append(vt).append("=\"string\"");
                if (cvt != null) sb.append(' ').append(cvt).append("=\"string\"");
                text = e.str;
                break;
            case Cell.BOOL:
                sb.append(' ').append(vt).append("=\"boolean\" ").append(Xml.q(o, "boolean-value")).append("=\"")
                        .append(e.bool ? "true" : "false").append('"');
                if (cvt != null) sb.append(' ').append(cvt).append("=\"boolean\"");
                text = e.bool ? "TRUE" : "FALSE";
                break;
            case Cell.ERROR:
                if (cvt != null) {
                    sb.append(' ').append(vt).append("=\"float\" ").append(Xml.q(o, "value")).append("=\"0\" ")
                            .append(cvt).append("=\"error\"");
                } else {
                    sb.append(' ').append(vt).append("=\"string\" ").append(Xml.q(o, "string-value")).append("=\"")
                            .append(Xml.escAttr(e.str)).append('"');
                }
                text = e.str;
                break;
            default:
                break;
        }
        if (text == null && kept.length() == 0) return sb.append("/>").toString();
        sb.append('>').append(kept);
        if (text != null) sb.append(OdfText.paragraphs(xp, text));
        sb.append("</").append(cell).append('>');
        return sb.toString();
    }

    @Override
    public void close() {
        if (pkg != null) pkg.close();
    }
}
