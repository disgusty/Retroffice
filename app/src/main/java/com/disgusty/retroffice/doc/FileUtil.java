package com.disgusty.retroffice.doc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;

/** Small file helpers that work on every Android version. */
public final class FileUtil {
    private FileUtil() {
    }

    public static byte[] readAll(File f, long max) throws IOException {
        if (f.length() > max) throw new IOException("File too large");
        FileInputStream in = new FileInputStream(f);
        try {
            return readAll(in, max);
        } finally {
            in.close();
        }
    }

    public static byte[] readAll(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        long total = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            total += r;
            if (total > max) throw new IOException("File too large");
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    /** Writes bytes and forces them to storage before returning. */
    public static void writeAll(File f, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(f);
        boolean ok = false;
        try {
            out.write(data);
            out.flush();
            out.getFD().sync();
            ok = true;
        } finally {
            out.close();
            if (!ok) //noinspection ResultOfMethodCallIgnored
                f.delete();
        }
    }

    /** Copies a stream to a file (synced), returning the number of bytes. */
    public static long copy(InputStream in, File dest) throws IOException {
        FileOutputStream out = new FileOutputStream(dest);
        boolean ok = false;
        try {
            long n = copy(in, out);
            out.flush();
            out.getFD().sync();
            ok = true;
            return n;
        } finally {
            out.close();
            if (!ok) //noinspection ResultOfMethodCallIgnored
                dest.delete();
        }
    }

    public static long copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        long total = 0;
        int r;
        while ((r = in.read(buf)) > 0) {
            out.write(buf, 0, r);
            total += r;
        }
        return total;
    }

    public static byte[] sha1(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return sha1(in);
        } finally {
            in.close();
        }
    }

    public static byte[] sha1(InputStream in) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) md.update(buf, 0, r);
            return md.digest();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 unavailable");
        }
    }

    public static boolean equal(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] != b[i]) return false;
        return true;
    }

    public static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (dot <= slash + 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(java.util.Locale.US);
    }

    public static String baseName(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
