package com.disgusty.retroffice.io;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.disgusty.retroffice.compat.Compat;
import com.disgusty.retroffice.doc.Doc;
import com.disgusty.retroffice.doc.FileUtil;

/**
 * Moving bytes between the user's files and the app.
 *
 * Opening copies the file into app storage first, so the original is never held open or touched
 * while editing. Saving writes a complete new file to app storage, verifies it, and only then
 * replaces the target; the write to the target is read back and compared byte for byte.
 */
public final class DocumentIO {
    private DocumentIO() {
    }

    public static final long MAX_SIZE = 200L * 1024 * 1024;

    /** Folder for one open document's private files. */
    public static File newWorkDir(Context ctx) {
        File base = new File(ctx.getFilesDir(), "work");
        //noinspection ResultOfMethodCallIgnored
        base.mkdirs();
        File d = new File(base, "d" + System.currentTimeMillis());
        int n = 0;
        while (d.exists()) d = new File(base, "d" + System.currentTimeMillis() + "_" + (++n));
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    /** Removes work folders of documents that are no longer open (e.g. after a crash). */
    public static void cleanWork(Context ctx, File keep) {
        File base = new File(ctx.getFilesDir(), "work");
        File[] kids = base.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (keep != null && k.equals(keep)) continue;
            FileUtil.deleteTree(k);
        }
    }

    public static String displayName(Context ctx, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            Cursor c = null;
            try {
                c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        String n = c.getString(idx);
                        if (n != null && n.length() > 0) return n;
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
        }
        String last = uri.getLastPathSegment();
        if (last == null || last.length() == 0) return "document";
        int slash = last.lastIndexOf('/');
        return slash >= 0 ? last.substring(slash + 1) : last;
    }

    /** Copies the document behind {@code uri} into {@code dest}. */
    public static void copyIn(Context ctx, Uri uri, File dest) throws IOException {
        InputStream in;
        if ("file".equals(uri.getScheme())) {
            in = new FileInputStream(new File(uri.getPath()));
        } else {
            in = ctx.getContentResolver().openInputStream(uri);
        }
        if (in == null) throw new IOException("Cannot read the document");
        try {
            long n = FileUtil.copy(in, dest);
            if (n > MAX_SIZE) throw new IOException("File too large");
        } finally {
            in.close();
        }
    }

    /** Whether we can currently write to the given target. */
    public static boolean canWrite(Context ctx, Uri uri) {
        if ("file".equals(uri.getScheme())) {
            File f = new File(uri.getPath());
            File parent = f.getParentFile();
            return f.exists() ? f.canWrite() && parent != null && parent.canWrite() : parent != null && parent.canWrite();
        }
        try {
            ParcelFileDescriptor pfd = ctx.getContentResolver().openFileDescriptor(uri, "rw");
            if (pfd == null) return false;
            pfd.close();
            return true;
        } catch (Exception e) {
            // Some providers only offer streams; try that (without truncating anything).
            try {
                OutputStream os = Compat.openOutputAppend(ctx.getContentResolver(), uri);
                if (os == null) return false;
                os.close();
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }

    /**
     * Writes, verifies and stores a document.
     *
     * @param out file in app storage that receives the new version (kept as the last good copy)
     */
    public static void save(Context ctx, Doc.SaveJob job, Uri target, File out) throws Exception {
        File tmp = new File(out.getParentFile(), out.getName() + ".part");
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        job.write(tmp);
        job.verify(tmp);
        writeTo(ctx, tmp, target);
        //noinspection ResultOfMethodCallIgnored
        out.delete();
        if (!tmp.renameTo(out)) {
            FileInputStream in = new FileInputStream(tmp);
            try {
                FileUtil.copy(in, out);
            } finally {
                in.close();
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /** Replaces the target with {@code src}, then reads it back and compares. */
    public static void writeTo(Context ctx, File src, Uri target) throws IOException {
        byte[] expected = FileUtil.sha1(src);
        if ("file".equals(target.getScheme())) {
            writeFile(src, new File(target.getPath()), expected);
            return;
        }
        ContentResolver cr = ctx.getContentResolver();
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                writeContent(cr, src, target);
                InputStream back = cr.openInputStream(target);
                if (back == null) throw new IOException("Cannot read back the saved file");
                byte[] actual;
                try {
                    actual = FileUtil.sha1(back);
                } finally {
                    back.close();
                }
                if (FileUtil.equal(expected, actual)) return;
                last = new IOException("The saved file did not match (attempt " + (attempt + 1) + ")");
            } catch (IOException e) {
                last = e;
            } catch (SecurityException e) {
                throw new IOException("No permission to write this file");
            }
        }
        throw last;
    }

    private static void writeContent(ContentResolver cr, File src, Uri target) throws IOException {
        long len = src.length();
        ParcelFileDescriptor pfd = null;
        try {
            pfd = cr.openFileDescriptor(target, "rw");
        } catch (Exception ignored) {
        }
        if (pfd != null) {
            FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
            try {
                // Write from the beginning, then cut off whatever was longer before.
                fos.getChannel().position(0);
                FileInputStream in = new FileInputStream(src);
                try {
                    FileUtil.copy(in, fos);
                } finally {
                    in.close();
                }
                fos.flush();
                fos.getChannel().truncate(len);
                try {
                    fos.getFD().sync();
                } catch (Exception ignored) {
                }
                return;
            } catch (IOException e) {
                // fall through to the stream path below
            } finally {
                try {
                    fos.close();
                } catch (Exception ignored) {
                }
                try {
                    pfd.close();
                } catch (Exception ignored) {
                }
            }
        }
        OutputStream os = Compat.openOutputTruncate(cr, target);
        if (os == null) throw new IOException("Cannot write the document");
        try {
            FileInputStream in = new FileInputStream(src);
            try {
                FileUtil.copy(in, os);
            } finally {
                in.close();
            }
            os.flush();
        } finally {
            os.close();
        }
    }

    /** Local files: write next to the target, then rename over it (atomic on Linux file systems). */
    private static void writeFile(File src, File dest, byte[] expected) throws IOException {
        File dir = dest.getParentFile();
        if (dir == null) throw new IOException("Bad target");
        File tmp = new File(dir, "." + dest.getName() + ".saving");
        FileInputStream in = new FileInputStream(src);
        try {
            FileUtil.copy(in, tmp);
        } finally {
            in.close();
        }
        if (!FileUtil.equal(expected, FileUtil.sha1(tmp))) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Writing to storage failed");
        }
        if (!tmp.renameTo(dest)) {
            // Some file systems refuse to rename over an existing file: move the old one aside first.
            File bak = new File(dir, "." + dest.getName() + ".old");
            //noinspection ResultOfMethodCallIgnored
            bak.delete();
            if (dest.exists() && !dest.renameTo(bak)) {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("Cannot replace the file");
            }
            if (!tmp.renameTo(dest)) {
                //noinspection ResultOfMethodCallIgnored
                bak.renameTo(dest);
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                throw new IOException("Cannot replace the file");
            }
            //noinspection ResultOfMethodCallIgnored
            bak.delete();
        }
        if (!FileUtil.equal(expected, FileUtil.sha1(dest))) throw new IOException("The saved file did not match");
    }
}
