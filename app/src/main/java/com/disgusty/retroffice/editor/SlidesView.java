package com.disgusty.retroffice.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

import com.disgusty.retroffice.R;
import com.disgusty.retroffice.slides.SlidesDoc;
import com.disgusty.retroffice.ui.Icons;
import com.disgusty.retroffice.ui.Skin;

/** Presentation editor: one slide at a time, shapes placed to scale, text edited in place. */
public final class SlidesView implements DocEditor {
    private final Context ctx;
    private final Skin skin;
    private final SlidesDoc doc;
    private final Host host;
    private final LinearLayout root;
    private final SlideCanvas canvas;
    private final TextView counter;
    private final ArrayList<DocEditText> fields = new ArrayList<DocEditText>();
    private DocEditText focused;
    private int current;

    public SlidesView(Context ctx, Skin skin, SlidesDoc doc, Host host) {
        this.ctx = ctx;
        this.skin = skin;
        this.doc = doc;
        this.host = host;
        root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(skin.deskColor());

        LinearLayout nav = new LinearLayout(ctx);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(skin.surface);
        nav.addView(skin.iconButton(new Skin.Action(Icons.PREV, ctx.getString(R.string.previous), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(current - 1);
            }
        }, null), skin.textPrimary, 48));
        counter = skin.text("", 14, skin.textPrimary, skin.regular());
        counter.setGravity(Gravity.CENTER);
        nav.addView(counter, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        nav.addView(skin.iconButton(new Skin.Action(Icons.NEXT, ctx.getString(R.string.next), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                show(current + 1);
            }
        }, null), skin.textPrimary, 48));
        root.addView(nav, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(skin.dividerView());

        FrameLayout stage = new FrameLayout(ctx);
        int pad = skin.dp(12);
        stage.setPadding(pad, pad, pad, pad);
        canvas = new SlideCanvas(ctx);
        stage.addView(canvas, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(stage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (doc.slides.isEmpty()) {
            counter.setText(ctx.getString(R.string.no_slides));
        } else {
            show(0);
        }
    }

    private void show(int idx) {
        if (doc.slides.isEmpty()) return;
        idx = Math.max(0, Math.min(doc.slides.size() - 1, idx));
        for (DocEditText f : fields) f.unbind();
        fields.clear();
        focused = null;
        canvas.removeAllViews();
        current = idx;
        counter.setText(ctx.getString(R.string.slide_of, idx + 1, doc.slides.size()));
        SlidesDoc.Slide s = doc.slides.get(idx);
        canvas.background = s.background;
        boolean darkBg = Skin.isDarkColor(s.background);
        for (SlidesDoc.Shape sh : s.shapes) {
            View v;
            if (sh.text != null) {
                final DocEditText e = new DocEditText(ctx, sh.text.text);
                e.setBackgroundDrawable(sh.fill != 0 ? new android.graphics.drawable.ColorDrawable(sh.fill) : null);
                boolean darkFill = sh.fill != 0 ? Skin.isDarkColor(sh.fill) : darkBg;
                e.setTextColor(darkFill ? 0xFFFFFFFF : 0xFF000000);
                e.setHintTextColor(darkFill ? 0x88FFFFFF : 0x66000000);
                e.setGravity(sh.title ? Gravity.CENTER_VERTICAL | Gravity.LEFT : Gravity.TOP | Gravity.LEFT);
                e.setHint(sh.title ? R.string.hint_title : (sh.baseSize >= 30 ? R.string.hint_subtitle : R.string.hint_text));
                e.setSelectionListener(new DocEditText.SelectionListener() {
                    @Override
                    public void onSelection(DocEditText f, int a, int b) {
                        focused = f;
                        host.onSelectionChanged(f);
                    }
                });
                e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                    @Override
                    public void onFocusChange(View view, boolean has) {
                        if (has) {
                            focused = e;
                            host.history().breakGroup();
                            host.onSelectionChanged(e);
                        }
                    }
                });
                host.history().watch(e);
                fields.add(e);
                v = e;
            } else if (sh.image != null) {
                ImageView iv = new ImageView(ctx);
                iv.setImageBitmap(sh.image);
                iv.setScaleType(ImageView.ScaleType.FIT_XY);
                v = iv;
            } else {
                TextView t = new TextView(ctx);
                t.setText(sh.label != null ? sh.label : "");
                t.setGravity(Gravity.CENTER);
                t.setTextColor(0xFF777777);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                t.setBackgroundDrawable(sh.fill != 0 ? new android.graphics.drawable.ColorDrawable(sh.fill)
                        : skin.stroked(0x10000000, 0x40000000, 1, 0));
                v = t;
            }
            canvas.addShape(v, sh);
        }
        canvas.requestLayout();
        canvas.invalidate();
    }

    /** Lays shapes out at slide coordinates scaled to the available space. */
    final class SlideCanvas extends ViewGroup {
        int background = 0xFFFFFFFF;
        final ArrayList<SlidesDoc.Shape> shapes = new ArrayList<SlidesDoc.Shape>();
        float scale = 1;
        int offX, offY;

        SlideCanvas(Context c) {
            super(c);
            setWillNotDraw(false);
        }

        void addShape(View v, SlidesDoc.Shape s) {
            shapes.add(s);
            addView(v);
        }

        @Override
        public void removeAllViews() {
            super.removeAllViews();
            shapes.clear();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
            setMeasuredDimension(w, h);
            float sw = Math.max(1, doc.width), sh = Math.max(1, doc.height);
            scale = Math.min(w / sw, h / sh);
            offX = (int) ((w - sw * scale) / 2);
            offY = (int) ((h - sh * scale) / 2);
            for (int i = 0; i < getChildCount(); i++) {
                SlidesDoc.Shape s = shapes.get(i);
                View c = getChildAt(i);
                if (c instanceof TextView && s.text != null) {
                    float px = Math.max(6, s.baseSize * scale);
                    TextView tv = (TextView) c;
                    if (Math.abs(tv.getTextSize() - px) > 0.5f) tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
                    int pad = (int) (7.2f * scale);
                    tv.setPadding(pad, (int) (3.6f * scale), pad, (int) (3.6f * scale));
                }
                int cw = Math.max(1, (int) (s.w * scale)), ch = Math.max(1, (int) (s.h * scale));
                c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            for (int i = 0; i < getChildCount(); i++) {
                SlidesDoc.Shape s = shapes.get(i);
                View c = getChildAt(i);
                int x = offX + (int) (s.x * scale), y = offY + (int) (s.y * scale);
                c.layout(x, y, x + c.getMeasuredWidth(), y + c.getMeasuredHeight());
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            android.graphics.Paint p = new android.graphics.Paint();
            p.setColor(0x33000000);
            float w = doc.width * scale, h = doc.height * scale;
            c.drawRect(offX + 2, offY + 2, offX + w + 2, offY + h + 2, p);
            p.setColor(background);
            c.drawRect(offX, offY, offX + w, offY + h, p);
        }
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public boolean hasFormatting() {
        return true;
    }

    @Override
    public DocEditText focusedField() {
        return focused;
    }

    @Override
    public void commitPending() {
    }

    @Override
    public void release() {
        for (DocEditText f : fields) f.unbind();
        fields.clear();
    }
}
