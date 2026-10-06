package com.minimal.launcher;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
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
 * Aplica o tema em um único lugar: cor de fundo e cor de texto sólidas
 * (nunca o papel de parede), edge-to-edge, barras do sistema e insets.
 *
 * <p>Os combos de cores (colors.md e searchScreen.md) são invertidos
 * automaticamente no modo noturno: fundo claro vira escuro e o texto
 * vira claro. Isso permite usar um único visual "confortável" em dia e noite.
 */
public final class ThemeHelper {

    private ThemeHelper() {
    }

    /** Deve ser chamado antes de {@code super.onCreate}. */
    public static void applyNightMode(@NonNull AppCompatActivity activity) {
        // Segue o sistema: as cores sao resolvidas por isDark(), que inverte o
        // combo escolhido. Fixo em dark/light cortaria a metade da paleta.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    /**
     * O tema efetivo é escuro?
     *
     * <p>Vem do modo noturno do sistema, e não da cor do combo: todos os combos
     * são claros por definição, e é a inversão deles que produz a versão
     * noturna. Se isto fosse derivado do próprio combo, inverter seria circular.
     */
    public static boolean isDark(@NonNull Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Brightness luminosa (0.299R + 0.587G + 0.114B). Usada em testes. */
    public static int getBrightness(int rgb) {
        int r = Color.red(rgb);
        int g = Color.green(rgb);
        int b = Color.blue(rgb);
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    public static int getHomeBackgroundColor(@NonNull Context context) {
        return homeOf(context)[0];
    }

    public static int getHomeForegroundColor(@NonNull Context context) {
        return homeOf(context)[1];
    }

    public static int getSearchBackgroundColor(@NonNull Context context) {
        return searchOf(context)[0];
    }

    public static int getSearchForegroundColor(@NonNull Context context) {
        return searchOf(context)[1];
    }

    public static int getSearchBarBorderColor(@NonNull Context context) {
        return searchOf(context)[3];
    }

    /**
     * Placeholder da busca.
     *
     * <p>Mantém o tom recuado que um placeholder deve ter, mas nunca abaixo de
     * 4.5:1: quando o fundo é escuro, a cor é puxada em direção ao texto real
     * daquele fundo. Numa paleta clara de dia, o valor do arquivo é usado como
     * veio.
     */
    public static int getSearchPlaceholderColor(@NonNull Context context) {
        int[] palette = searchOf(context);
        if (getBrightness(palette[0]) >= 128) {
            return palette[2];
        }
        return blend(palette[2], palette[1], 0.40f);
    }

    /**
     * Par fundo/texto da tela inicial, já na variante que vale agora.
     *
     * <p>Combos claros são invertidos à noite. Combos escuros por natureza — o
     * OLED — trocam de variante: de dia usam a versão clara declarada, à noite
     * a escura. Sem isso, um painel OLED ficaria preto sob a luz do dia.
     */
    @NonNull
    private static int[] homeOf(@NonNull Context context) {
        int index = colorIndex(context);
        if (!isDark(context) && ColorCombo.isDarkNative(index)) {
            return ColorCombo.getCombosDay()[index];
        }
        if (isDark(context) && !ColorCombo.isDarkNative(index)) {
            return new int[]{
                    invert(ColorCombo.getCombos()[index][0]),
                    invert(ColorCombo.getCombos()[index][1]),
            };
        }
        return ColorCombo.getCombos()[index];
    }

    /** Mesmo raciocínio de {@link #homeOf}, para a paleta da busca. */
    @NonNull
    private static int[] searchOf(@NonNull Context context) {
        int index = colorIndex(context);
        if (!isDark(context) && ColorCombo.isDarkNative(index)) {
            return ColorCombo.getSearchBarsDay()[index];
        }
        if (isDark(context) && !ColorCombo.isDarkNative(index)) {
            int[] base = ColorCombo.getSearchBars()[index];
            return new int[]{
                    invert(base[0]), invert(base[1]), invert(base[2]), invert(base[3]),
            };
        }
        return ColorCombo.getSearchBars()[index];
    }

    /**
     * A tela que o usuário está vendo é escura?
     *
     * <p>Duas coisas importam, e basta uma delas: o modo noturno do sistema, ou
     * um combo que é escuro por natureza. Os ícones das barras do sistema
     * dependem disto — claro sobre preto some.
     */
    public static boolean isDarkSurface(@NonNull Context context) {
        return ColorCombo.isDarkNative(colorIndex(context)) || isDark(context);
    }

    /** Índice do combo ativo, sempre dentro dos limites conhecidos. */
    private static int colorIndex(@NonNull Context context) {
        int index = new Prefs(context).getColorCombo();
        return Math.max(0, Math.min(ColorCombo.COUNT - 1, index));
    }

    private static int invert(int rgb) {
        return 0xFF000000 | (~rgb & 0x00FFFFFF);
    }

    /** Mistura linear de {@code from} em direção a {@code to}. */
    private static int blend(int from, int to, float amount) {
        int a = from & 0x00FFFFFF;
        int b = to & 0x00FFFFFF;
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * amount);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * amount);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /**
     * Pinta a tela toda: fundo + cor de texto de todos os TextViews da árvore.
     */
    public static void paint(@NonNull Context context, @NonNull View root, boolean isSearch) {
        if (isSearch) {
            root.setBackgroundColor(getSearchBackgroundColor(context));
            paintSearchTree(root, getSearchForegroundColor(context));
        } else {
            root.setBackgroundColor(getHomeBackgroundColor(context));
            paintTree(root, getHomeForegroundColor(context));
        }
    }

    private static void paintTree(@NonNull View view, int foreground) {
        if (view instanceof TextView) {
            TextView text = (TextView) view;
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
     * Mesma coisa, mas ignora a barra de busca (ela mantém suas próprias cores).
     */
    private static void paintSearchTree(@NonNull View view, int foreground) {
        if (view instanceof EditText) {
            // Barra de pesquisa: as cores são definidas explicitamente na activity.
            return;
        }
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(foreground);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                paintSearchTree(group.getChildAt(index), foreground);
            }
        }
    }

    /**
     * Retângulo arredondado com preenchimento e borda.
     * O inset faz a borda ficar visível na espessura correta.
     */
    public static Drawable createRoundedRectDrawable(@NonNull Context context,
                                                     int bgColor, int borderColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(context, 12));

        GradientDrawable border = new GradientDrawable();
        border.setColor(borderColor);
        border.setCornerRadius(dp(context, 12));
        border.setStroke(dp(context, 2), borderColor);

        Drawable[] layers = {border, bg};
        LayerDrawable result = new LayerDrawable(layers);
        int inset = dp(context, 2);
        result.setLayerInset(0, inset, inset, inset, inset);
        return result;
    }

    /**
     * Edge-to-edge de verdade: a activity desenha sob as barras, que ficam
     * transparentes. O respiro vem dos insets, com folga extra em dp.
     *
     * <p>O cutout entra junto das barras para o relógio nunca ficar sob a
     * câmera do aparelho.
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

    /**
     * Modo imersivo: as barras ficam escondidas e só aparecem ao puxar de cima
     * ou de baixo (BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE). Com isso, as
     * notificações continuam aparecendo normalmente quando o usuário puxa,
     * sem precisar de nenhum acesso especial do launcher.
     */
    public static void applySystemBars(@NonNull AppCompatActivity activity) {
        Window window = activity.getWindow();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        boolean dark = isDarkSurface(activity);

        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

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
