package com.relaycoord.test;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int BG = 0xFF0C141D;
    private static final int CARD = 0xFF152130;
    private static final int FIELD = 0xFF0E1A25;
    private static final int STROKE = 0xFF24384B;
    private static final int TEXT = 0xFFE7EFF7;
    private static final int MUTED = 0xFF8CA0B5;
    private static final int ACCENT = 0xFF35C6E8;
    private static final int ORANGE = 0xFFFFB020;
    private static final int GOOD = 0xFF4CD98A;
    private static final int BAD = 0xFFFF6B6B;
    private static final int TAB_ON = 0xFF17293A;

    private SharedPreferences prefs;
    private boolean loaded = false;

    private RelayPanel downPanel, upPanel;
    private CurvePlotView plot;
    private FrameLayout content;
    private final TextView[] tabs = new TextView[3];
    private final View[] pages = new View[3];

    private View headerView, tabBar, solverCard;
    private TextView hintTv;
    private LinearLayout chartCard;
    private TextView expandBtn;
    private boolean fullscreen = false;

    private EditText curEt, timeEt;
    private TextView resultTv, verdictTv;
    private boolean fromCurrent = true;
    private boolean guard = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("relay", MODE_PRIVATE);

        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(0xFF0A1219);
        w.setNavigationBarColor(0xFF0A1219);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFocusableInTouchMode(true);

        headerView = buildHeader();
        root.addView(headerView);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        downPanel = new RelayPanel("DOWNSTREAM RELAY", "feeder / load side", ACCENT, "d");
        upPanel = new RelayPanel("UPSTREAM RELAY", "source / bus side", ORANGE, "u");

        pages[0] = scrollPage(downPanel.root);
        pages[1] = scrollPage(upPanel.root);
        pages[2] = buildPlotPage();
        for (View p : pages) content.addView(p, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        tabBar = buildTabs();
        root.addView(tabBar);

        setContentView(root);
        root.requestFocus();

        Runnable onChange = new Runnable() {
            @Override
            public void run() {
                if (!loaded) return;
                persist();
                syncPlot();
                resolve();
            }
        };
        downPanel.setOnChange(onChange);
        upPanel.setOnChange(onChange);

        load();
        showTab(0);
    }

    // ---------------------------------------------------------------- header

    private View buildHeader() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(14), dp(9), dp(14), dp(9));
        h.setBackgroundColor(0xFF0A1219);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText("RELAY COORDINATION");
        t.setTextColor(TEXT);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        titles.addView(t);
        TextView s = new TextView(this);
        s.setText("IEC 60255-151  \u00b7  IEEE C37.112");
        s.setTextColor(MUTED);
        s.setTextSize(8.5f);
        titles.addView(s);
        h.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(this);
        badge.setText("A  \u00b7  s");
        badge.setTextColor(ACCENT);
        badge.setTextSize(10);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(8), dp(4), dp(8), dp(4));
        badge.setBackground(rounded(0xFF10202C, 6, 0xFF23404F));
        h.addView(badge);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(h, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View line = new View(this);
        line.setBackgroundColor(0xFF1B2B3A);
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dp(1)))));
        return wrap;
    }

    // ------------------------------------------------------------------ tabs

    private View buildTabs() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(6), dp(5), dp(6), dp(5));
        bar.setBackgroundColor(0xFF0A1219);

        String[] labels = {"DOWNSTREAM", "UPSTREAM", "CURVES"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView tab = new TextView(this);
            tab.setText(labels[i]);
            tab.setTextSize(9.5f);
            tab.setTypeface(Typeface.DEFAULT_BOLD);
            tab.setLetterSpacing(0.04f);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(4), dp(10), dp(4), dp(10));
            tab.setTextColor(MUTED);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showTab(idx);
                }
            });
            tabs[i] = tab;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            bar.addView(tab, lp);
        }

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View line = new View(this);
        line.setBackgroundColor(0xFF1B2B3A);
        wrap.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(dp(1)))));
        wrap.addView(bar);
        return wrap;
    }

    private void showTab(int idx) {
        for (int i = 0; i < 3; i++) {
            pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            boolean on = i == idx;
            tabs[i].setTextColor(on ? ACCENT : MUTED);
            tabs[i].setBackground(on ? rounded(TAB_ON, 8, 0xFF2A4A5F) : null);
        }
    }

    private void setFullscreen(boolean on) {
        if (on) showTab(2);
        fullscreen = on;
        headerView.setVisibility(on ? View.GONE : View.VISIBLE);
        tabBar.setVisibility(on ? View.GONE : View.VISIBLE);
        solverCard.setVisibility(on ? View.GONE : View.VISIBLE);
        hintTv.setVisibility(on ? View.GONE : View.VISIBLE);

        expandBtn.setText(on ? "BACK" : "FULL VIEW");
        expandBtn.setTextColor(on ? ORANGE : ACCENT);
        if (on) {
            chartCard.setPadding(dp(4), dp(4), dp(4), dp(4));
        } else {
            chartCard.setPadding(dp(8), dp(7), dp(8), dp(6));
        }

        if (plot != null) plot.setLarge(on);
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            setFullscreen(false);
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------- relay page

    private View scrollPage(View inner) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        sv.addView(inner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    private class StagePanel {
        final LinearLayout root;
        final Spinner spinner;
        final EditText pickupEt, tmsEt, delayEt;
        final TextView preview, formula;
        final int color;
        Runnable onChange;

        StagePanel(String title, String sub, int color, String defTms, String defDelay,
                   boolean high) {
            this.color = color;

            root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);

            LinearLayout card = card();
            card.setPadding(dp(10), dp(8), dp(10), dp(9));

            LinearLayout tr = new LinearLayout(MainActivity.this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setGravity(Gravity.CENTER_VERTICAL);

            View dot = new View(MainActivity.this);
            dot.setBackground(circle(color));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(9), dp(9));
            dlp.rightMargin = dp(8);
            tr.addView(dot, dlp);

            LinearLayout tt = new LinearLayout(MainActivity.this);
            tt.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(MainActivity.this);
            t.setText(title);
            t.setTextColor(TEXT);
            t.setTextSize(11);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setLetterSpacing(0.05f);
            tt.addView(t);
            TextView st = new TextView(MainActivity.this);
            st.setText(sub);
            st.setTextColor(MUTED);
            st.setTextSize(8);
            tt.addView(st);
            tr.addView(tt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(tr);

            spinner = new Spinner(MainActivity.this);
            ArrayAdapter<String> ad = new ArrayAdapter<>(MainActivity.this,
                    android.R.layout.simple_spinner_item, labels());
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(ad);
            spinner.setBackgroundTintList(ColorStateList.valueOf(0xFF3E5A73));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
            slp.topMargin = dp(6);
            card.addView(spinner, slp);

            pickupEt = addRow(card, "Pickup current Is", "A", high ? "500" : "100");
            tmsEt = addRow(card, "Time multiplier TMS", "", defTms);
            delayEt = addRow(card, "Time delay", "s", defDelay);

            formula = new TextView(MainActivity.this);
            formula.setTextColor(MUTED);
            formula.setTextSize(8);
            formula.setTypeface(Typeface.MONOSPACE);
            formula.setPadding(0, dp(6), 0, 0);
            card.addView(formula);

            preview = new TextView(MainActivity.this);
            preview.setTextColor(color);
            preview.setTextSize(10);
            preview.setTypeface(Typeface.DEFAULT_BOLD);
            preview.setPadding(0, dp(4), 0, 0);
            card.addView(preview);

            root.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    refresh();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            TextWatcher tw = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    refresh();
                }
            };
            pickupEt.addTextChangedListener(tw);
            tmsEt.addTextChangedListener(tw);
            delayEt.addTextChangedListener(tw);
        }

        void refresh() {
            if (curve().isDefinite()) {
                formula.setText("t = delay   (any I > Is)");
            } else {
                formula.setText(curve().formula);
            }
            double p = pickup();
            double t = curve().tripTime(3 * p, p, tms(), delay());
            preview.setText("At 3\u00d7 Is = " + fmtA(3 * p) + "  \u2192  " + fmtT(t));
            if (onChange != null) onChange.run();
        }

        Curve curve() {
            int i = Math.max(0, Math.min(Curve.ALL.length - 1, spinner.getSelectedItemPosition()));
            return Curve.ALL[i];
        }

        double pickup() {
            return num(pickupEt);
        }

        double tms() {
            return num(tmsEt);
        }

        double delay() {
            return num(delayEt);
        }
    }

    /** One relay = two independent trip stages (low set + high set). */
    private class RelayPanel {
        final LinearLayout root;
        final StagePanel low, high;

        RelayPanel(String title, String sub, int color, String key) {
            boolean down = key.equals("d");

            root = new LinearLayout(MainActivity.this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(10), dp(7), dp(10), dp(10));

            LinearLayout tr = new LinearLayout(MainActivity.this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(MainActivity.this);
            dot.setBackground(circle(color));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(9), dp(9));
            dlp.rightMargin = dp(8);
            tr.addView(dot, dlp);
            LinearLayout tt = new LinearLayout(MainActivity.this);
            tt.setOrientation(LinearLayout.VERTICAL);
            TextView t = new TextView(MainActivity.this);
            t.setText(title);
            t.setTextColor(TEXT);
            t.setTextSize(13);
            t.setTypeface(Typeface.DEFAULT_BOLD);
            t.setLetterSpacing(0.06f);
            tt.addView(t);
            TextView st = new TextView(MainActivity.this);
            st.setText(sub);
            st.setTextColor(MUTED);
            st.setTextSize(9);
            tt.addView(st);
            tr.addView(tt, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(tr);

            low = new StagePanel("STAGE 1  \u00b7  LOW SET", "IDMT inverse \u2014 overload", color,
                    down ? "0.10" : "0.30", "0.00", false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(6);
            root.addView(low.root, lp);

            high = new StagePanel("STAGE 2  \u00b7  HIGH SET", "definite time \u2014 short circuit", color,
                    "1.00", down ? "0.10" : "0.40", true);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.topMargin = dp(7);
            root.addView(high.root, hp);

            TextView note = new TextView(MainActivity.this);
            note.setText("Each stage has its own curve. Inverse stages use TMS; delay adds to all stages.");
            note.setTextColor(0xFF607A91);
            note.setTextSize(8);
            note.setPadding(dp(2), dp(8), dp(2), 0);
            root.addView(note);
        }

        void setOnChange(Runnable r) {
            low.onChange = r;
            high.onChange = r;
        }

        void refresh() {
            low.refresh();
            high.refresh();
        }
    }

    private EditText addRow(LinearLayout card, String label, String unit, String def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(MUTED);
        l.setTextSize(11);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (!unit.isEmpty()) {
            TextView u = new TextView(this);
            u.setText(unit);
            u.setTextColor(0xFF607A91);
            u.setTextSize(9);
            u.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(16),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            ulp.rightMargin = dp(4);
            row.addView(u, ulp);
        }

        EditText e = new EditText(this);
        e.setText(def);
        e.setTextColor(TEXT);
        e.setTextSize(13);
        e.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackground(rounded(FIELD, 7, STROKE));
        e.setPadding(dp(8), 0, dp(8), 0);
        e.setSelectAllOnFocus(true);
        row.addView(e, new LinearLayout.LayoutParams(dp(92), dp(38)));

        card.addView(row);
        return e;
    }

    // -------------------------------------------------------------- plot page

    private View buildPlotPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(10), dp(8), dp(10), dp(10));

        chartCard = card();
        chartCard.setPadding(dp(8), dp(7), dp(8), dp(6));

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.addView(legendItem("D1", ACCENT));
        legend.addView(legendItem("D2", 0xFF7FE8F8));
        legend.addView(legendItem("U1", ORANGE));
        legend.addView(legendItem("U2", 0xFFFFD27A));
        expandBtn = new TextView(this);
        expandBtn.setText("FULL VIEW");
        expandBtn.setTextColor(ACCENT);
        expandBtn.setTextSize(9);
        expandBtn.setTypeface(Typeface.DEFAULT_BOLD);
        expandBtn.setGravity(Gravity.CENTER);
        expandBtn.setPadding(dp(6), dp(2), dp(6), dp(2));
        expandBtn.setBackground(rounded(0xFF122533, 5, 0xFF2A4A5F));
        expandBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setFullscreen(!fullscreen);
            }
        });
        legend.addView(expandBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        chartCard.addView(legend, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));

        plot = new CurvePlotView(this);
        plot.setOnPick(new CurvePlotView.OnPick() {
            @Override
            public void onPick(double amps) {
                setCurrentField(amps);
            }
        });
        chartCard.addView(plot, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        hintTv = new TextView(this);
        hintTv.setText("tap plot \u2192 pick current");
        hintTv.setTextColor(0xFF5C748A);
        hintTv.setTextSize(8);
        hintTv.setGravity(Gravity.CENTER);
        chartCard.addView(hintTv);

        page.addView(chartCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout sc = card();
        solverCard = sc;
        sc.setPadding(dp(10), dp(9), dp(10), dp(10));

        curEt = solverRow(sc, "Current", "A");
        timeEt = solverRow(sc, "Time", "s");

        resultTv = new TextView(this);
        resultTv.setTextColor(TEXT);
        resultTv.setTextSize(10);
        resultTv.setTypeface(Typeface.MONOSPACE);
        resultTv.setLineSpacing(dp(2), 1f);
        resultTv.setPadding(0, dp(7), 0, 0);
        sc.addView(resultTv);

        verdictTv = new TextView(this);
        verdictTv.setTextColor(MUTED);
        verdictTv.setTextSize(10);
        verdictTv.setTypeface(Typeface.DEFAULT_BOLD);
        verdictTv.setPadding(0, dp(3), 0, 0);
        sc.addView(verdictTv);

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(8);
        page.addView(sc, slp);

        curEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (guard) return;
                guard = true;
                timeEt.setText("");
                guard = false;
                fromCurrent = true;
                resolve();
            }
        });

        timeEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (guard) return;
                guard = true;
                curEt.setText("");
                guard = false;
                fromCurrent = false;
                resolve();
            }
        });

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return sv;
    }

    private EditText solverRow(LinearLayout parent, String label, String unit) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(MUTED);
        l.setTextSize(10);
        row.addView(l, new LinearLayout.LayoutParams(dp(52),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        EditText e = new EditText(this);
        e.setTextColor(TEXT);
        e.setTextSize(13);
        e.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        e.setSingleLine(true);
        e.setHint(label.toLowerCase(Locale.US) + " \u2192 other");
        e.setHintTextColor(0xFF4E677C);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setBackground(rounded(FIELD, 7, STROKE));
        e.setPadding(dp(8), 0, dp(8), 0);
        row.addView(e, new LinearLayout.LayoutParams(0, dp(40), 1f));

        TextView u = new TextView(this);
        u.setText(unit);
        u.setTextColor(0xFF607A91);
        u.setTextSize(9);
        u.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ulp = new LinearLayout.LayoutParams(dp(18),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ulp.leftMargin = dp(4);
        row.addView(u, ulp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        parent.addView(row, lp);
        return e;
    }

    private View legendItem(String name, int color) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);

        View swatch = new View(this);
        swatch.setBackground(rounded(color, 2, 0));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(11), dp(3));
        slp.rightMargin = dp(4);
        item.addView(swatch, slp);

        TextView t = new TextView(this);
        t.setText(name);
        t.setTextColor(0xFFB7C8D8);
        t.setTextSize(8.5f);
        item.addView(t);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(7);
        item.setLayoutParams(lp);
        return item;
    }

    // --------------------------------------------------------------- solving

    private void resolve() {
        if (plot == null || downPanel == null) return;
        syncPlot();

        StagePanel[] st = {downPanel.low, downPanel.high, upPanel.low, upPanel.high};
        String[] nm = {"D1", "D2", "U1", "U2"};

        if (fromCurrent) {
            double i = num(curEt);
            if (!(i > 0)) {
                idle();
                return;
            }
            double[] tv = new double[4];
            StringBuilder sb = new StringBuilder("I = ").append(fmtA(i)).append('\n');
            for (int k = 0; k < 4; k++) {
                tv[k] = st[k].curve().tripTime(i, st[k].pickup(), st[k].tms(), st[k].delay());
                sb.append(nm[k]).append(' ').append(fmtT(tv[k]));
                sb.append(k == 1 ? "\n" : "    ");
            }

            plot.setMarkerCurrent(i);
            resultTv.setText(sb.toString());

            boolean dAny = finite(tv[0]) || finite(tv[1]);
            boolean uAny = finite(tv[2]) || finite(tv[3]);
            if (!dAny && !uAny) {
                verdict("Current below all pickups", BAD);
            } else if (!dAny) {
                verdict("Below downstream pickups \u2014 only upstream trips", BAD);
            } else if (!uAny) {
                verdict("Only downstream trips", MUTED);
            } else {
                int di = finite(tv[0]) && (!finite(tv[1]) || tv[0] <= tv[1]) ? 0 : 1;
                int ui = finite(tv[2]) && (!finite(tv[3]) || tv[2] <= tv[3]) ? 2 : 3;
                double m = tv[ui] - tv[di];
                String pair = nm[di] + "/" + nm[ui];
                if (m >= 0.3) {
                    verdict(pair + "  \u00b7  margin " + fmtT(m), GOOD);
                } else if (m > 0.0005) {
                    verdict(pair + "  \u00b7  margin " + fmtT(m) + " (tight)", ORANGE);
                } else if (m >= -0.0005) {
                    verdict(pair + "  \u00b7  no margin", BAD);
                } else {
                    verdict(nm[ui] + " first \u2014 miscoordinated", BAD);
                }
            }
        } else {
            double t = num(timeEt);
            if (!(t > 0)) {
                idle();
                return;
            }
            StringBuilder sb = new StringBuilder("t = ").append(fmtT(t)).append('\n');
            StringBuilder vb = new StringBuilder("At this time: ");
            for (int k = 0; k < 4; k++) {
                Curve c = st[k].curve();
                double pk = st[k].pickup();
                double iv = c.currentAtTime(t, pk, st[k].tms(), st[k].delay());
                String s = c.isDefinite()
                        ? (t >= st[k].delay() ? "\u2265" + fmtA(pk) : "\u2014")
                        : fmtA(iv);
                sb.append(nm[k]).append(' ').append(s);
                sb.append(k == 1 ? "\n" : "    ");

                String mult = c.isDefinite() ? "\u2265Is"
                        : (finite(iv) && pk > 0
                        ? String.format(Locale.US, "%.1f", iv / pk) + "\u00d7Is" : "\u2014");
                vb.append(nm[k]).append(' ').append(mult).append(k == 3 ? "" : "  ");
            }

            plot.setMarkerTime(t);
            resultTv.setText(sb.toString());
            verdict(vb.toString(), MUTED);
        }
    }

    private static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private void idle() {
        resultTv.setText("Type a current  \u2192  get times\nType a time  \u2192  get currents");
        verdictTv.setText("Tap the plot to pick a current");
        verdictTv.setTextColor(0xFF5C748A);
        plot.clearMarker();
    }

    private void verdict(String s, int color) {
        verdictTv.setText(s);
        verdictTv.setTextColor(color);
    }

    private void setCurrentField(double amps) {
        guard = true;
        curEt.setText(trimNum(amps));
        timeEt.setText("");
        guard = false;
        fromCurrent = true;
        resolve();
    }

    private void syncPlot() {
        if (plot == null || downPanel == null) return;
        StagePanel[] st = {downPanel.low, downPanel.high, upPanel.low, upPanel.high};
        int[] colors = {ACCENT, 0xFF7FE8F8, ORANGE, 0xFFFFD27A};
        CurvePlotView.Stage[] stages = new CurvePlotView.Stage[4];
        for (int k = 0; k < 4; k++) {
            stages[k] = new CurvePlotView.Stage(st[k].curve(), st[k].pickup(), st[k].tms(),
                    st[k].delay(), colors[k], k == 1 || k == 3);
        }
        plot.setStages(stages);
    }

    // ------------------------------------------------------------- persistence

    private void persist() {
        SharedPreferences.Editor e = prefs.edit();
        writeStage(e, "d", downPanel.low);
        writeStage(e, "d2", downPanel.high);
        writeStage(e, "u", upPanel.low);
        writeStage(e, "u2", upPanel.high);
        e.apply();
    }

    private void writeStage(SharedPreferences.Editor e, String k, StagePanel p) {
        e.putInt(k + "_curve", p.spinner.getSelectedItemPosition());
        e.putString(k + "_pickup", p.pickupEt.getText().toString());
        e.putString(k + "_tms", p.tmsEt.getText().toString());
        e.putString(k + "_delay", p.delayEt.getText().toString());
    }

    private void readStage(String k, StagePanel p, int curve, String pickup,
                           String tms, String delay) {
        int idx = prefs.getInt(k + "_curve", curve);
        p.spinner.setSelection(Math.max(0, Math.min(Curve.ALL.length - 1, idx)));
        p.pickupEt.setText(prefs.getString(k + "_pickup", pickup));
        p.tmsEt.setText(prefs.getString(k + "_tms", tms));
        p.delayEt.setText(prefs.getString(k + "_delay", delay));
    }

    private void load() {
        // Stage 1 reuses the original keys so existing settings are kept.
        readStage("d", downPanel.low, 0, "100", "0.10", "0.00");
        readStage("d2", downPanel.high, 7, "500", "1.00", "0.10");
        readStage("u", upPanel.low, 0, "100", "0.30", "0.30");
        readStage("u2", upPanel.high, 7, "500", "1.00", "0.40");

        loaded = true;
        downPanel.refresh();
        upPanel.refresh();
        syncPlot();
        idle();
    }

    // ------------------------------------------------------------------ utils

    private String[] labels() {
        String[] out = new String[Curve.ALL.length];
        for (int i = 0; i < Curve.ALL.length; i++) out[i] = Curve.ALL[i].label;
        return out;
    }

    private static double num(EditText e) {
        if (e == null) return 0;
        String s = e.getText().toString().trim().replace(',', '.');
        if (s.isEmpty()) return 0;
        try {
            return Double.parseDouble(s);
        } catch (Exception ex) {
            return 0;
        }
    }

    private static String trimNum(double v) {
        if (v >= 1000) return String.format(Locale.US, "%.0f", v);
        if (v >= 100) return String.format(Locale.US, "%.0f", v);
        if (v >= 10) return String.format(Locale.US, "%.1f", v);
        return String.format(Locale.US, "%.2f", v);
    }

    static String fmtA(double a) {
        if (Double.isNaN(a) || Double.isInfinite(a)) return "\u2014";
        if (a >= 1000) return String.format(Locale.US, "%.2f kA", a / 1000.0);
        if (a >= 100) return Math.round(a) + " A";
        if (a >= 10) return String.format(Locale.US, "%.1f A", a);
        return String.format(Locale.US, "%.2f A", a);
    }

    static String fmtT(double t) {
        if (Double.isNaN(t) || Double.isInfinite(t)) return "\u2014";
        if (t >= 100) return Math.round(t) + " s";
        if (t >= 10) return String.format(Locale.US, "%.1f s", t);
        if (t >= 1) return String.format(Locale.US, "%.2f s", t);
        return String.format(Locale.US, "%.3f s", t);
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(rounded(CARD, 12, STROKE));
        return l;
    }

    private GradientDrawable rounded(int fill, float radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, Math.round(dp(1))), stroke);
        return g;
    }

    private GradientDrawable circle(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
