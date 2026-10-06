package com.minimal.launcher;

import android.content.Context;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

/**
 * Combinacoes de fontes, conforme fonts.md.
 *
 * <p>Cada combinacao define as fontes de interface, relogio e titulos. Os
 * arquivos {@code .ttf} vivem em {@code res/font}. Se o recurso nao carregar,
 * o combo cai para a fonte padrao do sistema em vez de quebrar a tela.
 *
 * <p><b>Configuracao manual para testes:</b> mude
 * {@link #CURRENT_FONT_COMBO_INDEX}. Pelo app, em Configuracoes &gt; Fontes.
 */
public final class FontCombo {

    /** Indice da combinacao ativa: 0 = Noto Sans + Roboto Flex (padrao). */
    public static final int CURRENT_FONT_COMBO_INDEX = 0;

    private static final Typeface[] INTERFACE_FONTS = new Typeface[8];
    private static final Typeface[] CLOCK_FONTS = new Typeface[8];
    private static final Typeface[] TITLE_FONTS = new Typeface[8];
    private static final Typeface[] CLOCK_FONTS_CONDENSED = new Typeface[1];

    private FontCombo() {
    }

    /** Fonte de interface: os nomes dos apps nas listas. */
    @NonNull
    public static Typeface getInterfaceFont(@NonNull Context context) {
        return load(context, INTERFACE_FONTS,
                R.font.noto_sans_regular,   // 1. Noto Sans
                R.font.nunito_regular,      // 2. Nunito
                R.font.noto_sans_regular,   // 3. Noto Sans
                R.font.atkinson_regular,    // 4. Atkinson Hyperlegible
                R.font.roboto_regular,      // 5. Roboto
                R.font.ibm_plex_sans_regular, // 6. IBM Plex Sans
                R.font.source_sans_3_regular, // 7. Source Sans 3
                R.font.nunito_regular);     // 8. Nunito
    }

    /** Fonte do relogio: numeros com desenho proprio, diferente da interface. */
    @NonNull
    public static Typeface getClockFont(@NonNull Context context) {
        return load(context, CLOCK_FONTS,
                R.font.roboto_flex_regular, // 1. Roboto Flex
                R.font.roboto_flex_regular, // 2. Roboto Flex
                R.font.noto_sans_regular,   // 3. Noto Sans
                R.font.roboto_flex_regular, // 4. Roboto Flex
                R.font.roboto_flex_regular, // 5. Roboto Flex
                R.font.roboto_flex_regular, // 6. Roboto Flex
                R.font.roboto_flex_regular, // 7. Roboto Flex
                R.font.nunito_regular);     // 8. Nunito
    }

    /** Fonte de titulos: Configuracoes e outros destaques. */
    @NonNull
    public static Typeface getTitleFont(@NonNull Context context) {
        return load(context, TITLE_FONTS,
                R.font.noto_sans_medium,        // 1. Noto Sans Medium
                R.font.nunito_semibold,         // 2. Nunito SemiBold
                R.font.lora_medium,             // 3. Lora Medium
                R.font.atkinson_bold,           // 4. Atkinson Bold
                R.font.roboto_medium,           // 5. Roboto Medium
                R.font.ibm_plex_sans_semibold,  // 6. IBM Plex Sans SemiBold
                R.font.source_sans_3_semibold,  // 7. Source Sans 3 Semibold
                R.font.lora_medium);            // 8. Lora Medium
    }

    /**
     * Fonte condensada do relógio, para o formato de linha única.
     *
     * <p>Um horário de cinco caracteres esbarra na largura da tela antes da
     * altura: "18:02" ocupa cerca de 2.55 larguras de glifo na Roboto Flex, e
     * sobra quase metade da faixa de cima sem uso. A Roboto Condensed é da mesma
     * família, então o desenho continua coerente e o relógio cresce.
     *
     * <p>Vale para todos os combos, e não por accidento: fonts.md escolhe Roboto
     * Flex como a fonte de relógio justamente por ser a indicada para números.
     */
    @NonNull
    public static Typeface getClockFontCondensed(@NonNull Context context) {
        return load(context, CLOCK_FONTS_CONDENSED, R.font.roboto_condensed_regular);
    }

    /** Fonte da busca. Todos os combos usam a fonte de interface. */
    @NonNull
    public static Typeface getSearchFont(@NonNull Context context) {
        return getInterfaceFont(context);
    }

    /**
     * Carrega a fonte do combo escolhido, uma vez so por combo.
     *
     * <p>O índice vem das preferências, que por padrão valem
     * {@link #CURRENT_FONT_COMBO_INDEX}. Trocar o número no código muda o
     * padrão sem tocar em mais nada — é o mesmo esquema do wallpaper.
     */
    @NonNull
    private static Typeface load(@NonNull Context context,
                                 @NonNull Typeface[] cache,
                                 int... comboResIds) {
        int index = new Prefs(context).getFontCombo();
        index = Math.max(0, Math.min(comboResIds.length - 1, index));

        Typeface cached = cache[index];
        if (cached != null) {
            return cached;
        }
        Typeface font = ResourcesCompat.getFont(context, comboResIds[index]);
        if (font == null) {
            font = Typeface.create("sans-serif", Typeface.NORMAL);
        }
        cache[index] = font;
        return font;
    }
}
