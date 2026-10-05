package com.disgusty.retroffice.text;

import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

import com.disgusty.retroffice.xml.Patcher;
import com.disgusty.retroffice.xml.XNode;

/**
 * A run of consecutive source paragraphs edited as one text: each paragraph is one line.
 *
 * Every edit marks the paragraphs it touches as dirty (see {@link Watcher}). On save, clean
 * paragraphs keep their exact original markup; dirty ones are mapped back to the source
 * paragraph they grew from and rebuilt by the dialect from its original properties.
 *
 * Nothing here scans the whole text per paragraph: on old Android every span lookup in a
 * SpannableStringBuilder is linear, so per-paragraph scans made big documents take minutes.
 */
public final class TextSegment {
    public final TextDialect dialect;
    public final ArrayList<XNode> nodes = new ArrayList<XNode>();
    private final HashMap<XNode, Object> infos = new HashMap<XNode, Object>();
    private final HashMap<XNode, Integer> nodeIndex = new HashMap<XNode, Integer>();
    /** Plain text of each paragraph as loaded: a clean paragraph must still read the same. */
    private final HashMap<XNode, String> origPlain = new HashMap<XNode, String>();
    /** Containers that must keep at least one paragraph (table cell, text body...). */
    private final ArrayList<XNode> mustKeepParents = new ArrayList<XNode>();
    /** The live text the editor works on. */
    public SpannableStringBuilder text;
    /** Incremented on every change; documents compare it to tell whether they're modified. */
    public int modCount;

    public TextSegment(TextDialect dialect) {
        this.dialect = dialect;
    }

    public void requireParagraphIn(XNode container) {
        mustKeepParents.add(container);
    }

    /** Reads the given paragraphs. Call once. */
    public void load(List<XNode> paragraphs) {
        LoadSink sink = new LoadSink();
        for (int i = 0; i < paragraphs.size(); i++) {
            XNode p = paragraphs.get(i);
            if (i > 0) sink.newline();
            int s = sink.length();
            Object info = dialect.loadParagraph(p, sink);
            sink.endParagraph();
            int e = sink.length();
            sink.span(new Spans.ParaSpan(p), s, e, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            nodes.add(p);
            infos.put(p, info);
            nodeIndex.put(p, i);
            origPlain.put(p, sink.sb.subSequence(s, e).toString());
        }
        sink.applySpans();
        text = sink.sb;
        text.setSpan(new Watcher(this), 0, text.length(), Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }

    // ------------------------------------------------------------------ change tracking

    /** Attached to the text: marks paragraphs touched by any text change as dirty. */
    public static final class Watcher implements TextWatcher {
        final TextSegment seg;

        Watcher(TextSegment seg) {
            this.seg = seg;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            seg.markDirty(start, start + count);
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            seg.modCount++;
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }

    /** Marks every paragraph overlapping [a, b] (and its neighbours at the edges) as dirty. */
    public void markDirty(int a, int b) {
        int len = text.length();
        int qa = Math.max(0, Math.min(a, len) - 1), qb = Math.min(len, Math.max(b, a) + 1);
        Spans.ParaSpan[] ps = text.getSpans(qa, qb, Spans.ParaSpan.class);
        for (Spans.ParaSpan p : ps) p.dirty = true;
        modCount++;
    }

    public void markAllDirty() {
        Spans.ParaSpan[] ps = text.getSpans(0, text.length(), Spans.ParaSpan.class);
        for (Spans.ParaSpan p : ps) p.dirty = true;
        modCount++;
    }

    /** The segment owning a text, or null (used by formatting changes, which don't change text). */
    public static TextSegment of(Spanned t) {
        Watcher[] w = t.getSpans(0, t.length(), Watcher.class);
        return w.length > 0 ? w[0].seg : null;
    }

    // ------------------------------------------------------------------ saving

    /** Snapshots the current text into lines. Must run on the thread that owns {@link #text}. */
    public ArrayList<Line> extractLines() {
        SpannableStringBuilder t = text;
        String all = t.toString();
        int len = all.length();

        // Paragraph spans with their positions, looked up once and sorted by start.
        Spans.ParaSpan[] paras = t.getSpans(0, len, Spans.ParaSpan.class);
        final int n = paras.length;
        final int[] ps = new int[n], pe = new int[n], pi = new int[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            ps[i] = t.getSpanStart(paras[i]);
            pe[i] = t.getSpanEnd(paras[i]);
            Integer ix = nodeIndex.get(paras[i].node);
            pi[i] = ix == null ? -1 : ix;
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                if (ps[x] != ps[y]) return ps[x] < ps[y] ? -1 : 1;
                return pi[x] < pi[y] ? -1 : (pi[x] == pi[y] ? 0 : 1);
            }
        });

        // Line boundaries and their paragraphs.
        ArrayList<int[]> bounds = new ArrayList<int[]>();
        int[] lineNode;
        {
            int ls = 0;
            while (true) {
                int nl = all.indexOf('\n', ls);
                int le = nl < 0 ? len : nl;
                bounds.add(new int[]{ls, le});
                if (nl < 0) break;
                ls = nl + 1;
            }
            lineNode = new int[bounds.size()];
            int j = 0, minIdx = 0;
            for (int li = 0; li < bounds.size(); li++) {
                int ls2 = bounds.get(li)[0], le2 = bounds.get(li)[1];
                while (j < n && ps[order[j]] <= ls2) j++;
                // Covering paragraph with the largest start (look back a little; spans rarely overlap).
                int best = -1;
                for (int k = j - 1, steps = 0; k >= 0 && steps < 64; k--, steps++) {
                    int o = order[k];
                    if (pe[o] >= ls2 && pi[o] >= 0) {
                        if (best < 0 || ps[o] > ps[best] || (ps[o] == ps[best] && pi[o] > pi[best])) best = o;
                        if (best >= 0 && ps[o] < ps[best]) break;
                    }
                }
                int idx;
                if (best >= 0) idx = pi[best];
                else if (j < n && ps[order[j]] <= le2 && pi[order[j]] >= 0) idx = pi[order[j]];
                else idx = minIdx;
                idx = Math.max(idx, minIdx);
                lineNode[li] = idx;
                minIdx = idx;
            }
        }

        // A paragraph is clean when untouched, still a single line, and reads as loaded.
        int[] count = new int[nodes.size()];
        for (int li = 0; li < lineNode.length; li++) count[lineNode[li]]++;
        boolean[] dirtyNode = new boolean[nodes.size()];
        for (int i = 0; i < n; i++) if (pi[i] >= 0 && paras[i].dirty) dirtyNode[pi[i]] = true;
        for (int k = 0; k < nodes.size(); k++) if (count[k] != 1) dirtyNode[k] = true;
        for (int li = 0; li < lineNode.length; li++) {
            int k = lineNode[li];
            if (dirtyNode[k]) continue;
            int[] b = bounds.get(li);
            if (!all.substring(b[0], b[1]).equals(origPlain.get(nodes.get(k)))) dirtyNode[k] = true;
        }

        ArrayList<Line> out = new ArrayList<Line>(lineNode.length);
        for (int li = 0; li < lineNode.length; li++) {
            int[] b = bounds.get(li);
            XNode node = nodes.get(lineNode[li]);
            if (!dirtyNode[lineNode[li]]) out.add(new Line(node, null, all.substring(b[0], b[1])));
            else out.add(buildLine(t, all, b[0], b[1], node));
        }
        return out;
    }

    /** Spans of one kind overlapping a line, with their positions resolved once. */
    private static final class Ranges {
        final Object[] spans;
        final int[] s, e;

        Ranges(Spanned t, int ls, int le, Class<?> kind) {
            spans = t.getSpans(ls, le, kind);
            s = new int[spans.length];
            e = new int[spans.length];
            for (int i = 0; i < spans.length; i++) {
                s[i] = t.getSpanStart(spans[i]);
                e[i] = t.getSpanEnd(spans[i]);
            }
        }

        boolean covers(int i) {
            for (int k = 0; k < spans.length; k++) if (s[k] <= i && e[k] > i) return true;
            return false;
        }
    }

    private Line buildLine(SpannableStringBuilder t, String all, int ls, int le, XNode node) {
        Ranges frames = new Ranges(t, ls, le, Spans.FrameSpan.class);
        Ranges runs = new Ranges(t, ls, le, Spans.RunSpan.class);
        Ranges objs = new Ranges(t, ls, le, Spans.ObjSpan.class);
        Ranges bold = new Ranges(t, ls, le, Spans.FmtBold.class);
        Ranges ital = new Ranges(t, ls, le, Spans.FmtItalic.class);
        Ranges und = new Ranges(t, ls, le, Spans.FmtUnderline.class);
        Ranges strike = new Ranges(t, ls, le, Spans.FmtStrike.class);
        Ranges marks = new Ranges(t, ls, le, Spans.MarkSpan.class);
        // Marks by position, in document order.
        HashMap<Integer, ArrayList<Spans.Mark>> marksAt = new HashMap<Integer, ArrayList<Spans.Mark>>();
        for (int k = 0; k < marks.spans.length; k++) {
            int p = marks.s[k];
            if (p < ls || p > le) continue;
            ArrayList<Spans.Mark> l = marksAt.get(p);
            if (l == null) {
                l = new ArrayList<Spans.Mark>();
                marksAt.put(p, l);
            }
            l.add(((Spans.MarkSpan) marks.spans[k]).mark);
        }
        for (ArrayList<Spans.Mark> l : marksAt.values()) {
            Collections.sort(l, new Comparator<Spans.Mark>() {
                @Override
                public int compare(Spans.Mark a, Spans.Mark b) {
                    return a.seq < b.seq ? -1 : (a.seq == b.seq ? 0 : 1);
                }
            });
        }

        ArrayList<Atom> atoms = new ArrayList<Atom>();
        for (int i = ls; i <= le; i++) {
            ArrayList<Spans.Mark> ms = marksAt.get(i);
            if (ms != null) {
                for (Spans.Mark m : ms) atoms.add(new Atom(Atom.MARK, null, m.frames, null, 0, null, m));
            }
            if (i == le) break;
            char c = all.charAt(i);
            Spans.Frame[] fr = framesAt(frames, i);
            Object run = runAt(runs, i, ls, le);
            int fmt = (bold.covers(i) ? Spans.BOLD : 0) | (ital.covers(i) ? Spans.ITALIC : 0)
                    | (und.covers(i) ? Spans.UNDERLINE : 0) | (strike.covers(i) ? Spans.STRIKE : 0);
            if (c == OBJ_CHAR) {
                Spans.Obj obj = null;
                for (int k = 0; k < objs.spans.length; k++) {
                    if (objs.s[k] == i) {
                        obj = ((Spans.ObjSpan) objs.spans[k]).obj;
                        break;
                    }
                }
                // An object character whose object was removed is dropped.
                if (obj != null) atoms.add(new Atom(Atom.OBJ, null, fr, run, fmt, obj, null));
                continue;
            }
            String ch = String.valueOf(c);
            Atom last = atoms.isEmpty() ? null : atoms.get(atoms.size() - 1);
            Atom a = new Atom(Atom.TEXT, ch, fr, run, fmt, null, null);
            if (last != null && last.sameRunAs(a)) last.text = last.text + ch;
            else atoms.add(a);
        }
        return new Line(node, atoms, all.substring(ls, le));
    }

    static final char OBJ_CHAR = (char) 0xFFFC;

    private static Spans.Frame[] framesAt(Ranges r, int i) {
        ArrayList<Spans.Frame> l = null;
        for (int k = 0; k < r.spans.length; k++) {
            if (r.s[k] <= i && r.e[k] > i) {
                Spans.Frame f = ((Spans.FrameSpan) r.spans[k]).frame;
                if (l == null) l = new ArrayList<Spans.Frame>();
                if (!l.contains(f)) l.add(f);
            }
        }
        if (l == null) return NO_FRAMES;
        Spans.Frame[] a = l.toArray(new Spans.Frame[l.size()]);
        Arrays.sort(a, new Comparator<Spans.Frame>() {
            @Override
            public int compare(Spans.Frame x, Spans.Frame y) {
                return x.depth < y.depth ? -1 : (x.depth == y.depth ? 0 : 1);
            }
        });
        return a;
    }

    /** Run properties for a character; new text before any run borrows the nearest run's. */
    private static Object runAt(Ranges r, int i, int ls, int le) {
        int best = -1, bestStart = -1;
        for (int k = 0; k < r.spans.length; k++) {
            if (r.s[k] <= i && r.e[k] > i && r.s[k] >= bestStart) {
                best = k;
                bestStart = r.s[k];
            }
        }
        if (best >= 0) return ((Spans.RunSpan) r.spans[best]).props;
        int next = -1, prev = -1, nextStart = Integer.MAX_VALUE, prevEnd = -1;
        for (int k = 0; k < r.spans.length; k++) {
            if (r.s[k] > i && r.s[k] < nextStart && r.s[k] <= le) {
                next = k;
                nextStart = r.s[k];
            }
            if (r.e[k] <= i && r.e[k] > prevEnd && r.e[k] >= ls) {
                prev = k;
                prevEnd = r.e[k];
            }
        }
        if (next >= 0) return ((Spans.RunSpan) r.spans[next]).props;
        if (prev >= 0) return ((Spans.RunSpan) r.spans[prev]).props;
        return null;
    }

    static final Spans.Frame[] NO_FRAMES = new Spans.Frame[0];

    /** Adds the replacements needed to write {@code lines} back into the source part. */
    public void emitPatches(List<Line> lines, Patcher patcher, EmitContext ctx) {
        LinkedHashMap<XNode, ArrayList<Line>> groups = new LinkedHashMap<XNode, ArrayList<Line>>();
        for (Line l : lines) {
            ArrayList<Line> g = groups.get(l.node);
            if (g == null) {
                g = new ArrayList<Line>();
                groups.put(l.node, g);
            }
            g.add(l);
        }
        HashMap<XNode, Boolean> survivingParent = new HashMap<XNode, Boolean>();
        for (XNode nd : nodes) {
            if (groups.containsKey(nd)) survivingParent.put(nd.parent, Boolean.TRUE);
        }
        HashMap<XNode, Boolean> keptParent = new HashMap<XNode, Boolean>();
        for (XNode node : nodes) {
            ArrayList<Line> g = groups.get(node);
            Object info = infos.get(node);
            if (g == null || g.isEmpty()) {
                XNode parent = node.parent;
                boolean keep = parent != null && mustKeepParents.contains(parent)
                        && !survivingParent.containsKey(parent) && !keptParent.containsKey(parent);
                if (keep) {
                    keptParent.put(parent, Boolean.TRUE);
                    patcher.replace(node, dialect.emitEmptyParagraph(info, ctx));
                } else {
                    patcher.replace(node, "");
                }
                continue;
            }
            if (g.size() == 1 && g.get(0).atoms == null) continue; // clean: original markup stays
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < g.size(); k++) {
                sb.append(dialect.emitParagraph(info, g.get(k), k == 0, k == g.size() - 1, ctx));
            }
            patcher.replace(node, sb.toString());
        }
    }

    /** Plain text of the segment (for search/word counts/text export). */
    public String plainText() {
        return text.toString().replace(OBJ_CHAR, ' ');
    }
}
