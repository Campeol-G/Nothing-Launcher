package com.minimal.launcher.recents;

import android.content.Context;

import androidx.annotation.NonNull;

import com.minimal.launcher.apps.AppEntry;
import com.minimal.launcher.apps.AppRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Os apps abertos agora, vindos do sistema.
 *
 * <p>Não existe API pública para um app de terceiro ler a lista de tasks, então a
 * leitura passa pelo Shizuku, que roda {@code dumpsys activity recents} com a
 * identidade do shell. Sem Shizuku a tela não tem o que mostrar, e diz isso em vez
 * de fingir que a lista está vazia.
 *
 * <p>A ordem é a do sistema, e é a ordem que a pessoa espera: é a tela de recents
 * do Android sem os cards e sem as miniaturas.
 */
public final class RecentAppsSource {

    /** O que a tela recebe de volta, sempre na main thread. */
    public interface Callback {
        /**
         * @param entries apps na ordem de uso, mais recente primeiro.
         * @param shizukuMissing true quando não deu para ler a lista de tasks.
         *                "Vazia" e "não deu para saber" são coisas diferentes: a
         *                primeira é informação, a segunda é falta de permissão.
         */
        void onLoaded(@NonNull List<AppEntry> entries, boolean shizukuMissing);
    }

    /** Lê a saída de {@code dumpsys activity recents}._exists para testar. */
    public interface TaskLister {
        /** @return a saída bruta do comando, ou null se não deu para ler. */
        String readTasks();
    }

    private RecentAppsSource() {
    }

    public static void loadAsync(@NonNull Context context, @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        // executeAsync dá o trabalho em segundo plano e a entrega na main thread.
        // O resultado atravessa os dois pelo holder, porque a assinatura não leva
        // dado de um Runnable para o outro.
        final Result[] holder = new Result[1];
        AppRepository.get(appContext).executeAsync(
                () -> holder[0] = read(appContext),
                () -> {
                    Result result = holder[0];
                    if (result == null) {
                        // A fila falhou antes de read() devolver. Sem isto a tela
                        // ficaria sem lista e sem nenhuma explicação, que é o
                        // resultado que o resto deste arquivo existe para evitar.
                        callback.onLoaded(Collections.emptyList(), true);
                        return;
                    }
                    callback.onLoaded(result.entries, result.shizukuMissing);
                });
    }

    private static final class Result {
        final List<AppEntry> entries;
        final boolean shizukuMissing;

        Result(List<AppEntry> entries, boolean shizukuMissing) {
            this.entries = entries;
            this.shizukuMissing = shizukuMissing;
        }
    }

    /**
     * Lê, interpreta e cruza. Tudo na fila, nada na main thread.
     *
     * <p>Rodar o comando e varrer a saída custa tempo, e foi exatamente na main
     * thread que a busca travou na primeira tecla. Por isso nada aqui acontece no
     * caminho da tela.
     */
    @NonNull
    private static Result read(@NonNull Context context) {
        String output;
        try {
            output = new ShizukuTaskLister().readTasks();
        } catch (RuntimeException e) {
            // Binder morto, Shizuku parado, ROM sem o comando: estado esperado.
            return new Result(Collections.emptyList(), true);
        }
        if (output == null) {
            return new Result(Collections.emptyList(), true);
        }
        return new Result(crossWithInstalledApps(context, output), false);
    }

    /**
     * Pacotes das tasks → apps de verdade, com o rótulo que o Android mostra.
     *
     * <p>O {@code dumpsys} devolve o pacote, não o rótulo. Sem este cruzamento a
     * linha não teria nome e o toque não teria o componente que sabe abrir o app.
     *
     * <p>Pacote fora da lista de apps foi desinstalado, ou não é lançável. Some sem
     * erro.
     */
    @NonNull
    private static List<AppEntry> crossWithInstalledApps(@NonNull Context context,
                                                         @NonNull String dumpsysOutput) {
        TaskListParser.Tasks tasks = TaskListParser.recentTasks(dumpsysOutput);
        List<String> packages = RecentRanker.apply(
                tasks.packages,
                new DismissedStore(context).snapshot(),
                tasks.lastActiveAt);

        HashMap<String, AppEntry> byPackage = new HashMap<>();
        for (AppEntry installed : AppRepository.get(context).getAllApps()) {
            // putIfAbsent: a lista de apps vem ordenada por rótulo, pacote e
            // componente, e "último vence" deixaria o componente de nome maior. O
            // primeiro é determinístico e não depende do rótulo do app.
            byPackage.putIfAbsent(installed.packageName, installed);
        }

        List<AppEntry> result = new ArrayList<>(packages.size());
        for (String packageName : packages) {
            AppEntry installed = byPackage.get(packageName);
            if (installed != null) {
                result.add(installed);
            }
        }
        return result;
    }
}