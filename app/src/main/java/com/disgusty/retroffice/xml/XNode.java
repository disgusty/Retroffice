package com.disgusty.retroffice.xml;

import java.util.ArrayList;

/**
 * A node of a parsed XML document that remembers exactly where it came from in the source text.
 *
 * Saving never re-serializes a tree: it copies the untouched source ranges byte-for-byte and only
 * splices new markup over the nodes that actually changed (see {@link Patcher}). That is what keeps
 * files we don't fully understand intact.
 */
public final class XNode {
    public static final int ELEMENT = 1, TEXT = 2, CDATA = 3, COMMENT = 4, PI = 5;

    public final int type;
    public XNode parent;
    public final ArrayList<XNode> children = new ArrayList<XNode>();

    /** Element name as written (prefix:local), its prefix ("" when none) and resolved namespace. */
    public String qname, prefix, local, ns;
    /** Attribute names as written and their decoded values (parallel lists). */
    public final ArrayList<String> attrNames = new ArrayList<String>();
    public final ArrayList<String> attrValues = new ArrayList<String>();
    NsScope scope;

    /** Decoded character data for TEXT and CDATA nodes. */
    public String text;

    /** Source offsets: [start, end) is the whole node; [start, startTagEnd) its start tag. */
    public int start, startTagEnd, endTagStart, end;
    public boolean selfClosing;

    XNode(int type) {
        this.type = type;
    }

    public boolean isElement() {
        return type == ELEMENT;
    }

    public boolean is(String nsUri, String localName) {
        return type == ELEMENT && localName.equals(local) && eq(nsUri, ns);
    }

    /** Attribute value by namespace URI + local name (null namespace = unprefixed attribute). */
    public String attr(String nsUri, String localName) {
        for (int i = 0; i < attrNames.size(); i++) {
            String n = attrNames.get(i);
            int c = n.indexOf(':');
            String p = c < 0 ? "" : n.substring(0, c);
            String l = c < 0 ? n : n.substring(c + 1);
            if (!l.equals(localName)) continue;
            if (p.equals("xmlns") || n.equals("xmlns")) continue;
            String u = c < 0 ? null : resolvePrefix(p);
            if (eq(nsUri, u)) return attrValues.get(i);
        }
        return null;
    }

    /** Attribute value by its literal written name, e.g. "r" or "w:val". */
    public String attrRaw(String name) {
        int i = attrNames.indexOf(name);
        return i < 0 ? null : attrValues.get(i);
    }

    public String resolvePrefix(String p) {
        if (p.equals("xml")) return Xml.NS_XML;
        for (NsScope s = scope; s != null; s = s.parent) {
            if (s.prefix.equals(p)) return s.uri;
        }
        return null;
    }

    /** The prefix currently bound to a namespace URI here, or null. "" means default namespace. */
    public String prefixFor(String uri) {
        for (NsScope s = scope; s != null; s = s.parent) {
            if (s.uri.equals(uri)) {
                // Make sure the binding isn't shadowed by a nearer declaration of the same prefix.
                if (eq(resolvePrefix(s.prefix), uri)) return s.prefix;
            }
        }
        return null;
    }

    public XNode child(String nsUri, String localName) {
        for (int i = 0; i < children.size(); i++) {
            XNode c = children.get(i);
            if (c.is(nsUri, localName)) return c;
        }
        return null;
    }

    public ArrayList<XNode> childrenNamed(String nsUri, String localName) {
        ArrayList<XNode> out = new ArrayList<XNode>();
        for (int i = 0; i < children.size(); i++) {
            XNode c = children.get(i);
            if (c.is(nsUri, localName)) out.add(c);
        }
        return out;
    }

    /** First descendant (depth-first) with the given name. */
    public XNode find(String nsUri, String localName) {
        for (int i = 0; i < children.size(); i++) {
            XNode c = children.get(i);
            if (c.is(nsUri, localName)) return c;
            XNode d = c.find(nsUri, localName);
            if (d != null) return d;
        }
        return null;
    }

    public void findAll(String nsUri, String localName, ArrayList<XNode> out) {
        for (int i = 0; i < children.size(); i++) {
            XNode c = children.get(i);
            if (c.is(nsUri, localName)) out.add(c);
            c.findAll(nsUri, localName, out);
        }
    }

    /** All character data below this node, concatenated. */
    public String textContent() {
        StringBuilder sb = new StringBuilder();
        appendText(sb);
        return sb.toString();
    }

    private void appendText(StringBuilder sb) {
        if (type == TEXT || type == CDATA) {
            sb.append(text);
            return;
        }
        for (int i = 0; i < children.size(); i++) children.get(i).appendText(sb);
    }

    /** Exact source markup of this node. */
    public String raw(String src) {
        return src.substring(start, end);
    }

    public String rawStartTag(String src) {
        return src.substring(start, startTagEnd);
    }

    /** The end tag as written, or "" for a self-closing element. */
    public String rawEndTag(String src) {
        return selfClosing ? "" : src.substring(endTagStart, end);
    }

    /** Markup between the start and end tags. */
    public String rawInner(String src) {
        return selfClosing ? "" : src.substring(startTagEnd, endTagStart);
    }

    static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    static final class NsScope {
        final String prefix, uri;
        final NsScope parent;

        NsScope(String prefix, String uri, NsScope parent) {
            this.prefix = prefix;
            this.uri = uri;
            this.parent = parent;
        }
    }
}
