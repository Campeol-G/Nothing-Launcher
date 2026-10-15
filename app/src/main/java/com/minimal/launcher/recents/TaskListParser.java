package com.minimal.launcher.recents;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A lista de apps abertos agora, tirada da saída de {@code dumpsys activity recents}.
 *
 * <p>É a mesma lista que o Android mostra nos seus recents, e chega pelo mesmo
 * caminho: o Shizuku roda o comando com a identidade do shell e devolve o texto.
 * Não existe API pública para um app de terceiro ler as tasks, e por isso a tela
 * só funciona com o Shizuku ligado.
 *
 * <p>Por que texto e não uma chamada de binder: {@code dumpsys} é impresso pela
 * própria {@code ActivityTaskManager}, é o mesmo dado que enche a tela de recents
 * do sistema, e não exige nenhuma API escondida. O preço é depender do formato de
 * uma saída humana — por isso a classe é pura, testada com um recorte real, e
 * devolve lista vazia em vez de estourar quando o formato muda.
 *
 * <p>O formato que esta classe entende, medido num Android 13:
 * <pre>
 *   * Recent #0: Task{... type=standard A=10333:com.discord U=0 ...}
 *       mActivityComponent=com.discord/.main.MainActivity
 * </pre>
 *
 * <p>A ordem dos blocos já é da task mais recente para a mais antiga, então não há
 * nada para ordenar aqui: o que o sistema devolve é a ordem que a pessoa espera.
 */
final class TaskListParser {

    /** O que o sistema chama de task que não é app aberto: o launcher e os recents. */
    private static final Set<String> NON_APP_TASK_TYPES = Set.of("home", "recents");

    private static final String BLOCK_PREFIX = "* Recent #";
    /** Launcher de qualquer papel: não é app que a pessoa abriu. */
    private static final String OUR_PACKAGE = "com.minimal.launcher";
    private static final String TYPE_KEY = "type=";
    private static final String COMPONENT_KEY = "mActivityComponent=";
    private static final String COMPONENT_FALLBACK_KEY = "cmp=";
    private static final String LAST_ACTIVE_KEY = "lastActiveTime=";

    /**
     * Quantas activities a task tem. Zero = task fantasma de app encerrado.
     *
     * <p>O {@code sz} é o último campo da linha, e a linha fecha com uma chave:
     * {@code ... mode=fullscreen sz=0}. Por isso o valor precisa perder a chave antes
     * de virar número — do contrário {@code "0}"} não é número nenhum e a task
     * fantasma passa, que é justamente o que o filtro existe para impedir.
     */
    private static final String SIZE_KEY = "sz=";

    private TaskListParser() {
    }

    /**
     * Pacotes dos apps abertos, do mais recente para o mais antigo, um por app.
     *
     * <p>Launcher e a própria tela de recents saem fora porque chegam com
     * {@code type=home} e {@code type=recents}: são o lugar de onde a pessoa veio,
     * não um app que ela abriu. É o mesmo filtro que a tela de recents do sistema
     * faz.
     *
     * <p>Um detalhe que o filtro por tipo não pega, e que só aparece no aparelho:
     * o launcher também tem uma task {@code type=standard} própria, separada da
     * {@code type=home} — a task do processo do app, que o sistema abre como
     * qualquer outro quando o launcher está em primeiro plano. Sem a exclusão pelo
     * pacote, o launcher aparecia na própria lista, na primeira linha.
     *
     * <p>Pacote repetido aparece uma vez só, na posição da task mais recente.
     *
     * @return lista imutável, vazia se o formato não for reconhecido
     */
    @NonNull
    static List<String> launchablePackages(@Nullable String dumpsysOutput) {
        return recentTasks(dumpsysOutput).packages;
    }

    /** Uma task do sistema: o pacote e quando ele ficou ativo por último. */
    static final class Task {
        final String packageName;
        final long lastActiveTime;

        Task(@NonNull String packageName, long lastActiveTime) {
            this.packageName = packageName;
            this.lastActiveTime = lastActiveTime;
        }
    }

    /** O que o parser devolve: a lista na ordem do sistema, e o instante de cada uma. */
    static final class Tasks {
        final List<String> packages;
        final Map<String, Long> lastActiveAt;

        Tasks(@NonNull List<String> packages, @NonNull Map<String, Long> lastActiveAt) {
            this.packages = Collections.unmodifiableList(packages);
            this.lastActiveAt = Collections.unmodifiableMap(lastActiveAt);
        }
    }

    /**
     * As tasks do sistema, com o instante de cada uma.
     *
     * <p>O instante é o {@code lastActiveTime} do dumpsys, e é ele que diz à tela se
     * uma dispensa ainda vale: a task é a mesma antes e depois de o app ser
     * reaberto, e só o instante muda.
     */
    @NonNull
    static Tasks recentTasks(@Nullable String dumpsysOutput) {
        if (dumpsysOutput == null || dumpsysOutput.isEmpty()) {
            return new Tasks(Collections.emptyList(), Collections.emptyMap());
        }

        List<String> packages = new ArrayList<>();
        Map<String, Long> lastActiveAt = new HashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        String type = null;
        String packageName = null;
        long lastActive = 0L;
        // -1 = o dumpsys nao disse. Vira "nao descartar" no flush.
        int activities = -1;
        boolean insideBlock = false;

        for (String line : dumpsysOutput.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith(BLOCK_PREFIX)) {
                // Bloco novo: o que valia era do bloco anterior.
                flush(seen, packages, lastActiveAt, type, packageName, lastActive,
                        activities);
                type = valueAfter(trimmed, TYPE_KEY);
                packageName = null;
                lastActive = 0L;
                activities = -1;
                // `sz=` fica no fim da linha do Recent #N. So nesta linha ele conta:
                // uma linha de detalhe que comece com sz nao descreve a task.
                String tamanho = valueAfter(trimmed, SIZE_KEY);
                if (tamanho != null) {
                    try {
                        activities = Integer.parseInt(trimBrace(tamanho));
                    } catch (NumberFormatException ignored) {
                        // Valor ilegível: o campo existe mas nao se entende. Melhor
                        // mostrar o app do que esconde-lo por duvida de parse.
                        activities = -1;
                    }
                }
                insideBlock = true;
                continue;
            }
            if (!insideBlock || trimmed.isEmpty()) {
                continue;
            }
            // Só duas linhas importam aqui: a do componente e a do intent. O
            // filtro é por conteúdo e não por primeira letra: a linha do intent
            // começa com "i", e um beginsWith("m") a descartava antes do fallback
            // ter chance de rodar.
            if (trimmed.contains(LAST_ACTIVE_KEY)) {
                lastActive = parseMillis(valueAfter(trimmed, LAST_ACTIVE_KEY));
                continue;
            }

            boolean componentLine = trimmed.contains(COMPONENT_KEY);
            boolean intentLine = trimmed.startsWith("intent=");
            if (!componentLine && !intentLine) {
                continue;
            }

            String found = packageOf(valueAfter(trimmed, COMPONENT_KEY));
            if (found == null) {
                // O intent traz o mesmo componente em outra notação. Serve de
                // reserva para o caso de o bloco não ter a linha de componente.
                found = packageOf(valueAfter(intentOf(line), COMPONENT_FALLBACK_KEY));
            }
            if (found != null) {
                packageName = found;
            }
        }
        flush(seen, packages, lastActiveAt, type, packageName, lastActive, activities);
        return new Tasks(packages, lastActiveAt);
    }

    /** O dumpsys escreve {@code 208466837 (inactive for 0s)}: só o número importa. */
    private static long parseMillis(@Nullable String raw) {
        if (raw == null) {
            return 0L;
        }
        int paren = raw.indexOf(' ');
        String digits = paren < 0 ? raw : raw.substring(0, paren);
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ignored) {
            // Instante ilegível: 0, que trata a dispensa como mais nova que a task
            // e mantém o app escondido. Errar para esconde é melhor que errar para
            // mostrar um app que a pessoa mandou tirar.
            return 0L;
        }
    }

    /** Fecha o bloco atual, se ele for um app. */
    private static void flush(@NonNull Set<String> seen,
                              @NonNull List<String> packages,
                              @NonNull Map<String, Long> lastActiveAt,
                              @Nullable String type,
                              @Nullable String packageName,
                              long lastActive,
                              int activities) {
        if (packageName == null
                || NON_APP_TASK_TYPES.contains(type)
                || OUR_PACKAGE.equals(packageName)
                // Task fantasma: o dumpsys continua listando a task do app depois
                // que ele foi encerrado, mas com sz=0 — nenhuma activity dentro.
                //
                // Sem esta linha, o app que a pessoa fechou voltava à lista na
                // leitura seguinte. Era o bug mais visível da tela: fechar, sair,
                // voltar, e o app lá estava de novo. `sz` ausente não descarta
                // ninguém: root antigo que não escreve o campo continua funcionando
                // como antes.
                || activities == 0
                || !seen.add(packageName)) {
            return;
        }
        packages.add(packageName);
        lastActiveAt.put(packageName, lastActive);
    }

    /**
     * Tira a chave que fecha a linha.
     *
     * <p>O último campo da linha do Recent #N vem colado nela: {@code sz=0}. Sem
     * isso o valor seria {@code "0}"}, que não é número, e a task fantasma — que o
     * filtro existe justamente para descartar — passava como se fosse app aberto.
     */
    @NonNull
    private static String trimBrace(@NonNull String valor) {
        int fim = valor.length();
        while (fim > 0) {
            char c = valor.charAt(fim - 1);
            if (c == '}' || c == ')' || c == ',') {
                fim--;
                continue;
            }
            break;
        }
        return valor.substring(0, fim);
    }

    /** O trecho entre chaves da linha {@code intent={...}}, ou null se não houver. */
    @Nullable
    private static String intentOf(@NonNull String line) {
        int open = line.indexOf("intent={");
        if (open < 0) {
            return null;
        }
        int close = line.indexOf('}', open);
        return close < 0 ? null : line.substring(open, close);
    }

    /**
     * O primeiro valor de {@code key} na linha, cortado no espaço.
     *
     * <p>Tolerante a linha nula porque o fallback do componente vem de um bloco
     * sem linha de intent — e uma task qualquer tem linhas como
     * {@code mCallingPackage=}, que começam com "m" e não têm componente nem
     * intent. Sem esta guarda, uma task real derrubava a tela inteira.
     */
    @Nullable
    private static String valueAfter(@Nullable String line, @NonNull String key) {
        if (line == null) {
            return null;
        }
        int at = line.indexOf(key);
        if (at < 0) {
            return null;
        }
        int from = at + key.length();
        int to = line.indexOf(' ', from);
        return to < 0 ? line.substring(from) : line.substring(from, to);
    }

    /** {@code com.discord/.main.MainActivity} → {@code com.discord}. */
    @Nullable
    private static String packageOf(@Nullable String component) {
        if (component == null || component.isEmpty()) {
            return null;
        }
        int slash = component.indexOf('/');
        return slash < 0 ? component : component.substring(0, slash);
    }
}