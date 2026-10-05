package com.disgusty.retroffice.compat;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.net.Uri;
import android.text.Layout;
import android.text.style.AlignmentSpan;

import java.io.FileNotFoundException;
import java.io.OutputStream;

@TargetApi(3)
final class Api3 {
    private Api3() {
    }

    static Object alignment(int align) {
        return new AlignmentSpan.Standard(align == Compat.ALIGN_CENTER
                ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_OPPOSITE);
    }

    static OutputStream openOutput(ContentResolver cr, Uri uri, String mode) throws FileNotFoundException {
        return cr.openOutputStream(uri, mode);
    }

    static void outsideTouchable(android.widget.PopupWindow pw) {
        pw.setOutsideTouchable(true);
    }
}
