package com.disgusty.retroffice.compat;

import android.annotation.TargetApi;
import android.view.View;

@TargetApi(23)
final class Api23 {
    private Api23() {
    }

    @SuppressWarnings("deprecation")
    static void lightStatusBar(View decor, boolean light, boolean lightNav) {
        int f = decor.getSystemUiVisibility();
        if (light) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        // Light navigation bar icons exist from API 26.
        if (Compat.SDK >= 26) {
            if (lightNav) f |= 0x00000010;
            else f &= ~0x00000010;
        }
        decor.setSystemUiVisibility(f);
    }
}
