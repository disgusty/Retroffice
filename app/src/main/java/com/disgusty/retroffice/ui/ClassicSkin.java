package com.disgusty.retroffice.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The original Android look (1.x–2.x, before Holo): black window, grey gradient title bar,
 * light-grey rounded gradient buttons, orange pressed/focused highlights, TabWidget-style tabs
 * (light selected tab, dark unselected) and dark dialogs with a light button bar.
 */
public final class ClassicSkin extends Skin {
    static final int ORANGE_TOP = 0xFFFFC74A, ORANGE_BOTTOM = 0xFFFF8A00;

    public ClassicSkin(Context ctx) {
        super(ctx);
        dark = true;
        windowBg = 0xFF000000;
        surface = 0xFF1A1A1A;
        textPrimary = 0xFFFFFFFF;
        textSecondary = 0xFFBEBEBE;
        accent = 0xFFFF9900;
        onAccent = 0xFF000000;
        divider = 0xFF3A3A3A;
        appBarBg = 0xFF3A3A3A;
        appBarFg = 0xFFFFFFFF;
        navBg = 0xFF2B2B2B;
        navActive = 0xFF000000;
        navInactive = 0xFFCCCCCC;
        pressedOverlay = 0xFFFF9900;
        dialogBg = 0xFF2A2A2A;
        dialogTitle = 0xFFFFFFFF;
        dialogText = 0xFFFFFFFF;
        fieldText = 0xFF000000;
        fieldHint = 0xFF808080;
    }

    @Override
    public int id() {
        return CLASSIC;
    }

    @Override
    public int nativeTheme() {
        return android.R.style.Theme_NoTitleBar;
    }

    @Override
    public int statusBarColor() {
        return 0xFF000000;
    }

    private Drawable orange(float r) {
        return gradient(ORANGE_TOP, ORANGE_BOTTOM, r);
    }

    @Override
    protected Drawable iconButtonBackground() {
        return pressable(null, orange(dp(3)), false);
    }

    @Override
    public View appBar(String title, Action nav, List<Action> actions, boolean showAppIcon) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundDrawable(gradient(0xFF5E5E5E, 0xFF2C2C2C, 0));
        bar.setPadding(dp(2), 0, dp(2), 0);
        if (nav != null) bar.addView(iconButton(nav, 0xFFFFFFFF, 40));
        TextView t = text(title, 16, 0xFFFFFFFF, Typeface.DEFAULT_BOLD);
        t.setShadowLayer(1.5f, 0, 1, 0xFF000000);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(nav != null ? 2 : 8), 0, dp(4), 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (Action a : actions) bar.addView(iconButton(a, 0xFFFFFFFF, 40));
        wrap.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        View edge = new View(ctx);
        edge.setBackgroundColor(0xFF111111);
        wrap.addView(edge, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return wrap;
    }

    @Override
    public View primaryButton(String text, View.OnClickListener l) {
        return button(text, l);
    }

    @Override
    public View secondaryButton(String text, View.OnClickListener l) {
        return button(text, l);
    }

    private View button(String text, View.OnClickListener l) {
        TextView b = text(text, 16, 0xFF000000, regular());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackgroundDrawable(buttonDrawable());
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(2), 0, dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    /** btn_default: light grey gradient, dark rim, rounded; orange when pressed or focused. */
    private Drawable buttonDrawable() {
        float r = dp(5);
        int rim = Math.max(1, dp(1));
        Drawable normal = border(gradient(0xFFFBFBFB, 0xFFCBCBCB, r), 0xFF7A7A7A, rim, r);
        Drawable pressed = border(orange(r), 0xFFB36200, rim, r);
        return pressable(normal, pressed, false);
    }

    private Drawable border(Drawable face, int color, int w, float r) {
        LayerDrawable ld = new LayerDrawable(new Drawable[]{rounded(color, r + w), face});
        ld.setLayerInset(1, w, w, w, w);
        return ld;
    }

    @Override
    public View textButton(String text, View.OnClickListener l) {
        View b = button(text, l);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1));
        return b;
    }

    @Override
    public View toggle(String icon, String label, boolean on, View.OnClickListener l) {
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(Icons.make(icon, on ? 0xFF000000 : 0xFFFFFFFF, dp(24)));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setBackgroundDrawable(on ? pressable(orange(dp(4)), gradient(ORANGE_BOTTOM, ORANGE_TOP, dp(4)), false)
                : pressable(null, orange(dp(4)), false));
        com.disgusty.retroffice.compat.Compat.contentDescription(iv, label);
        iv.setClickable(true);
        iv.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(40));
        lp.setMargins(dp(2), dp(4), dp(2), dp(4));
        iv.setLayoutParams(lp);
        return iv;
    }

    @Override
    protected Drawable fieldBackground() {
        // White box with a grey rim; orange rim when focused.
        return new FieldDrawable(FieldDrawable.OUTLINE, 0xFF8E8E8E, 0xFFFF9900, Math.max(1, dp(1)), dp(2), dp(4), 0xFFFFFFFF);
    }

    @Override
    protected int listItemHeightDp() {
        return 64;
    }

    @Override
    protected float listTitleSp() {
        return 20;
    }

    @Override
    protected int listIconColor() {
        return 0xFFDDDDDD;
    }

    @Override
    protected Drawable listSelector() {
        return pressable(null, orange(0), false);
    }

    @Override
    public View dividerView() {
        View v = new View(ctx);
        v.setBackgroundDrawable(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF1C1C1C, 0xFF5A5A5A, 0xFF1C1C1C}));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return v;
    }

    @Override
    public View bottomNav(List<NavItem> items, int selected, final NavListener l) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFF000000);
        bar.setPadding(0, dp(2), 0, 0);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean sel = i == selected;
            NavItem it = items.get(i);
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            float r = dp(4);
            Drawable bg = sel ? border(gradient(0xFFF5F5F5, 0xFFC4C4C4, r), 0xFF8A8A8A, Math.max(1, dp(1)), r)
                    : border(gradient(0xFF5D5D5D, 0xFF2D2D2D, r), 0xFF1A1A1A, Math.max(1, dp(1)), r);
            cell.setBackgroundDrawable(pressable(bg, orange(r), false));
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(Icons.make(it.icon, sel ? 0xFF000000 : 0xFFCCCCCC, dp(28)));
            cell.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
            TextView label = text(it.label, 12, sel ? 0xFF000000 : 0xFFCCCCCC, regular());
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            cell.addView(label, matchWrap());
            cell.setClickable(true);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    l.onSelect(idx);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(62), 1);
            lp.setMargins(dp(1), 0, dp(1), 0);
            bar.addView(cell, lp);
        }
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        return bar;
    }

    @Override
    protected void buildDialog(Dialog d, LinearLayout panel, DialogSpec spec) {
        float r = dp(6);
        panel.setBackgroundDrawable(border(rounded(0xFF2A2A2A, r), 0xFF6E6E6E, Math.max(1, dp(1)), r));
        panel.setPadding(Math.max(1, dp(1)), Math.max(1, dp(1)), Math.max(1, dp(1)), Math.max(1, dp(1)));
        if (spec.title != null) {
            LinearLayout head = new LinearLayout(ctx);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setBackgroundDrawable(gradient(0xFF4C4C4C, 0xFF2E2E2E, 0));
            head.setPadding(dp(10), dp(10), dp(10), dp(10));
            ImageView ic = new ImageView(ctx);
            ic.setImageDrawable(Icons.make(spec.destructive ? Icons.CLEAR_HISTORY : Icons.DOC, 0xFFFFFFFF, dp(28)));
            head.addView(ic, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView t = text(spec.title, 20, 0xFFFFFFFF, regular());
            t.setPadding(dp(8), 0, 0, 0);
            head.addView(t);
            panel.addView(head, matchWrap());
            View rule = new View(ctx);
            rule.setBackgroundColor(0xFF5A5A5A);
            panel.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        }
        panel.addView(dialogBody(spec, 16, dp(12), dp(12)), matchWrap());
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setBackgroundDrawable(gradient(0xFFBDBDBD, 0xFF959595, 0));
        buttons.setPadding(dp(4), dp(4), dp(4), dp(4));
        if (spec.positive != null) buttons.addView(textButton(spec.positive, dismissThen(d, spec.onPositive)));
        if (spec.negative != null) buttons.addView(textButton(spec.negative, dismissThen(d, spec.onNegative)));
        panel.addView(buttons, matchWrap());
    }

    @Override
    protected int dialogWidthDp() {
        return 320;
    }

    @Override
    protected Drawable popupBackground() {
        // Options menu panel of the era: light grey with a dark rim.
        float r = dp(3);
        return border(gradient(0xFFF2F2F2, 0xFFD6D6D6, r), 0xFF555555, Math.max(1, dp(1)), r);
    }

    @Override
    protected int popupText() {
        return 0xFF000000;
    }

    @Override
    protected int popupPadDp() {
        return 2;
    }

    @Override
    public int deskColor() {
        return 0xFF202020;
    }
}
