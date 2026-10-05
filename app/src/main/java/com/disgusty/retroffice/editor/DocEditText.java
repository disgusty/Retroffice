package com.disgusty.retroffice.editor;

import android.content.Context;
import android.text.Editable;
import android.widget.EditText;

import com.disgusty.retroffice.compat.Compat;

/**
 * An EditText that edits the document model in place (no copy), so what is saved is exactly what
 * is on screen. Reports selection changes for the formatting bar.
 */
public class DocEditText extends EditText {
    public interface SelectionListener {
        void onSelection(DocEditText v, int start, int end);
    }

    private final Editable model;
    private SelectionListener selectionListener;

    public DocEditText(Context ctx, final Editable model) {
        super(ctx);
        this.model = model;
        setEditableFactory(new Editable.Factory() {
            @Override
            public Editable newEditable(CharSequence source) {
                if (source == model) return model;
                return super.newEditable(source);
            }
        });
        setSingleLine(false);
        setHorizontallyScrolling(false);
        if (Compat.SDK >= 3) EditorCompat.multiLineText(this);
        setText(model, BufferType.EDITABLE);
    }

    public Editable model() {
        return model;
    }

    public void setSelectionListener(SelectionListener l) {
        selectionListener = l;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (selectionListener != null) selectionListener.onSelection(this, selStart, selEnd);
    }

    /**
     * Detaches from the model: removes the spans the framework attached (watchers, selection,
     * IME composing) so a later view can bind to the same model cleanly.
     */
    public void unbind() {
        selectionListener = null;
        Object[] spans = model.getSpans(0, model.length(), Object.class);
        for (Object s : spans) {
            if (!SpanUtil.keepOnUnbind(s)) model.removeSpan(s);
        }
    }
}
