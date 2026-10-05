package com.disgusty.retroffice.text;

import com.disgusty.retroffice.xml.XNode;

/** Format-specific reading and writing of paragraphs (WordprocessingML, ODF text, DrawingML). */
public abstract class TextDialect {
    /** Source text of the XML part the paragraphs live in. */
    public final String src;

    protected TextDialect(String src) {
        this.src = src;
    }

    /** Appends the paragraph's content to the sink and returns what's needed to rebuild it. */
    public abstract Object loadParagraph(XNode p, LoadSink sink);

    /**
     * Builds the markup for one output line of a paragraph.
     *
     * @param info  value returned by {@link #loadParagraph} for the source paragraph
     * @param first true for the first line written in place of the source paragraph (it keeps
     *              identity attributes such as ids); later lines are new paragraphs
     * @param last  true for the last line (it keeps section breaks and the like)
     */
    public abstract String emitParagraph(Object info, Line line, boolean first, boolean last, EmitContext ctx);

    /** Markup for a paragraph that lost all its lines but must stay (e.g. the only one in a cell). */
    public String emitEmptyParagraph(Object info, EmitContext ctx) {
        return emitParagraph(info, new Line(null, new java.util.ArrayList<Atom>(), ""), true, true, ctx);
    }
}
