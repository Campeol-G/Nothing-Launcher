package com.minimal.launcher.recents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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

    private static Set<String> dismissed(String... packages) {
        return new LinkedHashSet<>(Arrays.asList(packages));
    }

    @Test public void mantemAOrdemDoSistema() {
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.discord", "com.android.chrome", "com.whatsapp"),
                Collections.emptySet());
        assertEquals(Arrays.asList("com.discord", "com.android.chrome", "com.whatsapp"), result);
    }

    @Test public void escondeODispensado() {
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.discord", "com.android.chrome", "com.whatsapp"),
                dismissed("com.android.chrome"));
        assertEquals(Arrays.asList("com.discord", "com.whatsapp"), result);
    }

    @Test public void esconderUmNaoMexeNosOutros() {
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.a", "com.b", "com.c"),
                dismissed("com.b"));
        assertEquals(Arrays.asList("com.a", "com.c"), result);
    }

    @Test public void dispensadoVoltaQuandoSomeDaDispensa() {
        // O contrafluxo do "limpar tudo": nada é mais descartado, então tudo volta.
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.a", "com.b"),
                Collections.emptySet());
        assertEquals(Arrays.asList("com.a", "com.b"), result);
    }

    @Test public void pacoteRepetidoApareceUmaVezNaPosicaoMaisRecente() {
        // A lista crua pode vir com o mesmo pacote duas vezes (duas tasks). A
        // primeira ocorrência é a task mais recente, que é a que a pessoa quer.
        List<String> result = RecentRanker.apply(
                Arrays.asList("com.a", "com.b", "com.a", "com.c"),
                Collections.emptySet());
        assertEquals(Arrays.asList("com.a", "com.b", "com.c"), result);
    }

    @Test public void listaVaziaDevolveListaVazia() {
        assertTrue(RecentRanker.apply(Collections.emptyList(), Collections.emptySet()).isEmpty());
    }
}