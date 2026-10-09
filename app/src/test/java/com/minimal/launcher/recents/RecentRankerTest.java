package com.minimal.launcher.recents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * O que a tela faz com a lista de apps abertos depois que o sistema a devolveu.
 *
 * <p>Antes a ordem era montada aqui, a partir do histórico de uso, e a dispensa
 * guardava o instante para o app voltar sozinho. As duas coisas caem fora: a ordem
 * agora é a do sistema, e dispensar é decisão da pessoa — se ela mandou tirar, o
 * app fica fora até ela mandar de volta.
 */
public class RecentRankerTest {

    private static Map<String, Long> dismissed(String... packages) {
        Map<String, Long> map = new java.util.LinkedHashMap<>();
        for (String p : packages) {
            map.put(p, 1000L);
        }
        return map;
    }

    @Test public void mantemAOrdemDoSistema() {
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.discord", "com.android.chrome", "com.whatsapp"),
                Collections.emptyMap(), Collections.emptyMap());
        assertEquals(Arrays.asList("com.discord", "com.android.chrome", "com.whatsapp"), result);
    }

    @Test public void escondeODispensado() {
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.discord", "com.android.chrome", "com.whatsapp"),
                dismissed("com.android.chrome"), Collections.emptyMap());
        assertEquals(Arrays.asList("com.discord", "com.whatsapp"), result);
    }

    @Test public void esconderUmNaoMexeNosOutros() {
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.a", "com.b", "com.c"),
                dismissed("com.b"), Collections.emptyMap());
        assertEquals(Arrays.asList("com.a", "com.c"), result);
    }

    @Test public void dispensadoVoltaQuandoSomeDaDispensa() {
        // O contrafluxo do "limpar tudo": nada é mais descartado, então tudo volta.
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.a", "com.b"),
                Collections.emptyMap(), Collections.emptyMap());
        assertEquals(Arrays.asList("com.a", "com.b"), result);
    }

    @Test public void pacoteRepetidoApareceUmaVezNaPosicaoMaisRecente() {
        // A lista crua pode vir com o mesmo pacote duas vezes (duas tasks). A
        // primeira ocorrência é a task mais recente, que é a que a pessoa quer.
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.a", "com.b", "com.a", "com.c"),
                Collections.emptyMap(), Collections.emptyMap());
        assertEquals(Arrays.asList("com.a", "com.b", "com.c"), result);
    }

    @Test public void listaVaziaDevolveListaVazia() {
        assertTrue(RecentRanker.apply(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap()).isEmpty());
    }

    @Test public void appReabertoDepoisDaDispensaVolta() {
        // Reproduz o aparelho: TikTok e Instagram foram dispensados com o X,
        // reabertos pela pessoa, e continuaram fora da lista para sempre.
        //
        // A dispensa tem que valer so enquanto o app nao foi reaberto. E por isso
        // que ela guarda o INSTANTE, e nao so o pacote: sem o instante nao ha como
        // saber se a task e de antes ou de depois da dispensa.
        Map<String, Long> dismissedAt = new java.util.HashMap<>();
        dismissedAt.put("com.a", 1000L);
        // A task e de antes da dispensa (active 500): continua fora.
        Map<String, Long> ativaAntes = Collections.singletonMap("com.a", 500L);
        // A pessoa reabriu depois (active 1500): volta sozinho.
        Map<String, Long> ativaDepois = Collections.singletonMap("com.a", 1500L);

        List<String> antes = RecentRanker.apply(
                Arrays.asList("com.a"), dismissedAt, ativaAntes);
        List<String> depois = RecentRanker.apply(
                Arrays.asList("com.a"), dismissedAt, ativaDepois);
        assertEquals(Collections.emptyList(), antes);
        assertEquals(Collections.singletonList("com.a"), depois);
    }
}
