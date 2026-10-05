package com.disgusty.retroffice.doc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.HashMap;

import com.disgusty.retroffice.pkg.OfficePackage;
import com.disgusty.retroffice.text.ImageResolver;

/** Decodes pictures from a package, downsampled to roughly their display size. */
public final class PackageImages implements ImageResolver {
    private final OfficePackage pkg;
    private final String ownerPart;
    private final HashMap<String, String> rels;
    private final float density;
    private final boolean enabled;
    private final int maxPx;
    /** Stop decoding after this many pixels to stay inside small heaps (old devices: 16 MB). */
    private long budget;

    public PackageImages(OfficePackage pkg, String ownerPart, HashMap<String, String> rels, float density,
                         boolean enabled, int maxPx) {
        this.pkg = pkg;
        this.ownerPart = ownerPart;
        this.rels = rels;
        this.density = density;
        this.enabled = enabled;
        this.maxPx = Math.max(64, maxPx);
        long heap = Runtime.getRuntime().maxMemory();
        this.budget = Math.max(512L * 1024, heap / 8 / 4);
    }

    public static PackageImages disabled() {
        return new PackageImages(null, null, null, 1f, false, 64);
    }

    @Override
    public float density() {
        return density;
    }

    @Override
    public Bitmap load(String ref, int wPx, int hPx) {
        if (!enabled || ref == null || budget <= 0) return null;
        String part;
        if (rels != null) {
            String target = rels.get(ref);
            if (target == null) return null;
            part = OfficePackage.resolve(ownerPart, target);
        } else {
            part = ref.startsWith("./") ? ref.substring(2) : ref;
        }
        String lower = part.toLowerCase(java.util.Locale.US);
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp"))) {
            return null;
        }
        try {
            byte[] data = pkg.readOrNull(part);
            if (data == null) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int target = Math.min(maxPx, wPx > 0 ? Math.max(wPx, 32) : maxPx);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= target) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, d);
            if (b != null) budget -= (long) b.getWidth() * b.getHeight();
            return b;
        } catch (OutOfMemoryError e) {
            budget = 0;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
