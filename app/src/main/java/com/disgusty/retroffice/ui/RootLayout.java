package com.disgusty.retroffice.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.LinearLayout;

import com.disgusty.retroffice.compat.Compat;

/**
 * Root of every screen. Where the system draws the app behind the status and navigation bars
 * (Android 15+), it pads itself by their size and paints those strips in the style's bar colors.
 */
public final class RootLayout extends LinearLayout {
    private int topColor, bottomColor;
    private int insetTop, insetBottom;
    private final Paint paint = new Paint();

    public RootLayout(Context ctx) {
        super(ctx);
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        if (Compat.edgeToEdge()) {
            Compat.listenInsets(this, new Compat.InsetsListener() {
                @Override
                public void onInsets(int left, int top, int right, int bottom) {
                    insetTop = top;
                    insetBottom = bottom;
                    setPadding(left, top, right, bottom);
                    invalidate();
                }
            });
        }
    }

    public void setBarColors(int top, int bottom) {
        topColor = top;
        bottomColor = bottom;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (insetTop > 0) {
            paint.setColor(topColor);
            c.drawRect(0, 0, getWidth(), insetTop, paint);
        }
        if (insetBottom > 0) {
            paint.setColor(bottomColor);
            c.drawRect(0, getHeight() - insetBottom, getWidth(), getHeight(), paint);
        }
    }
}
