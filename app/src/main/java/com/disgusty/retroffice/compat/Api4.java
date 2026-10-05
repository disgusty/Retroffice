package com.disgusty.retroffice.compat;

import android.annotation.TargetApi;
import android.view.View;

@TargetApi(4)
final class Api4 {
    private Api4() {
    }

    static void contentDescription(View v, CharSequence s) {
        v.setContentDescription(s);
    }
}
