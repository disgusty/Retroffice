package com.disgusty.retroffice.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Applies non-overlapping replacements to a source text. Everything outside the replaced ranges is
 * copied unchanged, so a part with no edits comes out identical to what was read.
 */
public final class Patcher {
    private final String src;
    private final ArrayList<int[]> ranges = new ArrayList<int[]>();
    private final ArrayList<String> texts = new ArrayList<String>();

    public Patcher(String src) {
        this.src = src;
    }

    public void replace(int start, int end, String text) {
        if (start < 0 || end < start || end > src.length()) throw new IllegalArgumentException("Bad range");
        ranges.add(new int[]{start, end, ranges.size()});
        texts.add(text);
    }

    public void replace(XNode node, String text) {
        replace(node.start, node.end, text);
    }

    public void insert(int at, String text) {
        replace(at, at, text);
    }

    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    public String apply() {
        ArrayList<int[]> sorted = new ArrayList<int[]>(ranges);
        Collections.sort(sorted, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                if (a[0] != b[0]) return a[0] < b[0] ? -1 : 1;
                // Pure insertions at a position go before a replacement starting there; otherwise keep order added.
                int la = a[1] - a[0], lb = b[1] - b[0];
                if ((la == 0) != (lb == 0)) return la == 0 ? -1 : 1;
                return a[2] < b[2] ? -1 : (a[2] == b[2] ? 0 : 1);
            }
        });
        StringBuilder sb = new StringBuilder(src.length() + 256);
        int cur = 0;
        for (int i = 0; i < sorted.size(); i++) {
            int[] r = sorted.get(i);
            if (r[0] < cur) throw new IllegalStateException("Overlapping patches");
            sb.append(src, cur, r[0]);
            sb.append(texts.get(r[2]));
            cur = r[1];
        }
        sb.append(src, cur, src.length());
        return sb.toString();
    }
}
