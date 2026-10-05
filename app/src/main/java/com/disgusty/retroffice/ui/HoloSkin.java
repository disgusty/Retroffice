package com.disgusty.retroffice.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import com.disgusty.retroffice.R;
import com.disgusty.retroffice.compat.Compat;

/**
 * Holo Dark (Android 3.0–4.4), after the Android Design guidelines of 2012: black window, #33B5E5
 * accent, 48dp action bar with the app icon, tabs with a 4dp blue indicator, borderless dialog
 * buttons under a blue title rule, and blue-tinted pressed states.
 */
public final class HoloSkin extends Skin {
    static final int BLUE = 0xFF33B5E5, BLUE_DARK = 0xFF0099CC, PRESSED = 0x9933B5E5;

    public HoloSkin(Context ctx) {
        super(ctx);
        dark = true;
        windowBg = 0xFF000000;
        surface = 0xFF1B1B1B;
        textPrimary = 0xFFF3F3F3;
        textSecondary = 0xFFBEBEBE;
        accent = BLUE;
        onAccent = 0xFFFFFFFF;
        divider = 0xFF333333;
        appBarBg = 0xFF222222;
        appBarFg = 0xFFFFFFFF;
        navBg = 0xFF222222;
        navActive = 0xFFFFFFFF;
        navInactive = 0xFFBEBEBE;
        pressedOverlay = PRESSED;
        dialogBg = 0xFF282828;
        dialogTitle = BLUE;
        dialogText = 0xFFF3F3F3;
        fieldText = 0xFFF3F3F3;
        fieldHint = 0xFF808080;
    }

    @Override
    public int id() {
        return HOLO;
    }

    @SuppressLint("InlinedApi")
    @Override
    public int nativeTheme() {
        if (Compat.SDK >= 13) return android.R.style.Theme_Holo_NoActionBar;
        if (Compat.SDK >= 11) return android.R.style.Theme_Holo;
        return android.R.style.Theme_Black_NoTitleBar;
    }

    @Override
    public int statusBarColor() {
        return 0xFF000000;
    }

    @Override
    public View appBar(String title, Action nav, List<Action> actions, boolean showAppIcon) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(appBarBg);
        if (nav != null) {
            // Holo "up" affordance: a caret next to the app icon, both tappable.
            LinearLayout up = new LinearLayout(ctx);
            up.setGravity(Gravity.CENTER_VERTICAL);
            up.setBackgroundDrawable(pressable(null, new ColorDrawable(PRESSED), false));
            ImageView caret = new ImageView(ctx);
            caret.setImageDrawable(Icons.make(Icons.PREV, 0xFFFFFFFF, dp(20)));
            up.addView(caret, new LinearLayout.LayoutParams(dp(16), dp(24)));
            ImageView logo = new ImageView(ctx);
            logo.setImageResource(R.drawable.ic_launcher);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            up.addView(logo, new LinearLayout.LayoutParams(dp(32), dp(32)));
            up.setPadding(0, 0, dp(8), 0);
            up.setClickable(true);
            up.setOnClickListener(nav.click);
            com.disgusty.retroffice.compat.Compat.contentDescription(up, nav.label);
            bar.addView(up, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        } else if (showAppIcon) {
            ImageView logo = new ImageView(ctx);
            logo.setImageResource(R.drawable.ic_launcher);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(32), dp(32));
            lp.leftMargin = dp(8);
            lp.rightMargin = dp(8);
            bar.addView(logo, lp);
        }
        TextView t = text(title, 18, 0xFFFFFFFF, regular());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(4), 0, dp(4), 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (Action a : actions) bar.addView(iconButton(a, 0xFFFFFFFF, 48));
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return bar;
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
        TextView b = text(text, 18, 0xFFF3F3F3, regular());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), 0, dp(16), 0);
        float r = dp(2);
        // btn_default_holo_dark: dark grey face with a faint lighter rim; blue when pressed.
        Drawable normal = stroked(0xFF353535, 0xFF4A4A4A, Math.max(1, dp(1)), r);
        Drawable pressed = stroked(0xFF1F7D9E, BLUE, Math.max(1, dp(1)), r);
        b.setBackgroundDrawable(pressable(normal, pressed, false));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        lp.setMargins(0, dp(2), 0, dp(2));
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    public View textButton(String text, View.OnClickListener l) {
        TextView b = text(text, 16, 0xFFF3F3F3, regular());
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));
        b.setBackgroundDrawable(pressable(null, new ColorDrawable(PRESSED), false));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected int toggleOnColor() {
        return PRESSED;
    }

    @Override
    protected int onAccentToggle() {
        return 0xFFFFFFFF;
    }

    @Override
    protected Drawable fieldBackground() {
        return new FieldDrawable(FieldDrawable.UNDERLINE_TICKS, 0xFF7F7F7F, BLUE, dp(1), dp(2), dp(4), 0);
    }

    @Override
    protected int listItemHeightDp() {
        return 64;
    }

    @Override
    protected float listTitleSp() {
        return 18;
    }

    @Override
    protected int listIconColor() {
        return 0xFFBEBEBE;
    }

    @Override
    public View bottomNav(List<NavItem> items, int selected, final NavListener l) {
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setBackgroundColor(navBg);
        View rule = new View(ctx);
        rule.setBackgroundColor(0x6633B5E5);
        wrap.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean sel = i == selected;
            NavItem it = items.get(i);
            FrameLayout cell = new FrameLayout(ctx);
            cell.setBackgroundDrawable(pressable(null, new ColorDrawable(PRESSED), false));
            LinearLayout content = new LinearLayout(ctx);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setGravity(Gravity.CENTER);
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(Icons.make(it.icon, sel ? 0xFFFFFFFF : 0xFF999999, dp(24)));
            content.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView label = text(upper(it.label), 12, sel ? 0xFFFFFFFF : 0xFF999999, android.graphics.Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            content.addView(label, matchWrap());
            cell.addView(content, frame(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
            if (sel) {
                View ind = new View(ctx);
                ind.setBackgroundColor(BLUE);
                cell.addView(ind, frame(ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP));
            }
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
        return wrap;
    }

    @Override
    protected void buildDialog(Dialog d, LinearLayout panel, DialogSpec spec) {
        panel.setBackgroundColor(0xFF282828);
        if (spec.title != null) {
            TextView t = text(spec.title, 22, BLUE, regular());
            t.setPadding(dp(16), dp(14), dp(16), dp(14));
            panel.addView(t);
            View rule = new View(ctx);
            rule.setBackgroundColor(BLUE);
            panel.addView(rule, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));
        }
        panel.addView(dialogBody(spec, 18, dp(16), dp(16)), matchWrap());
        View top = new View(ctx);
        top.setBackgroundColor(0x33FFFFFF);
        panel.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        if (spec.negative != null) {
            buttons.addView(textButton(spec.negative, dismissThen(d, spec.onNegative)), new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        if (spec.negative != null && spec.positive != null) {
            View sep = new View(ctx);
            sep.setBackgroundColor(0x33FFFFFF);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(32));
            lp.gravity = Gravity.CENTER_VERTICAL;
            buttons.addView(sep, lp);
        }
        if (spec.positive != null) {
            buttons.addView(textButton(spec.positive, dismissThen(d, spec.onPositive)), new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        panel.addView(buttons, matchWrap());
    }

    @Override
    protected Drawable popupBackground() {
        LayerDrawable ld = new LayerDrawable(new Drawable[]{new ColorDrawable(0xFF000000), new ColorDrawable(0xFF282828)});
        ld.setLayerInset(1, 1, 1, 1, 1);
        return ld;
    }

    @Override
    protected float popupTextSp() {
        return 18;
    }

    @Override
    public int deskColor() {
        return 0xFF1B1B1B;
    }
}
