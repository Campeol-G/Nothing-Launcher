package com.minimal.launcher;

import androidx.annotation.NonNull;

/**
 * As três decisões do arrasto horizontal, sem Android dentro.
 *
 * <p>Viviam na {@code EdgeGestureLayout}, onde não há como testá-las — e são
 * justamente as decisões que importam, porque o gesto agora vale em qualquer
 * lugar da tela e o erro delas aparece como a lista não rolando.
 *
 * <p>Sem Android aqui de propósito: é aritmética de deslocamento, e testá-la por
 * reflection numa View só produziria um teste que quebra a cada refatoração.
 */
public final class SideGesturePolicy {

    private SideGesturePolicy() {
    }

    /**
     * Trava de eixo, a mesma do arrasto vertical.
     *
     * <p>Um arrasto diagonal não diz de que lado a pessoa queria ir, e errar aqui
     * custa caro: gesto na direção errada abre a tela errada.
     */
    public static final float AXIS_LOCK = 1.2f;

    /**
     * Fração da largura que o arrasto precisa percorrer.
     *
     * <p>Medida na largura, e não na altura: o gesto corre de lado, e um percentual
     * da altura não diria nada sobre o quanto falta andar de lado.
     *
     * <p>Já foi 18%, e era longo demais — atravessar quase um quinto da tela com o
     * dedo para abrir uma lista é gesto de comando, não de leitura. Hoje é um
     * arrasto curto de polegar.
     */
    public static final float WIDTH_FRACTION = 0.08f;

    /**
     * Piso do percurso, em dp.
     *
     * <p>Só protege a tela estreita, onde 8% dariam um arrasto curto demais para
     * alguém acertar de propósito. Num aparelho normal quem manda é a porcentagem.
     */
    public static final int FLOOR_DP = 36;

    /**
     * Este deslocamento assume o gesto?
     *
     * @param dx deslocamento horizontal desde o toque, com o sinal de quem manda.
     * @param dy deslocamento vertical desde o toque.
     * @param touchSlop o quanto o dedo anda antes de o sistema chamar de arrasto.
     */
    public static boolean assume(float dx, float dy, float touchSlop) {
        if (Math.abs(dx) < Math.abs(dy) * AXIS_LOCK) {
            return false;
        }
        return Math.abs(dx) > touchSlop;
    }

    /**
     * O arrasto percorrido chega a valer?
     *
     * <p>Só distância, nunca velocidade: soltar o dedo com força dispara o gesto sem
     * querer, e o gesto errado é pior do que o gesto atrasado.
     *
     * @param travel o maior deslocamento horizontal já percorrido, em módulo.
     */
    public static boolean dispara(float travel, float minTravelPx) {
        return travel >= minTravelPx;
    }

    /** O percurso mínimo em px, dado o tamanho da tela e a densidade. */
    public static float percursoMinimo(float widthPx, float density) {
        return Math.max(FLOOR_DP * density, widthPx * WIDTH_FRACTION);
    }

    /**
     * O quanto o dedo andou, em módulo.
     *
     * <p>É o que faz o gesto ser ambidestro: arrastar para a esquerda acumula
     * percurso igual ao da direita, porque o gesto não distingue os dois lados.
     */
    public static float percorreu(float dx) {
        return Math.abs(dx);
    }

    /**
     * O arrasto lateral fecha a tela, ou é de uma linha que nasceu debaixo dele?
     *
     * <p>Na tela de apps recentes o mesmo arrasto significa duas coisas diferentes,
     * e quem decide é apenas de onde o dedo partiu: em cima de um app ele tira o
     * app da lista; fora dos apps ele fecha a tela.
     *
     * <p>A decisão mora aqui e não na View pelo mesmo motivo das outras — escrita
     * dentro do {@code EdgeGestureLayout}, ela só apareceria como "o arrasto parou
     * de funcionar em uma tela", sem nada que explicasse por quê.
     *
     * <p>Quando a linha fica com o gesto, o limiar de percurso nem entra na conta.
     * Apagar é gesto curto e o meio da tela é inchado de linhas, então exigir 8% da
     * largura ali seria gesto de comando, não de leitura.
     *
     * @param nasceuEmLinha o toque partiu de cima de uma linha da lista.
     */
    public static boolean oGestoDaTelaVale(boolean nasceuEmLinha) {
        return !nasceuEmLinha;
    }

    /** O texto do módulo serve só para deixar o vínculo explícito no leitor. */
    @NonNull
    @Override
    public String toString() {
        return "SideGesturePolicy{" + WIDTH_FRACTION + " da largura, piso " + FLOOR_DP + "dp}";
    }
}