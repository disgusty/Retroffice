package com.disgusty.retroffice.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.disgusty.retroffice.compat.Compat;

/**
 * One interface style. The app builds all of its UI through a Skin, so switching style swaps every
 * visual (colors, shapes, metrics, type) without touching screen logic. Subclasses follow the
 * platform look of their era: {@link ClassicSkin} (Android 1.x–2.x), {@link HoloSkin} (3.0–4.4),
 * {@link Material1Skin} (5.0–11) and {@link Material3Skin} (12+).
 */
public abstract class Skin {
    public static final int CLASSIC = 0, HOLO = 1, MATERIAL = 2, MATERIAL3 = 3;

    protected final Context ctx;
    protected final float density;

    // Palette, set by subclasses.
    public int windowBg, surface, textPrimary, textSecondary, accent, onAccent, divider;
    public int appBarBg, appBarFg;
    public int navBg, navActive, navInactive;
    public int pressedOverlay;
    public int dialogBg, dialogTitle, dialogText;
    /** Text field colors. */
    public int fieldText, fieldHint;
    public boolean dark;

    protected Skin(Context ctx) {
        this.ctx = ctx;
        this.density = ctx.getResources().getDisplayMetrics().density;
    }

    public static Skin create(Context ctx, int style) {
        switch (style) {
            case CLASSIC: return new ClassicSkin(ctx);
            case HOLO: return new HoloSkin(ctx);
            case MATERIAL: return new Material1Skin(ctx);
            default: return new Material3Skin(ctx);
        }
    }

    /** The style matching the running Android version. */
    public static int defaultStyle() {
        int sdk = Compat.SDK;
        if (sdk >= 31) return MATERIAL3;
        if (sdk >= 21) return MATERIAL;
        if (sdk >= 11) return HOLO;
        return CLASSIC;
    }

    public abstract int id();

    /** Platform theme to run under, so system-drawn bits (text selection, IME) roughly match. */
    public abstract int nativeTheme();

    public int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    // ------------------------------------------------------------------ typography

    public Typeface regular() {
        return Typeface.DEFAULT;
    }

    public Typeface medium() {
        return Typeface.DEFAULT_BOLD;
    }

    public TextView text(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        return t;
    }

    public TextView body(String s) {
        return text(s, 16, textPrimary, regular());
    }

    public TextView caption(String s) {
        return text(s, 14, textSecondary, regular());
    }

    // ------------------------------------------------------------------ app bar

    public static final class Action {
        public final String icon, label;
        public final View.OnClickListener click;
        public final View.OnLongClickListener longClick;

        public Action(String icon, String label, View.OnClickListener click, View.OnLongClickListener longClick) {
            this.icon = icon;
            this.label = label;
            this.click = click;
            this.longClick = longClick;
        }
    }

    /** Top bar with an optional navigation (back) button, a title and trailing actions. */
    public abstract View appBar(String title, Action nav, List<Action> actions, boolean showAppIcon);

    /** A square icon button for the app bar or toolbars. */
    public View iconButton(final Action a, int color, int sizeDp) {
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(Icons.make(a.icon, color, dp(24)));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setBackgroundDrawable(iconButtonBackground());
        iv.setClickable(true);
        iv.setFocusable(true);
        if (a.label != null) com.disgusty.retroffice.compat.Compat.contentDescription(iv, a.label);
        if (a.click != null) iv.setOnClickListener(a.click);
        if (a.longClick != null) iv.setOnLongClickListener(a.longClick);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }

    protected Drawable iconButtonBackground() {
        return pressable(null, new ColorDrawable(pressedOverlay), true);
    }

    // ------------------------------------------------------------------ buttons

    /** Main call-to-action button. */
    public abstract View primaryButton(String text, View.OnClickListener l);

    /** Secondary button. */
    public abstract View secondaryButton(String text, View.OnClickListener l);

    /** Flat text button (dialogs, toolbars). */
    public View textButton(String text, View.OnClickListener l) {
        TextView b = text(buttonLabel(text), 14, accent, medium());
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinWidth(dp(64));
        b.setMinHeight(dp(36));
        b.setBackgroundDrawable(pressable(null, rounded(pressedOverlay, dp(2)), true));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(l);
        return b;
    }

    protected String buttonLabel(String s) {
        return s;
    }

    /** A two-state toggle (bold/italic...) used in the formatting bar. */
    public View toggle(String icon, String label, boolean on, View.OnClickListener l) {
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(Icons.make(icon, on ? onAccentToggle() : textPrimary, dp(24)));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        Drawable onBg = rounded(toggleOnColor(), dp(toggleRadiusDp()));
        iv.setBackgroundDrawable(on ? pressable(onBg, new ColorDrawable(pressedOverlay), false)
                : pressable(null, new ColorDrawable(pressedOverlay), true));
        com.disgusty.retroffice.compat.Compat.contentDescription(iv, label);
        iv.setClickable(true);
        iv.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(40));
        lp.setMargins(dp(2), dp(4), dp(2), dp(4));
        iv.setLayoutParams(lp);
        return iv;
    }

    protected int toggleOnColor() {
        return accent;
    }

    protected int onAccentToggle() {
        return onAccent;
    }

    protected float toggleRadiusDp() {
        return 2;
    }

    // ------------------------------------------------------------------ fields

    public void styleField(EditText e) {
        e.setTextColor(fieldText);
        e.setHintTextColor(fieldHint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setBackgroundDrawable(fieldBackground());
        e.setPadding(dp(8), dp(8), dp(8), dp(8));
    }

    protected abstract Drawable fieldBackground();

    // ------------------------------------------------------------------ lists

    public View listItem(String icon, String title, String subtitle, View.OnClickListener click,
                         View.OnLongClickListener longClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(listItemHeightDp()));
        row.setPadding(dp(16), dp(8), dp(16), dp(8));
        row.setBackgroundDrawable(listSelector());
        row.setClickable(true);
        row.setFocusable(true);
        if (icon != null) {
            ImageView iv = new ImageView(ctx);
            iv.setImageDrawable(Icons.make(icon, listIconColor(), dp(24)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.rightMargin = dp(listIconGapDp());
            iv.setScaleType(ImageView.ScaleType.CENTER);
            row.addView(iv, lp);
        }
        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(title, listTitleSp(), textPrimary, regular());
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(t);
        if (subtitle != null) {
            TextView s = text(subtitle, 14, textSecondary, regular());
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(s);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (click != null) row.setOnClickListener(click);
        if (longClick != null) row.setOnLongClickListener(longClick);
        return row;
    }

    protected int listItemHeightDp() {
        return 72;
    }

    protected float listTitleSp() {
        return 16;
    }

    protected int listIconColor() {
        return textSecondary;
    }

    protected int listIconGapDp() {
        return 16;
    }

    protected Drawable listSelector() {
        return pressable(null, new ColorDrawable(pressedOverlay), true);
    }

    public View dividerView() {
        View v = new View(ctx);
        v.setBackgroundColor(divider);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1))));
        return v;
    }

    // ------------------------------------------------------------------ bottom navigation

    public static final class NavItem {
        public final String icon, label;

        public NavItem(String icon, String label) {
            this.icon = icon;
            this.label = label;
        }
    }

    public interface NavListener {
        void onSelect(int index);
    }

    public abstract View bottomNav(List<NavItem> items, int selected, NavListener l);

    // ------------------------------------------------------------------ dialogs

    public static final class DialogSpec {
        public String title, message, positive, negative;
        public View content;
        public Runnable onPositive, onNegative;
        public boolean destructive;
    }

    public Dialog dialog(final DialogSpec spec) {
        final Dialog d = new Dialog(ctx);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(ctx);
        panel.setOrientation(LinearLayout.VERTICAL);
        buildDialog(d, panel, spec);
        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            int screen = ctx.getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(screen - dp(32), dp(dialogWidthDp())), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        d.setCanceledOnTouchOutside(true);
        return d;
    }

    protected int dialogWidthDp() {
        return 360;
    }

    /** Fills a dialog panel; buttons must call {@code d.dismiss()} then the spec callback. */
    protected abstract void buildDialog(Dialog d, LinearLayout panel, DialogSpec spec);

    protected View.OnClickListener dismissThen(final Dialog d, final Runnable r) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
                if (r != null) r.run();
            }
        };
    }

    protected View dialogBody(DialogSpec spec, float messageSp, int padH, int padTop) {
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(padH, padTop, padH, dp(8));
        if (spec.message != null) {
            TextView m = text(spec.message, messageSp, dialogText, regular());
            body.addView(m);
        }
        if (spec.content != null) {
            if (spec.content.getParent() != null) ((ViewGroup) spec.content.getParent()).removeView(spec.content);
            body.addView(spec.content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        ScrollView sv = new ScrollView(ctx);
        // Not a focus target: otherwise key navigation paints a highlight over the message.
        sv.setFocusable(false);
        sv.addView(body);
        return sv;
    }

    // ------------------------------------------------------------------ popup menu

    public static final class MenuItem {
        public final String label;
        public final boolean enabled;
        public final Runnable action;

        public MenuItem(String label, boolean enabled, Runnable action) {
            this.label = label;
            this.enabled = enabled;
            this.action = action;
        }
    }

    public void popup(View anchor, List<MenuItem> items) {
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundDrawable(popupBackground());
        list.setPadding(0, dp(popupPadDp()), 0, dp(popupPadDp()));
        final PopupWindow pw = new PopupWindow(list, dp(220), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        Compat.outsideTouchable(pw);
        for (final MenuItem mi : items) {
            TextView t = text(mi.label, popupTextSp(), mi.enabled ? popupText() : (popupText() & 0x00FFFFFF) | 0x61000000, regular());
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(dp(16), 0, dp(16), 0);
            t.setMinHeight(dp(48));
            if (mi.enabled) {
                t.setBackgroundDrawable(listSelector());
                t.setClickable(true);
                // Reachable with a trackball / D-pad / keyboard, not only by touch.
                t.setFocusable(true);
                t.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pw.dismiss();
                        mi.action.run();
                    }
                });
            }
            list.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        pw.showAsDropDown(anchor, 0, -anchor.getHeight() / 4);
        // Without touch (trackball / D-pad), start on the first entry; in touch mode this is a no-op.
        for (int i = 0; i < list.getChildCount(); i++) {
            if (list.getChildAt(i).isFocusable()) {
                list.getChildAt(i).requestFocus();
                break;
            }
        }
    }

    protected Drawable popupBackground() {
        return rounded(dialogBg, dp(4));
    }

    protected int popupText() {
        return dialogText;
    }

    protected float popupTextSp() {
        return 16;
    }

    protected int popupPadDp() {
        return 8;
    }

    // ------------------------------------------------------------------ window

    /** Colors the system bars (where the platform allows). */
    public void decorate(Activity a) {
        Compat.barColors(a.getWindow(), statusBarColor(), navBg, !isDarkColor(statusBarColor()), !isDarkColor(navBg));
    }

    public int statusBarColor() {
        return appBarBg;
    }

    /** Background of the document "paper" area around pages. */
    public int deskColor() {
        return dark ? 0xFF303030 : 0xFFE0E0E0;
    }

    // ------------------------------------------------------------------ drawable helpers

    public static boolean isDarkColor(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 < 140;
    }

    public GradientDrawable rounded(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    public GradientDrawable stroked(int fill, int stroke, int strokePx, float radius) {
        GradientDrawable g = rounded(fill, radius);
        g.setStroke(strokePx, stroke);
        return g;
    }

    public GradientDrawable gradient(int top, int bottom, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(radius);
        return g;
    }

    /**
     * Normal/pressed states. With {@code ripple} true and Android 5.0+, a ripple is used instead of
     * a flat pressed color (only in Material skins).
     */
    protected Drawable pressable(Drawable normal, Drawable pressed, boolean ripple) {
        if (ripple && usesRipple()) {
            Drawable r = Compat.ripple(pressedOverlay, normal, normal == null ? new ColorDrawable(0xFFFFFFFF) : null);
            if (r != null) return r;
        }
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{android.R.attr.state_focused}, pressed);
        s.addState(new int[]{android.R.attr.state_selected}, pressed);
        s.addState(new int[]{}, normal != null ? normal : new ColorDrawable(0));
        return s;
    }

    protected boolean usesRipple() {
        return false;
    }

    // ------------------------------------------------------------------ shared layout helpers

    protected LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    protected FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    protected static String upper(String s) {
        return s.toUpperCase(Locale.getDefault());
    }

    /** Simple helper for building action lists inline. */
    public static List<Action> actions(Action... a) {
        ArrayList<Action> l = new ArrayList<Action>();
        for (Action x : a) if (x != null) l.add(x);
        return l;
    }
}
