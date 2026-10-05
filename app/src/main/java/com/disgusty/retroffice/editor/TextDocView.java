package com.disgusty.retroffice.editor;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import com.disgusty.retroffice.R;
import com.disgusty.retroffice.doc.Doc;
import com.disgusty.retroffice.doc.PlainDoc;
import com.disgusty.retroffice.doc.TextDoc;
import com.disgusty.retroffice.text.Spans;
import com.disgusty.retroffice.ui.Skin;

/** Word-processing view: the document as a white page on a desk, edited in place. */
public final class TextDocView implements DocEditor {
    private final Context ctx;
    private final Skin skin;
    private final Host host;
    private final ScrollView scroll;
    private final ArrayList<DocEditText> fields = new ArrayList<DocEditText>();
    private DocEditText focused;
    private final boolean formatting;

    public TextDocView(Context ctx, Skin skin, Doc doc, Host host) {
        this.ctx = ctx;
        this.skin = skin;
        this.host = host;
        formatting = doc instanceof TextDoc;
        scroll = new ScrollView(ctx);
        scroll.setBackgroundColor(skin.deskColor());
        scroll.setFillViewport(true);
        FrameLayout desk = new FrameLayout(ctx);
        int pad = skin.dp(12);
        desk.setPadding(pad, pad, pad, pad);
        final LinearLayout paper = new LinearLayout(ctx) {
            @Override
            protected void onSizeChanged(int w, int h, int ow, int oh) {
                super.onSizeChanged(w, h, ow, oh);
                Spans.ObjSpan.maxWidth = Math.max(100, w - getPaddingLeft() - getPaddingRight() - skin.dp(8));
            }
        };
        paper.setOrientation(LinearLayout.VERTICAL);
        paper.setBackgroundDrawable(skin.stroked(0xFFFFFFFF, 0x22000000, Math.max(1, skin.dp(1)), 0));
        int pp = skin.dp(20);
        paper.setPadding(pp, pp, pp, skin.dp(48));
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        // A page-like sheet even for short documents (A4-ish proportions, capped to the screen).
        paper.setMinimumHeight((int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.75f));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Math.min(screenW - 2 * pad, skin.dp(820)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        desk.addView(paper, plp);
        scroll.addView(desk, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (doc instanceof TextDoc) {
            TextDoc td = (TextDoc) doc;
            for (TextDoc.Block b : td.blocks) {
                if (b.segment != null) paper.addView(field(b.segment.text), matchWrap());
                else paper.addView(kept(b), keptParams());
            }
            if (td.blocks.isEmpty()) paper.addView(note(ctx.getString(R.string.no_editable_content)));
        } else if (doc instanceof PlainDoc) {
            paper.addView(field(((PlainDoc) doc).text), matchWrap());
        }
        // Tapping empty paper below the text puts the caret at the end of the last field.
        paper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (fields.isEmpty()) return;
                DocEditText last = fields.get(fields.size() - 1);
                last.requestFocus();
                last.setSelection(last.length());
            }
        });
        if (!fields.isEmpty()) {
            final DocEditText first = fields.get(0);
            first.post(new Runnable() {
                @Override
                public void run() {
                    first.requestFocus();
                }
            });
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams keptParams() {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.setMargins(0, skin.dp(8), 0, skin.dp(8));
        return lp;
    }

    private DocEditText field(Editable model) {
        final DocEditText e = new DocEditText(ctx, model);
        e.setBackgroundDrawable(null);
        e.setPadding(0, skin.dp(2), 0, skin.dp(2));
        e.setTextColor(0xFF000000);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setLineSpacing(0, 1.15f);
        e.setGravity(Gravity.TOP | Gravity.LEFT);
        e.setSelectionListener(new DocEditText.SelectionListener() {
            @Override
            public void onSelection(DocEditText v, int start, int end) {
                focused = v;
                host.onSelectionChanged(v);
            }
        });
        e.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    focused = e;
                    host.history().breakGroup();
                    host.onSelectionChanged(e);
                }
            }
        });
        host.history().watch(e);
        fields.add(e);
        if (focused == null) focused = e;
        return e;
    }

    private View kept(TextDoc.Block b) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(ctx);
        String name = "table".equals(b.label) ? ctx.getString(R.string.table) : b.label;
        label.setText(name + " — " + ctx.getString(R.string.kept_block));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        label.setTextColor(0xFF888888);
        label.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        box.addView(label);
        if (b.table != null) {
            box.addView(table(b.table));
        } else if (b.preview != null && b.preview.length() > 0) {
            TextView t = new TextView(ctx);
            t.setText(b.preview);
            t.setTextColor(0xFF444444);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            t.setPadding(skin.dp(8), skin.dp(4), skin.dp(4), skin.dp(4));
            t.setBackgroundDrawable(skin.stroked(0xFFF7F7F7, 0xFFDDDDDD, 1, 0));
            box.addView(t);
        }
        return box;
    }

    private View table(List<List<String>> rows) {
        LinearLayout t = new LinearLayout(ctx);
        t.setOrientation(LinearLayout.VERTICAL);
        int cols = 0;
        for (List<String> r : rows) cols = Math.max(cols, r.size());
        if (cols == 0) cols = 1;
        for (List<String> r : rows) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < cols; c++) {
                TextView cell = new TextView(ctx);
                cell.setText(c < r.size() ? r.get(c) : "");
                cell.setTextColor(0xFF000000);
                cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                cell.setPadding(skin.dp(4), skin.dp(3), skin.dp(4), skin.dp(3));
                cell.setBackgroundDrawable(skin.stroked(0xFFFFFFFF, 0xFF9E9E9E, 1, 0));
                row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            t.addView(row, matchWrap());
        }
        return t;
    }

    private View note(String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextColor(0xFF666666);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        return t;
    }

    @Override
    public View view() {
        return scroll;
    }

    @Override
    public boolean hasFormatting() {
        return formatting;
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
