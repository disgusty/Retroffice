package com.disgusty.retroffice.editor;

import android.view.View;

/** An editor surface for one open document. */
public interface DocEditor {
    View view();

    /** Whether the bold/italic/underline/strike bar applies. */
    boolean hasFormatting();

    /** The text field that currently has focus, or null. */
    DocEditText focusedField();

    /** Commits pending input (e.g. the spreadsheet formula bar) before saving. */
    void commitPending();

    /** Detaches views from the document model. */
    void release();

    interface Host {
        EditHistory history();

        void onSelectionChanged(DocEditText field);

        void onEdited();

        void toast(int resId);
    }
}
