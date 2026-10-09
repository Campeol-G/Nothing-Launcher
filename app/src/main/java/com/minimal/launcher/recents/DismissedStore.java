package com.minimal.launcher.recents;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.minimal.launcher.Prefs;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Quais apps a pessoa mandou tirar da tela.
 *
 * <p>A lista vem do sistema, com as tasks de verdade, então dispensar é uma decisão
 * dela e não um efeito colateral: o app continua aberto, continua no recents do
 * Android, e sai de <em>nossa</em> lista até ela mandar de volta. O contrafluxo é o
 * "limpar tudo".
 *
 * <p>Antes esta classe guardava o instante da dispensa, para o app voltar sozinho
 * quando fosse reaberto. Isso existia porque a lista era montada por histórico de
 * uso, e não havia como saber o que era task aberta. Agora que a lista é a real,
 * o instante não resolve nada — dispensar que é dispensar.
 *
 * <p>Mesmo formato do {@code FavoritesStore}: uma string só, separada por {@code \n}.
 */
public final class DismissedStore {

    private static final String KEY_DISMISSED = "dismissed_apps";
    private static final String SEPARATOR = "\n";

    private final SharedPreferences prefs;
    private final Set<String> dismissed = new LinkedHashSet<>();

    public DismissedStore(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        this.dismissed.addAll(parse(prefs.getString(KEY_DISMISSED, null)));
    }

    /** Cópia do estado atual. Quem chama pode iterate à vontade. */
    @NonNull
    public Set<String> snapshot() {
        return new LinkedHashSet<>(dismissed);
    }

    public void dismiss(@NonNull String packageName) {
        if (dismissed.add(packageName)) {
            persist();
        }
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
        prefs.edit().putString(KEY_DISMISSED, String.join(SEPARATOR, dismissed)).apply();
    }

    /**
     * Entrada malformada é ignorada, e não derruba a leitura.
     *
     * <p>Um valor de preferência vem de um arquivo que o usuário pode ter editado, e
     * de uma versão anterior do app que gravava outro formato. Ler isso não pode
     * custar a tela inteira.
     */
    @NonNull
    private static Set<String> parse(@Nullable String joined) {
        if (joined == null || joined.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>(Arrays.asList(joined.split(SEPARATOR, -1)));
        result.remove("");
        return result;
    }
}