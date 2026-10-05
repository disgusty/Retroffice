package com.disgusty.retroffice.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import java.util.HashMap;

/**
 * Vector icons drawn from SVG path data (Material Icons, Apache License 2.0), on a 24x24 grid.
 * Drawn with plain Canvas paths so they work, and stay sharp, on every Android version.
 */
public final class Icons {
    private Icons() {
    }

    public static final String BRUSH = "M7 14c-1.66 0-3 1.34-3 3 0 1.31-1.16 2-2 2 .92 1.22 2.49 2 4 2 2.21 0 4-1.79 4-4 0-1.66-1.34-3-3-3zm13.71-9.37l-1.34-1.34c-.39-.39-1.02-.39-1.41 0L9 12.25 11.75 15l8.96-8.96c.39-.39.39-1.02 0-1.41z";
    public static final String SAVE = "M17 3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V7l-4-4zm-5 16c-1.66 0-3-1.34-3-3s1.34-3 3-3 3 1.34 3 3-1.34 3-3 3zm3-10H5V5h10v4z";
    public static final String CLEAR_HISTORY = "M15 16h4v2h-4zm0-8h7v2h-7zm0 4h6v2h-6zM3 18c0 1.1.9 2 2 2h6c1.1 0 2-.9 2-2V8H3v10zM14 5h-3l-1-1H6L5 5H2v2h12z";
    public static final String HOME = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z";
    public static final String CLOUD = "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z";
    public static final String BACK = "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z";
    public static final String UNDO = "M12.5 8c-2.65 0-5.05.99-6.9 2.6L2 7v9h9l-3.62-3.62c1.39-1.16 3.16-1.88 5.12-1.88 3.54 0 6.55 2.31 7.6 5.5l2.37-.78C21.08 11.03 17.15 8 12.5 8z";
    public static final String REDO = "M18.4 10.6C16.55 8.99 14.15 8 11.5 8c-4.65 0-8.58 3.03-9.96 7.22L3.9 16c1.05-3.19 4.05-5.5 7.6-5.5 1.95 0 3.73.72 5.12 1.88L13 16h9V7l-3.6 3.6z";
    public static final String MORE = "M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z";
    public static final String BOLD = "M15.6 10.79c.97-.67 1.65-1.77 1.65-2.79 0-2.26-1.75-4-4-4H7v14h7.04c2.09 0 3.71-1.7 3.71-3.79 0-1.52-.86-2.82-2.15-3.42zM10 6.5h3c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5h-3v-3zm3.5 9H10v-3h3.5c.83 0 1.5.67 1.5 1.5s-.67 1.5-1.5 1.5z";
    public static final String ITALIC = "M10 4v3h2.21l-3.42 8H6v3h8v-3h-2.21l3.42-8H18V4z";
    public static final String UNDERLINE = "M12 17c3.31 0 6-2.69 6-6V3h-2.5v8c0 1.93-1.57 3.5-3.5 3.5S8.5 12.93 8.5 11V3H6v8c0 3.31 2.69 6 6 6zm-7 2v2h14v-2H5z";
    public static final String STRIKE = "M10 19h4v-3h-4v3zM5 4v3h5v3h4V7h5V4H5zM3 14h18v-2H3v2z";
    public static final String DOC = "M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z";
    public static final String SHEET = "M10 10.02h5V21h-5zM17 21h3c1.1 0 2-.9 2-2v-9h-5v11zm3-18H5c-1.1 0-2 .9-2 2v3h19V5c0-1.1-.9-2-2-2zM3 19c0 1.1.9 2 2 2h3V10H3v9z";
    public static final String SLIDES = "M10 8v8l5-4-5-4zm9-5H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z";
    public static final String FOLDER = "M10 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2h-8l-2-2z";
    public static final String CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    public static final String PREV = "M15.41 7.41L14 6l-6 6 6 6 1.41-1.41L10.83 12z";
    public static final String NEXT = "M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z";
    public static final String UP = "M4 12l1.41 1.41L11 7.83V20h2V7.83l5.58 5.59L20 12l-8-8-8 8z";
    public static final String CLOSE = "M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z";
    public static final String SEARCH = "M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z";

    private static final HashMap<String, Path> CACHE = new HashMap<String, Path>();

    static synchronized Path path(String data) {
        Path p = CACHE.get(data);
        if (p == null) {
            p = PathParser.parse(data);
            CACHE.put(data, p);
        }
        return p;
    }

    /** An icon drawable of {@code sizePx} square in the given color. */
    public static Drawable make(String data, int color, int sizePx) {
        return new IconDrawable(path(data), color, sizePx);
    }

    static final class IconDrawable extends Drawable {
        private final Path src;
        private final Path scaled = new Path();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private int lastW = -1, lastH = -1;

        IconDrawable(Path src, int color, int size) {
            this.src = src;
            this.size = size;
            paint.setColor(color);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() != lastW || b.height() != lastH) {
                Matrix m = new Matrix();
                float s = Math.min(b.width(), b.height()) / 24f;
                m.setScale(s, s);
                m.postTranslate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
                scaled.reset();
                src.transform(m, scaled);
                lastW = b.width();
                lastH = b.height();
            }
            canvas.drawPath(scaled, paint);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            lastW = -1;
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /** Minimal SVG path-data parser: M m L l H h V v C c S s Q q T t Z z (no arcs). */
    static final class PathParser {
        private final String s;
        private int i;

        private PathParser(String s) {
            this.s = s;
        }

        static Path parse(String d) {
            return new PathParser(d).run();
        }

        private Path run() {
            Path p = new Path();
            p.setFillType(Path.FillType.WINDING);
            float x = 0, y = 0, sx = 0, sy = 0, cx = 0, cy = 0;
            char cmd = 'M', prev = ' ';
            while (true) {
                skip();
                if (i >= s.length()) break;
                char c = s.charAt(i);
                if (Character.isLetter(c)) {
                    cmd = c;
                    i++;
                } else if (cmd == 'M') {
                    cmd = 'L';
                } else if (cmd == 'm') {
                    cmd = 'l';
                }
                boolean rel = Character.isLowerCase(cmd);
                switch (Character.toUpperCase(cmd)) {
                    case 'M': {
                        float nx = num(), ny = num();
                        if (rel) {
                            nx += x;
                            ny += y;
                        }
                        p.moveTo(nx, ny);
                        x = sx = cx = nx;
                        y = sy = cy = ny;
                        break;
                    }
                    case 'L': {
                        float nx = num(), ny = num();
                        if (rel) {
                            nx += x;
                            ny += y;
                        }
                        p.lineTo(nx, ny);
                        x = cx = nx;
                        y = cy = ny;
                        break;
                    }
                    case 'H': {
                        float nx = num();
                        if (rel) nx += x;
                        p.lineTo(nx, y);
                        x = cx = nx;
                        cy = y;
                        break;
                    }
                    case 'V': {
                        float ny = num();
                        if (rel) ny += y;
                        p.lineTo(x, ny);
                        y = cy = ny;
                        cx = x;
                        break;
                    }
                    case 'C': {
                        float x1 = num(), y1 = num(), x2 = num(), y2 = num(), nx = num(), ny = num();
                        if (rel) {
                            x1 += x; y1 += y; x2 += x; y2 += y; nx += x; ny += y;
                        }
                        p.cubicTo(x1, y1, x2, y2, nx, ny);
                        cx = x2;
                        cy = y2;
                        x = nx;
                        y = ny;
                        break;
                    }
                    case 'S': {
                        float x2 = num(), y2 = num(), nx = num(), ny = num();
                        if (rel) {
                            x2 += x; y2 += y; nx += x; ny += y;
                        }
                        char pu = Character.toUpperCase(prev);
                        float x1 = pu == 'C' || pu == 'S' ? 2 * x - cx : x;
                        float y1 = pu == 'C' || pu == 'S' ? 2 * y - cy : y;
                        p.cubicTo(x1, y1, x2, y2, nx, ny);
                        cx = x2;
                        cy = y2;
                        x = nx;
                        y = ny;
                        break;
                    }
                    case 'Q': {
                        float x1 = num(), y1 = num(), nx = num(), ny = num();
                        if (rel) {
                            x1 += x; y1 += y; nx += x; ny += y;
                        }
                        p.quadTo(x1, y1, nx, ny);
                        cx = x1;
                        cy = y1;
                        x = nx;
                        y = ny;
                        break;
                    }
                    case 'T': {
                        float nx = num(), ny = num();
                        if (rel) {
                            nx += x;
                            ny += y;
                        }
                        char pu = Character.toUpperCase(prev);
                        float x1 = pu == 'Q' || pu == 'T' ? 2 * x - cx : x;
                        float y1 = pu == 'Q' || pu == 'T' ? 2 * y - cy : y;
                        p.quadTo(x1, y1, nx, ny);
                        cx = x1;
                        cy = y1;
                        x = nx;
                        y = ny;
                        break;
                    }
                    case 'Z':
                        p.close();
                        x = cx = sx;
                        y = cy = sy;
                        break;
                    default:
                        return p;
                }
                prev = cmd;
            }
            return p;
        }

        private void skip() {
            while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == ',')) i++;
        }

        private float num() {
            skip();
            int st = i;
            if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
            boolean dot = false;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isDigit(c)) i++;
                else if (c == '.' && !dot) {
                    dot = true;
                    i++;
                } else if ((c == 'e' || c == 'E') && i + 1 < s.length()) {
                    i++;
                    if (s.charAt(i) == '-' || s.charAt(i) == '+') i++;
                } else break;
            }
            if (st == i) return 0;
            return Float.parseFloat(s.substring(st, i));
        }
    }
}
