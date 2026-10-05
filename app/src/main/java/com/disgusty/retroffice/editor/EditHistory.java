package com.disgusty.retroffice.editor;

import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.ArrayList;

/**
 * Undo/redo across all text fields of a document. Typing is grouped into steps (a pause or moving
 * to another field starts a new step); formatting changes are their own steps. Memory is capped,
 * dropping the oldest steps first, which matters on 16 MB devices.
 */
public final class EditHistory {
    public interface Listener {
        void onHistoryChanged();

        void onEdited();
    }

    static final class Step {
        final Editable target;
        final SpannableStringBuilder before;
        final int selection;

        Step(Editable target, SpannableStringBuilder before, int selection) {
            this.target = target;
            this.before = before;
            this.selection = selection;
        }
    }

    /** Hook for non-text edits (spreadsheet cells): undo/redo callbacks. */
    public interface Custom {
        void undo();

        void redo();
    }

    private final ArrayList<Object> undo = new ArrayList<Object>();
    private final ArrayList<Object> redo = new ArrayList<Object>();
    private final Listener listener;
    private final long budgetChars;
    private boolean restoring;
    private Editable lastTarget;
    private long lastTime;

    public EditHistory(Listener listener) {
        this.listener = listener;
        long heap = Runtime.getRuntime().maxMemory();
        budgetChars = Math.max(200000, Math.min(4000000, heap / 16));
    }

    public void watch(final EditText field) {
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (restoring) return;
                Editable e = field.getText();
                long now = System.currentTimeMillis();
                if (e != lastTarget || now - lastTime > 1200) record(e, field.getSelectionStart());
                lastTarget = e;
                lastTime = now;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!restoring) listener.onEdited();
            }
        });
    }

    /** Records the state of {@code e} before a change. */
    public void record(Editable e, int selection) {
        undo.add(new Step(e, SpanUtil.snapshot(e), selection));
        redo.clear();
        trim();
        listener.onHistoryChanged();
    }

    /** Ends the current typing group (the next keystroke starts a new undo step). */
    public void breakGroup() {
        lastTarget = null;
    }

    public void recordCustom(Custom c) {
        undo.add(c);
        redo.clear();
        trim();
        listener.onHistoryChanged();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** @return the field content that was restored, or null */
    public Editable undo() {
        return move(undo, redo, true);
    }

    public Editable redo() {
        return move(redo, undo, false);
    }

    private Editable move(ArrayList<Object> from, ArrayList<Object> to, boolean isUndo) {
        if (from.isEmpty()) return null;
        Object o = from.remove(from.size() - 1);
        Editable result = null;
        restoring = true;
        try {
            if (o instanceof Custom) {
                Custom c = (Custom) o;
                if (isUndo) c.undo();
                else c.redo();
                to.add(c);
            } else {
                Step s = (Step) o;
                to.add(new Step(s.target, SpanUtil.snapshot(s.target), s.selection));
                SpanUtil.restore(s.target, s.before);
                result = s.target;
            }
        } finally {
            restoring = false;
        }
        lastTarget = null;
        listener.onEdited();
        listener.onHistoryChanged();
        return result;
    }

    private void trim() {
        long total = 0;
        for (Object o : undo) if (o instanceof Step) total += ((Step) o).before.length();
        while (total > budgetChars && undo.size() > 1) {
            Object o = undo.remove(0);
            if (o instanceof Step) total -= ((Step) o).before.length();
        }
        while (undo.size() > 200) undo.remove(0);
    }

    public void clear() {
        undo.clear();
        redo.clear();
        listener.onHistoryChanged();
    }
}
