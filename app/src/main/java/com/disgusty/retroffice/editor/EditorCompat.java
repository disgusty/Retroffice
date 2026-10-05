package com.disgusty.retroffice.editor;

import android.annotation.TargetApi;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

/** Input-type helpers (Android 1.5+). Only called after an SDK check. */
@TargetApi(3)
final class EditorCompat {
    private EditorCompat() {
    }

    static void multiLineText(EditText e) {
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }

    static void singleLineDone(EditText e, final Runnable onDone) {
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                    onDone.run();
                    return true;
                }
                return false;
            }
        });
    }
}
