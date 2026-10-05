package com.disgusty.retroffice.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;

import com.disgusty.retroffice.sheet.Cell;
import com.disgusty.retroffice.sheet.CellRef;
import com.disgusty.retroffice.sheet.Sheet;

/**
 * A scrollable spreadsheet grid drawn directly on a Canvas, with row/column headers. Works with
 * the Android 1.0 view toolkit (no two-way scroll containers exist there).
 */
public final class SheetGridView extends View {
    public interface Listener {
        void onCellSelected(int row, int col);
    }

    private Sheet sheet;
    private final float d;
    private final int headerW, headerH, rowH, defColW;
    private int scrollXp, scrollYp;
    private int selRow, selCol;
    private Listener listener;
    private final Paint grid = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint();
    private final Scroller scroller;
    private VelocityTracker velocity;
    private float downX, downY, lastX, lastY;
    private boolean dragging;
    private final int slop;
    private final int accent;

    public SheetGridView(Context ctx, int accent) {
        super(ctx);
        this.accent = accent;
        d = ctx.getResources().getDisplayMetrics().density;
        headerW = (int) (44 * d);
        headerH = (int) (26 * d);
        rowH = (int) (28 * d);
        defColW = (int) (84 * d);
        slop = (int) (8 * d);
        scroller = new Scroller(ctx);
        grid.setColor(0xFFD0D0D0);
        text.setTextSize(14 * d * ctx.getResources().getConfiguration().fontScale);
        text.setColor(0xFF000000);
        setFocusable(true);
        setClickable(true);
    }

    public void setSheet(Sheet s) {
        sheet = s;
        scrollXp = scrollYp = 0;
        selRow = selCol = 0;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public int selRow() {
        return selRow;
    }

    public int selCol() {
        return selCol;
    }

    public void select(int row, int col) {
        selRow = Math.max(0, row);
        selCol = Math.max(0, col);
        ensureVisible();
        invalidate();
        if (listener != null) listener.onCellSelected(selRow, selCol);
    }

    private int colW(int c) {
        if (sheet != null) {
            Float pt = sheet.colWidthPt.get(c);
            if (pt != null && pt > 1) return Math.max((int) (24 * d), (int) (pt * 4f / 3f * d));
        }
        return defColW;
    }

    private int colX(int c) {
        int x = 0;
        for (int i = 0; i < c; i++) x += colW(i);
        return x;
    }

    private int maxRows() {
        return Math.min(1048576, Math.max(sheet == null ? 0 : sheet.maxRow + 60, 200));
    }

    private int maxCols() {
        return Math.min(16384, Math.max(sheet == null ? 0 : sheet.maxCol + 12, 26));
    }

    private int contentW() {
        return colX(maxCols());
    }

    private int contentH() {
        return maxRows() * rowH;
    }

    private void clampScroll() {
        int maxX = Math.max(0, contentW() - (getWidth() - headerW));
        int maxY = Math.max(0, contentH() - (getHeight() - headerH));
        scrollXp = Math.max(0, Math.min(scrollXp, maxX));
        scrollYp = Math.max(0, Math.min(scrollYp, maxY));
    }

    private void ensureVisible() {
        int x = colX(selCol), w = colW(selCol), y = selRow * rowH;
        int vw = getWidth() - headerW, vh = getHeight() - headerH;
        if (vw <= 0 || vh <= 0) return;
        if (x < scrollXp) scrollXp = x;
        else if (x + w > scrollXp + vw) scrollXp = x + w - vw;
        if (y < scrollYp) scrollYp = y;
        else if (y + rowH > scrollYp + vh) scrollYp = y + rowH - vh;
        clampScroll();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(0xFFFFFFFF);
        if (sheet == null) return;
        int firstRow = scrollYp / rowH;
        int lastRow = Math.min(maxRows() - 1, (scrollYp + h - headerH) / rowH + 1);
        int firstCol = 0, x0 = 0;
        while (firstCol < maxCols() - 1 && x0 + colW(firstCol) <= scrollXp) {
            x0 += colW(firstCol);
            firstCol++;
        }
        Paint.FontMetrics fm = text.getFontMetrics();
        float baselineOff = (rowH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
        // Cells
        c.save();
        c.clipRect(headerW, headerH, w, h);
        for (int r = firstRow; r <= lastRow; r++) {
            int y = headerH + r * rowH - scrollYp;
            int x = headerW + x0 - scrollXp;
            for (int col = firstCol; col < maxCols() && x < w; col++) {
                int cw = colW(col);
                Cell cell = sheet.get(r, col);
                if (cell != null && cell.covered) {
                    fill.setColor(0xFFF4F4F4);
                    c.drawRect(x, y, x + cw, y + rowH, fill);
                }
                if (cell != null && cell.display != null && cell.display.length() > 0) {
                    String s = cell.display.replace('\n', ' ');
                    c.save();
                    c.clipRect(x + 1, y, x + cw - 1, y + rowH);
                    float tw = text.measureText(s);
                    float tx = cell.type == Cell.NUMBER ? x + cw - 4 * d - tw : x + 4 * d;
                    if (cell.type == Cell.ERROR) text.setColor(0xFFC62828);
                    c.drawText(s, tx, y + baselineOff, text);
                    text.setColor(0xFF000000);
                    c.restore();
                }
                c.drawLine(x + cw, y, x + cw, y + rowH, grid);
                x += cw;
            }
            c.drawLine(headerW, y + rowH, w, y + rowH, grid);
        }
        // Selection
        int sx = headerW + colX(selCol) - scrollXp, sy = headerH + selRow * rowH - scrollYp;
        Paint sel = new Paint();
        sel.setStyle(Paint.Style.STROKE);
        sel.setStrokeWidth(2 * d);
        sel.setColor(accent);
        c.drawRect(sx + d, sy + d, sx + colW(selCol) - d, sy + rowH - d, sel);
        c.restore();
        // Column headers
        fill.setColor(0xFFF1F1F1);
        c.drawRect(0, 0, w, headerH, fill);
        Paint ht = new Paint(text);
        ht.setTextSize(text.getTextSize() * 0.85f);
        ht.setColor(0xFF555555);
        Paint.FontMetrics hfm = ht.getFontMetrics();
        float hb = (headerH - (hfm.descent - hfm.ascent)) / 2f - hfm.ascent;
        c.save();
        c.clipRect(headerW, 0, w, headerH);
        int x = headerW + x0 - scrollXp;
        for (int col = firstCol; col < maxCols() && x < w; col++) {
            int cw = colW(col);
            if (col == selCol) {
                fill.setColor(0xFFDCDCDC);
                c.drawRect(x, 0, x + cw, headerH, fill);
            }
            String name = CellRef.colName(col);
            c.drawText(name, x + (cw - ht.measureText(name)) / 2f, hb, ht);
            c.drawLine(x + cw, 0, x + cw, headerH, grid);
            x += cw;
        }
        c.restore();
        // Row headers
        fill.setColor(0xFFF1F1F1);
        c.drawRect(0, headerH, headerW, h, fill);
        c.save();
        c.clipRect(0, headerH, headerW, h);
        for (int r = firstRow; r <= lastRow; r++) {
            int y = headerH + r * rowH - scrollYp;
            if (r == selRow) {
                fill.setColor(0xFFDCDCDC);
                c.drawRect(0, y, headerW, y + rowH, fill);
            }
            String name = String.valueOf(r + 1);
            c.drawText(name, (headerW - ht.measureText(name)) / 2f, y + (rowH - (hfm.descent - hfm.ascent)) / 2f - hfm.ascent, ht);
            c.drawLine(0, y + rowH, headerW, y + rowH, grid);
        }
        c.restore();
        fill.setColor(0xFFE4E4E4);
        c.drawRect(0, 0, headerW, headerH, fill);
        c.drawLine(headerW, 0, headerW, h, grid);
        c.drawLine(0, headerH, w, headerH, grid);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (velocity == null) velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (!scroller.isFinished()) scroller.abortAnimation();
                downX = lastX = e.getX();
                downY = lastY = e.getY();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - lastX, dy = e.getY() - lastY;
                if (!dragging && (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop)) {
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    scrollXp -= (int) dx;
                    scrollYp -= (int) dy;
                    clampScroll();
                    invalidate();
                }
                lastX = e.getX();
                lastY = e.getY();
                return true;
            }
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    velocity.computeCurrentVelocity(1000);
                    int vx = (int) velocity.getXVelocity(), vy = (int) velocity.getYVelocity();
                    scroller.fling(scrollXp, scrollYp, -vx, -vy, 0, Math.max(0, contentW() - getWidth() + headerW),
                            0, Math.max(0, contentH() - getHeight() + headerH));
                    invalidate();
                } else {
                    tap(e.getX(), e.getY());
                }
                velocity.recycle();
                velocity = null;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (velocity != null) {
                    velocity.recycle();
                    velocity = null;
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void tap(float x, float y) {
        if (x < headerW || y < headerH) return;
        int row = (int) ((y - headerH + scrollYp) / rowH);
        int px = (int) (x - headerW + scrollXp);
        int col = 0, acc = 0;
        while (col < maxCols() - 1 && acc + colW(col) <= px) {
            acc += colW(col);
            col++;
        }
        select(row, col);
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollXp = scroller.getCurrX();
            scrollYp = scroller.getCurrY();
            clampScroll();
            invalidate();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        clampScroll();
    }
}
