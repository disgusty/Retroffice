package com.disgusty.retroffice.io;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Recently opened/created documents. Only references are stored: clearing the history never
 * touches the files themselves.
 */
public final class HistoryStore {
    private static final String PREFS = "history", KEY = "entries";
    private static final int MAX = 100;

    public static final class Entry {
        public final String uri, name;
        public final long time;

        Entry(String uri, String name, long time) {
            this.uri = uri;
            this.name = name;
            this.time = time;
        }
    }

    private HistoryStore() {
    }

    public static ArrayList<Entry> load(Context ctx) {
        ArrayList<Entry> out = new ArrayList<Entry>();
        String raw = prefs(ctx).getString(KEY, null);
        if (raw == null) return out;
        try {
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Entry(o.getString("uri"), o.getString("name"), o.getLong("time")));
            }
        } catch (Exception e) {
            // A damaged history is not worth crashing over; start fresh.
            out.clear();
        }
        return out;
    }

    public static boolean isEmpty(Context ctx) {
        return load(ctx).isEmpty();
    }

    public static void add(Context ctx, String uri, String name) {
        ArrayList<Entry> list = load(ctx);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).uri.equals(uri)) list.remove(i);
        list.add(0, new Entry(uri, name, System.currentTimeMillis()));
        while (list.size() > MAX) list.remove(list.size() - 1);
        save(ctx, list);
    }

    public static void remove(Context ctx, String uri) {
        ArrayList<Entry> list = load(ctx);
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).uri.equals(uri)) list.remove(i);
        save(ctx, list);
    }

    public static void clear(Context ctx) {
        prefs(ctx).edit().remove(KEY).commit();
    }

    private static void save(Context ctx, ArrayList<Entry> list) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("uri", e.uri);
                o.put("name", e.name);
                o.put("time", e.time);
                a.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(ctx).edit().putString(KEY, a.toString()).commit();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
