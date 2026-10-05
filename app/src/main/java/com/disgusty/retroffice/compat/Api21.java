package com.disgusty.retroffice.compat;

import android.annotation.TargetApi;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowManager;

/** Material (Android 5.0+): ripples, elevation, colored system bars, Roboto Medium. */
@TargetApi(21)
final class Api21 {
    private Api21() {
    }

    static Drawable ripple(int color, Drawable content, Drawable mask) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, mask != null ? mask : (content == null ? new ColorDrawable(0xFFFFFFFF) : null));
    }

    static void elevation(View v, float px) {
        v.setElevation(px);
    }

    static void roundOutline(View v, final float radius) {
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    static void barColors(Window w, int status, int nav) {
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(status);
        w.setNavigationBarColor(nav);
    }

    static Typeface medium() {
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }
}
