package com.disgusty.retroffice.doc;

import java.util.HashMap;

import com.disgusty.retroffice.pkg.OfficePackage;
import com.disgusty.retroffice.xml.Xml;
import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XNode;

/** OOXML relationship helpers. */
public final class Rels {
    private Rels() {
    }

    /** Id -> target for internal relationships of a part. */
    public static HashMap<String, String> targets(OfficePackage pkg, String part) {
        HashMap<String, String> m = new HashMap<String, String>();
        try {
            XDoc d = pkg.xmlOrNull(OfficePackage.relsFor(part));
            if (d == null) return m;
            for (XNode r : d.root.childrenNamed(Xml.PKG_REL, "Relationship")) {
                if ("External".equals(r.attr(null, "TargetMode"))) continue;
                String id = r.attr(null, "Id"), t = r.attr(null, "Target");
                if (id != null && t != null) m.put(id, t);
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    /** Resolved part name of the first relationship whose type ends with {@code typeSuffix}. */
    public static String byType(OfficePackage pkg, String part, String typeSuffix) {
        try {
            String relsName = part.length() == 0 ? "_rels/.rels" : OfficePackage.relsFor(part);
            XDoc d = pkg.xmlOrNull(relsName);
            if (d == null) return null;
            for (XNode r : d.root.childrenNamed(Xml.PKG_REL, "Relationship")) {
                String type = r.attr(null, "Type");
                if (type != null && type.endsWith(typeSuffix) && !"External".equals(r.attr(null, "TargetMode"))) {
                    String t = r.attr(null, "Target");
                    if (t == null) continue;
                    return part.length() == 0 ? OfficePackage.resolve("", t) : OfficePackage.resolve(part, t);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
