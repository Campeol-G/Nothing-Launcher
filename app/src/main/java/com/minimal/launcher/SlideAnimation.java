package com.minimal.launcher;

import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

/**
 * A entrada e a saída das telas, em slide.
 *
 * <p>Existe para as duas telas de consulta (busca e apps recentes) usarem o mesmo
 * gesto e a mesma duração. Animação feita em dois lugares com dois números diferentes
 * é como uma tela acaba com 220ms e a outra com 170ms sem ninguém perceber.
 *
 * <p>A regra é a mesma nas duas: a tela entra pelo lado <b>oposto</b> ao do dedo, e
 * sai indo <b>com</b> ele. O olho já sabe para que lado a coisa vai — ela só confirma.
 */
public final class SlideAnimation {

    private SlideAnimation() {
    }

    /**
     * Curto o suficiente para não virar espera.
     *
     * <p>200ms é o que o olho acompanha sem achar que está esperando. Abaixo disso
     * a tela pisca; acima, o gesto parece lento.
     */
    public static final long DURACAO_ENTRADA_MS = 200L;
    public static final long DURACAO_SAIDA_MS = 170L;

    /** Desliza para dentro, vindo de baixo, como quem puxa uma gaveta. */
    public static void entrarDeBaixo(@NonNull View tela) {
        float altura = alturaDe(tela);
        tela.setTranslationY(altura);
        tela.setAlpha(0f);
        tela.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(DURACAO_ENTRADA_MS)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /** Some descendo, e só então faz o que veio depois. */
    public static void sairParaBaixo(@NonNull View tela, @NonNull Runnable aoTerminar) {
        tela.animate()
                .translationY(alturaDe(tela))
                .alpha(0f)
                .setDuration(DURACAO_SAIDA_MS)
                .setInterpolator(new AccelerateInterpolator(1.6f))
                .withEndAction(aoTerminar)
                .start();
    }

    /**
     * Desliza para dentro vindo de um lado.
     *
     * @param lado {@link #LADO_ESQUERDO} ou {@link #LADO_DIREITO}, de onde ela entra.
     */
    public static void entrarDoLado(@NonNull View tela, int lado) {
        float largura = larguraDe(tela);
        tela.setTranslationX(lado == LADO_ESQUERDO ? -largura : largura);
        tela.setAlpha(0f);
        tela.animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(DURACAO_ENTRADA_MS)
                .setInterpolator(new DecelerateInterpolator(1.6f))
                .start();
    }

    /** Sai na direção de onde o dedo foi. */
    public static void sairParaOLado(@NonNull View tela, int direcaoDoDedo,
                                      @NonNull Runnable aoTerminar) {
        tela.animate()
                .translationX(direcaoDoDedo == EdgeGestureLayout.DIRECTION_RIGHT
                        ? larguraDe(tela) : -larguraDe(tela))
                .alpha(0f)
                .setDuration(DURACAO_SAIDA_MS)
                .setInterpolator(new AccelerateInterpolator(1.6f))
                .withEndAction(aoTerminar)
                .start();
    }

    public static final int LADO_ESQUERDO = 1;
    public static final int LADO_DIREITO = 2;

    private static float alturaDe(@NonNull View tela) {
        return tela.getHeight() > 0 ? tela.getHeight()
                : tela.getResources().getDisplayMetrics().heightPixels;
    }

    private static float larguraDe(@NonNull View tela) {
        return tela.getWidth() > 0 ? tela.getWidth()
                : tela.getResources().getDisplayMetrics().widthPixels;
    }
}