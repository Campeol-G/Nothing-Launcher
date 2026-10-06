package com.minimal.launcher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Todas as preferências em um lugar só, com nomes e valores válidos
 * centralizados. Nada mais toca em {@link SharedPreferences} diretamente.
 */
public final class Prefs {

    public static final String NAME = "minimal_launcher";

    // Valores válidos.
    public static final String ALIGN_RIGHT = "right";   // dominante: direita
    public static final String ALIGN_LEFT = "left";     // dominante: esquerda

    public static final String VPOS_CENTER = "center";
    public static final String VPOS_BOTTOM = "bottom";

    // Chaves.
    private static final String KEY_ALIGNMENT = "dominant_hand";
    private static final String KEY_VERTICAL_POSITION = "vertical_position";
    private static final String KEY_FONT_SCALE = "font_scale";
    private static final String KEY_COLOR_COMBO = "color_combo";
    private static final String KEY_FONT_COMBO = "font_combo";

    /** Escalas de fonte oferecidas nas configurações, do menor ao maior. */
    public static final float[] FONT_SCALES = {0.85f, 1.0f, 1.2f, 1.45f};

    private final SharedPreferences prefs;

    public Prefs(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------- dominante da mão (esq/dir)

    public String getDominantHand() {
        return prefs.getString(KEY_ALIGNMENT, ALIGN_RIGHT);
    }

    public void setDominantHand(String value) {
        prefs.edit().putString(KEY_ALIGNMENT, value).apply();
    }

    // -------------------------------------------------- posição vertical

    public String getVerticalPosition() {
        return prefs.getString(KEY_VERTICAL_POSITION, VPOS_CENTER);
    }

    public void setVerticalPosition(String value) {
        prefs.edit().putString(KEY_VERTICAL_POSITION, value).apply();
    }

    // -------------------------------------------------- escala da fonte

    public float getFontScale() {
        return prefs.getFloat(KEY_FONT_SCALE, 1.0f);
    }

    public void setFontScale(float value) {
        prefs.edit().putFloat(KEY_FONT_SCALE, value).apply();
    }

    // -------------------------------------------------- cor e fontes (teste)

    public int getColorCombo() {
        return prefs.getInt(KEY_COLOR_COMBO, ColorCombo.CURRENT_COLOR_COMBO_INDEX);
    }

    public void setColorCombo(int value) {
        prefs.edit().putInt(KEY_COLOR_COMBO, value).apply();
    }

    public int getFontCombo() {
        return prefs.getInt(KEY_FONT_COMBO, FontCombo.CURRENT_FONT_COMBO_INDEX);
    }

    public void setFontCombo(int value) {
        prefs.edit().putInt(KEY_FONT_COMBO, value).apply();
    }
}
