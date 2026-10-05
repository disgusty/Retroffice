package com.disgusty.retroffice.text;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.text.Layout;

import com.disgusty.retroffice.xml.XNode;

/**
 * Span types attached to the live editing text.
 *
 * Structural spans (Para/Run/Frame/Obj/Mark) remember where each character came from in the
 * source XML so that unchanged paragraphs can be written back verbatim and changed ones rebuilt
 * with their original properties. Fmt* spans are the user-editable formatting. Disp* spans only
 * affect how text looks on screen and are never written anywhere.
 */
public final class Spans {
    private Spans() {
    }

    /** Covers one paragraph of the source. Inclusive on both ends so new text joins a paragraph. */
    public static final class ParaSpan {
        public final XNode node;
        /** Set once any edit touches this paragraph; clean paragraphs are saved verbatim. */
        public boolean dirty;

        public ParaSpan(XNode node) {
            this.node = node;
        }
    }

    /** Dialect-specific run properties (e.g. a DOCX w:rPr) for the characters it covers. */
    public static final class RunSpan {
        public final Object props;

        public RunSpan(Object props) {
            this.props = props;
        }
    }

    /** A wrapper element (hyperlink, ODF span, tracked insertion, ...) around some characters. */
    public static final class Frame {
        public final String open, close;
        /** Nesting depth at load time; frames are re-opened outermost first. */
        public final int depth;
        /** Formatting-like frames extend while typing at their end; links don't. */
        public final boolean extendsOnType;
        /** ODF span style name, used to resolve the formatting baseline; null otherwise. */
        public final String styleName;

        public Frame(String open, String close, int depth, boolean extendsOnType, String styleName) {
            this.open = open;
            this.close = close;
            this.depth = depth;
            this.extendsOnType = extendsOnType;
            this.styleName = styleName;
        }
    }

    public static final class FrameSpan {
        public final Frame frame;

        public FrameSpan(Frame frame) {
            this.frame = frame;
        }
    }

    /** Markup we keep but don't edit (image, footnote, field...), shown as one object character. */
    public static final class Obj {
        public final String xml;
        /** True when {@link #xml} is a run child that must be re-wrapped in a run on output (DOCX). */
        public final boolean runChild;
        public final String label;
        public Bitmap bitmap;
        /** Preferred on-screen size in px for a bitmap (0 = natural). */
        public int width, height;

        public Obj(String xml, boolean runChild, String label) {
            this.xml = xml;
            this.runChild = runChild;
            this.label = label;
        }
    }

    public static final class ObjSpan extends ReplacementSpan {
        public final Obj obj;
        /** Max width available for images, set by the editor from the page width. */
        public static int maxWidth = 600;

        public ObjSpan(Obj obj) {
            this.obj = obj;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            if (obj.bitmap != null) {
                int[] wh = imageSize();
                if (fm != null) {
                    fm.ascent = fm.top = -wh[1];
                    fm.descent = fm.bottom = 0;
                }
                return wh[0];
            }
            Paint p = chipPaint(paint);
            return (int) (p.measureText(obj.label) + p.getTextSize() * 0.8f);
        }

        private int[] imageSize() {
            int w = obj.width > 0 ? obj.width : obj.bitmap.getWidth();
            int h = obj.height > 0 ? obj.height : obj.bitmap.getHeight();
            if (w > maxWidth && w > 0) {
                h = h * maxWidth / w;
                w = maxWidth;
            }
            return new int[]{Math.max(1, w), Math.max(1, h)};
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            if (obj.bitmap != null) {
                int[] wh = imageSize();
                Rect src = new Rect(0, 0, obj.bitmap.getWidth(), obj.bitmap.getHeight());
                RectF dst = new RectF(x, y - wh[1], x + wh[0], y);
                canvas.drawBitmap(obj.bitmap, src, dst, null);
                return;
            }
            Paint p = chipPaint(paint);
            float pad = p.getTextSize() * 0.4f;
            float w = p.measureText(obj.label) + pad * 2;
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(0x22000000);
            float fh = p.getTextSize();
            RectF r = new RectF(x + 1, y - fh, x + w - 1, y + fh * 0.25f);
            canvas.drawRoundRect(r, fh * 0.25f, fh * 0.25f, bg);
            p.setColor(0xFF555555);
            canvas.drawText(obj.label, x + pad, y, p);
        }

        private static Paint chipPaint(Paint base) {
            Paint p = new Paint(base);
            p.setTextSize(base.getTextSize() * 0.8f);
            p.setTypeface(Typeface.DEFAULT);
            p.setUnderlineText(false);
            p.setStrikeThruText(false);
            return p;
        }
    }

    /** Invisible markup (bookmark, proofing mark, field code...) kept at a position. */
    public static final class Mark {
        public final String xml;
        public final int seq;
        public final Frame[] frames;

        public Mark(String xml, int seq, Frame[] frames) {
            this.xml = xml;
            this.seq = seq;
            this.frames = frames;
        }
    }

    public static final class MarkSpan {
        public final Mark mark;

        public MarkSpan(Mark mark) {
            this.mark = mark;
        }
    }

    // ---- User-editable formatting ----

    public static final class FmtBold extends StyleSpan {
        public FmtBold() {
            super(Typeface.BOLD);
        }
    }

    public static final class FmtItalic extends StyleSpan {
        public FmtItalic() {
            super(Typeface.ITALIC);
        }
    }

    public static final class FmtUnderline extends UnderlineSpan {
    }

    public static final class FmtStrike extends StrikethroughSpan {
    }

    public static final int BOLD = 1, ITALIC = 2, UNDERLINE = 4, STRIKE = 8;

    // ---- Display only ----

    public static final class DispSize extends RelativeSizeSpan {
        public DispSize(float proportion) {
            super(proportion);
        }
    }

    public static final class DispColor extends ForegroundColorSpan {
        public DispColor(int color) {
            super(color);
        }
    }

    /** Bullet for list paragraphs (display only; the list itself lives in the paragraph properties). */
    public static final class DispBullet implements LeadingMarginSpan {
        private final int indent;
        private final int level;

        public DispBullet(int indent, int level) {
            this.indent = indent;
            this.level = level;
        }

        @Override
        public int getLeadingMargin(boolean first) {
            return indent * (level + 1);
        }

        @Override
        public void drawLeadingMargin(Canvas c, Paint p, int x, int dir, int top, int baseline, int bottom,
                                      CharSequence text, int start, int end, boolean first, Layout layout) {
            if (!first) return;
            if (!(text instanceof android.text.Spanned)) return;
            android.text.Spanned sp = (android.text.Spanned) text;
            if (sp.getSpanStart(this) != start) return;
            Paint.Style style = p.getStyle();
            p.setStyle(Paint.Style.FILL);
            float r = p.getTextSize() * 0.15f;
            float cx = x + dir * (indent * level + indent * 0.45f);
            float cy = baseline - p.getTextSize() * 0.32f;
            c.drawCircle(cx, cy, r, p);
            p.setStyle(style);
        }
    }
}
