package com.minimal.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Todas as preferências em um lugar só, com nomes e valores válidoscentralizados.
 * Nada mais toca em {@link SharedPreferences} diretamente.
 */
public final class Prefs {

    public static final String NAME = "minimal_launcher";

    // Valores válidos.
    public static final String THEME_SYSTEM = "system";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    public static final String ALIGN_RIGHT = "right";
    public static final String ALIGN_LEFT = "left";

    public static final String VPOS_CENTER = "center";
    public static final String VPOS_BOTTOM = "bottom";

    // Chaves.
    private static final String KEY_THEME = "theme";
    private static final String KEY_ALIGNMENT = "alignment";
    private static final String KEY_VERTICAL_POSITION = "vertical_position";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_HIDE_SYSTEM_BARS = "hide_system_bars";
    private static final String KEY_NOTIFICATION_DOT = "notification_dot";
    private static final String KEY_DOUBLE_TAP_LOCK = "double_tap_lock";

    /** Escalas de fonte oferecidas nas configurações, do menor ao maior. */
    public static final float[] FONT_SCALES = {0.85f, 1.0f, 1.2f, 1.45f};

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public String getTheme() {
        return prefs.getString(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(String value) {
        prefs.edit().putString(KEY_THEME, value).apply();
    }

    public String getAlignment() {
        return prefs.getString(KEY_ALIGNMENT, ALIGN_RIGHT);
    }

    public void setAlignment(String value) {
        prefs.edit().putString(KEY_ALIGNMENT, value).apply();
    }

    public String getVerticalPosition() {
        return prefs.getString(KEY_VERTICAL_POSITION, VPOS_CENTER);
    }

    public void setVerticalPosition(String value) {
        prefs.edit().putString(KEY_VERTICAL_POSITION, value).apply();
    }

    public float getFontScale() {
        return prefs.getFloat(KEY_FONT_SCALE, 1.0f);
    }

    public void setFontScale(float value) {
        prefs.edit().putFloat(KEY_FONT_SCALE, value).apply();
    }

    public boolean getHideSystemBars() {
        return prefs.getBoolean(KEY_HIDE_SYSTEM_BARS, false);
    }

    public void setHideSystemBars(boolean value) {
        prefs.edit().putBoolean(KEY_HIDE_SYSTEM_BARS, value).apply();
    }

    public boolean getNotificationDot() {
        return prefs.getBoolean(KEY_NOTIFICATION_DOT, true);
    }

    public void setNotificationDot(boolean value) {
        prefs.edit().putBoolean(KEY_NOTIFICATION_DOT, value).apply();
    }

    public boolean getDoubleTapLock() {
        return prefs.getBoolean(KEY_DOUBLE_TAP_LOCK, false);
    }

    public void setDoubleTapLock(boolean value) {
        prefs.edit().putBoolean(KEY_DOUBLE_TAP_LOCK, value).apply();
    }
}