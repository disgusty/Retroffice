package com.disgusty.retroffice.text;

import com.disgusty.retroffice.xml.Xml;

/** Writes plain text as ODF paragraph content, preserving spaces and tabs exactly. */
public final class OdfText {
    private OdfText() {
    }

    /** Content (without the enclosing text:p) for one line of text. */
    public static String encode(String textPrefix, String line) {
        StringBuilder sb = new StringBuilder();
        boolean prevSpace = true;
        int n = line.length();
        int lastNonSpace = n - 1;
        while (lastNonSpace >= 0 && line.charAt(lastNonSpace) == ' ') lastNonSpace--;
        int k = 0;
        while (k < n) {
            char c = line.charAt(k);
            if (c == ' ') {
                int j = k;
                while (j < n && line.charAt(j) == ' ') j++;
                int count = j - k;
                boolean trailing = k > lastNonSpace;
                if (!prevSpace && !trailing) {
                    sb.append(' ');
                    count--;
                }
                if (count > 0) {
                    sb.append('<').append(Xml.q(textPrefix, "s"));
                    if (count > 1) sb.append(' ').append(Xml.q(textPrefix, "c")).append("=\"").append(count).append('"');
                    sb.append("/>");
                }
                prevSpace = true;
                k = j;
            } else if (c == '\t') {
                sb.append('<').append(Xml.q(textPrefix, "tab")).append("/>");
                prevSpace = true;
                k++;
            } else {
                int j = k;
                while (j < n && line.charAt(j) != ' ' && line.charAt(j) != '\t') j++;
                sb.append(Xml.escText(line.substring(k, j)));
                prevSpace = false;
                k = j;
            }
        }
        return sb.toString();
    }

    /** One or more text:p elements for (possibly multi-line) text. */
    public static String paragraphs(String textPrefix, String text) {
        String p = Xml.q(textPrefix, "p");
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (String l : lines) {
            if (l.length() == 0) sb.append('<').append(p).append("/>");
            else sb.append('<').append(p).append('>').append(encode(textPrefix, l)).append("</").append(p).append('>');
        }
        return sb.toString();
    }
}
