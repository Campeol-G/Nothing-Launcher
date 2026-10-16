package com.minimal.launcher;

/**
 * Quando o app deve repintar o wallpaper do aparelho.
 *
 * <p>Fica separada do {@link WallpaperHelper} porque o Android entra aqui: repintar
 * pode mostrar um diálogo de confirmação do sistema, e a decisão é aritmética, não
 * trabalho de sistema.
 */
public final class WallpaperPolicy {

    private WallpaperPolicy() {
    }

    /** A máscara dos três canais de cor, sem o alpha. */
    private static final int CORES = 0x00FFFFFF;

    /**
     * Deve repintar agora?
     *
     * @param ligado o item das configurações está ligado?
     * @param corPedida a cor que o app está usando de fundo agora.
     * @param corJaAplicada a cor que este app deixou por último. Zero = nunca
     *                      pintou, e então não se sabe o que há no aparelho.
     */
    public static boolean deveRepintar(boolean ligado, int corPedida, int corJaAplicada) {
        if (!ligado) {
            return false;
        }
        if (corJaAplicada == 0) {
            return true;
        }
        // Só os canais de cor: o wallpaper não tem alpha, e comparar o alpha faria
        // a mesma cor parecer diferente a cada vez.
        return (corPedida & CORES) != (corJaAplicada & CORES);
    }
}