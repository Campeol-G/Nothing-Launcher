package com.minimal.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Aplica o tema em um unico lugar: fundo solido (nunca o wallpaper), cor do
 * texto, edge-to-edge, barras do sistema e insets.
 *
 * <p>"sistema / preto / branco" vira {@code AppCompatDelegate}; {@link #isDark}
 * le o modo ja resolvido pela plataforma, entao todo o resto segue sem duplicar
 * regra.
 */
public final class ThemeHelper {

    private ThemeHelper() {
    }

    /** Deve ser chamado antes de {@code super.onCreate}. */
    public static void applyNightMode(@NonNull AppCompatActivity activity) {
        String theme = new Prefs(activity).getTheme();
        if (Prefs.THEME_DARK.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else if (Prefs.THEME_LIGHT.equals(theme)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    /** O tema efetivo e escuro? Base de tudo: cores e icones das barras. */
    public static boolean isDark(@NonNull Context context) {
        int mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int backgroundColor(@NonNull Context context) {
        return isDark(context) ? Color.BLACK : Color.WHITE;
    }

    public static int foregroundColor(@NonNull Context context) {
        return isDark(context) ? Color.WHITE : Color.BLACK;
    }

    /**
     * Edge-to-edge de verdade: a activity desenha sob as barras, que ficam
     * transparentes. O respiro vem dos insets, com folga extra em dp.
     *
     * <p>O cutout entra junto das barras para o relogio nunca ficar sob a
     *camera do aparelho.
     */
    public static void applyInsets(@NonNull AppCompatActivity activity,
                                   @NonNull View root,
                                   int sideDp,
                                   int topDp,
                                   int bottomDp) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        final int side = dp(root, sideDp);
        final int top = dp(root, topDp);
        final int bottom = dp(root, bottomDp);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(
                    bars.left + side,
                    bars.top + top,
                    bars.right + side,
                    bars.bottom + bottom);
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    /** Fundo solido e cores de texto de toda a arvore da tela, de uma vez. */
    public static void paint(@NonNull View root) {
        int background = backgroundColor(root.getContext());
        int foreground = foregroundColor(root.getContext());
        root.setBackgroundColor(background);
        paintTree(root, foreground);
    }

    private static void paintTree(@NonNull View view, int foreground) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            // Hint e cursor nao seguem textColor; precisam do mesmo cuidado.
            if (text.getHint() != null) {
                text.setHintTextColor(foreground);
            }
            text.setTextColor(foreground);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                paintTree(group.getChildAt(index), foreground);
            }
        }
    }

    /**
     * Modo imersivo opcional. Mesmo escondido, o usuario ainda puxa a barra
     * para ve-la (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE).
     */
    public static void applySystemBars(@NonNull AppCompatActivity activity) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        boolean dark = isDark(activity);

        if (new Prefs(activity).getHideSystemBars()) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars());
        }

        controller.setAppearanceLightStatusBars(!dark);
        controller.setAppearanceLightNavigationBars(!dark);
    }

    public static int dp(@NonNull View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    public static int dp(@NonNull Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}