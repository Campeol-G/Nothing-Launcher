package com.minimal.launcher;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.minimal.launcher.recents.RecentAppsActivity;

/**
 * Medidas da tela que o launcher se adapta: quantos favoritos cabem e o quanto
 * a tipografia precisa crescer ou encolher.
 *
 * <p>A ideia é que o app pareça desenhado para o aparelho em que está, e não o
 * mesmo arquivo em todo mundo. Duas compensações, nesta ordem:
 *
 * <ol>
 *   <li><b>Tipografia automática</b> — telas baixas e comprimidas recebem texto
 *       proporcionalmente maior, porque é nelas que 22sp parece pequeno. Telas
 *       muito altas recebem um pouco menos, para o relógio não virar um outdoor.
 *       O valor é sempre perto de 1: um tamanho em sp já é independente de
 *       densidade, então a densidade não entra no cálculo.</li>
 *   <li><b>Preferência do usuário</b> — os quatro passos de Configurações
 *       multiplicam o automático. Quem não mexe fica no automático.</li>
 * </ol>
 */
public final class LauncherUtils {

    /** Quantos favoritos cabem, de 5 a 14. */
    private static final int MAX = 14;
    private static final int MIN = 5;

    /**
     * Altura de referência, em dp, em que a tipografia fica exatamente como
     * escrita nos resources. Telas acima disso encolhem; abaixo, crescem.
     */
    private static final float REFERENCE_HEIGHT_DP = 800f;

    /** Quanto a tipografia pode variar sozinha. */
    private static final float FONT_FACTOR_MIN = 0.92f;
    private static final float FONT_FACTOR_MAX = 1.30f;

    /**
     * Fração da altura da tela ocupada pelo bloco de hora e data.
     *
     * <p>28% dá folga sobre os 20% pedidos, e sobra um terço de tela para a
     * lista. O bloco é a faixa, não a tinta: o relógio ainda aperta contra a
     * largura da tela — "21:41" ocupa uns 2.6 larguras de glifo, então numa tela
     * estreita ele esbarra no limite de largura muito antes do de altura. Ver a
     * nota em {@code HomeActivity.applyClockAutosize}.
     */
    private static final float HEADER_FRACTION = 0.28f;

    /** Folga abaixo da lista: padding de baixo e inset da barra de navegação. */
    private static final float BOTTOM_CHROME_DP = 28f;

    private LauncherUtils() {
    }

    /** Altura útil da tela, em pixels, já sem a status bar e a nav bar. */
    private static int usableHeightPx(@NonNull Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    /** Altura do bloco de hora e data, em pixels. */
    public static int getHeaderHeightPx(@NonNull Context context) {
        return Math.round(usableHeightPx(context) * HEADER_FRACTION);
    }

    /**
     * Fator tipográfico automático da tela.
     *
     * <p>Razão entre a altura de referência e a altura real, limitada. Uma tela
     * de 640dp recebe 1.25; uma de 1000dp recebe 0.92.
     */
    public static float getFontFactor(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        float heightDp = usableHeightPx(context) / density;
        if (heightDp <= 0f) {
            return 1f;
        }
        float factor = REFERENCE_HEIGHT_DP / heightDp;
        return Math.max(FONT_FACTOR_MIN, Math.min(FONT_FACTOR_MAX, factor));
    }

    /**
     * Escala final do texto: automático pelo aparelho, multiplicado pela
     * preferência. É este valor que aparece em todo {@code setTextSize}.
     */
    public static float getFontScale(@NonNull Context context) {
        return getFontFactor(context) * new Prefs(context).getFontScale();
    }

    /**
     * Quantos favoritos cabem na faixa de baixo da tela.
     *
     * <p>Desconta a altura do bloco de hora e data — que é fração fixa da tela —
     * e divide o que sobra pela altura de uma linha.
     */
    public static int getMaxFavorites(@NonNull Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        float scale = getFontScale(context);

        float headerHeight = getHeaderHeightPx(context);
        float nameHeight = context.getResources().getDimension(R.dimen.app_name_size) * scale;
        float rowHeight = nameHeight
                + 2f * context.getResources().getDimension(R.dimen.app_row_padding);
        if (rowHeight <= 0f) {
            return MIN;
        }

        float gap = context.getResources().getDimension(R.dimen.clock_list_gap);
        float usable = usableHeightPx(context) - headerHeight - gap
                - BOTTOM_CHROME_DP * density;
        return Math.max(MIN, Math.min(MAX, (int) (usable / rowHeight)));
    }

    /**
     * Liga o arrasto horizontal que abre a tela de apps recentes.
     *
     * <p>Fica aqui porque as quatro telas precisam do mesmo gesto e da mesma regra.
     * Se as telas decidissem isso sozinhas, uma delas esqueceria e o gesto pararia de
     * funcionar em uma delas sem ninguem perceber.
     *
     * <p>A mao dominante deixou de contar: o gesto e ambidestro e nasce em qualquer
     * lugar da tela. A direcao do dedo e repassada para a activity, que e quem
     * escolhe de que lado a tela entra.
     */
    public static void bindRecentAppsGesture(@NonNull EdgeGestureLayout root) {
        root.setSideGesture(dragDirection ->
                root.getContext().startActivity(
                        RecentAppsActivity.intentEnteringFrom(root.getContext(), dragDirection)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
    }
}
