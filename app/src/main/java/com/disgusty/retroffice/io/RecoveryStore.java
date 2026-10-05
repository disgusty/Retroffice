package com.disgusty.retroffice.io;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

/**
 * A copy of the open document with its unsaved changes, written when the app goes to the
 * background. If the app is killed, the next start offers to restore it.
 */
public final class RecoveryStore {
    private RecoveryStore() {
    }

    private static final String PREFS = "recovery";

    public static File dir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "recovery");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    public static File draft(Context ctx) {
        return new File(dir(ctx), "draft.bin");
    }

    public static File partial(Context ctx) {
        return new File(dir(ctx), "draft.part");
    }

    /** Records metadata after the draft file was completely written. */
    public static void commit(Context ctx, String name, String target) {
        File part = partial(ctx), fin = draft(ctx);
        //noinspection ResultOfMethodCallIgnored
        fin.delete();
        if (!part.renameTo(fin)) return;
        prefs(ctx).edit().putString("name", name).putString("target", target).putLong("time", System.currentTimeMillis()).commit();
    }

    public static boolean has(Context ctx) {
        return draft(ctx).exists() && prefs(ctx).getString("name", null) != null;
    }

    public static String name(Context ctx) {
        return prefs(ctx).getString("name", null);
    }

    public static String target(Context ctx) {
        return prefs(ctx).getString("target", null);
    }

    public static void clear(Context ctx) {
        //noinspection ResultOfMethodCallIgnored
        draft(ctx).delete();
        //noinspection ResultOfMethodCallIgnored
        partial(ctx).delete();
        prefs(ctx).edit().clear().commit();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
