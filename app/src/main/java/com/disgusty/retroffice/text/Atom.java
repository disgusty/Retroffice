package com.disgusty.retroffice.text;

/** One piece of a paragraph line captured for saving: text, an object character or a marker. */
public final class Atom {
    public static final int TEXT = 0, OBJ = 1, MARK = 2;

    public final int kind;
    public String text;
    public final Spans.Frame[] frames;
    /** RunSpan props (dialect specific) or null. */
    public final Object run;
    /** Spans.BOLD | ITALIC | UNDERLINE | STRIKE */
    public final int fmt;
    public final Spans.Obj obj;
    public final Spans.Mark mark;

    Atom(int kind, String text, Spans.Frame[] frames, Object run, int fmt, Spans.Obj obj, Spans.Mark mark) {
        this.kind = kind;
        this.text = text;
        this.frames = frames;
        this.run = run;
        this.fmt = fmt;
        this.obj = obj;
        this.mark = mark;
    }

    boolean sameRunAs(Atom o) {
        return o.kind == TEXT && kind == TEXT && o.run == run && o.fmt == fmt && sameFrames(o.frames, frames);
    }

    static boolean sameFrames(Spans.Frame[] a, Spans.Frame[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }
}
