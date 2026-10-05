package com.disgusty.retroffice.pkg;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.disgusty.retroffice.xml.XDoc;
import com.disgusty.retroffice.xml.XmlException;
import com.disgusty.retroffice.xml.XmlParser;

/** Read-only view of a ZIP-based office package (ODF or OOXML), entries kept in original order. */
public final class OfficePackage {
    /** Refuse absurd part sizes instead of running out of memory half-way through. */
    public static final long MAX_PART = 64L * 1024 * 1024;

    public final File file;
    public final ArrayList<String> names = new ArrayList<String>();
    private final HashMap<String, ZipEntry> entries = new HashMap<String, ZipEntry>();
    private final HashMap<String, XDoc> xmlCache = new HashMap<String, XDoc>();
    private ZipFile zip;

    private OfficePackage(File file) {
        this.file = file;
    }

    public static OfficePackage open(File f) throws IOException {
        OfficePackage p = new OfficePackage(f);
        p.zip = new ZipFile(f);
        HashSet<String> seen = new HashSet<String>();
        Enumeration<? extends ZipEntry> en = p.zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String name = e.getName();
            if (!seen.add(name)) {
                p.close();
                throw new IOException("Damaged package: duplicate entry " + name);
            }
            if (name.startsWith("/") || name.contains("..") || name.indexOf('\\') >= 0) {
                p.close();
                throw new IOException("Damaged package: bad entry name " + name);
            }
            p.names.add(name);
            p.entries.put(name, e);
        }
        return p;
    }

    public boolean has(String name) {
        return entries.containsKey(name);
    }

    public boolean isDirectory(String name) {
        ZipEntry e = entries.get(name);
        return e != null && e.isDirectory();
    }

    public long time(String name) {
        ZipEntry e = entries.get(name);
        return e == null ? -1 : e.getTime();
    }

    public byte[] read(String name) throws IOException {
        ZipEntry e = entries.get(name);
        if (e == null) throw new IOException("Missing part " + name);
        if (e.getSize() > MAX_PART) throw new IOException("Part too large: " + name);
        InputStream in = zip.getInputStream(e);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(e.getSize() > 0 ? (int) e.getSize() : 8192);
            byte[] buf = new byte[16384];
            long total = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > MAX_PART) throw new IOException("Part too large: " + name);
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    public byte[] readOrNull(String name) {
        if (!has(name)) return null;
        try {
            return read(name);
        } catch (IOException e) {
            return null;
        }
    }

    /** Parses (and caches) an XML part. */
    public XDoc xml(String name) throws IOException, XmlException {
        XDoc d = xmlCache.get(name);
        if (d == null) {
            d = XmlParser.parse(read(name));
            xmlCache.put(name, d);
        }
        return d;
    }

    public XDoc xmlOrNull(String name) throws IOException, XmlException {
        return has(name) ? xml(name) : null;
    }

    public void close() {
        try {
            if (zip != null) zip.close();
        } catch (IOException ignored) {
        }
        zip = null;
    }

    /** Resolves a relationship target relative to the folder of the part that owns it. */
    public static String resolve(String ownerPart, String target) {
        if (target.startsWith("/")) return normalize(target.substring(1));
        int slash = ownerPart.lastIndexOf('/');
        String base = slash < 0 ? "" : ownerPart.substring(0, slash + 1);
        return normalize(base + target);
    }

    static String normalize(String path) {
        String[] parts = path.split("/");
        ArrayList<String> out = new ArrayList<String>();
        for (String p : parts) {
            if (p.length() == 0 || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!out.isEmpty()) out.remove(out.size() - 1);
            } else {
                out.add(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < out.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(out.get(i));
        }
        return sb.toString();
    }

    /** The relationships part for a given part, e.g. word/document.xml -> word/_rels/document.xml.rels. */
    public static String relsFor(String part) {
        int slash = part.lastIndexOf('/');
        String dir = slash < 0 ? "" : part.substring(0, slash + 1);
        return dir + "_rels/" + part.substring(slash + 1) + ".rels";
    }
}
