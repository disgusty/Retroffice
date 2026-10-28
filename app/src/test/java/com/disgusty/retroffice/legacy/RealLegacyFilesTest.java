package com.disgusty.retroffice.legacy;

import org.junit.Test;

import java.io.File;

/**
 * Converts every legacy file in the folder named by the LEGACY_DIR environment variable
 * (for manual checks with real documents; skipped when the variable isn't set).
 */
public class RealLegacyFilesTest {
    @Test
    public void convertFolder() throws Exception {
        String dir = System.getenv("LEGACY_DIR");
        if (dir == null) return;
        File[] files = new File(dir).listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile() || f.getName().endsWith(".odt") || f.getName().endsWith(".ods") || f.getName().endsWith(".odp")) continue;
            byte[] head = new byte[64];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            in.read(head);
            in.close();
            if (!LegacyConverter.isLegacy(head)) continue;
            File out = new File(dir, f.getName() + "-out");
            out.mkdirs();
            long t0 = System.currentTimeMillis();
            LegacyConverter.Result r = LegacyConverter.convert(f, out);
            File dest = new File(dir, f.getName() + "." + r.extension);
            dest.delete();
            r.file.renameTo(dest);
            System.out.println(f.getName() + " -> " + dest.getName() + " " + dest.length() + " bytes, "
                    + (System.currentTimeMillis() - t0) + " ms");
        }
    }
}
