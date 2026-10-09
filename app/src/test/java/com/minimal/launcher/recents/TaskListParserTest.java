package com.minimal.launcher.recents;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * O parser da saída de {@code dumpsys activity recents}.
 *
 * <p>É a única parte da tela que depende de texto que o Android imprime e que muda
 * de ROM para ROM. Por isso ela é pura e testada: um formatinho diferente tem que
 * falhar num teste, não numa lista vazia na frente do usuário.
 *
 * <p>O recorte abaixo é a saída real de um Samsung com Android 13, podada. O que
 * importa aqui: a ordem dos blocos já é da mais recente para a mais antiga, o
 * {@code type=} diz o que a task é, e o componente está em {@code mActivityComponent}.
 */
public class TaskListParserTest {

    /** Recorte real: 6 tasks, sendo 3 do tipo que não devem entrar na lista. */
    private static final String SAMPLE =
            "ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)\n"
            + "  * Recent #0: Task{b2f9d36 #565 type=standard A=10333:com.discord U=0"
            + " visible=false mode=fullscreen translucent=true sz=1}\n"
            + "    userId=0 effectiveUid=u0a333\n"
            + "    intent={act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]"
            + " flg=0x10200000 cmp=com.discord/.main.MainActivity}\n"
            + "    mActivityComponent=com.discord/.main.MainActivity\n"
            + "    lastActiveTime=207714025 (inactive for 753s)\n"
            + "  * Recent #1: Task{aca64ed #561 type=home A=10279:com.minimal.launcher U=0"
            + " rootTaskId=1 visible=false mode=fullscreen translucent=true sz=3}\n"
            + "    intent={act=android.intent.action.MAIN cat=[android.intent.category.HOME]"
            + " flg=0x10800100 cmp=com.minimal.launcher/.HomeActivity}\n"
            + "    mActivityComponent=com.minimal.launcher/.HomeActivity\n"
            + "    lastActiveTime=208342756 (inactive for 124s)\n"
            + "  * Recent #2: Task{2349f41 #353 type=home A=10124:com.sec.android.app.launcher"
            + " U=0 rootTaskId=1 visible=false mode=fullscreen translucent=true sz=1}\n"
            + "    mActivityComponent=com.sec.android.app.launcher/com.android.launcher3.uioverrides"
            + ".QuickstepLauncher\n"
            + "    lastActiveTime=203971041 (inactive for 4495s)\n"
            + "  * Recent #3: Task{9875ec0 #385 type=recents I=com.sec.android.app.launcher"
            + "/com.android.quickstep.RecentsActivity U=0 rootTaskId=384 visible=false}\n"
            + "    mActivityComponent=com.sec.android.app.launcher/com.android.quickstep.RecentsActivity\n"
            + "  * Recent #4: Task{583be02 #563 type=standard A=10375:org.chromium.chrome U=0"
            + " visible=false mode=fullscreen translucent=true sz=2}\n"
            + "    mActivityComponent=org.chromium.chrome/org.chromium.chrome.tabs.TabbedActivity\n"
            + "  * Recent #5: Task{1a2b3c4 #570 type=standard A=10376:com.android.chrome U=0"
            + " visible=false mode=fullscreen translucent=true sz=1}\n"
            + "    mActivityComponent=com.android.chrome/com.google.android.apps.chrome.Main\n"
            + "  * Recent #6: Task{9f8e7d #571 type=standard A=10377:com.discord U=0"
            + " visible=false mode=fullscreen translucent=true sz=1}\n"
            + "    mActivityComponent=com.discord/.main.MainActivity\n";

    @Test public void devolveOsPacotesNaOrdemDoSistema() {
        assertEquals(Arrays.asList("com.discord", "org.chromium.chrome", "com.android.chrome"),
                TaskListParser.launchablePackages(SAMPLE));
    }

    @Test public void descartaTaskDeHome() {
        // O launcher Samsung, o nosso e o FallbackHome entram com type=home. E a
        // tela de recents do sistema entra com type=recents. Nenhum deles e um app
        // que a pessoa abriu, entao nenhum deve aparecer na lista.
        for (String packageName : TaskListParser.launchablePackages(SAMPLE)) {
            if (packageName.startsWith("com.sec.android.app.launcher")
                    || packageName.equals("com.minimal.launcher")
                    || packageName.equals("com.android.settings")) {
                throw new AssertionError("task de home na lista: " + packageName);
            }
        }
    }

    @Test public void pacoteComVariasTasksApareceUmaVez() {
        // O mesmo pacote em duas tasks é o mesmo app para a pessoa: uma linha só,
        // na posição da task mais recente, que é a primeira do recorte.
        assertEquals(1, Collections.frequency(
                TaskListParser.launchablePackages(SAMPLE), "com.discord"));
    }

    @Test public void pacoteComDuasActivitiesDiferentesApareceUmaVez() {
        // Chrome e Chrome sao pacotes diferentes mesmo com activities parecidas.
        assertEquals(Arrays.asList("org.chromium.chrome", "com.android.chrome"),
                TaskListParser.launchablePackages(SAMPLE).subList(1, 3));
    }

    @Test public void saidaVaziaNaoQuebra() {
        assertEquals(Collections.emptyList(), TaskListParser.launchablePackages(""));
        assertEquals(Collections.emptyList(),
                TaskListParser.launchablePackages("ACTIVITY MANAGER ACTIVITIES\n"));
    }

    @Test public void formatoDesconhecidoNaoQuebra() {
        // Nenhuma linha de Recent: o parser devolve vazio e a tela avisa, em vez
        // de devolver a lista inteira nem de estourar.
        assertEquals(Collections.emptyList(), TaskListParser.launchablePackages("texto sem nada útil"));
    }

    @Test public void componenteVazioNoBlocoESqueceOBlooco() {
        String semComponente = "  * Recent #0: Task{a #1 type=standard A=1:com.x U=0}\n"
                + "    lastActiveTime=1\n"
                + "  * Recent #1: Task{b #2 type=standard A=2:com.y U=0}\n"
                + "    mActivityComponent=com.y/.Main\n";
        assertEquals(Collections.singletonList("com.y"),
                TaskListParser.launchablePackages(semComponente));
    }

    @Test public void launcherEmTaskStandardNaoEntra() {
        // Reproduz o aparelho: o launcher tem DUAS tasks. Uma e type=home, que o
        // filtro por tipo pega. A outra e type=standard — a task do processo do
        // app, que o sistema abre como qualquer outro quando o launcher esta em
        // primeiro plano. Sem excluir pelo pacote, ela aparecia na primeira linha.
        String comLauncher = "  * Recent #0: Task{a #1 type=standard A=10279:com.minimal.launcher"
                + " U=0 visible=true}\n"
                + "    mActivityComponent=com.minimal.launcher/.HomeActivity\n"
                + "  * Recent #1: Task{b #2 type=standard A=10350:com.discord U=0}\n"
                + "    mActivityComponent=com.discord/.main.MainActivity\n"
                + "  * Recent #2: Task{c #3 type=home A=10280:com.sec.android.app.launcher U=0}\n"
                + "    mActivityComponent=com.sec.android.app.launcher/.Home\n";
        assertEquals(Collections.singletonList("com.discord"),
                TaskListParser.launchablePackages(comLauncher));
    }

    @Test public void taskSemComponenteComLinhasQueComecamComM() {
        // Reproduz o crash do aparelho: uma task real tem linhas como
        // "mCallingPackage=", que comecam com "m" e nao tem componente nem
        // intent. O fallback chamava valueAfter com null e derrubava a tela
        // inteira com NPE -- a lista sumia sem nenhuma explicacao.
        String realista = "  * Recent #0: Task{a #1 type=standard A=1:com.x U=0}\n"
                + "    userId=0 effectiveUid=u0a1 mCallingUid=2000 mCallingPackage=com.android.shell\n"
                + "    affinity=1:com.x\n"
                + "  * Recent #1: Task{b #2 type=standard A=2:com.y U=0}\n"
                + "    mActivityComponent=com.y/.Main\n"
                + "  * Recent #2: Task{c #3 type=home A=3:com.z U=0}\n"
                + "    mActivityComponent=com.z/.Home\n";
        assertEquals(Collections.singletonList("com.y"),
                TaskListParser.launchablePackages(realista));
    }

    @Test public void blocoSemComponenteMasComIntentAindaEntra() {
        // O fallback pelo intent tem que continuar funcionando: nem toda task
        // traz mActivityComponent, e o intent traz o mesmo componente.
        String soIntent = "  * Recent #0: Task{a #1 type=standard A=1:com.x U=0}\n"
                + "    intent={act=android.intent.action.MAIN cmp=com.x/.Main flg=0x10200000}\n";
        assertEquals(Collections.singletonList("com.x"),
                TaskListParser.launchablePackages(soIntent));
    }
}
