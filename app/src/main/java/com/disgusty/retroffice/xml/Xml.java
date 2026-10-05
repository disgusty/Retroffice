package com.disgusty.retroffice.xml;

/** Namespace constants and escaping helpers. */
public final class Xml {
    private Xml() {
    }

    public static final String NS_XML = "http://www.w3.org/XML/1998/namespace";

    // OOXML (transitional)
    public static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    public static final String R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    public static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    public static final String P = "http://schemas.openxmlformats.org/presentationml/2006/main";
    public static final String S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    public static final String WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing";
    public static final String PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships";
    public static final String CT = "http://schemas.openxmlformats.org/package/2006/content-types";
    public static final String MC = "http://schemas.openxmlformats.org/markup-compatibility/2006";
    public static final String VML = "urn:schemas-microsoft-com:vml";
    // OOXML strict variants of the main namespaces.
    public static final String W_STRICT = "http://purl.oclc.org/ooxml/wordprocessingml/main";
    public static final String S_STRICT = "http://purl.oclc.org/ooxml/spreadsheetml/main";
    public static final String P_STRICT = "http://purl.oclc.org/ooxml/presentationml/main";
    public static final String A_STRICT = "http://purl.oclc.org/ooxml/drawingml/main";
    public static final String R_STRICT = "http://purl.oclc.org/ooxml/officeDocument/relationships";

    // ODF
    public static final String OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    public static final String TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    public static final String TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    public static final String STYLE = "urn:oasis:names:tc:opendocument:xmlns:style:1.0";
    public static final String FO = "urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0";
    public static final String DRAW = "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0";
    public static final String SVG = "urn:oasis:names:tc:opendocument:xmlns:svg-compatible:1.0";
    public static final String XLINK = "http://www.w3.org/1999/xlink";
    public static final String PRESENTATION = "urn:oasis:names:tc:opendocument:xmlns:presentation:1.0";
    public static final String MANIFEST = "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0";
    public static final String CALCEXT = "urn:org:documentfoundation:names:experimental:calc:xmlns:calcext:1.0";
    public static final String OF = "urn:oasis:names:tc:opendocument:xmlns:of:1.2";

    public static String escText(String s) {
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            String rep = null;
            if (c == '&') rep = "&amp;";
            else if (c == '<') rep = "&lt;";
            else if (c == '>') rep = "&gt;";
            else if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') rep = "";
            else if (c == '￾' || c == '￿') rep = "";
            if (rep != null) {
                if (sb == null) {
                    sb = new StringBuilder(s.length() + 16);
                    sb.append(s, 0, i);
                }
                sb.append(rep);
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
    }

    public static String escAttr(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\n': sb.append("&#10;"); break;
                case '\r': sb.append("&#13;"); break;
                case '\t': sb.append("&#9;"); break;
                default:
                    if (c < 0x20 || c == '￾' || c == '￿') break;
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Qualified name for a local name with the given prefix ("" = default namespace). */
    public static String q(String prefix, String local) {
        return prefix == null || prefix.length() == 0 ? local : prefix + ":" + local;
    }

    /**
     * Rebuilds a start tag from an element with some attributes removed and/or replaced.
     * {@code set} holds name/value pairs (written names); a null value removes the attribute.
     */
    public static String startTag(XNode e, String[] set, boolean selfClose) {
        StringBuilder sb = new StringBuilder("<").append(e.qname);
        for (int i = 0; i < e.attrNames.size(); i++) {
            String name = e.attrNames.get(i);
            boolean overridden = false;
            if (set != null) {
                for (int k = 0; k < set.length; k += 2) {
                    if (set[k].equals(name)) {
                        overridden = true;
                        break;
                    }
                }
            }
            if (overridden) continue;
            sb.append(' ').append(name).append("=\"").append(escAttr(e.attrValues.get(i))).append('"');
        }
        if (set != null) {
            for (int k = 0; k < set.length; k += 2) {
                if (set[k + 1] != null) sb.append(' ').append(set[k]).append("=\"").append(escAttr(set[k + 1])).append('"');
            }
        }
        sb.append(selfClose ? "/>" : ">");
        return sb.toString();
    }

    static boolean isValidUtf8(byte[] b) {
        int i = 0, n = b.length;
        while (i < n) {
            int c = b[i] & 0xFF;
            int extra;
            if (c < 0x80) extra = 0;
            else if (c >= 0xC2 && c <= 0xDF) extra = 1;
            else if (c >= 0xE0 && c <= 0xEF) extra = 2;
            else if (c >= 0xF0 && c <= 0xF4) extra = 3;
            else return false;
            for (int k = 1; k <= extra; k++) {
                if (i + k >= n) return false;
                if ((b[i + k] & 0xC0) != 0x80) return false;
            }
            i += extra + 1;
        }
        return true;
    }
}
