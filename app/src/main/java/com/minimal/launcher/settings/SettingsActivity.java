package com.minimal.launcher.settings;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.minimal.launcher.EdgeGestureLayout;
import com.minimal.launcher.Prefs;
import com.minimal.launcher.R;
import com.minimal.launcher.ThemeHelper;
import com.minimal.launcher.services.LauncherLockService;
import com.minimal.launcher.services.LauncherNotificationListener;

/**
 * Configuracoes dentro do proprio launcher, no mesmo estilo: so texto, sem
 * icones, sem barra de acao.
 *
 * <p>Toda opcao que muda a tela principal chama {@code recreate()}, entao nao
 * existe nenhum mecanismo de "a tela de baixo se atualize".
 */
public final class SettingsActivity extends AppCompatActivity {

    private Prefs prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyNightMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new Prefs(this);

        EdgeGestureLayout root = findViewById(R.id.root);
        ThemeHelper.applyInsets(this, root, 26, 30, 18);
        ThemeHelper.applySystemBars(this);

        buildRows(findViewById(R.id.settings_rows));
        // Depois das linhas: as que foram criadas em código também são pintadas.
        ThemeHelper.paint(root);
    }

    private void buildRows(@NonNull LinearLayout rows) {
        rows.addView(option(getString(R.string.pref_theme_title), null, themeLabel(),
                () -> choose(getString(R.string.pref_theme_title),
                        new String[]{getString(R.string.theme_system),
                                getString(R.string.theme_dark),
                                getString(R.string.theme_light)},
                        new String[]{Prefs.THEME_SYSTEM, Prefs.THEME_DARK, Prefs.THEME_LIGHT},
                        prefs.getTheme(),
                        value -> {
                            prefs.setTheme(value);
                            recreate();
                        })));

        rows.addView(option(getString(R.string.pref_alignment_title), null, alignmentLabel(),
                () -> choose(getString(R.string.pref_alignment_title),
                        new String[]{getString(R.string.align_right),
                                getString(R.string.align_left)},
                        new String[]{Prefs.ALIGN_RIGHT, Prefs.ALIGN_LEFT},
                        prefs.getAlignment(),
                        value -> {
                            prefs.setAlignment(value);
                            recreate();
                        })));

        rows.addView(option(getString(R.string.pref_vertical_position_title), null,
                verticalPositionLabel(),
                () -> choose(getString(R.string.pref_vertical_position_title),
                        new String[]{getString(R.string.vpos_center),
                                getString(R.string.vpos_bottom)},
                        new String[]{Prefs.VPOS_CENTER, Prefs.VPOS_BOTTOM},
                        prefs.getVerticalPosition(),
                        value -> {
                            prefs.setVerticalPosition(value);
                            recreate();
                        })));

        rows.addView(option(getString(R.string.pref_font_size_title), null, fontScaleLabel(),
                () -> choose(getString(R.string.pref_font_size_title),
                        new String[]{getString(R.string.font_size_small),
                                getString(R.string.font_size_medium),
                                getString(R.string.font_size_large),
                                getString(R.string.font_size_huge)},
                        fontScaleKeys(),
                        String.valueOf(prefs.getFontScale()),
                        value -> {
                            prefs.setFontScale(Float.parseFloat(value));
                            recreate();
                        })));

        rows.addView(switchOption(getString(R.string.pref_hide_bars_title),
                getString(R.string.pref_hide_bars_summary),
                prefs.getHideSystemBars(),
                value -> {
                    prefs.setHideSystemBars(value);
                    ThemeHelper.applySystemBars(this);
                }));

        rows.addView(switchOption(getString(R.string.pref_notification_dot_title),
                getString(R.string.pref_notification_dot_summary),
                prefs.getNotificationDot(),
                value -> prefs.setNotificationDot(value)));

        if (prefs.getNotificationDot() && !LauncherNotificationListener.isConnected()) {
            rows.addView(option(getString(R.string.notification_access_title),
                    getString(R.string.notification_access_summary), null,
                    () -> startActivity(
                            new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        }

        rows.addView(switchOption(getString(R.string.pref_double_tap_lock_title),
                getString(R.string.pref_double_tap_lock_summary),
                prefs.getDoubleTapLock(),
                value -> {
                    prefs.setDoubleTapLock(value);
                    if (value && !LauncherLockService.isRunning()) {
                        explainLockService();
                    }
                }));

        rows.addView(option(getString(R.string.accessibility_service_title), null,
                LauncherLockService.isRunning()
                        ? getString(R.string.accessibility_service_active) : null,
                () -> startActivity(LauncherLockService.settingsIntent())));

        rows.addView(option(getString(R.string.pref_manage_favorites_title), null, null,
                () -> startActivity(new Intent(this, ManageFavoritesActivity.class))));
    }

    // ------------------------------------------------------------- construção

    /**
     * Linha de opcao: titulo, resumo opcional e valor atual opcional, tudo
     * empurrado para a esquerda para manter a coluna legível.
     */
    private View option(String title, @Nullable String summary, @Nullable String valueLabel,
                        @NonNull Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.START);
        row.setBackground(ripple());
        row.setClickable(true);
        row.setFocusable(true);

        row.addView(label(title, R.style.RowTitle));

        if (summary != null) {
            row.addView(label(summary, R.style.RowSummary));
        }

        if (!TextUtils.isEmpty(valueLabel)) {
            TextView value = label(valueLabel, R.style.RowSummary);
            value.setGravity(Gravity.END);
            row.addView(value, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private View switchOption(String title, String summary, boolean checked,
                              @NonNull OnToggle onToggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // Sem padding aqui: RowTitle e RowSummary ja trazem o respiro vertical.
        row.setBackground(ripple());
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(label(title, R.style.RowTitle));
        labels.addView(label(summary, R.style.RowSummary));

        SwitchCompat toggle = new SwitchCompat(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> onToggle.onChanged(isChecked));

        row.addView(labels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle);

        // Tocar na linha alterna: uma area grande, sem precisar acertar o switch.
        row.setOnClickListener(v -> toggle.setChecked(!toggle.isChecked()));
        return row;
    }

    private TextView label(String text, int styleRes) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextAppearance(this, styleRes);
        return view;
    }

    /** selectableItemBackground resolvido pelo tema - nao existe como recurso direto. */
    private android.graphics.drawable.Drawable ripple() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId != 0
                ? androidx.core.content.ContextCompat.getDrawable(this, value.resourceId)
                : null;
    }

    // ---------------------------------------------------------------- diálogo

    private void choose(String title, String[] labels, String[] keys, String selectedKey,
                        @NonNull OnPicked onPicked) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, indexOf(keys, selectedKey),
                        (dialog, which) -> {
                            onPicked.onPicked(keys[which]);
                            dialog.dismiss();
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void explainLockService() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.accessibility_service_summary_title)
                .setMessage(R.string.accessibility_service_summary_message)
                .setPositiveButton(R.string.accessibility_service_summary_positive,
                        (dialog, which) -> startActivity(LauncherLockService.settingsIntent()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private interface OnPicked {
        void onPicked(String value);
    }

    private interface OnToggle {
        void onChanged(boolean checked);
    }

    // ---------------------------------------------------------------- rótulos

    private String themeLabel() {
        String theme = prefs.getTheme();
        if (Prefs.THEME_DARK.equals(theme)) {
            return getString(R.string.theme_dark);
        }
        if (Prefs.THEME_LIGHT.equals(theme)) {
            return getString(R.string.theme_light);
        }
        return getString(R.string.theme_system);
    }

    private String alignmentLabel() {
        return Prefs.ALIGN_LEFT.equals(prefs.getAlignment())
                ? getString(R.string.align_left)
                : getString(R.string.align_right);
    }

    private String verticalPositionLabel() {
        return Prefs.VPOS_BOTTOM.equals(prefs.getVerticalPosition())
                ? getString(R.string.vpos_bottom)
                : getString(R.string.vpos_center);
    }

    private String fontScaleLabel() {
        return fontScaleLabelFor(prefs.getFontScale());
    }

    private String fontScaleLabelFor(float scale) {
        int best = 1;
        float bestDistance = Math.abs(Prefs.FONT_SCALES[best] - scale);
        for (int index = 0; index < Prefs.FONT_SCALES.length; index++) {
            float distance = Math.abs(Prefs.FONT_SCALES[index] - scale);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        switch (best) {
            case 0:
                return getString(R.string.font_size_small);
            case 2:
                return getString(R.string.font_size_large);
            case 3:
                return getString(R.string.font_size_huge);
            case 1:
            default:
                return getString(R.string.font_size_medium);
        }
    }

    private static String[] fontScaleKeys() {
        String[] keys = new String[Prefs.FONT_SCALES.length];
        for (int index = 0; index < keys.length; index++) {
            keys[index] = String.valueOf(Prefs.FONT_SCALES[index]);
        }
        return keys;
    }

    private static int indexOf(String[] keys, String value) {
        for (int index = 0; index < keys.length; index++) {
            if (keys[index].equals(value)) {
                return index;
            }
        }
        return -1;
    }
}