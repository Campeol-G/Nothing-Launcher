package com.minimal.launcher.recents;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A ordem da tela de apps recentes, sem nada de Android dentro.
 *
 * <p>Esta é a única parte da tela que pode falhar em silêncio. Se a comparação de
 * dispensa estiver errada, a tela só mostra a lista errada — e nada aparece em
 * log nenhum. Por isso a regra mora aqui, separada do {@link RecentAppsSource},
 * que é quem vai buscar os eventos de uso no sistema.
 *
 * <p>Mesma ideia do {@code AppSearch}: lógica que dá para testar sem aparelho.
 */
public final class RecentRanker {

    /** Um uso de app: o pacote e quando foi usado por último. */
    public static final class Use {

        public final String packageName;
        public final long lastTimeUsed;

        public Use(@NonNull String packageName, long lastTimeUsed) {
            this.packageName = packageName;
            this.lastTimeUsed = lastTimeUsed;
        }
    }

    private RecentRanker() {
    }

    /**
     * Mais recente primeiro, um pacote uma vez só, e sem os que têm dispensa vigente.
     *
     * <p>A dispensa vale enquanto {@code dismissedAt >= lastTimeUsed}: o app foi
     * usado por último antes de você tê-lo tirado da tela. Se ele for aberto de novo
     * depois, o evento novo é mais recente que a dispensa e o app volta sozinho.
     * É isso que impede o "limpar tudo" de apagar um app para sempre.
     *
     * <p>O dedup importa porque o {@code UsageStatsManager} devolve um evento por
     * task, não por app: sem isso o mesmo nome sai duas vezes e o X age na task errada.
     *
     * @param dismissedAt pacote → instante em que foi dispensado. Ausente = nunca.
     * @return lista imutável. Os argumentos não são modificados.
     */
    @NonNull
    public static List<Use> rank(@NonNull List<Use> uses, @NonNull Map<String, Long> dismissedAt) {
        List<Use> sorted = new ArrayList<>(uses);
        sorted.sort(Comparator.comparingLong((Use u) -> u.lastTimeUsed).reversed());

        Set<String> seen = new LinkedHashSet<>();
        List<Use> result = new ArrayList<>();
        for (Use use : sorted) {
            if (!seen.add(use.packageName)) {
                continue;
            }
            Long dismissed = dismissedAt.get(use.packageName);
            if (dismissed != null && dismissed >= use.lastTimeUsed) {
                continue;
            }
            result.add(use);
        }
        return Collections.unmodifiableList(result);
    }
}