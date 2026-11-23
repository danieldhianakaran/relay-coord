package com.relaycoord.test;

/**
 * Inverse-time overcurrent curve families.
 *
 * IEC 60255-151:  t = TMS * K / ((I/Is)^alpha - 1) + delay
 * IEEE C37.112:   t = TMS * A / ((I/Is)^p - B) + delay
 * Definite time:  t = delay  (any I > Is)
 */
public final class Curve {

    public final String label;
    public final String formula;

    private final boolean ieee;
    private final boolean definite;
    private final double k;
    private final double alpha;
    private final double a;
    private final double b;
    private final double p;

    private Curve(String label, String formula, boolean ieee, boolean definite,
                  double k, double alpha, double a, double b, double p) {
        this.label = label;
        this.formula = formula;
        this.ieee = ieee;
        this.definite = definite;
        this.k = k;
        this.alpha = alpha;
        this.a = a;
        this.b = b;
        this.p = p;
    }

    public static final Curve[] ALL = new Curve[]{
            new Curve("IEC Standard Inverse",
                    "t = 0.14\u00b7TMS / ((I/Is)^0.02 \u2212 1) + delay",
                    false, false, 0.14, 0.02, 0, 0, 0),
            new Curve("IEC Very Inverse",
                    "t = 13.5\u00b7TMS / ((I/Is) \u2212 1) + delay",
                    false, false, 13.5, 1.0, 0, 0, 0),
            new Curve("IEC Extremely Inverse",
                    "t = 80\u00b7TMS / ((I/Is)^2 \u2212 1) + delay",
                    false, false, 80.0, 2.0, 0, 0, 0),
            new Curve("IEC Long-Time Inverse",
                    "t = 120\u00b7TMS / ((I/Is) \u2212 1) + delay",
                    false, false, 120.0, 1.0, 0, 0, 0),
            new Curve("IEEE Moderately Inverse",
                    "t = TMS\u00b7(0.0515 / ((I/Is)^0.02 \u2212 0.114)) + delay",
                    true, false, 0, 0, 0.0515, 0.114, 0.02),
            new Curve("IEEE Very Inverse",
                    "t = TMS\u00b7(19.61 / ((I/Is)^2 \u2212 0.491)) + delay",
                    true, false, 0, 0, 19.61, 0.491, 2.0),
            new Curve("IEEE Extremely Inverse",
                    "t = TMS\u00b7(28.2 / ((I/Is)^2 \u2212 0.1217)) + delay",
                    true, false, 0, 0, 28.2, 0.1217, 2.0),
            new Curve("Definite Time",
                    "t = delay   (for any I > Is)",
                    false, true, 0, 0, 0, 0, 0)
    };

    public boolean isDefinite() {
        return definite;
    }

    /** Trip time in seconds for a given current, or +Infinity if it never trips. */
    public double tripTime(double current, double pickup, double tms, double delay) {
        if (pickup <= 0 || current <= pickup) return Double.POSITIVE_INFINITY;
        if (definite) return delay;
        double m = current / pickup;
        double t;
        if (ieee) {
            double denom = Math.pow(m, p) - b;
            if (denom <= 0) return Double.POSITIVE_INFINITY;
            t = tms * a / denom;
        } else {
            double denom = Math.pow(m, alpha) - 1.0;
            if (denom <= 0) return Double.POSITIVE_INFINITY;
            t = tms * k / denom;
        }
        if (!(t > 0)) return Double.POSITIVE_INFINITY;
        return t + delay;
    }

    /**
     * Current in amps at which this curve trips in exactly {@code time} seconds,
     * or NaN when no solution exists (time at/below the delay, or definite time).
     */
    public double currentAtTime(double time, double pickup, double tms, double delay) {
        if (pickup <= 0 || tms <= 0) return Double.NaN;
        if (definite) return time >= delay ? pickup : Double.NaN;
        double tt = time - delay;
        if (tt <= 0) return Double.NaN;
        double m;
        if (ieee) {
            m = Math.pow(a * tms / tt + b, 1.0 / p);
        } else {
            m = Math.pow(k * tms / tt + 1.0, 1.0 / alpha);
        }
        if (!(m > 1.0) || Double.isInfinite(m) || Double.isNaN(m)) return Double.NaN;
        return m * pickup;
    }
}
