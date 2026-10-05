package com.disgusty.retroffice.text;

import android.graphics.Bitmap;

/** Loads pictures referenced from a document for display. Returns null when not possible. */
public interface ImageResolver {
    /**
     * @param ref  relationship id (OOXML) or package path (ODF)
     * @param wPx  preferred display width in pixels (0 = unknown)
     * @param hPx  preferred display height in pixels (0 = unknown)
     */
    Bitmap load(String ref, int wPx, int hPx);

    /** Pixels per CSS pixel (1/96 inch) on this screen. */
    float density();
}
