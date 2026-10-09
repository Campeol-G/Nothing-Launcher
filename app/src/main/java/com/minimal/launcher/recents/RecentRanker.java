package com.minimal.launcher.recents;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * O que a tela faz com a lista de apps abertos depois que o sistema a devolveu.
 *
 * <p>Esta classe era o lugar onde a ordem era montada, medindo o histórico de uso.
 * Não é mais: a lista vem do {@code dumpsys activity recents}, que já vem na ordem
 * da task mais recente para a mais antiga. Reordenar aqui seria trabalho que
 * estraga o resultado.
 *
 * <p>O que sobra é tirar o que não é app e o que a pessoa mandou dispensar. É pouco
 * de propósito — a mesma ideia do {@code AppSearch} no outro lado do app: lógica
 * que dá para testar sem aparelho, longe da tela.
 */
public final class RecentRanker {

    private RecentRanker() {
    }

    /**
     * A lista que a tela mostra: na ordem do sistema, sem repetido, sem o que foi
     * dispensado.
     *
     * <p>Repetido aparece uma vez só. O sistema pode devolver o mesmo pacote em
     * duas tasks, e para a pessoa são o mesmo app — a primeira ocorrência é a task
     * mais recente, que é a que ela quer.
     *
     * @param dismissed pacotes que ela mandou tirar. Enquanto estiverem aqui, não
     *                  voltam sozinhos: quem traz de volta é a pessoa, e a única
     *                  forma de trazer é "limpar tudo". Isso é o oposto do
     *                  comportamento antigo, que existia só porque a lista era
     *                  montada por uso e não havia como saber o que era task.
     * @return lista imutável. Os argumentos não são modificados.
     */
    @NonNull
    public static List<String> apply(@NonNull List<String> packages, @NonNull Set<String> dismissed) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>(packages.size());
        for (String packageName : packages) {
            if (packageName == null || packageName.isEmpty()) {
                continue;
            }
            if (dismissed.contains(packageName) || !seen.add(packageName)) {
                continue;
            }
            result.add(packageName);
        }
        return Collections.unmodifiableList(result);
    }
}