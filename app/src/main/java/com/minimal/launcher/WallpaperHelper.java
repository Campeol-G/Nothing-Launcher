package com.minimal.launcher;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Pinta o wallpaper do aparelho com a cor de fundo do app.
 *
 * <p>Por que o app mexe no wallpaper, quando ele nem desenha essas telas: o
 * bloqueio, a janela de recents do sistema e a transição entre apps **não são telas
 * nossas**. São do sistema, e nenhum app de terceiro pode tomá-las — o bloqueio
 * exige permissão restrita e o recents mora dentro do SystemUI. São exatamente os
 * lugares onde a pessoa via a imagem do aparelho atravessando o app.
 *
 * <p>Trocar o wallpaper por uma cor sólida é a única alavanca que o app tem sobre
 * esses vãos. Não conserta o desenho — conserta o que aparece quando o desenho não
 * chega.
 */
public final class WallpaperHelper {

    private WallpaperHelper() {
    }

    private static final String TAG = "WALLPAPER";

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
        Bitmap tela = telaNaCor(context, pedida);
        if (tela == null) {
            return;
        }
        try {
            // allowBackup = false: com ele ligado, o serviço de backup do
            // wallpaper restaura a imagem antiga por cima da que acabamos de pôr —
            // o comando voltava sem erro e a tela continuava com a cor anterior.
            WallpaperManager.getInstance(context).setBitmap(tela, null, false);
            prefs.setWallpaperAplicada(pedida);
            Log.pintou(pedida);
        } catch (IOException | SecurityException | IllegalStateException e) {
            // ROM sem serviço de wallpaper, sem permissão, ou sistema pedindo
            // confirmação e negando. O app continua funcionando sem isso — é um
            // item das configurações, não uma dependência.
            Log.recusou(e);
        } finally {
            tela.recycle();
        }
    }

    /**
     * Um bitmap do tamanho da tela, pintado de uma cor só.
     *
     * <p>Do tamanho da tela, e não de um quadrado pequeno: um wallpaper menor que a
     * tela é recusado pelo motor de render em alguns aparelhos. O comando voltava sem
     * exceção nenhuma e a imagem continuava a antiga — foi assim que 480px enganou o
     * diagnóstico por um bom tempo.
     */
    private static Bitmap telaNaCor(@NonNull Context context, int cor) {
        try {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int largura = Math.max(metrics.widthPixels, 1);
            int altura = Math.max(metrics.heightPixels, 1);
            // RGB_565, e não ARGB_8888: a imagem é de uma cor chapada, não usa
            // canal alfa, e o RGB_565 corta o custo pela metade.
            Bitmap tela = Bitmap.createBitmap(largura, altura, Bitmap.Config.RGB_565);
            new Canvas(tela).drawColor(cor);
            return tela;
        } catch (OutOfMemoryError ignored) {
            return null;
        }
    }
}