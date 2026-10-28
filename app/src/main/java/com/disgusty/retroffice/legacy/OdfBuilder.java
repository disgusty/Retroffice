package com.disgusty.retroffice.legacy;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.disgusty.retroffice.pkg.PackageWriter;
import com.disgusty.retroffice.sheet.NumFormat;
import com.disgusty.retroffice.text.OdfText;
import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.Xml;

/**
 * Builds OpenDocument files from plain data read out of legacy formats. The result is opened by
 * the normal engine, so converted documents can be edited and saved like any ODF file.
 */
public final class OdfBuilder {
    private OdfBuilder() {
    }

    // ------------------------------------------------------------------ data model

    public static final int LEFT = 0, CENTER = 1, RIGHT = 2, JUSTIFY = 3;

    public static final class Run {
        public final String text;
        public boolean bold, italic, underline, strike;
        /** Font size in half-points, 0 = default. */
        public int halfPoints;

        public Run(String text) {
            this.text = text;
        }

        String key() {
            return (bold ? "b" : "") + (italic ? "i" : "") + (underline ? "u" : "") + (strike ? "s" : "") + halfPoints;
        }

        boolean plain() {
            return !bold && !italic && !underline && !strike && halfPoints == 0;
        }
    }

    public static final class Para {
        public final ArrayList<Run> runs = new ArrayList<Run>();
        public int align = LEFT;

        public String text() {
            StringBuilder sb = new StringBuilder();
            for (Run r : runs) sb.append(r.text);
            return sb.toString();
        }
    }

    public static final class Cell {
        public static final int STRING = 0, NUMBER = 1, BOOL = 2, DATE = 3;
        public int type;
        public String str = "";
        public double num;
        public boolean bool;
    }

    public static final class SheetData {
        public final String name;
        public final TreeMap<Integer, TreeMap<Integer, Cell>> rows = new TreeMap<Integer, TreeMap<Integer, Cell>>();

        public SheetData(String name) {
            this.name = name;
        }

        public void put(int row, int col, Cell c) {
            if (row < 0 || col < 0 || row > 1048575 || col > 16383) return;
            TreeMap<Integer, Cell> r = rows.get(row);
            if (r == null) {
                r = new TreeMap<Integer, Cell>();
                rows.put(row, r);
            }
            r.put(col, c);
        }

        public Cell get(int row, int col) {
            TreeMap<Integer, Cell> r = rows.get(row);
            return r == null ? null : r.get(col);
        }
    }

    public static final class SlideData {
        public String title = "";
        public final ArrayList<String> body = new ArrayList<String>();
    }

    // ------------------------------------------------------------------ writers

    private static final String NS = " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
            + " xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\""
            + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\""
            + " xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\""
            + " xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\""
            + " xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\""
            + " xmlns:svg=\"urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0\""
            + " xmlns:presentation=\"urn:oasis:names:tc:opendocument:xmlns:presentation:1.0\""
            + " xmlns:meta=\"urn:oasis:names:tc:opendocument:xmlns:meta:1.0\""
            + " xmlns:of=\"urn:oasis:names:tc:opendocument:xmlns:of:1.2\""
            + " office:version=\"1.2\"";

    public static void writeText(List<Para> paras, File out) throws IOException {
        // One automatic style per distinct paragraph alignment and run formatting.
        LinkedHashMap<String, String> textStyles = new LinkedHashMap<String, String>();
        StringBuilder body = new StringBuilder();
        for (Para p : paras) {
            String ps = p.align == CENTER ? "Pc" : p.align == RIGHT ? "Pr" : p.align == JUSTIFY ? "Pj" : "Standard";
            StringBuilder inner = new StringBuilder();
            for (Run r : p.runs) {
                if (r.text.length() == 0) continue;
                String enc = OdfText.encode("text", r.text);
                if (r.plain()) {
                    inner.append(enc);
                } else {
                    String name = textStyles.get(r.key());
                    if (name == null) {
                        name = "T" + (textStyles.size() + 1);
                        textStyles.put(r.key(), name);
                    }
                    inner.append("<text:span text:style-name=\"").append(name).append("\">").append(enc).append("</text:span>");
                }
            }
            if (inner.length() == 0) body.append("<text:p text:style-name=\"").append(ps).append("\"/>");
            else body.append("<text:p text:style-name=\"").append(ps).append("\">").append(inner).append("</text:p>");
        }
        if (paras.isEmpty()) body.append("<text:p text:style-name=\"Standard\"/>");

        StringBuilder auto = new StringBuilder();
        String[][] aligns = {{"Pc", "center"}, {"Pr", "end"}, {"Pj", "justify"}};
        for (String[] a : aligns) {
            auto.append("<style:style style:name=\"").append(a[0]).append("\" style:family=\"paragraph\" style:parent-style-name=\"Standard\">")
                    .append("<style:paragraph-properties fo:text-align=\"").append(a[1]).append("\"/></style:style>");
        }
        for (Map.Entry<String, String> e : textStyles.entrySet()) {
            auto.append("<style:style style:name=\"").append(e.getValue()).append("\" style:family=\"text\"><style:text-properties")
                    .append(textProps(e.getKey())).append("/></style:style>");
        }
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content" + NS + "><office:automatic-styles>"
                + auto + "</office:automatic-styles><office:body><office:text>" + body + "</office:text></office:body></office:document-content>";
        write("application/vnd.oasis.opendocument.text", content, styles(false), out);
    }

    /** text-properties attributes from a Run.key() string. */
    private static String textProps(String key) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < key.length() && Character.isLetter(key.charAt(i))) {
            char c = key.charAt(i++);
            if (c == 'b') sb.append(" fo:font-weight=\"bold\" style:font-weight-asian=\"bold\" style:font-weight-complex=\"bold\"");
            else if (c == 'i') sb.append(" fo:font-style=\"italic\" style:font-style-asian=\"italic\" style:font-style-complex=\"italic\"");
            else if (c == 'u') sb.append(" style:text-underline-style=\"solid\" style:text-underline-width=\"auto\" style:text-underline-color=\"font-color\"");
            else if (c == 's') sb.append(" style:text-line-through-style=\"solid\" style:text-line-through-type=\"single\"");
        }
        int hp = Integer.parseInt(key.substring(i));
        if (hp > 0) {
            String pt = (hp % 2 == 0 ? String.valueOf(hp / 2) : (hp / 2) + ".5") + "pt";
            sb.append(" fo:font-size=\"").append(pt).append("\" style:font-size-asian=\"").append(pt)
                    .append("\" style:font-size-complex=\"").append(pt).append('"');
        }
        return sb.toString();
    }

    public static void writeSheets(List<SheetData> sheets, File out) throws IOException {
        StringBuilder body = new StringBuilder();
        for (SheetData sh : sheets) {
            int maxCol = 0;
            for (TreeMap<Integer, Cell> r : sh.rows.values()) if (!r.isEmpty()) maxCol = Math.max(maxCol, r.lastKey());
            body.append("<table:table table:name=\"").append(Xml.escAttr(sh.name)).append("\">");
            body.append("<table:table-column table:number-columns-repeated=\"").append(maxCol + 1).append("\"/>");
            int next = 0;
            for (Map.Entry<Integer, TreeMap<Integer, Cell>> re : sh.rows.entrySet()) {
                int row = re.getKey();
                if (row > next) {
                    body.append("<table:table-row table:number-rows-repeated=\"").append(row - next)
                            .append("\"><table:table-cell/></table:table-row>");
                }
                body.append("<table:table-row>");
                int col = 0;
                for (Map.Entry<Integer, Cell> ce : re.getValue().entrySet()) {
                    int c = ce.getKey();
                    if (c > col) body.append("<table:table-cell table:number-columns-repeated=\"").append(c - col).append("\"/>");
                    body.append(cellXml(ce.getValue()));
                    col = c + 1;
                }
                if (col == 0) body.append("<table:table-cell/>");
                body.append("</table:table-row>");
                next = row + 1;
            }
            if (sh.rows.isEmpty()) body.append("<table:table-row><table:table-cell/></table:table-row>");
            body.append("</table:table>");
        }
        if (sheets.isEmpty()) {
            body.append("<table:table table:name=\"Sheet1\"><table:table-column/><table:table-row><table:table-cell/></table:table-row></table:table>");
        }
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content" + NS
                + "><office:automatic-styles/><office:body><office:spreadsheet>" + body
                + "</office:spreadsheet></office:body></office:document-content>";
        write("application/vnd.oasis.opendocument.spreadsheet", content, styles(false), out);
    }

    private static String cellXml(Cell c) {
        switch (c.type) {
            case Cell.NUMBER:
                return "<table:table-cell office:value-type=\"float\" office:value=\"" + NumFormat.store(c.num) + "\"><text:p>"
                        + NumFormat.general(c.num) + "</text:p></table:table-cell>";
            case Cell.BOOL:
                return "<table:table-cell office:value-type=\"boolean\" office:boolean-value=\"" + (c.bool ? "true" : "false")
                        + "\"><text:p>" + (c.bool ? "TRUE" : "FALSE") + "</text:p></table:table-cell>";
            case Cell.DATE:
                return "<table:table-cell office:value-type=\"date\" office:date-value=\"" + Xml.escAttr(isoDate(c.num))
                        + "\"><text:p>" + Xml.escText(displayDate(c.num)) + "</text:p></table:table-cell>";
            default:
                if (c.str.length() == 0) return "<table:table-cell/>";
                return "<table:table-cell office:value-type=\"string\">" + OdfText.paragraphs("text", c.str) + "</table:table-cell>";
        }
    }

    /** Excel serial date (1900 system) to {Y, M, D, h, m, s}. */
    static int[] dateParts(double serial) {
        long days = (long) Math.floor(serial);
        long secs = Math.round((serial - days) * 86400);
        if (secs >= 86400) {
            days++;
            secs -= 86400;
        }
        if (days < 61) days += 1; // Excel's fictitious 29 Feb 1900
        // Days since 1899-12-30 to a calendar date (civil-from-days algorithm by H. Hinnant).
        long z = days - 25569 + 719468; // 25569 = serial of 1970-01-01; shift epoch to 0000-03-01
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long dd = doy - (153 * mp + 2) / 5 + 1;
        long mm = mp < 10 ? mp + 3 : mp - 9;
        if (mm <= 2) y++;
        return new int[]{(int) y, (int) mm, (int) dd, (int) (secs / 3600), (int) (secs % 3600 / 60), (int) (secs % 60)};
    }

    static String isoDate(double serial) {
        int[] p = dateParts(serial);
        String s = p[0] + "-" + two(p[1]) + "-" + two(p[2]);
        if (p[3] != 0 || p[4] != 0 || p[5] != 0) s += "T" + two(p[3]) + ":" + two(p[4]) + ":" + two(p[5]);
        return s;
    }

    static String displayDate(double serial) {
        int[] p = dateParts(serial);
        String s = two(p[2]) + "." + two(p[1]) + "." + p[0];
        if (p[3] != 0 || p[4] != 0 || p[5] != 0) s += " " + p[3] + ":" + two(p[4]);
        return s;
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    public static void writeSlides(List<SlideData> slides, File out) throws IOException {
        StringBuilder body = new StringBuilder();
        int n = 0;
        for (SlideData s : slides) {
            n++;
            body.append("<draw:page draw:name=\"page").append(n).append("\" draw:master-page-name=\"Default\">");
            body.append("<draw:frame presentation:class=\"title\" svg:x=\"1.4cm\" svg:y=\"0.6cm\" svg:width=\"25.2cm\" svg:height=\"2.8cm\">")
                    .append("<draw:text-box>").append(OdfText.paragraphs("text", s.title)).append("</draw:text-box></draw:frame>");
            body.append("<draw:frame presentation:class=\"outline\" svg:x=\"1.4cm\" svg:y=\"3.8cm\" svg:width=\"25.2cm\" svg:height=\"11cm\"><draw:text-box>");
            if (s.body.isEmpty()) body.append("<text:p/>");
            for (String line : s.body) body.append(OdfText.paragraphs("text", line));
            body.append("</draw:text-box></draw:frame></draw:page>");
        }
        if (slides.isEmpty()) {
            body.append("<draw:page draw:name=\"page1\" draw:master-page-name=\"Default\"/>");
        }
        String content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content" + NS
                + "><office:automatic-styles/><office:body><office:presentation>" + body
                + "</office:presentation></office:body></office:document-content>";
        write("application/vnd.oasis.opendocument.presentation", content, styles(true), out);
    }

    private static String styles(boolean slides) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-styles").append(NS).append("><office:styles>");
        sb.append("<style:default-style style:family=\"paragraph\"><style:text-properties fo:font-size=\"12pt\"/></style:default-style>");
        sb.append("<style:style style:name=\"Standard\" style:family=\"paragraph\"/>");
        sb.append("</office:styles><office:automatic-styles>");
        if (slides) {
            sb.append("<style:page-layout style:name=\"PM1\"><style:page-layout-properties fo:page-width=\"28cm\" fo:page-height=\"15.75cm\" style:print-orientation=\"landscape\"/></style:page-layout>");
        } else {
            sb.append("<style:page-layout style:name=\"PM1\"><style:page-layout-properties fo:page-width=\"21cm\" fo:page-height=\"29.7cm\" fo:margin-top=\"2cm\" fo:margin-bottom=\"2cm\" fo:margin-left=\"2cm\" fo:margin-right=\"2cm\"/></style:page-layout>");
        }
        sb.append("</office:automatic-styles><office:master-styles><style:master-page style:name=\"")
                .append(slides ? "Default" : "Standard").append("\" style:page-layout-name=\"PM1\"/></office:master-styles></office:document-styles>");
        return sb.toString();
    }

    private static void write(String mime, String content, String styles, File out) throws IOException {
        LinkedHashMap<String, byte[]> parts = new LinkedHashMap<String, byte[]>();
        parts.put("mimetype", mime.getBytes("US-ASCII"));
        parts.put("content.xml", XDoc.encode(content));
        parts.put("styles.xml", XDoc.encode(styles));
        parts.put("meta.xml", XDoc.encode("<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-meta" + NS
                + "><office:meta><meta:generator>Retroffice</meta:generator></office:meta></office:document-meta>"));
        parts.put("META-INF/manifest.xml", XDoc.encode("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\">"
                + "<manifest:file-entry manifest:full-path=\"/\" manifest:version=\"1.2\" manifest:media-type=\"" + mime + "\"/>"
                + "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>"
                + "<manifest:file-entry manifest:full-path=\"styles.xml\" manifest:media-type=\"text/xml\"/>"
                + "<manifest:file-entry manifest:full-path=\"meta.xml\" manifest:media-type=\"text/xml\"/>"
                + "</manifest:manifest>"));
        PackageWriter.writeNew(parts, out);
    }
}
