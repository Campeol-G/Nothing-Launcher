package com.minimal.launcher;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Pinta o wallpaper do aparelho com a cor de fundo do app.
 *
 * <p>Por que o app mexe no wallpaper do aparelho, quando ele nem desenha essas telas:
 * o bloqueio, a janela de recents do sistema e a transição entre apps **não são
 * telas nossas**. São do sistema, e nenhum app de terceiro pode tomá-las — o
 * bloqueio exige permissão restrita e o recents mora dentro do SystemUI. São
 * exatamente os lugares onde a pessoa via a imagem do aparelho atravessando o app.
 *
 * <p>Trocar o wallpaper por uma cor sólida é a única alavanca que o app tem sobre
 * esses vãos. Não conserta o desenho — conserta o que aparece quando o desenho não
 * chega.
 */
public final class WallpaperHelper {

    private WallpaperHelper() {
    }

    /**
     * Tamanho do bitmap gerado.
     *
     * <p>Pequeno de propósito: a imagem é uma cor sólida, e cor sólida não pixelata.
     * Não há ganho nenhum em gerar um bitmap do tamanho da tela.
     */
    private static final int LADO = 480;

    /**
     * Repinta se for preciso. Chamar a cada vez que a tela aparece é seguro: quem
     * decide se vale o trabalho é {@link WallpaperPolicy}.
     */
    public static void aplicarSePrecisar(@NonNull Context context) {
        Prefs prefs = new Prefs(context);
        int pedida = ThemeHelper.getHomeBackgroundColor(context);
        int jaAplicada = prefs.getWallpaperAplicada();

        if (!WallpaperPolicy.deveRepintar(prefs.isWallpaperSolidario(), pedida, jaAplicada)) {
            return;
        }
        Bitmap tela = telaNaCor(pedida);
        if (tela == null) {
            return;
        }
        try {
            WallpaperManager.getInstance(context).setBitmap(tela);
            // Só grava o que foi realmente pintado: se o sistema recusar, o próximo
            // Called tentará de novo em vez de achar que já está feito.
            prefs.setWallpaperAplicada(pedida);
        } catch (IOException | SecurityException | IllegalStateException ignored) {
            // ROM sem serviço de wallpaper, sem permissão, ou sistema pedindo
            // confirmação e negando. O app continua funcionando sem isso — é um
            // item das configurações, não uma dependência.
        } finally {
            tela.recycle();
        }
    }

    /** Um bitmap de uma cor só, ou null se nem isso deu. */
    private static Bitmap telaNaCor(int cor) {
        try {
            Bitmap tela = Bitmap.createBitmap(LADO, LADO, Bitmap.Config.ARGB_8888);
            new Canvas(tela).drawColor(cor);
            return tela;
        } catch (OutOfMemoryError ignored) {
            return null;
        }
    }
}