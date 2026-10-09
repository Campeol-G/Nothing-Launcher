package com.minimal.launcher.recents;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final String TYPE_KEY = "type=";
    private static final String COMPONENT_KEY = "mActivityComponent=";
    private static final String COMPONENT_FALLBACK_KEY = "cmp=";

    private TaskListParser() {
    }

    /**
     * Pacotes dos apps abertos, do mais recente para o mais antigo, um por app.
     *
     * <p>Launcher e a própria tela de recents saem fora porque chegam com
     * {@code type=home} e {@code type=recents}: são o lugar de onde a pessoa veio,
     * não um app que ela abriu. Isso é o mesmo filtro que a tela de recents do
     * sistema faz, e é o que garante que o launcher nunca apareça na própria lista.
     *
     * <p>Pacote repetido aparece uma vez só, na posição da task mais recente.
     *
     * @return lista imutável, vazia se o formato não for reconhecido
     */
    @NonNull
    static List<String> launchablePackages(@Nullable String dumpsysOutput) {
        if (dumpsysOutput == null || dumpsysOutput.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> packages = new LinkedHashSet<>();
        String type = null;
        boolean insideBlock = false;

        for (String line : dumpsysOutput.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith(BLOCK_PREFIX)) {
                // Bloco novo: o que valia era do bloco anterior.
                type = valueAfter(trimmed, TYPE_KEY);
                insideBlock = true;
                continue;
            }
            if (!insideBlock || trimmed.isEmpty() || !trimmed.startsWith("m")) {
                continue;
            }

            String packageName = packageOf(valueAfter(trimmed, COMPONENT_KEY));
            if (packageName == null) {
                // O intent traz o mesmo componente em outra notação. Serve de
                // reserva para o caso de o bloco não ter a linha de componente.
                packageName = packageOf(valueAfter(intentOf(line), COMPONENT_FALLBACK_KEY));
            }
            if (packageName == null || NON_APP_TASK_TYPES.contains(type)) {
                continue;
            }
            packages.add(packageName);
        }
        return Collections.unmodifiableList(new ArrayList<>(packages));
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

    /** O primeiro valor de {@code key} na linha, cortado no espaço. */
    @Nullable
    private static String valueAfter(@NonNull String line, @NonNull String key) {
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