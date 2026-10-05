package com.disgusty.retroffice.doc;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

/** Detects and preserves the encoding, BOM and line endings of plain-text files. */
public final class TextCodec {
    public final String charset;
    public final boolean bom;
    public final String lineEnding;

    private TextCodec(String charset, boolean bom, String lineEnding) {
        this.charset = charset;
        this.bom = bom;
        this.lineEnding = lineEnding;
    }

    /** Decoded text with "\n" line breaks, plus the codec that reproduces the original bytes. */
    public static final class Decoded {
        public final String text;
        public final TextCodec codec;

        Decoded(String text, TextCodec codec) {
            this.text = text;
            this.codec = codec;
        }
    }

    public static boolean looksBinary(byte[] b) {
        int n = Math.min(b.length, 8192);
        boolean utf16 = n >= 2 && (((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) || ((b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF));
        if (utf16) return false;
        for (int i = 0; i < n; i++) if (b[i] == 0) return true;
        return false;
    }

    public static Decoded decode(byte[] b) throws UnsupportedEncodingException {
        String cs;
        int skip = 0;
        boolean bom = false;
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            cs = "UTF-8";
            skip = 3;
            bom = true;
        } else if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            cs = "UTF-16LE";
            skip = 2;
            bom = true;
        } else if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            cs = "UTF-16BE";
            skip = 2;
            bom = true;
        } else if (validUtf8(b)) {
            cs = "UTF-8";
        } else {
            cs = guessSingleByte(b);
        }
        String raw = new String(b, skip, b.length - skip, cs);
        int crlf = 0, lf = 0, cr = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\r') {
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\n') {
                    crlf++;
                    i++;
                } else {
                    cr++;
                }
            } else if (c == '\n') {
                lf++;
            }
        }
        String le = crlf > lf && crlf >= cr ? "\r\n" : (cr > lf ? "\r" : "\n");
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        return new Decoded(text, new TextCodec(cs, bom, le));
    }

    private static String guessSingleByte(byte[] b) {
        int high = 0, cyr = 0;
        for (byte x : b) {
            int v = x & 0xFF;
            if (v >= 0x80) high++;
            if (v >= 0xC0) cyr++;
        }
        if (high > 0 && cyr * 2 >= high && supported("windows-1251")) return "windows-1251";
        if (supported("windows-1252")) return "windows-1252";
        return "ISO-8859-1";
    }

    private static boolean supported(String cs) {
        try {
            return Charset.isSupported(cs);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean validUtf8(byte[] b) {
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
                if (i + k >= n || (b[i + k] & 0xC0) != 0x80) return false;
            }
            i += extra + 1;
        }
        return true;
    }

    /** True if every character of {@code text} survives this codec. */
    public boolean canEncode(String text) {
        if (charset.startsWith("UTF")) return true;
        try {
            return Charset.forName(charset).newEncoder().canEncode(text);
        } catch (Exception e) {
            return false;
        }
    }

    /** UTF-8 codec with the same line endings (used when the original charset can't hold the text). */
    public TextCodec asUtf8() {
        return new TextCodec("UTF-8", bom, lineEnding);
    }

    public byte[] encode(String text) throws UnsupportedEncodingException {
        String t = lineEnding.equals("\n") ? text : text.replace("\n", lineEnding);
        byte[] body = t.getBytes(charset.equals("UTF-16LE") || charset.equals("UTF-16BE") ? charset : charset);
        if (!bom) return body;
        byte[] mark;
        if (charset.equals("UTF-8")) mark = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        else if (charset.equals("UTF-16LE")) mark = new byte[]{(byte) 0xFF, (byte) 0xFE};
        else if (charset.equals("UTF-16BE")) mark = new byte[]{(byte) 0xFE, (byte) 0xFF};
        else mark = new byte[0];
        byte[] out = new byte[mark.length + body.length];
        System.arraycopy(mark, 0, out, 0, mark.length);
        System.arraycopy(body, 0, out, mark.length, body.length);
        return out;
    }
}
