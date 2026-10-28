package com.disgusty.retroffice.legacy;

import java.nio.charset.Charset;

/** Windows code page numbers (as used in RTF, DOC, XLS) to Java charsets, with safe fallbacks. */
final class Codepages {
    private Codepages() {
    }

    static String charsetName(int cp) {
        String n;
        switch (cp) {
            case 437: n = "IBM437"; break;
            case 850: n = "IBM850"; break;
            case 866: n = "IBM866"; break;
            case 874: n = "x-windows-874"; break;
            case 932: n = "Shift_JIS"; break;
            case 936: n = "GBK"; break;
            case 949: n = "EUC-KR"; break;
            case 950: n = "Big5"; break;
            case 1200: n = "UTF-16LE"; break;
            case 10000: n = "x-MacRoman"; break;
            case 65001: n = "UTF-8"; break;
            default:
                n = cp >= 1250 && cp <= 1258 ? "windows-" + cp : "windows-1252";
        }
        try {
            if (Charset.isSupported(n)) return n;
        } catch (Exception ignored) {
        }
        return "ISO-8859-1";
    }

    /** RTF/OLE font charset number to code page; 0 means "the document's own code page". */
    static int fromCharset(int charset) {
        switch (charset) {
            case 77: return 10000;
            case 128: return 932;
            case 129: return 949;
            case 134: return 936;
            case 136: return 950;
            case 161: return 1253;
            case 162: return 1254;
            case 163: return 1258;
            case 177: return 1255;
            case 178: return 1256;
            case 186: return 1257;
            case 204: return 1251;
            case 222: return 874;
            case 238: return 1250;
            case 255: return 437;
            default: return 0;
        }
    }

    static String decode(byte[] b, int off, int len, int cp) {
        try {
            return new String(b, off, len, charsetName(cp));
        } catch (Exception e) {
            char[] c = new char[len];
            for (int i = 0; i < len; i++) c[i] = (char) (b[off + i] & 0xFF);
            return new String(c);
        }
    }
}
