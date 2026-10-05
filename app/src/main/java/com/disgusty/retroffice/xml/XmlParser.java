package com.disgusty.retroffice.xml;

import java.io.UnsupportedEncodingException;

/**
 * A small, strict, position-preserving XML parser.
 *
 * Android's XmlPullParser can't report source offsets, which we need to copy unchanged markup
 * verbatim on save. This parser is strict on purpose: malformed input throws instead of being
 * "repaired", so we never save a document we misunderstood.
 */
public final class XmlParser {
    /** Byte order mark (kept as a value: a literal would be invisible in the source). */
    static final char BOM = (char) 0xFEFF;

    private final String s;
    private final int n;
    private int pos;
    private final boolean namespaces;

    private XmlParser(String src, boolean namespaces) {
        this.s = src;
        this.n = src.length();
        this.namespaces = namespaces;
    }

    /** Parses a whole document. */
    public static XDoc parse(byte[] data) throws XmlException {
        return parse(decode(data));
    }

    public static XDoc parse(String src) throws XmlException {
        boolean bom = src.length() > 0 && src.charAt(0) == BOM;
        XmlParser p = new XmlParser(src, true);
        if (bom) p.pos = 1;
        XNode root = p.document();
        return new XDoc(src, root, bom);
    }

    /**
     * Parses a fragment (one or more sibling nodes) without namespace resolution — used for small
     * pieces such as a run's property block, whose prefixes match the enclosing document.
     */
    public static XNode parseFragment(String src) throws XmlException {
        XmlParser p = new XmlParser(src, false);
        XNode holder = new XNode(XNode.ELEMENT);
        holder.qname = holder.local = "#fragment";
        holder.prefix = "";
        holder.start = 0;
        holder.end = src.length();
        p.content(holder, null);
        if (p.pos < p.n) throw p.err("Unexpected content in fragment");
        return holder;
    }

    /** Decodes XML bytes, honouring a UTF-8 or UTF-16 byte order mark. */
    public static String decode(byte[] data) throws XmlException {
        try {
            if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
                return String.valueOf(BOM) + new String(data, 2, data.length - 2, "UTF-16BE");
            }
            if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
                return String.valueOf(BOM) + new String(data, 2, data.length - 2, "UTF-16LE");
            }
            String str = new String(data, "UTF-8");
            if (str.indexOf('�') >= 0 && !Xml.isValidUtf8(data)) {
                throw new XmlException("Part is not valid UTF-8", 0);
            }
            return str;
        } catch (UnsupportedEncodingException e) {
            throw new XmlException("Unsupported encoding", 0);
        }
    }

    private XNode document() throws XmlException {
        XNode root = null;
        while (pos < n) {
            skipWs();
            if (pos >= n) break;
            if (startsWith("<?")) {
                skipPi();
            } else if (startsWith("<!--")) {
                skipComment();
            } else if (startsWith("<!DOCTYPE")) {
                // Office packages never use DTDs; refusing them also rules out entity expansion tricks.
                throw err("DOCTYPE is not supported");
            } else if (s.charAt(pos) == '<') {
                if (root != null) throw err("More than one root element");
                root = element(null, null);
            } else {
                throw err("Text outside the root element");
            }
        }
        if (root == null) throw err("No root element");
        return root;
    }

    private XNode element(XNode parent, XNode.NsScope scope) throws XmlException {
        XNode e = new XNode(XNode.ELEMENT);
        e.parent = parent;
        e.start = pos;
        pos++; // '<'
        e.qname = name();
        while (true) {
            boolean ws = skipWs();
            if (pos >= n) throw err("Unterminated start tag <" + e.qname);
            char c = s.charAt(pos);
            if (c == '/') {
                if (pos + 1 >= n || s.charAt(pos + 1) != '>') throw err("Expected />");
                pos += 2;
                e.selfClosing = true;
                break;
            }
            if (c == '>') {
                pos++;
                break;
            }
            if (!ws) throw err("Expected whitespace before attribute");
            String an = name();
            skipWs();
            expect('=');
            skipWs();
            if (pos >= n) throw err("Unterminated attribute");
            char q = s.charAt(pos);
            if (q != '"' && q != '\'') throw err("Attribute value must be quoted");
            int vs = ++pos;
            int ve = s.indexOf(q, vs);
            if (ve < 0) throw err("Unterminated attribute value");
            String rawVal = s.substring(vs, ve);
            if (rawVal.indexOf('<') >= 0) throw err("'<' in attribute value");
            pos = ve + 1;
            if (e.attrNames.contains(an)) throw err("Duplicate attribute " + an);
            String val = decodeAttr(rawVal, vs);
            e.attrNames.add(an);
            e.attrValues.add(val);
            if (namespaces) {
                if (an.equals("xmlns")) scope = new XNode.NsScope("", val, scope);
                else if (an.startsWith("xmlns:")) scope = new XNode.NsScope(an.substring(6), val, scope);
            }
        }
        e.startTagEnd = pos;
        e.scope = scope;
        int colon = e.qname.indexOf(':');
        e.prefix = colon < 0 ? "" : e.qname.substring(0, colon);
        e.local = colon < 0 ? e.qname : e.qname.substring(colon + 1);
        if (namespaces) {
            e.ns = e.resolvePrefix(e.prefix);
            if (e.ns == null && colon >= 0) throw err("Undeclared prefix " + e.prefix);
        }
        if (e.selfClosing) {
            e.endTagStart = pos;
            e.end = pos;
            return e;
        }
        content(e, scope);
        // content() stops at "</"
        e.endTagStart = pos;
        pos += 2;
        String close = name();
        if (!close.equals(e.qname)) throw err("Mismatched end tag </" + close + "> for <" + e.qname + ">");
        skipWs();
        expect('>');
        e.end = pos;
        return e;
    }

    /** Parses child content until "</" (or end of input for a fragment holder). */
    private void content(XNode parent, XNode.NsScope scope) throws XmlException {
        boolean fragment = parent.qname.equals("#fragment");
        while (true) {
            if (pos >= n) {
                if (fragment) return;
                throw err("Unexpected end of document inside <" + parent.qname + ">");
            }
            char c = s.charAt(pos);
            if (c == '<') {
                if (startsWith("</")) {
                    if (fragment) throw err("Unexpected end tag in fragment");
                    return;
                }
                if (startsWith("<!--")) {
                    XNode cm = new XNode(XNode.COMMENT);
                    cm.start = pos;
                    skipComment();
                    cm.end = pos;
                    cm.parent = parent;
                    parent.children.add(cm);
                } else if (startsWith("<![CDATA[")) {
                    int st = pos;
                    int e = s.indexOf("]]>", pos + 9);
                    if (e < 0) throw err("Unterminated CDATA");
                    XNode t = new XNode(XNode.CDATA);
                    t.start = st;
                    t.text = s.substring(pos + 9, e);
                    pos = e + 3;
                    t.end = pos;
                    t.parent = parent;
                    parent.children.add(t);
                } else if (startsWith("<?")) {
                    XNode pi = new XNode(XNode.PI);
                    pi.start = pos;
                    skipPi();
                    pi.end = pos;
                    pi.parent = parent;
                    parent.children.add(pi);
                } else if (startsWith("<!")) {
                    throw err("Unsupported markup declaration");
                } else {
                    parent.children.add(element(parent, scope));
                }
            } else {
                int st = pos;
                int lt = s.indexOf('<', pos);
                if (lt < 0) lt = n;
                XNode t = new XNode(XNode.TEXT);
                t.start = st;
                t.end = lt;
                t.text = decodeText(s.substring(st, lt), st);
                t.parent = parent;
                pos = lt;
                parent.children.add(t);
            }
        }
    }

    private String name() throws XmlException {
        int st = pos;
        while (pos < n) {
            char c = s.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '>' || c == '/' || c == '='
                    || c == '<' || c == '"' || c == '\'') break;
            pos++;
        }
        if (pos == st) throw err("Expected a name");
        return s.substring(st, pos);
    }

    private boolean skipWs() {
        int st = pos;
        while (pos < n) {
            char c = s.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') pos++;
            else break;
        }
        return pos > st;
    }

    private void skipComment() throws XmlException {
        int e = s.indexOf("-->", pos + 4);
        if (e < 0) throw err("Unterminated comment");
        pos = e + 3;
    }

    private void skipPi() throws XmlException {
        int e = s.indexOf("?>", pos + 2);
        if (e < 0) throw err("Unterminated processing instruction");
        pos = e + 2;
    }

    private void expect(char c) throws XmlException {
        if (pos >= n || s.charAt(pos) != c) throw err("Expected '" + c + "'");
        pos++;
    }

    private boolean startsWith(String t) {
        return s.startsWith(t, pos);
    }

    private String decodeText(String raw, int offset) throws XmlException {
        if (raw.indexOf('&') < 0) {
            if (raw.indexOf('\r') >= 0) return normalizeNewlines(raw);
            return raw;
        }
        return normalizeNewlines(decodeEntities(raw, offset));
    }

    private String decodeAttr(String raw, int offset) throws XmlException {
        String v = raw.indexOf('&') < 0 ? raw : decodeEntities(raw, offset);
        // Attribute-value normalization: literal whitespace becomes spaces.
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\t') >= 0 || raw.indexOf('\r') >= 0) {
            StringBuilder sb = new StringBuilder(v.length());
            // Only literal (not character-referenced) whitespace is normalized; good enough for office files.
            String norm = raw.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
            sb.append(norm.indexOf('&') < 0 ? norm : decodeEntities(norm, offset));
            return sb.toString();
        }
        return v;
    }

    private static String normalizeNewlines(String t) {
        if (t.indexOf('\r') < 0) return t;
        return t.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String decodeEntities(String raw, int offset) throws XmlException {
        StringBuilder sb = new StringBuilder(raw.length());
        int i = 0, len = raw.length();
        while (i < len) {
            char c = raw.charAt(i);
            if (c != '&') {
                sb.append(c);
                i++;
                continue;
            }
            int semi = raw.indexOf(';', i);
            if (semi < 0) throw new XmlException("Unterminated entity", offset + i);
            String ent = raw.substring(i + 1, semi);
            if (ent.equals("lt")) sb.append('<');
            else if (ent.equals("gt")) sb.append('>');
            else if (ent.equals("amp")) sb.append('&');
            else if (ent.equals("quot")) sb.append('"');
            else if (ent.equals("apos")) sb.append('\'');
            else if (ent.startsWith("#")) {
                int cp;
                try {
                    cp = ent.startsWith("#x") || ent.startsWith("#X")
                            ? Integer.parseInt(ent.substring(2), 16)
                            : Integer.parseInt(ent.substring(1));
                } catch (NumberFormatException e) {
                    throw new XmlException("Bad character reference &" + ent + ";", offset + i);
                }
                if (cp < 0 || cp > 0x10FFFF) throw new XmlException("Bad character reference", offset + i);
                if (cp >= 0x10000) {
                    cp -= 0x10000;
                    sb.append((char) (0xD800 + (cp >> 10)));
                    sb.append((char) (0xDC00 + (cp & 0x3FF)));
                } else {
                    sb.append((char) cp);
                }
            } else {
                throw new XmlException("Unknown entity &" + ent + ";", offset + i);
            }
            i = semi + 1;
        }
        return sb.toString();
    }

    private XmlException err(String msg) {
        return new XmlException(msg, pos);
    }
}
