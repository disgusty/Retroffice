package com.disgusty.retroffice.sheet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.disgusty.retroffice.doc.Rels;
import com.disgusty.retroffice.pkg.OfficePackage;
import com.disgusty.retroffice.pkg.PackageVerifier;
import com.disgusty.retroffice.pkg.PackageWriter;
import com.disgusty.retroffice.xml.Patcher;
import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XNode;

/** Excel workbook (.xlsx). Saving rewrites only the rows that contain changed cells. */
public final class XlsxDoc extends SheetDoc {
    private OfficePackage pkg;
    private String wbPart;
    private XDoc wb;
    private String sns;
    private final ArrayList<String> sharedStrings = new ArrayList<String>();
    private final ArrayList<Integer> xfNumFmt = new ArrayList<Integer>();
    private final HashMap<Integer, String> numFmtCodes = new HashMap<Integer, String>();

    static final class XS {
        String part;
        XDoc doc;
        String prefix;
        XNode sheetData, dimension;
        final TreeMap<Integer, XNode> rows = new TreeMap<Integer, XNode>();
        final HashSet<XNode> implicitRows = new HashSet<XNode>();
        final HashMap<XNode, ArrayList<XNode>> rowCells = new HashMap<XNode, ArrayList<XNode>>();
        final HashMap<XNode, Integer> cellCol = new HashMap<XNode, Integer>();
        final HashSet<XNode> implicitCells = new HashSet<XNode>();
    }

    static final class XC {
        XNode c, row;
        String s;
        String fType, si, ref;
        boolean hadFormula;
    }

    @Override
    public String formatName() {
        return "XLSX";
    }

    public static XlsxDoc open(OfficePackage pkg, String wbPart) throws Exception {
        XlsxDoc d = new XlsxDoc();
        d.pkg = pkg;
        d.wbPart = wbPart;
        d.wb = pkg.xml(wbPart);
        XNode root = d.wb.root;
        d.sns = Xml.S.equals(root.ns) ? Xml.S : Xml.S_STRICT.equals(root.ns) ? Xml.S_STRICT : null;
        if (d.sns == null || !"workbook".equals(root.local)) throw new IOException("Not an Excel workbook");
        HashMap<String, String> rels = Rels.targets(pkg, wbPart);
        d.readSharedStrings(Rels.byType(pkg, wbPart, "/sharedStrings"));
        d.readStyles(Rels.byType(pkg, wbPart, "/styles"));
        XNode sheetsEl = root.child(d.sns, "sheets");
        if (sheetsEl == null) throw new IOException("Workbook has no sheets");
        for (XNode s : sheetsEl.childrenNamed(d.sns, "sheet")) {
            String name = s.attr(null, "name");
            String rid = s.attr(Xml.R, "id");
            if (rid == null) rid = s.attr(Xml.R_STRICT, "id");
            Sheet sheet = new Sheet(name == null ? "Sheet" : name);
            String target = rid == null ? null : rels.get(rid);
            String part = target == null ? null : OfficePackage.resolve(wbPart, target);
            if (part == null || !pkg.has(part) || part.contains("chartsheets/") || part.contains("dialogsheets/")) {
                sheet.readOnly = true;
            } else {
                d.readSheet(sheet, part);
            }
            d.sheets.add(sheet);
        }
        if (d.sheets.isEmpty()) throw new IOException("Workbook has no sheets");
        d.markLoaded();
        return d;
    }

    private void readSharedStrings(String part) throws Exception {
        if (part == null || !pkg.has(part)) return;
        XDoc d = pkg.xml(part);
        for (XNode si : d.root.childrenNamed(sns, "si")) sharedStrings.add(richText(si));
    }

    /** Text of a CT_Rst: either <t> or runs of <r><t>, ignoring phonetic runs. */
    private String richText(XNode si) {
        StringBuilder sb = new StringBuilder();
        for (XNode c : si.children) {
            if (c.is(sns, "t")) sb.append(c.textContent());
            else if (c.is(sns, "r")) {
                XNode t = c.child(sns, "t");
                if (t != null) sb.append(t.textContent());
            }
        }
        return sb.toString();
    }

    private void readStyles(String part) throws Exception {
        if (part == null || !pkg.has(part)) return;
        XNode root = pkg.xml(part).root;
        XNode fmts = root.child(sns, "numFmts");
        if (fmts != null) {
            for (XNode f : fmts.childrenNamed(sns, "numFmt")) {
                try {
                    numFmtCodes.put(Integer.parseInt(f.attr(null, "numFmtId")), f.attr(null, "formatCode"));
                } catch (Exception ignored) {
                }
            }
        }
        XNode xfs = root.child(sns, "cellXfs");
        if (xfs != null) {
            for (XNode xf : xfs.childrenNamed(sns, "xf")) {
                int id = 0;
                try {
                    id = Integer.parseInt(xf.attr(null, "numFmtId"));
                } catch (Exception ignored) {
                }
                xfNumFmt.add(id);
            }
        }
    }

    private void readSheet(Sheet sheet, String part) throws Exception {
        XS xs = new XS();
        xs.part = part;
        xs.doc = pkg.xml(part);
        XNode root = xs.doc.root;
        xs.prefix = root.prefix;
        xs.sheetData = root.child(sns, "sheetData");
        xs.dimension = root.child(sns, "dimension");
        sheet.src = xs;
        XNode cols = root.child(sns, "cols");
        if (cols != null) {
            for (XNode col : cols.childrenNamed(sns, "col")) {
                try {
                    int min = Integer.parseInt(col.attr(null, "min")), max = Integer.parseInt(col.attr(null, "max"));
                    float w = Float.parseFloat(col.attr(null, "width"));
                    for (int c = min; c <= Math.min(max, min + 200); c++) sheet.colWidthPt.put(c - 1, (w * 7 + 5) * 0.75f);
                } catch (Exception ignored) {
                }
            }
        }
        if (xs.sheetData == null) {
            sheet.readOnly = true;
            return;
        }
        HashMap<String, int[]> masterPos = new HashMap<String, int[]>();
        HashMap<String, String> masterFormula = new HashMap<String, String>();
        ArrayList<Object[]> sharedChildren = new ArrayList<Object[]>();
        int rowIdx = -1;
        for (XNode row : xs.sheetData.children) {
            if (!row.is(sns, "row")) continue;
            String r = row.attr(null, "r");
            if (r != null) {
                try {
                    rowIdx = Integer.parseInt(r) - 1;
                } catch (NumberFormatException e) {
                    throw new IOException("Damaged sheet: bad row number");
                }
            } else {
                rowIdx++;
                xs.implicitRows.add(row);
            }
            xs.rows.put(rowIdx, row);
            ArrayList<XNode> cells = new ArrayList<XNode>();
            xs.rowCells.put(row, cells);
            int colIdx = -1;
            for (XNode c : row.children) {
                if (!c.is(sns, "c")) continue;
                String ref = c.attr(null, "r");
                int[] rc = CellRef.parse(ref);
                if (rc != null) colIdx = rc[1];
                else {
                    colIdx++;
                    xs.implicitCells.add(c);
                }
                cells.add(c);
                xs.cellCol.put(c, colIdx);
                Cell cell = readCell(c, row);
                XC xc = (XC) cell.src;
                if ("shared".equals(xc.fType) && xc.si != null) {
                    if (cell.formula != null && xc.ref != null) {
                        masterPos.put(xc.si, new int[]{rowIdx, colIdx});
                        masterFormula.put(xc.si, cell.formula);
                    } else if (cell.formula == null) {
                        sharedChildren.add(new Object[]{cell, rowIdx, colIdx, xc.si});
                    }
                }
                sheet.put(rowIdx, colIdx, cell);
            }
        }
        for (Object[] ch : sharedChildren) {
            String si = (String) ch[3];
            int[] mp = masterPos.get(si);
            String mf = masterFormula.get(si);
            if (mp == null || mf == null) continue;
            try {
                ((Cell) ch[0]).formula = Formula.shift(mf, (Integer) ch[1] - mp[0], (Integer) ch[2] - mp[1]);
            } catch (Formula.Unsupported ignored) {
            }
        }
        XNode merges = root.child(sns, "mergeCells");
        if (merges != null) {
            for (XNode m : merges.childrenNamed(sns, "mergeCell")) {
                String ref = m.attr(null, "ref");
                if (ref == null || ref.indexOf(':') < 0) continue;
                int[] a = CellRef.parse(ref.substring(0, ref.indexOf(':')));
                int[] b = CellRef.parse(ref.substring(ref.indexOf(':') + 1));
                if (a == null || b == null) continue;
                for (int r = a[0]; r <= b[0] && r <= a[0] + 1000; r++) {
                    for (int c = a[1]; c <= b[1] && c <= a[1] + 200; c++) {
                        if (r == a[0] && c == a[1]) continue;
                        Cell cell = sheet.get(r, c);
                        if (cell == null) {
                            cell = new Cell();
                            sheet.cells.put(CellRef.key(r, c), cell);
                        }
                        cell.covered = true;
                    }
                }
            }
        }
    }

    private Cell readCell(XNode c, XNode row) {
        Cell cell = new Cell();
        XC xc = new XC();
        xc.c = c;
        xc.row = row;
        xc.s = c.attr(null, "s");
        cell.src = xc;
        String t = c.attr(null, "t");
        XNode f = c.child(sns, "f");
        if (f != null) {
            xc.hadFormula = true;
            xc.fType = f.attr(null, "t");
            xc.si = f.attr(null, "si");
            xc.ref = f.attr(null, "ref");
            String ft = f.textContent();
            if (ft.length() > 0) cell.formula = ft;
        }
        XNode v = c.child(sns, "v");
        String vt = v == null ? null : v.textContent();
        if (t == null || t.equals("n")) {
            if (vt != null && vt.length() > 0) {
                try {
                    cell.type = Cell.NUMBER;
                    cell.num = Double.parseDouble(vt);
                } catch (NumberFormatException e) {
                    cell.type = Cell.STRING;
                    cell.str = vt;
                }
            }
        } else if (t.equals("s")) {
            try {
                int idx = Integer.parseInt(vt.trim());
                cell.type = Cell.STRING;
                cell.str = idx >= 0 && idx < sharedStrings.size() ? sharedStrings.get(idx) : "";
            } catch (Exception e) {
                cell.type = Cell.STRING;
                cell.str = "";
            }
        } else if (t.equals("inlineStr")) {
            XNode is = c.child(sns, "is");
            cell.type = Cell.STRING;
            cell.str = is == null ? "" : richText(is);
        } else if (t.equals("str")) {
            cell.type = Cell.STRING;
            cell.str = vt == null ? "" : vt;
        } else if (t.equals("b")) {
            cell.type = Cell.BOOL;
            cell.bool = "1".equals(vt) || "true".equalsIgnoreCase(vt);
        } else if (t.equals("e")) {
            cell.type = Cell.ERROR;
            cell.str = vt == null ? "#N/A" : vt;
        } else if (t.equals("d")) {
            cell.type = Cell.STRING;
            cell.str = vt == null ? "" : vt;
        }
        refreshDisplay(cell);
        return cell;
    }

    @Override
    protected String displayNumber(Cell c, double v) {
        XC xc = c.src instanceof XC ? (XC) c.src : null;
        if (xc == null || xc.s == null) return NumFormat.general(v);
        try {
            int xf = Integer.parseInt(xc.s);
            if (xf < 0 || xf >= xfNumFmt.size()) return NumFormat.general(v);
            int id = xfNumFmt.get(xf);
            return NumFormat.excel(v, id, numFmtCodes.get(id));
        } catch (Exception e) {
            return NumFormat.general(v);
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    public SaveJob prepareSave() {
        final String fp = fingerprint();
        final HashMap<Integer, ArrayList<EditedCell>> edits = editedCells();
        // A changed shared-formula master would orphan its dependents: write theirs out explicitly.
        for (Map.Entry<Integer, ArrayList<EditedCell>> en : edits.entrySet()) {
            Sheet sh = sheets.get(en.getKey());
            HashSet<String> brokenGroups = new HashSet<String>();
            for (EditedCell e : en.getValue()) {
                XC xc = e.src instanceof XC ? (XC) e.src : null;
                if (xc != null && "shared".equals(xc.fType) && xc.ref != null && xc.si != null) brokenGroups.add(xc.si);
            }
            if (brokenGroups.isEmpty()) continue;
            for (Map.Entry<Long, Cell> ce : sh.cells.entrySet()) {
                Cell c = ce.getValue();
                XC xc = c.src instanceof XC ? (XC) c.src : null;
                if (c.edited || xc == null || !"shared".equals(xc.fType) || !brokenGroups.contains(xc.si)) continue;
                en.getValue().add(EditedCell.of(en.getKey(), CellRef.keyRow(ce.getKey()), CellRef.keyCol(ce.getKey()), c));
            }
            java.util.Collections.sort(en.getValue(), new java.util.Comparator<EditedCell>() {
                @Override
                public int compare(EditedCell a, EditedCell b) {
                    if (a.row != b.row) return a.row < b.row ? -1 : 1;
                    return a.col < b.col ? -1 : (a.col == b.col ? 0 : 1);
                }
            });
        }
        final ArrayList<XS> sheetSrc = new ArrayList<XS>();
        for (Sheet s : sheets) sheetSrc.add(s.src instanceof XS ? (XS) s.src : null);
        final ArrayList<String> sheetNames = new ArrayList<String>();
        for (Sheet s : sheets) sheetNames.add(s.name);

        return new SaveJob() {
            final LinkedHashMap<String, byte[]> replaced = new LinkedHashMap<String, byte[]>();
            final HashSet<String> removed = new HashSet<String>();

            @Override
            public String fingerprint() {
                return fp;
            }

            @Override
            public void write(File out) throws Exception {
                boolean formulasChanged = false;
                for (Map.Entry<Integer, ArrayList<EditedCell>> en : edits.entrySet()) {
                    XS xs = sheetSrc.get(en.getKey());
                    if (xs == null) continue;
                    for (EditedCell e : en.getValue()) {
                        XC xc = e.src instanceof XC ? (XC) e.src : null;
                        if (e.formula != null || (xc != null && xc.hadFormula)) formulasChanged = true;
                    }
                    replaced.put(xs.part, XDoc.encode(patchSheet(xs, en.getValue())));
                }
                if (formulasChanged) workbookForRecalc();
                PackageWriter.write(pkg, replaced, removed, out);
            }

            private void workbookForRecalc() throws Exception {
                // calcChain lists formula cells; a stale one makes Excel report a damaged file.
                String calcChain = Rels.byType(pkg, wbPart, "/calcChain");
                if (calcChain != null && pkg.has(calcChain)) {
                    removed.add(calcChain);
                    String relsPart = OfficePackage.relsFor(wbPart);
                    XDoc rels = pkg.xml(relsPart);
                    Patcher rp = new Patcher(rels.src);
                    for (XNode r : rels.root.childrenNamed(Xml.PKG_REL, "Relationship")) {
                        String type = r.attr(null, "Type");
                        if (type != null && type.endsWith("/calcChain")) rp.replace(r, "");
                    }
                    replaced.put(relsPart, XDoc.encode(rp.apply()));
                    XDoc ct = pkg.xml("[Content_Types].xml");
                    Patcher cp = new Patcher(ct.src);
                    for (XNode o : ct.root.childrenNamed(Xml.CT, "Override")) {
                        String pn = o.attr(null, "PartName");
                        if (pn != null && pn.equals("/" + calcChain)) cp.replace(o, "");
                    }
                    replaced.put("[Content_Types].xml", XDoc.encode(cp.apply()));
                }
                // Ask Excel to recalculate everything when the file is opened.
                Patcher wp = new Patcher(wb.src);
                XNode root = wb.root;
                XNode calcPr = root.child(sns, "calcPr");
                if (calcPr != null) {
                    String tag = Xml.startTag(calcPr, new String[]{"fullCalcOnLoad", "1"}, calcPr.selfClosing);
                    wp.replace(calcPr.start, calcPr.startTagEnd, tag);
                } else {
                    String[] before = {"definedNames", "externalReferences", "functionGroups", "sheets"};
                    XNode anchor = null;
                    for (String b : before) {
                        anchor = root.child(sns, b);
                        if (anchor != null) break;
                    }
                    String el = "<" + Xml.q(root.prefix, "calcPr") + " fullCalcOnLoad=\"1\"/>";
                    if (anchor != null) wp.insert(anchor.end, el);
                }
                if (!wp.isEmpty()) replaced.put(wbPart, XDoc.encode(wp.apply()));
            }

            @Override
            public void verify(File written) throws Exception {
                ArrayList<String> xmlParts = new ArrayList<String>(replaced.keySet());
                ArrayList<String> req = new ArrayList<String>();
                req.add("[Content_Types].xml");
                req.add(wbPart);
                PackageVerifier.verify(written, req, xmlParts);
                OfficePackage again = OfficePackage.open(written);
                try {
                    XlsxDoc d = open(again, wbPart);
                    verifyCells(d, edits);
                } finally {
                    again.close();
                }
            }
        };
    }

    static void verifyCells(SheetDoc d, HashMap<Integer, ArrayList<EditedCell>> edits) throws IOException {
        for (Map.Entry<Integer, ArrayList<EditedCell>> en : edits.entrySet()) {
            Sheet sh = d.sheets.get(en.getKey());
            for (EditedCell e : en.getValue()) {
                Cell c = sh.get(e.row, e.col);
                int type = c == null ? Cell.EMPTY : c.type;
                boolean ok;
                if (type != e.type) ok = false;
                else if (type == Cell.NUMBER) ok = Double.compare(c.num, e.num) == 0;
                else if (type == Cell.STRING || type == Cell.ERROR) ok = c.str.equals(e.str);
                else if (type == Cell.BOOL) ok = c.bool == e.bool;
                else ok = true;
                String f1 = c == null ? null : c.formula, f2 = e.formula;
                if (ok && f2 != null && (f1 == null || !normalizeFormula(f1).equals(normalizeFormula(f2)))) ok = false;
                if (!ok) {
                    throw new IOException("Verification failed: cell " + CellRef.name(e.col, e.row) + " of sheet "
                            + sh.name + " reads back differently");
                }
            }
        }
    }

    static String normalizeFormula(String f) {
        return f.replace(" ", "").replace("'", "").replace(';', ',').toUpperCase(java.util.Locale.US);
    }

    private String patchSheet(XS xs, ArrayList<EditedCell> list) {
        Patcher p = new Patcher(xs.doc.src);
        // Group edits by row.
        TreeMap<Integer, ArrayList<EditedCell>> byRow = new TreeMap<Integer, ArrayList<EditedCell>>();
        for (EditedCell e : list) {
            ArrayList<EditedCell> l = byRow.get(e.row);
            if (l == null) {
                l = new ArrayList<EditedCell>();
                byRow.put(e.row, l);
            }
            l.add(e);
        }
        boolean insertedRows = false;
        StringBuilder appendAtEnd = new StringBuilder();
        HashSet<XNode> rebuilt = new HashSet<XNode>();
        for (Map.Entry<Integer, ArrayList<EditedCell>> en : byRow.entrySet()) {
            int row = en.getKey();
            XNode rowNode = xs.rows.get(row);
            if (rowNode != null) {
                p.replace(rowNode, rowXml(xs, rowNode, row, en.getValue()));
                rebuilt.add(rowNode);
            } else {
                String xml = rowXml(xs, null, row, en.getValue());
                if (xml.length() == 0) continue;
                insertedRows = true;
                java.util.SortedMap<Integer, XNode> tail = xs.rows.tailMap(row + 1);
                if (!tail.isEmpty()) p.insert(tail.get(tail.firstKey()).start, xml);
                else appendAtEnd.append(xml);
            }
        }
        if (appendAtEnd.length() > 0) {
            XNode sd = xs.sheetData;
            if (sd.selfClosing) {
                p.replace(sd, Xml.startTag(sd, null, false) + appendAtEnd + "</" + sd.qname + ">");
            } else {
                p.insert(sd.endTagStart, appendAtEnd.toString());
            }
        }
        if (insertedRows) {
            // Rows without an explicit number would shift; give them one.
            for (Map.Entry<Integer, XNode> en : xs.rows.entrySet()) {
                XNode r = en.getValue();
                if (!xs.implicitRows.contains(r) || rebuilt.contains(r)) continue;
                p.replace(r.start, r.startTagEnd, Xml.startTag(r, new String[]{"r", String.valueOf(en.getKey() + 1)}, r.selfClosing));
            }
        }
        if (xs.dimension != null) {
            String ref = xs.dimension.attr(null, "ref");
            int r1 = Integer.MAX_VALUE, c1 = Integer.MAX_VALUE, r2 = -1, c2 = -1;
            if (ref != null) {
                String[] parts = ref.split(":");
                int[] a = CellRef.parse(parts[0]);
                int[] b = parts.length > 1 ? CellRef.parse(parts[1]) : a;
                if (a != null && b != null) {
                    r1 = a[0];
                    c1 = a[1];
                    r2 = b[0];
                    c2 = b[1];
                }
            }
            boolean grew = false;
            for (EditedCell e : list) {
                if (e.type == Cell.EMPTY && e.formula == null) continue;
                if (e.row < r1) { r1 = e.row; grew = true; }
                if (e.col < c1) { c1 = e.col; grew = true; }
                if (e.row > r2) { r2 = e.row; grew = true; }
                if (e.col > c2) { c2 = e.col; grew = true; }
            }
            if (grew && r2 >= 0) {
                String nr = CellRef.name(c1, r1) + (r1 == r2 && c1 == c2 ? "" : ":" + CellRef.name(c2, r2));
                XNode dn = xs.dimension;
                p.replace(dn.start, dn.startTagEnd, Xml.startTag(dn, new String[]{"ref", nr}, dn.selfClosing));
            }
        }
        return p.apply();
    }

    private String rowXml(XS xs, XNode rowNode, int row, ArrayList<EditedCell> edits) {
        String pre = xs.prefix;
        TreeMap<Integer, String> cells = new TreeMap<Integer, String>();
        if (rowNode != null) {
            for (XNode c : xs.rowCells.get(rowNode)) {
                int col = xs.cellCol.get(c);
                String raw = c.raw(xs.doc.src);
                if (xs.implicitCells.contains(c)) {
                    raw = Xml.startTag(c, new String[]{"r", CellRef.name(col, row)}, c.selfClosing)
                            + (c.selfClosing ? "" : xs.doc.src.substring(c.startTagEnd, c.end));
                }
                cells.put(col, raw);
            }
        }
        for (EditedCell e : edits) {
            String xml = cellXml(pre, e);
            if (xml.length() == 0) cells.remove(e.col);
            else cells.put(e.col, xml);
        }
        StringBuilder body = new StringBuilder();
        for (String s : cells.values()) body.append(s);
        if (rowNode == null) {
            if (body.length() == 0) return "";
            String q = Xml.q(pre, "row");
            return "<" + q + " r=\"" + (row + 1) + "\">" + body + "</" + q + ">";
        }
        // Keep the row's other attributes (height, style...) but drop the optional "spans" hint.
        StringBuilder sb = new StringBuilder();
        sb.append(Xml.startTag(rowNode, new String[]{"r", String.valueOf(row + 1), "spans", null}, false));
        sb.append(body);
        // Non-cell children (rare, e.g. extLst) stay after the cells.
        for (XNode ch : rowNode.children) {
            if (ch.type == XNode.ELEMENT && !ch.is(sns, "c")) sb.append(ch.raw(xs.doc.src));
        }
        sb.append("</").append(rowNode.qname).append('>');
        return sb.toString();
    }

    private String cellXml(String pre, EditedCell e) {
        XC xc = e.src instanceof XC ? (XC) e.src : null;
        String c = Xml.q(pre, "c");
        String attrs = " r=\"" + CellRef.name(e.col, e.row) + "\"" + (xc != null && xc.s != null ? " s=\"" + Xml.escAttr(xc.s) + "\"" : "");
        if (e.formula != null) {
            String t = "";
            String v = null;
            switch (e.type) {
                case Cell.NUMBER: v = NumFormat.store(e.num); break;
                case Cell.STRING: t = " t=\"str\""; v = e.str; break;
                case Cell.BOOL: t = " t=\"b\""; v = e.bool ? "1" : "0"; break;
                case Cell.ERROR: t = " t=\"e\""; v = e.str; break;
                default: break;
            }
            String f = Xml.q(pre, "f"), vq = Xml.q(pre, "v");
            StringBuilder sb = new StringBuilder("<").append(c).append(attrs).append(t).append('>');
            sb.append('<').append(f).append('>').append(Xml.escText(e.formula)).append("</").append(f).append('>');
            if (v != null) sb.append('<').append(vq).append('>').append(Xml.escText(v)).append("</").append(vq).append('>');
            return sb.append("</").append(c).append('>').toString();
        }
        String vq = Xml.q(pre, "v");
        switch (e.type) {
            case Cell.NUMBER:
                return "<" + c + attrs + "><" + vq + ">" + NumFormat.store(e.num) + "</" + vq + "></" + c + ">";
            case Cell.BOOL:
                return "<" + c + attrs + " t=\"b\"><" + vq + ">" + (e.bool ? 1 : 0) + "</" + vq + "></" + c + ">";
            case Cell.ERROR:
                return "<" + c + attrs + " t=\"e\"><" + vq + ">" + Xml.escText(e.str) + "</" + vq + "></" + c + ">";
            case Cell.STRING: {
                String is = Xml.q(pre, "is"), t = Xml.q(pre, "t");
                boolean preserve = e.str.length() > 0 && (Character.isWhitespace(e.str.charAt(0))
                        || Character.isWhitespace(e.str.charAt(e.str.length() - 1)) || e.str.indexOf('\n') >= 0);
                return "<" + c + attrs + " t=\"inlineStr\"><" + is + "><" + t + (preserve ? " xml:space=\"preserve\"" : "")
                        + ">" + Xml.escText(e.str) + "</" + t + "></" + is + "></" + c + ">";
            }
            default:
                return xc != null && xc.s != null ? "<" + c + attrs + "/>" : "";
        }
    }

    @Override
    public void close() {
        if (pkg != null) pkg.close();
    }
}
