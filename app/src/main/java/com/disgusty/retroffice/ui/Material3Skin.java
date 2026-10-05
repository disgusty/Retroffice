package com.disgusty.retroffice.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

import com.disgusty.retroffice.compat.Compat;

/**
 * Material Design 3 ("Material You"), per m3.material.io: tonal color roles (dynamic on Android 12+),
 * 64dp small top app bar, 80dp navigation bar with a 64x32dp pill indicator, fully rounded 40dp
 * buttons, 28dp-corner dialogs and Roboto type scale.
 */
public final class Material3Skin extends Skin {
    int primary, onPrimary, primaryContainer, secondaryContainer, onSecondaryContainer;
    int surfaceContainer, surfaceContainerHigh, onSurface, onSurfaceVariant, outline, outlineVariant;

    public Material3Skin(Context ctx) {
        super(ctx);
        dark = Compat.isNightMode(ctx);
        int[][] pal = Compat.dynamicPalettes(ctx);
        if (pal != null) fromPalettes(pal);
        else if (dark) baselineDark();
        else baselineLight();

        windowBg = surface;
        textPrimary = onSurface;
        textSecondary = onSurfaceVariant;
        accent = primary;
        onAccent = onPrimary;
        divider = outlineVariant;
        appBarBg = surface;
        appBarFg = onSurface;
        navBg = surfaceContainer;
        navActive = onSecondaryContainer;
        navInactive = onSurfaceVariant;
        pressedOverlay = (onSurface & 0x00FFFFFF) | 0x1F000000;
        dialogBg = surfaceContainerHigh;
        dialogTitle = onSurface;
        dialogText = onSurfaceVariant;
        fieldText = onSurface;
        fieldHint = onSurfaceVariant;
    }

    private void baselineLight() {
        primary = 0xFF6750A4;
        onPrimary = 0xFFFFFFFF;
        primaryContainer = 0xFFEADDFF;
        secondaryContainer = 0xFFE8DEF8;
        onSecondaryContainer = 0xFF1D192B;
        surface = 0xFFFEF7FF;
        surfaceContainer = 0xFFF3EDF7;
        surfaceContainerHigh = 0xFFECE6F0;
        onSurface = 0xFF1D1B20;
        onSurfaceVariant = 0xFF49454F;
        outline = 0xFF79747E;
        outlineVariant = 0xFFCAC4D0;
    }

    private void baselineDark() {
        primary = 0xFFD0BCFF;
        onPrimary = 0xFF381E72;
        primaryContainer = 0xFF4F378B;
        secondaryContainer = 0xFF4A4458;
        onSecondaryContainer = 0xFFE8DEF8;
        surface = 0xFF141218;
        surfaceContainer = 0xFF211F26;
        surfaceContainerHigh = 0xFF2B2930;
        onSurface = 0xFFE6E0E9;
        onSurfaceVariant = 0xFFCAC4D0;
        outline = 0xFF938F99;
        outlineVariant = 0xFF49454F;
    }

    /** Color roles from the system tonal palettes (tones 0,10,50,100..900,1000 -> index 0..12). */
    private void fromPalettes(int[][] p) {
        int[] a1 = p[0], a2 = p[1], n1 = p[2], n2 = p[3];
        if (!dark) {
            primary = a1[8];
            onPrimary = a1[0];
            primaryContainer = a1[3];
            secondaryContainer = a2[3];
            onSecondaryContainer = a2[11];
            surface = n1[1];
            surfaceContainer = n1[2];
            surfaceContainerHigh = mix(n1[2], n1[3], 0.5f);
            onSurface = n1[11];
            onSurfaceVariant = n2[9];
            outline = n2[7];
            outlineVariant = n2[4];
        } else {
            primary = a1[4];
            onPrimary = a1[10];
            primaryContainer = a1[9];
            secondaryContainer = a2[9];
            onSecondaryContainer = a2[3];
            surface = mix(n1[11], n1[12], 0.4f);
            surfaceContainer = mix(n1[11], n1[10], 0.25f);
            surfaceContainerHigh = mix(n1[11], n1[10], 0.55f);
            onSurface = n1[3];
            onSurfaceVariant = n2[4];
            outline = n2[6];
            outlineVariant = n2[9];
        }
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    @Override
    public int id() {
        return MATERIAL3;
    }

    @SuppressLint("InlinedApi")
    @Override
    public int nativeTheme() {
        if (Compat.SDK >= 29) return android.R.style.Theme_DeviceDefault_DayNight;
        if (Compat.SDK >= 21) return dark ? android.R.style.Theme_Material_NoActionBar : android.R.style.Theme_Material_Light_NoActionBar;
        if (Compat.SDK >= 13) return dark ? android.R.style.Theme_Holo_NoActionBar : android.R.style.Theme_Holo_Light_NoActionBar;
        return dark ? android.R.style.Theme_Black_NoTitleBar : android.R.style.Theme_Light_NoTitleBar;
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
    protected Drawable iconButtonBackground() {
        // Circular state layer, 40dp inside the 48dp target.
        return pressable(null, ovalLayer(), true);
    }

    private Drawable ovalLayer() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(pressedOverlay);
        return g;
    }

    @Override
    public View appBar(String title, Action nav, List<Action> actions, boolean showAppIcon) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(appBarBg);
        bar.setPadding(dp(4), 0, dp(4), 0);
        bar.setMinimumHeight(dp(64));
        if (nav != null) bar.addView(iconButton(nav, onSurface, 48));
        TextView t = text(title, 22, onSurface, regular());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setPadding(dp(nav != null ? 4 : 12), 0, dp(8), 0);
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        for (Action a : actions) bar.addView(iconButton(a, onSurfaceVariant, 48));
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        return bar;
    }

    @Override
    public View primaryButton(String text, View.OnClickListener l) {
        return button(text, l, true);
    }

    @Override
    public View secondaryButton(String text, View.OnClickListener l) {
        return button(text, l, false);
    }

    private View button(String text, View.OnClickListener l, boolean filled) {
        TextView b = text(text, 14, filled ? onPrimary : primary, medium());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(24), 0, dp(24), 0);
        float r = dp(20);
        Drawable face = filled ? rounded(primary, r) : stroked(0, outline, Math.max(1, dp(1)), r);
        Drawable pressed = filled ? rounded(mix(primary, onPrimary, 0.12f), r) : rounded(pressedOverlay, r);
        Drawable bg = Compat.ripple((filled ? onPrimary : primary) & 0x00FFFFFF | 0x1F000000, face, rounded(0xFFFFFFFF, r));
        b.setBackgroundDrawable(bg != null ? bg : pressable(face, pressed, false));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        b.setLayoutParams(lp);
        return b;
    }

    @Override
    public View textButton(String text, View.OnClickListener l) {
        TextView b = text(text, 14, primary, medium());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(dp(40));
        b.setMinWidth(dp(48));
        b.setBackgroundDrawable(pressable(null, rounded(pressedOverlay, dp(20)), true));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    @Override
    protected int toggleOnColor() {
        return secondaryContainer;
    }

    @Override
    protected int onAccentToggle() {
        return onSecondaryContainer;
    }

    @Override
    protected float toggleRadiusDp() {
        return 20;
    }

    @Override
    protected Drawable fieldBackground() {
        return new FieldDrawable(FieldDrawable.OUTLINE, outline, primary, dp(1), dp(2), dp(4), 0);
    }

    @Override
    protected int listIconColor() {
        return onSurfaceVariant;
    }

    @Override
    public View bottomNav(List<NavItem> items, int selected, final NavListener l) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(navBg);
        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            boolean sel = i == selected;
            NavItem it = items.get(i);
            LinearLayout cell = new LinearLayout(ctx);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, dp(12), 0, dp(16));
            FrameLayout pill = new FrameLayout(ctx);
            if (sel) pill.setBackgroundDrawable(rounded(secondaryContainer, dp(16)));
            else pill.setBackgroundDrawable(pressable(null, rounded(pressedOverlay, dp(16)), true));
            ImageView icon = new ImageView(ctx);
            icon.setImageDrawable(Icons.make(it.icon, sel ? onSecondaryContainer : onSurfaceVariant, dp(24)));
            icon.setScaleType(ImageView.ScaleType.CENTER);
            pill.addView(icon, frame(dp(64), dp(32), Gravity.CENTER));
            cell.addView(pill, new LinearLayout.LayoutParams(dp(64), dp(32)));
            TextView label = text(it.label, 12, sel ? onSurface : onSurfaceVariant, medium());
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            label.setSingleLine(true);
            cell.addView(label, matchWrap());
            cell.setClickable(true);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    l.onSelect(idx);
                }
            });
            bar.addView(cell, new LinearLayout.LayoutParams(0, dp(80), 1));
        }
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80)));
        return bar;
    }

    @Override
    protected void buildDialog(Dialog d, LinearLayout panel, DialogSpec spec) {
        panel.setBackgroundDrawable(rounded(surfaceContainerHigh, dp(28)));
        panel.setPadding(0, dp(24), 0, dp(20));
        if (spec.title != null) {
            TextView t = text(spec.title, 24, onSurface, regular());
            t.setPadding(dp(24), 0, dp(24), dp(16));
            panel.addView(t);
        }
        panel.addView(dialogBody(spec, 14, dp(24), 0), matchWrap());
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        buttons.setPadding(dp(16), dp(16), dp(16), 0);
        if (spec.negative != null) buttons.addView(textButton(spec.negative, dismissThen(d, spec.onNegative)));
        if (spec.positive != null) {
            View p = textButton(spec.positive, dismissThen(d, spec.onPositive));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(8);
            buttons.addView(p, lp);
        }
        panel.addView(buttons, matchWrap());
    }

    @Override
    protected int dialogWidthDp() {
        return 400;
    }

    @Override
    protected Drawable popupBackground() {
        return rounded(surfaceContainer, dp(4));
    }

    @Override
    protected int popupText() {
        return onSurface;
    }

    @Override
    protected float popupTextSp() {
        return 14;
    }

    @Override
    public int deskColor() {
        return surfaceContainer;
    }
}
