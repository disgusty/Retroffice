package com.disgusty.retroffice.pkg;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a package: every entry of the source in its original order, with changed parts swapped
 * in. ODF's "mimetype" member is always written first and uncompressed, as the spec requires.
 */
public final class PackageWriter {
    private PackageWriter() {
    }

    /**
     * @param source   original package, or null for a document built entirely from {@code replaced}
     * @param replaced new content for existing parts and any added parts (added in map order)
     * @param removed  parts to drop
     */
    public static void write(OfficePackage source, LinkedHashMap<String, byte[]> replaced, Set<String> removed,
                             File out) throws IOException {
        FileOutputStream fos = new FileOutputStream(out);
        boolean ok = false;
        try {
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos, 65536));
            ArrayList<String> order = new ArrayList<String>();
            if (source != null) order.addAll(source.names);
            for (String name : replaced.keySet()) if (!order.contains(name)) order.add(name);

            if (order.contains("mimetype") && (removed == null || !removed.contains("mimetype"))) {
                byte[] mt = replaced.containsKey("mimetype") ? replaced.get("mimetype") : source.read("mimetype");
                writeStored(zos, "mimetype", mt, source != null ? source.time("mimetype") : -1);
            }
            for (String name : order) {
                if (name.equals("mimetype")) continue;
                if (removed != null && removed.contains(name)) continue;
                long time = source != null && source.has(name) ? source.time(name) : -1;
                if (name.endsWith("/") && (source == null || source.isDirectory(name)) && !replaced.containsKey(name)) {
                    ZipEntry d = new ZipEntry(name);
                    if (time > 0) d.setTime(time);
                    zos.putNextEntry(d);
                    zos.closeEntry();
                    continue;
                }
                byte[] data = replaced.containsKey(name) ? replaced.get(name) : source.read(name);
                ZipEntry e = new ZipEntry(name);
                e.setMethod(ZipEntry.DEFLATED);
                if (time > 0 && !replaced.containsKey(name)) e.setTime(time);
                zos.putNextEntry(e);
                zos.write(data);
                zos.closeEntry();
            }
            zos.finish();
            zos.flush();
            fos.getFD().sync();
            zos.close();
            ok = true;
        } finally {
            if (!ok) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
                //noinspection ResultOfMethodCallIgnored
                out.delete();
            }
        }
    }

    private static void writeStored(ZipOutputStream zos, String name, byte[] data, long time) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(data.length);
        e.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        e.setCrc(crc.getValue());
        if (time > 0) e.setTime(time);
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    /** Convenience for building a brand-new package from parts (templates). */
    public static void writeNew(Map<String, byte[]> parts, File out) throws IOException {
        LinkedHashMap<String, byte[]> m = new LinkedHashMap<String, byte[]>(parts);
        write(null, m, null, out);
    }
}
