package com.minimal.launcher.recents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * O que é um app "aberto" na lista.
 *
 * <p>Este é o bug que a pessoa viu: ela encerra um app, a linha some, e quando volta
 * os apps que ela fechou aparecem de novo. A causa não é a dispensa, que está
 * certíssima: é que o sistema guarda a task mesmo depois do processo morrer, e ela
 * volta a ser lida na leitura seguinte como se o app continuasse aberto.
 *
 * <p>O {@code dumpsys} marca esse fantasma com {@code sz=0} — zero activities na task.
 * Uma task de verdade tem pelo menos uma.
 */
public class TaskListParserTest {

    @Test public void taskComZeroActivitiesNaoEAppAberto() {
        // Este é o texto que o aparelho devolve depois de um `am force-stop`: a task
        // continua listada, com o pacote dela intacto, mas sem nenhuma activity
        // dentro. Sem o filtro, o app volta a aparecer como aberto.
        //
        // A linha de intent é o que dá o pacote ao parser. Sem ela o teste passaria
        // pelo motivo errado — o parser não acharia pacote nenhum, e não porque
        // tivesse descartado o fantasma.
        String saida = "  * Recent #3: Task{f3c0be2 #1009 type=standard "
                + "A=10241:com.android.chrome U=0 visible=false mode=fullscreen sz=0}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.android.chrome/.Main}";

        assertEquals(Collections.emptyList(),
                TaskListParser.launchablePackages(saida));
    }

    @Test public void taskComActivitiesContinuaSendoAppAberto() {
        String saida = "  * Recent #1: Task{aaa #500 type=standard "
                + "A=10241:com.android.chrome U=0 mode=fullscreen sz=1}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.android.chrome/.Main}";

        assertEquals(Collections.singletonList("com.android.chrome"),
                TaskListParser.launchablePackages(saida));
    }

    @Test public void fantasmaNaoVoltaNemDispensadoNemLimpo() {
        // O symptom a pessoa-relatado: fechar, voltar e encontrar o app de novo.
        // Com o fantasma filtrado na origem, ele nao aparece em nenhuma das duas
        // situacoes, porque nunca chega a ser lido.
        String saida = "  * Recent #1: Task{a #1 type=standard A=10241:com.android.chrome "
                + "U=0 mode=fullscreen sz=0}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.android.chrome/.Main}\n"
                + "  * Recent #2: Task{b #2 type=standard A=10242:com.whatsapp "
                + "U=0 mode=fullscreen sz=1}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.whatsapp/.Main}";

        List<String> pacotes = TaskListParser.launchablePackages(saida);

        assertEquals(Collections.singletonList("com.whatsapp"), pacotes);
        assertFalse(pacotes.contains("com.android.chrome"));
    }

    @Test public void taskSemSzContinuaSendoLida() {
        // Root antigo ou ROM que não escreve o campo: não se pode descartar task por
        // falta de informação. Sem `sz` a task é tratada como app aberto, que é o
        // comportamento de antes e o mais seguro.
        String saida = "  * Recent #1: Task{a #1 type=standard A=10241:com.android.chrome "
                + "U=0 mode=fullscreen}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.android.chrome/.Main}";

        assertEquals(Collections.singletonList("com.android.chrome"),
                TaskListParser.launchablePackages(saida));
    }

    @Test public void oInicioDoBlocoNaoTemSz() {
        // `sz` aparece no fim da linha do Recent #N. Uma linha de detalhe que comece
        // com "sz" não pode ser confundida com o cabeçalho do bloco.
        String saida = "  * Recent #1: Task{a #1 type=standard A=10241:com.android.chrome "
                + "U=0 mode=fullscreen sz=1}\n"
                + "    sz=1\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.android.chrome/.Main}";

        assertEquals(Collections.singletonList("com.android.chrome"),
                TaskListParser.launchablePackages(saida));
    }

    @Test public void appsReaisContinuamSendoLidos() {
        // Guarda contra o filtro novo engolir app de verdade por engano.
        String saida = "  * Recent #1: Task{a #1 type=standard A=10241:com.android.chrome "
                + "U=0 mode=fullscreen sz=1}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.android.chrome/.Main}\n"
                + "  * Recent #2: Task{c #2 type=standard A=10242:com.whatsapp "
                + "U=0 mode=fullscreen sz=2}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.whatsapp/.Main}";

        assertEquals(Arrays.asList("com.android.chrome", "com.whatsapp"),
                TaskListParser.launchablePackages(saida));
    }

    @Test public void taskDeSistemaContinuaSendoFora() {
        // O filtro do fantasma não pode virar brecha: home e recents seguem fora.
        String saida = "  * Recent #1: Task{a #1 type=home A=10000:com.minimal.launcher "
                + "U=0 mode=fullscreen sz=1}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.minimal.launcher/.HomeActivity}\n"
                + "  * Recent #2: Task{c #2 type=recents A=10001:com.sec.android.app.launcher "
                + "U=0 mode=fullscreen sz=1}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.sec.android.app.launcher/.R}";

        assertTrue(TaskListParser.launchablePackages(saida).isEmpty());
    }
}