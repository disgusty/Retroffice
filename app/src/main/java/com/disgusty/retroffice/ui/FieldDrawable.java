package com.disgusty.retroffice.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * Text field backgrounds that react to focus: an underline (Holo, Material 1) or an outline
 * (Material 3, classic). Drawn in code so every style works on every Android version.
 */
public final class FieldDrawable extends Drawable {
    public static final int UNDERLINE = 0, UNDERLINE_TICKS = 1, OUTLINE = 2;

    private final int kind, normal, focused, thin, thick;
    private final float radius;
    private final int fill;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean isFocused;

    public FieldDrawable(int kind, int normal, int focused, int thinPx, int thickPx, float radius, int fill) {
        this.kind = kind;
        this.normal = normal;
        this.focused = focused;
        this.thin = Math.max(1, thinPx);
        this.thick = Math.max(1, thickPx);
        this.radius = radius;
        this.fill = fill;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean f = false;
        for (int s : state) if (s == android.R.attr.state_focused) f = true;
        if (f != isFocused) {
            isFocused = f;
            invalidateSelf();
            return true;
        }
        return false;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        int col = isFocused ? focused : normal;
        int w = isFocused ? thick : thin;
        if (fill != 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fill);
            c.drawRoundRect(new RectF(b), radius, radius, paint);
        }
        paint.setColor(col);
        if (kind == OUTLINE) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w);
            RectF r = new RectF(b.left + w / 2f, b.top + w / 2f, b.right - w / 2f, b.bottom - w / 2f);
            c.drawRoundRect(r, radius, radius, paint);
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        int inset = (int) radius; // reused as horizontal inset for underline styles
        float bottom = b.bottom - inset / 2f;
        c.drawRect(b.left + inset, bottom - w, b.right - inset, bottom, paint);
        if (kind == UNDERLINE_TICKS) {
            float tick = b.height() / 5f;
            c.drawRect(b.left + inset, bottom - tick, b.left + inset + w, bottom, paint);
            c.drawRect(b.right - inset - w, bottom - tick, b.right - inset, bottom, paint);
        }
    }

    @Override
    public boolean getPadding(Rect padding) {
        return false;
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
