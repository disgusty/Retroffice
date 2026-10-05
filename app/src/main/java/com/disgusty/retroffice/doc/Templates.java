package com.disgusty.retroffice.doc;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

import com.disgusty.retroffice.pkg.PackageWriter;
import com.disgusty.retroffice.xml.XDoc;

/** Minimal, valid OpenDocument files used for "New document/spreadsheet/presentation". */
public final class Templates {
    private Templates() {
    }

    public static final int TEXT = 1, SHEET = 2, SLIDES = 3;

    private static final String NS = " xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\""
            + " xmlns:style=\"urn:oasis:names:tc:opendocument:xmlns:style:1.0\""
            + " xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\""
            + " xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\""
            + " xmlns:draw=\"urn:oasis:names:tc:opendocument:xmlns:drawing:1.0\""
            + " xmlns:fo=\"urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0\""
            + " xmlns:xlink=\"http://www.w3.org/1999/xlink\""
            + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
            + " xmlns:meta=\"urn:oasis:names:tc:opendocument:xmlns:meta:1.0\""
            + " xmlns:svg=\"urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0\""
            + " xmlns:presentation=\"urn:oasis:names:tc:opendocument:xmlns:presentation:1.0\""
            + " xmlns:of=\"urn:oasis:names:tc:opendocument:xmlns:of:1.2\""
            + " office:version=\"1.2\"";

    public static String extension(int kind) {
        return kind == TEXT ? "odt" : kind == SHEET ? "ods" : "odp";
    }

    public static String mime(int kind) {
        return "application/vnd.oasis.opendocument." + (kind == TEXT ? "text" : kind == SHEET ? "spreadsheet" : "presentation");
    }

    /** Writes a new empty document of the given kind to {@code out}. */
    public static void write(int kind, File out) throws IOException {
        LinkedHashMap<String, byte[]> parts = new LinkedHashMap<String, byte[]>();
        String mime = mime(kind);
        parts.put("mimetype", ascii(mime));
        parts.put("content.xml", XDoc.encode(content(kind)));
        parts.put("styles.xml", XDoc.encode(styles(kind)));
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

    private static byte[] ascii(String s) {
        try {
            return s.getBytes("US-ASCII");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String content(int kind) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<office:document-content").append(NS).append("><office:automatic-styles/><office:body>");
        if (kind == TEXT) {
            sb.append("<office:text><text:p text:style-name=\"Standard\"/></office:text>");
        } else if (kind == SHEET) {
            sb.append("<office:spreadsheet><table:table table:name=\"Sheet1\">")
                    .append("<table:table-column table:number-columns-repeated=\"1024\"/>")
                    .append("<table:table-row table:number-rows-repeated=\"1048576\">")
                    .append("<table:table-cell table:number-columns-repeated=\"1024\"/></table:table-row>")
                    .append("</table:table></office:spreadsheet>");
        } else {
            sb.append("<office:presentation><draw:page draw:name=\"page1\" draw:master-page-name=\"Default\">")
                    .append("<draw:frame presentation:class=\"title\" draw:layer=\"layout\" svg:x=\"2cm\" svg:y=\"5.5cm\" svg:width=\"24cm\" svg:height=\"3cm\">")
                    .append("<draw:text-box><text:p text:style-name=\"MATitle\"/></draw:text-box></draw:frame>")
                    .append("<draw:frame presentation:class=\"subtitle\" draw:layer=\"layout\" svg:x=\"2cm\" svg:y=\"9cm\" svg:width=\"24cm\" svg:height=\"2.5cm\">")
                    .append("<draw:text-box><text:p text:style-name=\"MASubtitle\"/></draw:text-box></draw:frame>")
                    .append("</draw:page></office:presentation>");
        }
        sb.append("</office:body></office:document-content>");
        return sb.toString();
    }

    private static String styles(int kind) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<office:document-styles").append(NS).append("><office:styles>");
        sb.append("<style:default-style style:family=\"paragraph\"><style:text-properties fo:font-size=\"12pt\"/></style:default-style>");
        sb.append("<style:style style:name=\"Standard\" style:family=\"paragraph\"/>");
        if (kind == SLIDES) {
            sb.append("<style:style style:name=\"MATitle\" style:family=\"paragraph\"><style:paragraph-properties fo:text-align=\"center\"/>")
                    .append("<style:text-properties fo:font-size=\"40pt\"/></style:style>");
            sb.append("<style:style style:name=\"MASubtitle\" style:family=\"paragraph\"><style:paragraph-properties fo:text-align=\"center\"/>")
                    .append("<style:text-properties fo:font-size=\"24pt\"/></style:style>");
        }
        sb.append("</office:styles><office:automatic-styles>");
        if (kind == SLIDES) {
            sb.append("<style:page-layout style:name=\"PM1\"><style:page-layout-properties fo:page-width=\"28cm\" fo:page-height=\"15.75cm\" style:print-orientation=\"landscape\"/></style:page-layout>");
        } else {
            sb.append("<style:page-layout style:name=\"PM1\"><style:page-layout-properties fo:page-width=\"21cm\" fo:page-height=\"29.7cm\" fo:margin-top=\"2cm\" fo:margin-bottom=\"2cm\" fo:margin-left=\"2cm\" fo:margin-right=\"2cm\"/></style:page-layout>");
        }
        sb.append("</office:automatic-styles><office:master-styles>");
        sb.append("<style:master-page style:name=\"").append(kind == SLIDES ? "Default" : "Standard").append("\" style:page-layout-name=\"PM1\"/>");
        sb.append("</office:master-styles></office:document-styles>");
        return sb.toString();
    }
}
