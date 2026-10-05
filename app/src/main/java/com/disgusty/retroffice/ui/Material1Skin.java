package com.disgusty.retroffice.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import com.disgusty.retroffice.compat.Compat;

/**
 * Material Design (2014 spec, Android 5.0–11): Indigo 500 / Pink A200 palette, 56dp toolbar with
 * 4dp elevation, raised buttons with all-caps Roboto Medium 14sp labels and 2dp corners, the 56dp
 * bottom navigation (2016 addendum), ripples, and light window background #FAFAFA.
 */
public final class Material1Skin extends Skin {
    static final int PRIMARY = 0xFF3F51B5, PRIMARY_DARK = 0xFF303F9F, ACCENT = 0xFFFF4081;

    public Material1Skin(Context ctx) {
        super(ctx);
        dark = false;
        windowBg = 0xFFFAFAFA;
        surface = 0xFFFFFFFF;
        textPrimary = 0xDE000000;
        textSecondary = 0x8A000000;
        accent = ACCENT;
        onAccent = 0xFFFFFFFF;
        divider = 0x1F000000;
        appBarBg = PRIMARY;
        appBarFg = 0xFFFFFFFF;
        navBg = 0xFFFFFFFF;
        navActive = PRIMARY;
        navInactive = 0x8A000000;
        pressedOverlay = 0x1F000000;
        dialogBg = 0xFFFFFFFF;
        dialogTitle = 0xDE000000;
        dialogText = 0x8A000000;
        fieldText = 0xDE000000;
        fieldHint = 0x61000000;
    }

    @Override
    public int id() {
        return MATERIAL;
    }

    @SuppressLint("InlinedApi")
    @Override
    public int nativeTheme() {
        if (Compat.SDK >= 21) return android.R.style.Theme_Material_Light_NoActionBar;
        if (Compat.SDK >= 13) return android.R.style.Theme_Holo_Light_NoActionBar;
        return android.R.style.Theme_Light_NoTitleBar;
    }

    @Override
    public Typeface medium() {
        return Compat.medium();
    }

    @Override
    protected boolean usesRipple() {
        return true;
    }

    @Override
    protected String buttonLabel(String s) {
        return upper(s);
    }

    @Override
    public int statusBarColor() {
        return PRIMARY_DARK;
    }

    @Override
    public View appBar(String title, Action nav, List<Action> actions, boolean showAppIcon) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(PRIMARY);
        bar.setPadding(dp(4), 0, dp(4), 0);
        if (nav != null) bar.addView(iconButton(nav, 0xFFFFFFFF, 48));
        TextView t = text(title, 20, 0xFFFFFFFF, medium());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(nav != null ? 24 : 12), 0, dp(8), 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (Action a : actions) bar.addView(iconButton(a, 0xFFFFFFFF, 48));
        wrap.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        if (Compat.SDK >= 21) {
            Compat.elevation(wrap, dp(4));
            wrap.setBackgroundColor(PRIMARY);
        } else {
            // 4dp elevation drawn as a soft shadow under the bar.
            View shadow = new View(ctx);
            shadow.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x40000000, 0x00000000}));
            wrap.addView(shadow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));
        }
        return wrap;
    }

    @Override
    protected Drawable iconButtonBackground() {
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(0x33FFFFFF);
        return pressable(null, oval, true);
    }

    @Override
    public View primaryButton(String text, View.OnClickListener l) {
        return raised(text, l, ACCENT, 0xFFFFFFFF);
    }

    @Override
    public View secondaryButton(String text, View.OnClickListener l) {
        return raised(text, l, 0xFFD6D7D7, 0xDE000000);
    }

    private View raised(String text, View.OnClickListener l, int color, int textColor) {
        TextView b = text(upper(text), 14, textColor, medium());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        float r = dp(2);
        Drawable face;
        if (Compat.SDK >= 21) {
            face = rounded(color, r);
            Drawable rip = Compat.ripple(0x33000000, face, null);
            b.setBackgroundDrawable(rip);
            Compat.elevation(b, dp(2));
            Compat.roundOutline(b, r);
        } else {
            // Shadow layer 1dp below the face approximates the 2dp resting elevation.
            LayerDrawable normal = new LayerDrawable(new Drawable[]{rounded(0x33000000, r), rounded(color, r)});
            normal.setLayerInset(0, 0, dp(1), 0, 0);
            normal.setLayerInset(1, 0, 0, 0, dp(1));
            LayerDrawable pressed = new LayerDrawable(new Drawable[]{rounded(0x44000000, r), rounded(Material3Skin.mix(color, 0xFF000000, 0.12f), r)});
            pressed.setLayerInset(0, 0, dp(2), 0, 0);
            pressed.setLayerInset(1, 0, 0, 0, dp(1));
            b.setBackgroundDrawable(pressable(normal, pressed, false));
        }
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    protected int toggleOnColor() {
        return 0x29000000;
    }

    @Override
    protected int onAccentToggle() {
        return PRIMARY;
    }

    @Override
    protected Drawable fieldBackground() {
        return new FieldDrawable(FieldDrawable.UNDERLINE, 0x61000000, ACCENT, dp(1), dp(2), dp(4), 0);
    }

    @Override
    public View bottomNav(List<NavItem> items, int selected, final NavListener l) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        if (Compat.SDK < 21) {
            View shadow = new View(ctx);
            shadow.setBackgroundDrawable(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x30000000, 0x00000000}));
            wrap.addView(shadow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));
        }
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xFFFFFFFF);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean sel = i == selected;
            NavItem it = items.get(i);
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(sel ? 6 : 8), 0, dp(10));
            cell.setBackgroundDrawable(pressable(null, new android.graphics.drawable.ColorDrawable(pressedOverlay), true));
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(Icons.make(it.icon, sel ? PRIMARY : 0x8A000000, dp(24)));
            cell.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = text(it.label, sel ? 14 : 12, sel ? PRIMARY : 0x8A000000, regular());
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
            bar.addView(cell, new LinearLayout.LayoutParams(0, dp(56), 1));
        }
        wrap.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));
        if (Compat.SDK >= 21) {
            wrap.setBackgroundColor(0xFFFFFFFF);
            Compat.elevation(wrap, dp(8));
        }
        return wrap;
    }

    @Override
    protected void buildDialog(Dialog d, LinearLayout panel, DialogSpec spec) {
        panel.setBackgroundDrawable(rounded(0xFFFFFFFF, dp(2)));
        panel.setPadding(0, dp(24), 0, dp(8));
        if (spec.title != null) {
            TextView t = text(spec.title, 20, 0xDE000000, medium());
            t.setPadding(dp(24), 0, dp(24), dp(20));
            panel.addView(t);
        }
        panel.addView(dialogBody(spec, 16, dp(24), 0), matchWrap());
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        buttons.setPadding(dp(8), dp(8), dp(8), 0);
        buttons.setMinimumHeight(dp(52));
        if (spec.negative != null) buttons.addView(textButton(spec.negative, dismissThen(d, spec.onNegative)));
        if (spec.positive != null) buttons.addView(textButton(spec.positive, dismissThen(d, spec.onPositive)));
        panel.addView(buttons, matchWrap());
    }

    @Override
    protected int dialogWidthDp() {
        return 360;
    }

    @Override
    protected Drawable popupBackground() {
        return stroked(0xFFFFFFFF, 0x1F000000, 1, dp(2));
    }

    @Override
    protected int popupText() {
        return 0xDE000000;
    }
}
