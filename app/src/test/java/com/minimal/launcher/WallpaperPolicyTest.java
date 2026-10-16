package com.minimal.launcher;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Quando o app deve repintar o wallpaper do aparelho.
 *
 * <p>Repintar não é gratuito: o Android pode mostrar um diálogo de confirmação, e
 * um diálogo a cada troca de cor seria pior que o problema. Então a regra tem de
 * ser: só repinta quando o fundo pedido é mesmo diferente do que já está lá.
 */
public class WallpaperPolicyTest {

    private static final int CREME = 0xFFF4EFE3;
    private static final int PRETO = 0xFF000000;

    @Test public void desligadoNuncaRepinta() {
        assertFalse(WallpaperPolicy.deveRepintar(false, CREME, PRETO));
    }

    @Test public void ligadoComCorDiferenteRepinta() {
        assertTrue(WallpaperPolicy.deveRepintar(true, CREME, PRETO));
    }

    @Test public void ligadoComAMesmaCorNaoRepinta() {
        // Repintar o mesmogerava um dialogo para nao mudar nada.
        assertFalse(WallpaperPolicy.deveRepintar(true, CREME, CREME));
    }

    @Test public void semCorAnteriorRepintaSempre() {
        // Primeira vez: nao se sabe o que esta la, entaoassume-se que tem algo.
        assertTrue(WallpaperPolicy.deveRepintar(true, CREME, 0));
    }

    @Test public void soImportaOsCanaisDeCor() {
        // O wallpaper nao tem alpha; comparar o alpha faria o mesmo cor parecer
        // diferente e repintar a toa.
        assertFalse(WallpaperPolicy.deveRepintar(true, 0xFFF4EFE3, 0x00F4EFE3));
    }
}