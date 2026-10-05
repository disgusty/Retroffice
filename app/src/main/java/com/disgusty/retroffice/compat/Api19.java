package com.disgusty.retroffice.compat;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** Storage Access Framework (Android 4.4+). */
@TargetApi(19)
final class Api19 {
    private Api19() {
    }

    static Intent openDocument(String[] mimeTypes) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return i;
    }

    static Intent createDocument(String mime, String name) {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_TITLE, name);
        return i;
    }

    /** Keeps access across restarts; falls back to read-only when the provider refuses write. */
    static void persist(Context ctx, Uri uri, int intentFlags) {
        ContentResolver cr = ctx.getContentResolver();
        int rw = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        try {
            cr.takePersistableUriPermission(uri, rw & intentFlags);
            return;
        } catch (Exception ignored) {
        }
        try {
            cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    static void release(Context ctx, Uri uri) {
        try {
            ctx.getContentResolver().releasePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }
}
