package com.minimal.launcher.recents;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O que a tela faz com a lista de apps abertos depois que o sistema a devolveu.
 *
 * <p>Esta classe já foi só um filtro de dispensados, e deu errado no aparelho: um
 * app dispensado com o X e depois reaberto pela pessoa continuava fora da lista
 * para sempre. A tela ficava menor que a de verdade, sem nenhum aviso, e parecia
 * decorativa.
 *
 * <p>A causa foi dar só o pacote. Sem o instante da dispensa não há como saber se a
 * task que chegou é de antes ou de depois do X — e a task é sempre a mesma, com o
 * mesmo id. Por isso a dispensa guarda o instante de novo: o app fica fora
 * enquanto a task for mais velha que a dispensa, e volta sozinho assim que a
 * pessoa reabre.
 */
public final class RecentRanker {

    private RecentRanker() {
    }

    /**
     * A lista que a tela mostra: na ordem do sistema, sem repetido, sem o que foi
     * dispensado depois de aberto.
     *
     * <p>Repetido aparece uma vez só. O sistema pode devolver o mesmo pacote em
     * duas tasks, e para a pessoa são o mesmo app — a primeira ocorrência é a task
     * mais recente, que é a que ela quer.
     *
     * @param dismissedAt pacote → instante em que foi dispensado. Ausente = nunca.
     * @param lastActiveAt  pacote → instante da última vez que a task ficou ativa.
     *                      Vem do {@code lastActiveTime} do dumpsys, que é o
     *                      mesmo relógio que o sistema usa para ordenar.
     * @return lista imutável. Os argumentos não são modificados.
     */
    @NonNull
    public static List<String> apply(@NonNull List<String> packages,
                                     @NonNull Map<String, Long> dismissedAt,
                                     @NonNull Map<String, Long> lastActiveAt) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>(packages.size());
        for (String packageName : packages) {
            if (packageName == null || packageName.isEmpty()) {
                continue;
            }
            if (!seen.add(packageName)) {
                continue;
            }
            Long dismissed = dismissedAt.get(packageName);
            Long active = lastActiveAt.get(packageName);
            if (dismissed != null && (active == null || dismissed >= active)) {
                // A task é de antes da dispensa (ou não há instante para comparar):
                // a pessoa já mandou tirar, então o app fica fora.
                continue;
            }
            result.add(packageName);
        }
        return Collections.unmodifiableList(result);
    }
}