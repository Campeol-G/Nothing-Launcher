package com.minimal.launcher.recents;

import android.app.usage.UsageStats;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

import androidx.annotation.NonNull;

import com.minimal.launcher.apps.AppEntry;
import com.minimal.launcher.apps.AppRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Os apps usados recentemente, vindos do sistema.
 *
 * <p>A ordem vem do {@code UsageStatsManager}, e não de rastrear o que passou pelo
 * launcher. A diferença aparece no uso diário: um app aberto por notificação, por
 * link ou por outro launcher aparece aqui, e não apareceria no outro caminho — que é
 * justamente a lista que o usuário esperava ver.
 *
 * <p>O {@code UsageStatsManager} só devolve uso de qualquer app depois que o usuário
 * liga a permissão de acesso a dados de uso nas configurações. Sem isso ele lança
 * {@link SecurityException}, e isso é um estado esperado: a tela mostra como
 * conseguir a permissão, em vez de ficar vazia sem explicação.
 */
public final class RecentAppsSource {

    /** Quanto tempo para trás a tela olha. Recente é recente; mês já é arquivo. */
    private static final long WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    /** O que a tela recebe de volta, sempre na main thread. */
    public interface Callback {
        /**
         * @param entries apps em ordem de uso, mais recente primeiro. Vazio é
         *                resultado legítimo: uso limpo, ou nenhum app instalável.
         * @param permissionMissing true quando a permissão de uso não foi concedida,
         *                e a lista não diz nada sobre uso — ela só não pôde ser lida.
         */
        void onLoaded(@NonNull List<AppEntry> entries, boolean permissionMissing);
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
                    if (result != null) {
                        callback.onLoaded(result.entries, result.permissionMissing);
                    }
                });
    }

    private static final class Result {
        final List<AppEntry> entries;
        final boolean permissionMissing;

        Result(List<AppEntry> entries, boolean permissionMissing) {
            this.entries = entries;
            this.permissionMissing = permissionMissing;
        }
    }

    /**
     * Lê, ordena, dispensa e cruza. Tudo na fila, nada na main thread.
     *
     * <p>Ler os apps instalados custa meio segundo, e foi exatamente na main thread
     * que a busca travou na primeira tecla. Por isso nada aqui é feito no caminho
     * da tela.
     */
    @NonNull
    private static Result read(@NonNull Context context) {
        List<UsageStats> stats;
        try {
            UsageStatsManager usage = context.getSystemService(UsageStatsManager.class);
            if (usage == null) {
                return new Result(new ArrayList<>(), true);
            }
            long now = System.currentTimeMillis();
            stats = usage.queryUsageStats(UsageEvents.Event.MOVE_TO_FOREGROUND, now - WINDOW_MILLIS, now);
        } catch (SecurityException noPermission) {
            return new Result(new ArrayList<>(), true);
        } catch (RuntimeException e) {
            // Falha de leitura não pode virar tela branca.
            return new Result(new ArrayList<>(), false);
        }

        List<RecentRanker.Use> uses = new ArrayList<>(stats.size());
        for (UsageStats stat : stats) {
            if (stat != null && stat.getPackageName() != null && stat.getLastTimeUsed() > 0) {
                uses.add(new RecentRanker.Use(stat.getPackageName(), stat.getLastTimeUsed()));
            }
        }

        DismissedStore store = new DismissedStore(context);
        List<RecentRanker.Use> ranked = RecentRanker.rank(uses, store.snapshot());
        if (ranked.isEmpty()) {
            return new Result(new ArrayList<>(), false);
        }

        return new Result(crossWithInstalledApps(context, ranked), false);
    }

    /**
     * Troca o pacote pelo app de verdade, que sabe abrir.
     *
     * <p>O {@code UsageStatsManager} devolve só o pacote. Sem este cruzamento, a
     * linha não teria o componente achatado que {@code launch()} precisa, e o toque
     * cairia no fallback por pacote — abrindo a task errada quando o app tem várias.
     *
     * <p>Pacote que não está na lista foi desinstalado, ou não é lançável. Some sem
     * erro: uso registrado de algo que já não existe é rotina.
     */
    @NonNull
    private static List<AppEntry> crossWithInstalledApps(@NonNull Context context,
                                                         @NonNull List<RecentRanker.Use> ranked) {
        HashMap<String, AppEntry> byPackage = new HashMap<>();
        for (AppEntry installed : AppRepository.get(context).getAllApps()) {
            byPackage.put(installed.packageName, installed);
        }

        List<AppEntry> result = new ArrayList<>(ranked.size());
        for (RecentRanker.Use use : ranked) {
            AppEntry installed = byPackage.get(use.packageName);
            if (installed != null) {
                result.add(installed);
            }
        }
        return result;
    }
}