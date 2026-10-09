package com.minimal.launcher.recents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A regra que decide se um app aparece na tela de recentes.
 *
 * <p>Estes testes existem porque a falha deles é invisível: se a comparação de
 * dispensa estiver errada, a tela simplesmente mostra a lista errada, e nada
 * aparece em log nenhum. Por isso a regra ficou numa classe sem Android.
 */
public class RecentRankerTest {

    private static RecentRanker.Use use(String pkg, long when) {
        return new RecentRanker.Use(pkg, when);
    }

    private static List<String> packagesOf(List<RecentRanker.Use> uses) {
        List<String> out = new ArrayList<>();
        for (RecentRanker.Use u : uses) {
            out.add(u.packageName);
        }
        return out;
    }

    @Test public void ordenaDoMaisRecenteParaOMaisAntigo() {
        List<RecentRanker.Use> result = RecentRanker.rank(
                Arrays.asList(use("a", 100L), use("b", 300L), use("c", 200L)),
                Collections.emptyMap());
        assertEquals(Arrays.asList("b", "c", "a"), packagesOf(result));
    }

    @Test public void pacoteComVariasTasksApareceUmaVezComOTimeMaisNovo() {
        // O UsageStatsManager devolve um evento por task, nao por app: sem dedup
        // o mesmo nome aparece varias vezes e o X age na task errada.
        List<RecentRanker.Use> result = RecentRanker.rank(
                Arrays.asList(use("a", 100L), use("a", 900L), use("a", 400L)),
                Collections.emptyMap());
        assertEquals(1, result.size());
        assertEquals(900L, result.get(0).lastTimeUsed);
    }

    @Test public void dispensaVigenteEscondeOApp() {
        // dismissedAt igual a lastTimeUsed esconde: foi dispensado agora.
        List<RecentRanker.Use> result = RecentRanker.rank(
                Arrays.asList(use("a", 500L)),
                Collections.singletonMap("a", 500L));
        assertTrue(result.isEmpty());
    }

    @Test public void appReabertoDepoisVoltaSozinho() {
        // dismissedAt menor que lastTimeUsed: o app foi usado de novo depois de
        // dispensado, entao o historico manda e ele volta, sem nenhuma acao.
        List<RecentRanker.Use> result = RecentRanker.rank(
                Arrays.asList(use("a", 900L)),
                Collections.singletonMap("a", 500L));
        assertEquals(1, result.size());
        assertEquals("a", result.get(0).packageName);
    }

    @Test public void dispensaDeUmAppNaoAfetaOutro() {
        List<RecentRanker.Use> result = RecentRanker.rank(
                Arrays.asList(use("a", 500L), use("b", 500L)),
                Collections.singletonMap("a", 500L));
        assertEquals(Arrays.asList("b"), packagesOf(result));
    }

    @Test public void listaVaziaDevolveListaVazia() {
        assertTrue(RecentRanker.rank(Collections.emptyList(), Collections.emptyMap()).isEmpty());
    }
}