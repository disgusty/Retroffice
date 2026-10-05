package com.disgusty.retroffice.compat;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.view.Window;

import java.io.FileNotFoundException;
import java.io.OutputStream;

/**
 * Entry point for anything newer than Android 1.0. Each API level lives in its own class so that
 * old Dalvik VMs never load (and fail to verify) code that references missing framework classes.
 */
public final class Compat {
    private Compat() {
    }

    public static final int SDK = sdk();

    private static int sdk() {
        try {
            // Build.VERSION.SDK_INT appeared in API 4; API 1-3 only have the string field.
            return Integer.parseInt(Build.VERSION.SDK);
        } catch (Throwable t) {
            return 1;
        }
    }

    public static final int ALIGN_NORMAL = 0, ALIGN_CENTER = 1, ALIGN_OPPOSITE = 2;

    /** A paragraph alignment span, or null where AlignmentSpan doesn't exist (API < 3). */
    public static Object alignmentSpan(int align) {
        if (SDK < 3 || align == ALIGN_NORMAL) return null;
        return Api3.alignment(align);
    }

    // ---- Storage Access Framework

    public static boolean hasSaf() {
        return SDK >= 19;
    }

    public static Intent openDocumentIntent(String[] mimes) {
        return Api19.openDocument(mimes);
    }

    public static Intent createDocumentIntent(String mime, String name) {
        return Api19.createDocument(mime, name);
    }

    public static void persistUri(Context ctx, Uri uri, int flags) {
        if (SDK >= 19) Api19.persist(ctx, uri, flags);
    }

    public static void releaseUri(Context ctx, Uri uri) {
        if (SDK >= 19) Api19.release(ctx, uri);
    }

    /** Opens a content Uri for writing, truncating where the platform supports the mode string. */
    public static OutputStream openOutputTruncate(ContentResolver cr, Uri uri) throws FileNotFoundException {
        if (SDK >= 3) return Api3.openOutput(cr, uri, "wt");
        return cr.openOutputStream(uri);
    }

    /** Opens for appending (never truncates); used only to probe write access. */
    public static OutputStream openOutputAppend(ContentResolver cr, Uri uri) throws FileNotFoundException {
        if (SDK >= 3) return Api3.openOutput(cr, uri, "wa");
        return null;
    }

    // ---- Look

    /** Accessibility label (TalkBack); not available before Android 1.6. */
    public static void contentDescription(View v, CharSequence s) {
        if (SDK >= 4 && s != null) Api4.contentDescription(v, s);
    }

    public static void outsideTouchable(android.widget.PopupWindow pw) {
        if (SDK >= 3) Api3.outsideTouchable(pw);
    }

    public static Drawable ripple(int color, Drawable content, Drawable mask) {
        if (SDK < 21) return null;
        return Api21.ripple(color, content, mask);
    }

    public static void elevation(View v, float px) {
        if (SDK >= 21) Api21.elevation(v, px);
    }

    public static void roundOutline(View v, float radius) {
        if (SDK >= 21) Api21.roundOutline(v, radius);
    }

    public static void barColors(Window w, int status, int nav, boolean lightStatus, boolean lightNav) {
        if (SDK >= 21) Api21.barColors(w, status, nav);
        if (SDK >= 23) Api23.lightStatusBar(w.getDecorView(), lightStatus, lightNav);
    }

    /** Roboto Medium where the platform has it, else bold. */
    public static Typeface medium() {
        if (SDK >= 21) {
            try {
                return Api21.medium();
            } catch (Throwable ignored) {
            }
        }
        return Typeface.DEFAULT_BOLD;
    }

    public static boolean isNightMode(Context ctx) {
        if (SDK < 8) return false;
        return Api8.night(ctx);
    }

    /** Material You palettes, or null below Android 12. */
    public static int[][] dynamicPalettes(Context ctx) {
        if (SDK < 31) return null;
        try {
            return Api31.palettes(ctx);
        } catch (Throwable t) {
            return null;
        }
    }

    public interface InsetsListener {
        void onInsets(int left, int top, int right, int bottom);
    }

    /** True where the system forces edge-to-edge drawing for this app (Android 15+). */
    public static boolean edgeToEdge() {
        return SDK >= 35;
    }

    public static void listenInsets(View root, final InsetsListener l) {
        if (SDK < 30) return;
        Api30.listen(root, new Api30.InsetsSink() {
            @Override
            public void onInsets(int left, int top, int right, int bottom) {
                l.onInsets(left, top, right, bottom);
            }
        });
    }
}
