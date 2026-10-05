package com.disgusty.retroffice.xml;

import java.io.UnsupportedEncodingException;

/** A parsed XML part: the original text plus its node tree. */
public final class XDoc {
    public final String src;
    public final XNode root;
    public final boolean hasBom;

    XDoc(String src, XNode root, boolean hasBom) {
        this.src = src;
        this.root = root;
        this.hasBom = hasBom;
    }

    /** Encodes (possibly patched) source text back to UTF-8, keeping a BOM if one was there. */
    public static byte[] encode(String text) {
        try {
            return text.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
