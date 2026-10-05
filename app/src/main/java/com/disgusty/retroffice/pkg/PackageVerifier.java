package com.disgusty.retroffice.pkg;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.disgusty.retroffice.xml.XmlException;
import com.disgusty.retroffice.xml.XmlParser;

/**
 * Re-reads a freshly written package before it is allowed to replace the user's file: every entry
 * is decompressed (which checks its CRC), names must be unique, the expected parts must exist and
 * every XML part we touched must parse.
 */
public final class PackageVerifier {
    private PackageVerifier() {
    }

    public static void verify(File f, Collection<String> requiredParts, Collection<String> xmlParts) throws IOException {
        HashSet<String> seen = new HashSet<String>();
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(new FileInputStream(f), 65536));
        try {
            ZipEntry e;
            byte[] buf = new byte[16384];
            boolean first = true;
            while ((e = zin.getNextEntry()) != null) {
                String name = e.getName();
                if (!seen.add(name)) throw new IOException("Verification failed: duplicate entry " + name);
                if (first && name.equals("mimetype") && e.getMethod() != ZipEntry.STORED) {
                    throw new IOException("Verification failed: mimetype must be stored");
                }
                first = false;
                boolean parse = xmlParts != null && xmlParts.contains(name);
                ByteArrayOutputStream bos = parse ? new ByteArrayOutputStream() : null;
                int r;
                while ((r = zin.read(buf)) > 0) {
                    if (bos != null) bos.write(buf, 0, r);
                }
                if (bos != null) {
                    try {
                        XmlParser.parse(bos.toByteArray());
                    } catch (XmlException x) {
                        throw new IOException("Verification failed: " + name + " is not well-formed: " + x.getMessage());
                    }
                }
                zin.closeEntry();
            }
        } finally {
            zin.close();
        }
        if (requiredParts != null) {
            for (String p : requiredParts) {
                if (!seen.contains(p)) throw new IOException("Verification failed: missing " + p);
            }
        }
    }
}
