package com.disgusty.retroffice.doc;

import java.io.File;

/**
 * An open document. Mutated only on the UI thread; saving snapshots it first.
 *
 * The package read at open time stays the base of every save: changes are always computed against
 * it, so saving repeatedly never compounds earlier edits or loses untouched content.
 */
public abstract class Doc {
    public static final int TEXT = 1, SHEET = 2, SLIDES = 3, PLAIN = 4;

    /** File name shown to the user, including extension. */
    public String displayName;
    protected String savedFingerprint;

    public abstract int kind();

    /** Short upper-case format name, e.g. "DOCX". */
    public abstract String formatName();

    /** A value that changes whenever the saveable content changes. */
    public abstract String fingerprint();

    public boolean isModified() {
        return savedFingerprint == null || !savedFingerprint.equals(fingerprint());
    }

    /** Records the state captured by a job as saved. */
    public void markSaved(SaveJob job) {
        savedFingerprint = job.fingerprint();
    }

    /** Call at the end of loading: the loaded state is the "saved" state. */
    protected void markLoaded() {
        savedFingerprint = fingerprint();
    }

    /**
     * Captures everything needed to write the document. Called on the UI thread; the returned job
     * runs on a background thread and must not touch live document state.
     */
    public abstract SaveJob prepareSave();

    public abstract void close();

    /** Full plain text (for word count / sharing). */
    public String plainText() {
        return "";
    }

    public interface SaveJob {
        /** Writes the complete new file to {@code out}. */
        void write(File out) throws Exception;

        /** Throws if {@code written} does not contain what was intended. */
        void verify(File written) throws Exception;

        /** Fingerprint of the captured state. */
        String fingerprint();
    }
}
