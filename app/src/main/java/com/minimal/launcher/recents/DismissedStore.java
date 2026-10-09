package com.minimal.launcher.recents;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.minimal.launcher.Prefs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Quais apps foram tirados da tela de recentes, e quando.
 *
 * <p>A lista é relida do {@code UsageStatsManager} a cada {@code onResume}, então
 * tirar a linha só da memória não limpa nada: ela volta no próximo clique no
 * atalho. E o {@code UsageStatsManager} não tem API pública para apagar um evento.
 * Daí a dispensa ser persistida.
 *
 * <p>O que se guarda é o <em>instante</em>, não um flag permanente, e é isso que
 * deixa a regra se manter honesta sozinha: o filtro em {@link RecentRanker} compara
 * a dispensa com o último uso do app, então abrir o app de novo traz ele de volta
 * sem nenhuma ação. Se fosse um flag, o "limpar tudo" seria um apagão que o
 * usuário não consegue desfazer.
 *
 * <p>Nada aqui expira: a comparação com o último uso resolve sozinha. Mesmo formato
 * do {@code FavoritesStore} — uma string só, componentes separados por {@code \n}.
 */
public final class DismissedStore {

    private static final String KEY_DISMISSED = "dismissed_apps";
    private static final String SEPARATOR = "\n";
    private static final String FIELD_SEPARATOR = "=";

    private final SharedPreferences prefs;

    /** Pacote → instante da dispensa, na ordem em que foram tirados. */
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

    /** Dispensa o app agora. Usado de novo quando ele voltar, e o instante avança. */
    public void dismiss(@NonNull String packageName, long atMillis) {
        dismissed.put(packageName, atMillis);
        persist();
    }

    /** Esquece tudo. Usado pelo "limpar tudo". */
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
     * <p>Um valor de preferência vem de um arquivo que o usuário pode ter editado,
     * e de uma versão anterior do app que pode ter gravado outro formato. Ler isso
     * não pode custar a tela inteira.
     */
    @NonNull
    private static Map<String, Long> parse(String joined) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (joined == null || joined.isEmpty()) {
            return result;
        }
        for (String line : joined.split(SEPARATOR, -1)) {
            int split = line.lastIndexOf(FIELD_SEPARATOR);
            if (split <= 0 || split == line.length() - 1) {
                continue;
            }
            String packageName = line.substring(0, split);
            try {
                result.put(packageName, Long.parseLong(line.substring(split + 1)));
            } catch (NumberFormatException ignored) {
                // Linha ilegível: a dispensa dela se perde, o app volta a aparecer.
            }
        }
        return result;
    }
}