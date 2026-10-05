package com.disgusty.retroffice.doc;

import java.util.HashMap;

import com.disgusty.retroffice.pkg.OfficePackage;
import com.disgusty.retroffice.text.ImageResolver;

/** Creates image resolvers for document parts (or none, e.g. when only verifying a file). */
public class Images {
    public static final Images NONE = new Images(1f, 0);

    public final float density;
    public final int maxPx;

    public Images(float density, int maxPx) {
        this.density = density;
        this.maxPx = maxPx;
    }

    public ImageResolver forPart(OfficePackage pkg, String part, HashMap<String, String> rels) {
        if (maxPx <= 0) return new PackageImages(null, null, null, density, false, 64);
        return new PackageImages(pkg, part, rels, density, true, maxPx);
    }
}
