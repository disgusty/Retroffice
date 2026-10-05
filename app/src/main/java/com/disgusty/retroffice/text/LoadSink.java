package com.disgusty.retroffice.text;

import android.text.Spannable;
import android.text.SpannableStringBuilder;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Collects a segment's text and spans while paragraphs are read. Spans are only applied at the end:
 * appending to a SpannableStringBuilder would otherwise stretch inclusive spans over the next
 * paragraph.
 */
public final class LoadSink {
    final SpannableStringBuilder sb = new SpannableStringBuilder();
    private final ArrayList<Object> spanObjs = new ArrayList<Object>();
    private final ArrayList<int[]> spanRanges = new ArrayList<int[]>();
    private final IdentityHashMap<Spans.Frame, int[]> frames = new IdentityHashMap<Spans.Frame, int[]>();
    private final IdentityHashMap<Object, int[]> runs = new IdentityHashMap<Object, int[]>();
    private final int[] fmtStart = {-1, -1, -1, -1};
    private int markSeq;
    /** Display defaults for the current paragraph (applied to every piece). */
    float paraSize = 1f;
    int paraColor = 0;

    public int length() {
        return sb.length();
    }

    public void setParagraphDisplay(float relSize, int color) {
        paraSize = relSize;
        paraColor = color;
    }

    /** Appends text. {@code relSize} 0 means "use paragraph default"; color 0 means none. */
    public void text(String t, Spans.Frame[] fr, Object run, int fmt, float relSize, int color) {
        if (t.length() == 0) return;
        int s = sb.length();
        sb.append(t);
        piece(s, sb.length(), fr, run, fmt, relSize, color);
    }

    public void obj(Spans.Obj o, Spans.Frame[] fr, Object run, int fmt) {
        int s = sb.length();
        sb.append('￼');
        span(new Spans.ObjSpan(o), s, s + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        piece(s, s + 1, fr, run, fmt, 0, 0);
    }

    public void mark(String xml, Spans.Frame[] fr) {
        int p = sb.length();
        span(new Spans.MarkSpan(new Spans.Mark(xml, markSeq++, fr)), p, p, Spannable.SPAN_POINT_POINT);
    }

    private void piece(int s, int e, Spans.Frame[] fr, Object run, int fmt, float relSize, int color) {
        for (Spans.Frame f : fr) {
            int[] r = frames.get(f);
            if (r == null) frames.put(f, new int[]{s, e});
            else r[1] = e;
        }
        if (run != null) {
            int[] r = runs.get(run);
            if (r == null) runs.put(run, new int[]{s, e});
            else r[1] = e;
        }
        int[] bits = {Spans.BOLD, Spans.ITALIC, Spans.UNDERLINE, Spans.STRIKE};
        for (int k = 0; k < 4; k++) {
            boolean on = (fmt & bits[k]) != 0;
            if (on && fmtStart[k] < 0) fmtStart[k] = s;
            if (!on && fmtStart[k] >= 0) closeFmt(k, s);
        }
        // Display size/color: consecutive pieces with the same value share one span.
        float size = relSize > 0 ? relSize : paraSize;
        if (Math.abs(size - 1f) <= 0.01f) size = 0;
        if (size != dispSize || s != dispSizeEnd) {
            closeDispSize();
            dispSize = size;
            dispSizeStart = s;
        }
        dispSizeEnd = e;
        int col = color != 0 ? color : paraColor;
        if (col != dispColor || s != dispColorEnd) {
            closeDispColor();
            dispColor = col;
            dispColorStart = s;
        }
        dispColorEnd = e;
    }

    private float dispSize;
    private int dispSizeStart = -1, dispSizeEnd = -1;
    private int dispColor, dispColorStart = -1, dispColorEnd = -1;

    private void closeDispSize() {
        if (dispSize > 0 && dispSizeStart >= 0 && dispSizeEnd > dispSizeStart) {
            span(new Spans.DispSize(dispSize), dispSizeStart, dispSizeEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        }
        dispSize = 0;
        dispSizeStart = dispSizeEnd = -1;
    }

    private void closeDispColor() {
        if (dispColor != 0 && dispColorStart >= 0 && dispColorEnd > dispColorStart) {
            span(new Spans.DispColor(dispColor), dispColorStart, dispColorEnd, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        }
        dispColor = 0;
        dispColorStart = dispColorEnd = -1;
    }

    private void closeFmt(int k, int end) {
        int s = fmtStart[k];
        fmtStart[k] = -1;
        if (end <= s) return;
        Object o;
        switch (k) {
            case 0: o = new Spans.FmtBold(); break;
            case 1: o = new Spans.FmtItalic(); break;
            case 2: o = new Spans.FmtUnderline(); break;
            default: o = new Spans.FmtStrike(); break;
        }
        span(o, s, end, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
    }

    /** Ends a paragraph: closes formatting so it never bleeds across the paragraph separator. */
    public void endParagraph() {
        int end = sb.length();
        for (int k = 0; k < 4; k++) if (fmtStart[k] >= 0) closeFmt(k, end);
        closeDispSize();
        closeDispColor();
        for (Map.Entry<Spans.Frame, int[]> en : frames.entrySet()) {
            Spans.Frame f = en.getKey();
            int[] r = en.getValue();
            span(new Spans.FrameSpan(f), r[0], r[1],
                    f.extendsOnType ? Spannable.SPAN_EXCLUSIVE_INCLUSIVE : Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        frames.clear();
        for (Map.Entry<Object, int[]> en : runs.entrySet()) {
            int[] r = en.getValue();
            span(new Spans.RunSpan(en.getKey()), r[0], r[1], Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        }
        runs.clear();
        paraSize = 1f;
        paraColor = 0;
    }

    public void span(Object o, int s, int e, int flags) {
        spanObjs.add(o);
        spanRanges.add(new int[]{s, e, flags});
    }

    void newline() {
        sb.append('\n');
    }

    void applySpans() {
        for (int i = 0; i < spanObjs.size(); i++) {
            int[] r = spanRanges.get(i);
            sb.setSpan(spanObjs.get(i), r[0], r[1], r[2]);
        }
        spanObjs.clear();
        spanRanges.clear();
    }
}
