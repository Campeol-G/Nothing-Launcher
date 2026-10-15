package com.minimal.launcher;

/**
 * O log do wallpaper, em um lugar so.
 *
 * <p>Existe porque a falha e silenciosa por natureza: o {@code setBitmap} voltava sem
 * excecao, o app achava que tinha pintado, e a tela continuava com a cor antiga. Sem
 * esta linha no logcat nao havia como distinguir "nao pintou" de "pintou e o aparelho
 * ignorou".
 */
final class Log {

    private Log() {
    }

    private static final String TAG = "WALLPAPER";

    static void pintou(int cor) {
        android.util.Log.i(TAG, "pintado #" + Integer.toHexString(cor));
    }

    static void recusou(Throwable erro) {
        android.util.Log.e(TAG, "recusado: " + erro);
    }
}