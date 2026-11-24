package com.relaycoord.test;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/** Log-log plot of four trip stages (D1/D2/U1/U2), with current/time markers. */
public class CurvePlotView extends View {

    public interface OnPick {
        void onPick(double amps);
    }

    /** One trip stage: curve + settings + how it is drawn. */
    public static final class Stage {
        final Curve curve;
        final double pickup, tms, delay;
        final int color;
        final boolean dashed;

        public Stage(Curve curve, double pickup, double tms, double delay,
                     int color, boolean dashed) {
            this.curve = curve;
            this.pickup = pickup;
            this.tms = tms;
            this.delay = delay;
            this.color = color;
            this.dashed = dashed;
        }
    }

    private static final int C_GRID = 0xFF1B2A3A;
    private static final int C_GRID_MINOR = 0xFF152230;
    private static final int C_TEXT = 0xFF8098AE;
    private static final int C_PLOT_BG = 0xFF0A1219;
    private static final int C_MARK = 0xFFCBDCEB;

    private static final double Y_MIN = 0.01;
    private static final double Y_MAX = 1000.0;

    private final Paint pCurve = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pGrid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLabel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DashPathEffect dashEffect;

    private final RectF plot = new RectF();
    private final float d;
    private boolean large = false;

    private Stage[] stages = new Stage[0];

    private int markerMode = 0; // 0 none, 1 current, 2 time
    private double markerVal;

    private double xMin = 1, xMax = 10000;

    private OnPick onPick;

    public CurvePlotView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;

        pCurve.setStyle(Paint.Style.STROKE);
        pCurve.setStrokeWidth(d * 2.2f);
        pCurve.setStrokeCap(Paint.Cap.ROUND);

        pGrid.setStyle(Paint.Style.STROKE);
        pGrid.setStrokeWidth(d * 0.7f);

        pText.setColor(C_TEXT);
        pText.setTextSize(sp(9));

        pLabel.setTextSize(sp(8.5f));

        pFill.setStyle(Paint.Style.FILL);
        pFill.setColor(C_PLOT_BG);

        pMark.setStyle(Paint.Style.STROKE);
        pMark.setStrokeWidth(d * 1.1f);
        pMark.setColor(C_MARK);
        pMark.setPathEffect(new DashPathEffect(new float[]{d * 3.5f, d * 3.5f}, 0));

        pDot.setStyle(Paint.Style.FILL);
        dashEffect = new DashPathEffect(new float[]{d * 5f, d * 3f}, 0);
    }

    public void setOnPick(OnPick l) {
        onPick = l;
    }

    /** Larger text and wider lines for the fullscreen view. */
    public void setLarge(boolean v) {
        large = v;
        pText.setTextSize(sp(large ? 11 : 9));
        pLabel.setTextSize(sp(large ? 10.5f : 8.5f));
        pCurve.setStrokeWidth(d * (large ? 2.8f : 2.2f));
        invalidate();
    }

    /** Stages are ordered D1, D2, U1, U2. */
    public void setStages(Stage[] s) {
        stages = s == null ? new Stage[0] : s;
        invalidate();
    }

    public void setMarkerCurrent(double amps) {
        markerMode = 1;
        markerVal = amps;
        invalidate();
    }

    public void setMarkerTime(double seconds) {
        markerMode = 2;
        markerVal = seconds;
        invalidate();
    }

    public void clearMarker() {
        markerMode = 0;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        updatePlotRect(w, h);
    }

    private void updatePlotRect(int w, int h) {
        plot.set(d * (large ? 46 : 38), d * (large ? 12 : 8),
                w - d * (large ? 12 : 8), h - d * (large ? 36 : 27));
    }

    @Override
    protected void onDraw(Canvas c) {
        updatePlotRect(getWidth(), getHeight());
        computeRange();
        c.drawRoundRect(plot, d * 4, d * 4, pFill);
        drawGrid(c);
        for (int k = 0; k < stages.length; k++) {
            Stage s = stages[k];
            if (s == null || s.curve == null || !(s.pickup > 0)) continue;
            pCurve.setColor(s.color);
            pCurve.setPathEffect(s.dashed ? dashEffect : null);
            drawCurve(c, s, k);
        }
        pCurve.setPathEffect(null);
        drawMarker(c);
        drawAxes(c);
    }

    private void computeRange() {
        double lo = Double.POSITIVE_INFINITY, hiPickup = 0;
        for (Stage s : stages) {
            if (s == null || !(s.pickup > 0)) continue;
            lo = Math.min(lo, s.pickup);
            hiPickup = Math.max(hiPickup, s.pickup);
        }
        if (Double.isInfinite(lo)) {
            lo = 100;
            hiPickup = 100;
        }
        double base = lo;
        double hi = hiPickup * 400.0;
        xMin = Math.pow(10, Math.floor(Math.log10(base * 0.4)));
        xMax = Math.pow(10, Math.ceil(Math.log10(hi)));
        if (xMax / xMin < 1000) xMax = xMin * 1000;
        if (xMax / xMin > 1e6) xMax = xMin * 1e6;
        if (!(xMax > xMin)) {
            xMin = 1;
            xMax = 10000;
        }
    }

    private float xOf(double amps) {
        double a = Math.log10(xMin), b = Math.log10(xMax);
        return plot.left + (float) ((Math.log10(amps) - a) / (b - a) * plot.width());
    }

    private double iOf(float x) {
        double a = Math.log10(xMin), b = Math.log10(xMax);
        double f = (x - plot.left) / plot.width();
        return Math.pow(10, a + f * (b - a));
    }

    private float yOf(double sec) {
        double a = Math.log10(Y_MIN), b = Math.log10(Y_MAX);
        return plot.top + (float) ((1.0 - (Math.log10(sec) - a) / (b - a)) * plot.height());
    }

    private void drawGrid(Canvas c) {
        // minor lines at 2 and 5 of each decade
        pGrid.setColor(C_GRID_MINOR);
        for (double dec = xMin; dec <= xMax * 1.0001; dec *= 10) {
            for (int m : new int[]{2, 5}) {
                double v = dec * m;
                if (v < xMin || v > xMax) continue;
                float x = xOf(v);
                c.drawLine(x, plot.top, x, plot.bottom, pGrid);
            }
        }
        for (double dec = Y_MIN; dec <= Y_MAX * 1.0001; dec *= 10) {
            for (int m : new int[]{2, 5}) {
                double v = dec * m;
                if (v < Y_MIN || v > Y_MAX) continue;
                float y = yOf(v);
                c.drawLine(plot.left, y, plot.right, y, pGrid);
            }
        }

        pGrid.setColor(C_GRID);
        pText.setColor(C_TEXT);
        pText.setTextAlign(Paint.Align.CENTER);
        for (double v = xMin; v <= xMax * 1.0001; v *= 10) {
            float x = xOf(v);
            c.drawLine(x, plot.top, x, plot.bottom, pGrid);
            String s = tick(v);
            c.drawText(s, x, plot.bottom + d * 11.5f, pText);
        }

        pText.setTextAlign(Paint.Align.RIGHT);
        for (double v = Y_MIN; v <= Y_MAX * 1.0001; v *= 10) {
            float y = yOf(v);
            c.drawLine(plot.left, y, plot.right, y, pGrid);
            c.drawText(tick(v), plot.left - d * 4f, y + d * 3f, pText);
        }
    }

    /** High definite-time stage takes over above its pickup. */
    private boolean highDefinite(int lowIdx) {
        int hi = lowIdx + 1;
        if (hi >= stages.length) return false;
        Stage h = stages[hi];
        return h != null && h.curve != null && h.curve.isDefinite() && h.pickup > 0;
    }

    private double highPickup(int lowIdx) {
        Stage h = stages[lowIdx + 1];
        return h == null ? 0 : h.pickup;
    }

    /** Low set is not plotted beyond an active high definite-time pickup. */
    private double lowLimit(int idx) {
        return (idx == 0 || idx == 2) && highDefinite(idx) ? highPickup(idx)
                : Double.POSITIVE_INFINITY;
    }

    private void drawCurve(Canvas c, Stage s, int idx) {
        double limit = lowLimit(idx);
        Path path = new Path();
        boolean pen = false;
        for (float x = plot.left; x <= plot.right + 0.5f; x += d) {
            double amps = iOf(x);
            if (amps > limit) break;
            double t = s.curve.tripTime(amps, s.pickup, s.tms, s.delay);
            if (Double.isNaN(t) || Double.isInfinite(t) || t < Y_MIN || t > Y_MAX) {
                pen = false;
                continue;
            }
            float y = yOf(t);
            if (y < plot.top || y > plot.bottom) {
                pen = false;
                continue;
            }
            if (!pen) {
                path.moveTo(x, y);
                pen = true;
            } else {
                path.lineTo(x, y);
            }
        }
        c.drawPath(path, pCurve);
    }

    private void drawMarker(Canvas c) {
        if (markerMode == 0) return;

        if (markerMode == 1) {
            float x = xOf(markerVal);
            if (x < plot.left || x > plot.right) return;
            c.drawLine(x, plot.top, x, plot.bottom, pMark);
            for (int k = 0; k < stages.length; k++) dot(c, x, k);
            pText.setColor(C_MARK);
            pText.setTextAlign(Paint.Align.LEFT);
            c.drawText(fmtA(markerVal), Math.min(x + d * 3, plot.right - d * 40),
                    plot.top + d * 10, pText);
        } else {
            float y = yOf(markerVal);
            if (y < plot.top || y > plot.bottom) return;
            c.drawLine(plot.left, y, plot.right, y, pMark);
            for (int k = 0; k < stages.length; k++) timeDot(c, y, k);
            pText.setColor(C_MARK);
            pText.setTextAlign(Paint.Align.RIGHT);
            c.drawText(fmtT(markerVal), plot.right - d * 3, y - d * 4, pText);
        }
    }

    /** Stagger labels so four stage dots stay readable on a small screen. */
    private float labelY(int idx, float y) {
        float off = (idx == 0 ? 11 : idx == 1 ? -5 : idx == 2 ? 22 : -16) * d;
        return Math.max(plot.top + d * 8, Math.min(y + off, plot.bottom - d * 3));
    }

    private void dot(Canvas c, float x, int idx) {
        Stage s = stages[idx];
        if (s == null || s.curve == null || !(s.pickup > 0)) return;
        if (markerVal > lowLimit(idx)) return;
        double t = s.curve.tripTime(markerVal, s.pickup, s.tms, s.delay);
        if (Double.isNaN(t) || Double.isInfinite(t) || t < Y_MIN || t > Y_MAX) return;
        float y = yOf(t);
        if (y < plot.top || y > plot.bottom) return;
        pDot.setColor(s.color);
        c.drawCircle(x, y, d * 3f, pDot);
        pDot.setColor(0xFF0A1219);
        c.drawCircle(x, y, d * 1.2f, pDot);
        pLabel.setColor(s.color);
        boolean left = idx == 1 || idx == 3;
        pLabel.setTextAlign(left ? Paint.Align.RIGHT : Paint.Align.LEFT);
        float lx = left ? Math.max(x - d * 4, plot.left + d * 36)
                : Math.min(x + d * 4, plot.right - d * 36);
        c.drawText(fmtT(t), lx, labelY(idx, y), pLabel);
    }

    private void timeDot(Canvas c, float y, int idx) {
        Stage s = stages[idx];
        if (s == null || s.curve == null || !(s.pickup > 0)) return;
        double amps = s.curve.currentAtTime(markerVal, s.pickup, s.tms, s.delay);
        if (Double.isNaN(amps) || amps < xMin || amps > xMax) return;
        if (amps > lowLimit(idx)) return;
        float x = xOf(amps);
        pDot.setColor(s.color);
        c.drawCircle(x, y, d * 3f, pDot);
        pDot.setColor(0xFF0A1219);
        c.drawCircle(x, y, d * 1.2f, pDot);
        pLabel.setColor(s.color);
        pLabel.setTextAlign(Paint.Align.CENTER);
        float ly = labelY(idx, y);
        c.drawText(fmtA(amps), Math.max(plot.left + d * 20,
                Math.min(x, plot.right - d * 20)), ly, pLabel);
    }

    private void drawAxes(Canvas c) {
        pText.setColor(C_TEXT);
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTextSize(sp(large ? 10 : 8.5f));
        c.drawText("Current (A)", plot.centerX(), getHeight() - d * 3.5f, pText);

        c.save();
        c.rotate(-90, d * (large ? 12 : 9.5f), plot.centerY());
        c.drawText("Time (s)", d * (large ? 12 : 9.5f), plot.centerY() + d * 3f, pText);
        c.restore();

        pText.setTextSize(sp(large ? 11 : 9));
    }

    private String tick(double v) {
        if (v >= 1000) {
            double k = v / 1000.0;
            return (k == Math.rint(k) ? String.valueOf((long) k) : String.format(Locale.US, "%.1f", k)) + "k";
        }
        if (v >= 1) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private String fmtA(double a) {
        if (Double.isNaN(a) || Double.isInfinite(a)) return "\u2014";
        if (a >= 1000) return String.format(Locale.US, "%.2f kA", a / 1000.0);
        if (a >= 100) return Math.round(a) + " A";
        if (a >= 10) return String.format(Locale.US, "%.1f A", a);
        return String.format(Locale.US, "%.2f A", a);
    }

    private String fmtT(double t) {
        if (Double.isNaN(t) || Double.isInfinite(t)) return "\u2014";
        if (t >= 100) return Math.round(t) + " s";
        if (t >= 10) return String.format(Locale.US, "%.1f s", t);
        if (t >= 1) return String.format(Locale.US, "%.2f s", t);
        return String.format(Locale.US, "%.3f s", t);
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            if (onPick != null && plot.contains(e.getX(), e.getY())) {
                onPick.onPick(iOf(e.getX()));
            }
            return true;
        }
        return true;
    }
}
