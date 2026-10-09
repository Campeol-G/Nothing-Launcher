package com.minimal.launcher.recents;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.minimal.launcher.Prefs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quais apps a pessoa mandou tirar da tela, e quando.
 *
 * <p>A lista vem do sistema com as tasks de verdade, e a task não muda quando o app
 * é reaberto — é a mesma task, com o mesmo id. Sem o instante da dispensa, não há
 * como distinguir "a task é de antes do X" de "a pessoa reabriu depois do X", e o
 * app ficava fora da lista para sempre.
 *
 * <p>Isso foi o que aconteceu no aparelho: TikTok e Instagram foram dispensados,
 * reabertos, e nunca mais apareceram. A tela ficava menor que a de verdade sem
 * nenhum aviso.
 *
 * <p>Mesmo formato do {@code FavoritesStore}: uma string só, separada por {@code \n}.
 */
public final class DismissedStore {

    private static final String KEY_DISMISSED = "dismissed_apps";
    private static final String SEPARATOR = "\n";

    private static final String FIELD_SEPARATOR = "=";

    private final SharedPreferences prefs;
    /** Pacote → instante da dispensa. O instante é o que faz o app voltar. */
    private final Map<String, Long> dismissed = new LinkedHashMap<>();

    public DismissedStore(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        this.dismissed.putAll(parse(prefs.getString(KEY_DISMISSED, null)));
    }

    /** Cópia do estado atual. Quem chama pode iterate à vontade. */
    @NonNull
    public Map<String, Long> snapshot() {
        return new LinkedHashMap<>(dismissed);
    }

    public void dismiss(@NonNull String packageName, long atMillis) {
        dismissed.put(packageName, atMillis);
        persist();
    }

    /** Esquece tudo. É o que o "limpar tudo" faz, e o contrafluxo do dispensado. */
    public void clear() {
        if (dismissed.isEmpty()) {
            return;
        }
        dismissed.clear();
        persist();
    }

    private void persist() {
        StringBuilder joined = new StringBuilder();
        for (Map.Entry<String, Long> entry : dismissed.entrySet()) {
            if (joined.length() > 0) {
                joined.append(SEPARATOR);
            }
            joined.append(entry.getKey()).append(FIELD_SEPARATOR).append(entry.getValue());
        }
        prefs.edit().putString(KEY_DISMISSED, joined.toString()).apply();
    }

    /**
     * Entrada malformada é ignorada, e não derruba a leitura.
     *
     * <p>Um valor de preferência vem de um arquivo que o usuário pode ter editado, e
     * de uma versão anterior do app que gravava outro formato. Ler isso não pode
     * custar a tela inteira.
     */
    @NonNull
    private static Map<String, Long> parse(@Nullable String joined) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (joined == null || joined.isEmpty()) {
            return result;
        }
        for (String line : joined.split(SEPARATOR, -1)) {
            int split = line.lastIndexOf(FIELD_SEPARATOR);
            if (split <= 0 || split == line.length() - 1) {
                continue;
            }
            try {
                result.put(line.substring(0, split),
                        Long.parseLong(line.substring(split + 1)));
            } catch (NumberFormatException ignored) {
                // Linha ilegível: a dispensa se perde e o app volta a aparecer.
            }
        }
        return result;
    }
}