package com.disgusty.retroffice.editor;

import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;

import com.disgusty.retroffice.text.Spans;
import com.disgusty.retroffice.text.TextSegment;

/** Span bookkeeping for the editors: undo snapshots and formatting toggles. */
public final class SpanUtil {
    private SpanUtil() {
    }

    /** Spans a view must leave on the model when it detaches (model spans and the change watcher). */
    public static boolean keepOnUnbind(Object s) {
        return s instanceof TextSegment.Watcher || isOurs(s);
    }

    /** Spans that belong to the document model (as opposed to framework/IME spans). */
    public static boolean isOurs(Object s) {
        if (s instanceof Spans.ParaSpan || s instanceof Spans.RunSpan || s instanceof Spans.FrameSpan
                || s instanceof Spans.ObjSpan || s instanceof Spans.MarkSpan || s instanceof Spans.FmtBold
                || s instanceof Spans.FmtItalic || s instanceof Spans.FmtUnderline || s instanceof Spans.FmtStrike
                || s instanceof Spans.DispSize || s instanceof Spans.DispColor || s instanceof Spans.DispBullet) {
            return true;
        }
        return s.getClass().getName().equals("android.text.style.AlignmentSpan$Standard");
    }

    /** A copy of the text with only model spans. */
    public static SpannableStringBuilder snapshot(Spanned src) {
        SpannableStringBuilder s = new SpannableStringBuilder(src.toString());
        Object[] spans = src.getSpans(0, src.length(), Object.class);
        for (Object o : spans) {
            if (!isOurs(o)) continue;
            s.setSpan(o, src.getSpanStart(o), src.getSpanEnd(o), src.getSpanFlags(o));
        }
        return s;
    }

    /** Replaces the editable's content and model spans with a snapshot. */
    public static void restore(Editable e, Spanned snap) {
        // Paragraph spans are removed below, so the watcher can't see which ones change: mark all.
        TextSegment seg = TextSegment.of(e);
        if (seg != null) seg.markAllDirty();
        Object[] spans = e.getSpans(0, e.length(), Object.class);
        for (Object o : spans) if (isOurs(o)) e.removeSpan(o);
        e.replace(0, e.length(), snap.toString());
        // replace() may have stretched leftover framework spans; model spans are re-applied exactly.
        Object[] ss = snap.getSpans(0, snap.length(), Object.class);
        for (Object o : ss) {
            if (!isOurs(o)) continue;
            int st = snap.getSpanStart(o), en = snap.getSpanEnd(o);
            if (st < 0 || en > e.length()) continue;
            e.setSpan(o, st, en, snap.getSpanFlags(o));
        }
    }

    static Class<?> fmtClass(int bit) {
        switch (bit) {
            case Spans.BOLD: return Spans.FmtBold.class;
            case Spans.ITALIC: return Spans.FmtItalic.class;
            case Spans.UNDERLINE: return Spans.FmtUnderline.class;
            default: return Spans.FmtStrike.class;
        }
    }

    static Object newFmt(int bit) {
        switch (bit) {
            case Spans.BOLD: return new Spans.FmtBold();
            case Spans.ITALIC: return new Spans.FmtItalic();
            case Spans.UNDERLINE: return new Spans.FmtUnderline();
            default: return new Spans.FmtStrike();
        }
    }

    /** True if every non-newline character in [a, b) has the formatting. */
    public static boolean hasFormat(Spanned t, int a, int b, int bit) {
        if (a >= b) {
            // Caret: look at the character before it (what typing would continue).
            int p = Math.max(0, a - 1);
            if (t.length() == 0) return false;
            return covered(t, p, bit);
        }
        for (int i = a; i < b; i++) {
            if (t.charAt(i) == '\n') continue;
            if (!covered(t, i, bit)) return false;
        }
        return true;
    }

    private static boolean covered(Spanned t, int i, int bit) {
        Object[] spans = t.getSpans(i, i + 1, fmtClass(bit));
        for (Object s : spans) {
            if (t.getSpanStart(s) <= i && t.getSpanEnd(s) > i) return true;
        }
        return false;
    }

    /** Turns the formatting on or off for [a, b). */
    public static void setFormat(Editable t, int a, int b, int bit, boolean on) {
        // Formatting doesn't change the text, so the watcher won't notice: mark the paragraphs here.
        TextSegment seg = TextSegment.of(t);
        if (seg != null) seg.markDirty(a, b);
        Object[] spans = t.getSpans(a, b, fmtClass(bit));
        for (Object s : spans) {
            int st = t.getSpanStart(s), en = t.getSpanEnd(s), fl = t.getSpanFlags(s);
            if (en <= a || st >= b) continue;
            t.removeSpan(s);
            if (st < a) t.setSpan(newFmt(bit), st, a, fl);
            if (en > b) t.setSpan(newFmt(bit), b, en, fl);
        }
        if (on) t.setSpan(newFmt(bit), a, b, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
    }

    /** Word boundaries around a caret, or null if the caret isn't in/next to a word. */
    public static int[] wordAt(CharSequence t, int pos) {
        int n = t.length();
        int s = pos, e = pos;
        while (s > 0 && isWordChar(t.charAt(s - 1))) s--;
        while (e < n && isWordChar(t.charAt(e))) e++;
        return s < e ? new int[]{s, e} : null;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == '-' || c == '_';
    }
}
