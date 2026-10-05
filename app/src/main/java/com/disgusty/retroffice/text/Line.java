package com.disgusty.retroffice.text;

import java.util.ArrayList;

import com.disgusty.retroffice.xml.XNode;

/** An immutable snapshot of one paragraph line, safe to hand to a background thread. */
public final class Line {
    public final XNode node;
    /** Content to rebuild the paragraph from, or null when the paragraph is unchanged. */
    public final ArrayList<Atom> atoms;
    public final String plain;

    Line(XNode node, ArrayList<Atom> atoms, String plain) {
        this.node = node;
        this.atoms = atoms;
        this.plain = plain;
    }
}
