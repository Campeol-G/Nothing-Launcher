package com.minimal.launcher.recents;

import android.app.usage.UsageStats;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Build;
import android.os.Process;

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
 * <p>A permissão de acesso a dados de uso é um app-op, não uma permissão de runtime:
 * não existe diálogo, o usuário liga em Configurações. E o sistema não avisa que
 * faltou — sem a permissão, {@code queryUsageStats} devolve lista vazia e segue
 * vivo. Por isso a checagem é feita antes, e é explícita: sem ela a tela ficaria
 * vazia sem nenhuma explicação, que é o pior resultado possível.
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
     * O app-op que decide se a tela pode ler uso de apps.
     *
     * <p>Este é o valor de {@code AppOpsManager.OPSTR_GET_USAGE_STATS}. A constante
     * é pública nesta API, mas o nome do app-op é mais estável que qualquer
     * constante: ele é o que fica no {@code dumpsys}, e é assim que se conferiu
     * que a permissão estava negada no aparelho.
     */
    private static final String OPSTR_USAGE_STATS = "android:get_usage_stats";

    /**
     * O usuário deu acesso a dados de uso?
     *
     * <p>Só {@code MODE_ALLOWED} conta. Modo {@code default} é "ninguém se
     * manifestou", que na prática é o mesmo que não ter — e a tela precisa dizer
     * como conseguir, não ficar vazia.
     */
    private static boolean hasUsagePermission(@NonNull Context context) {
        try {
            AppOpsManager ops = context.getSystemService(AppOpsManager.class);
            if (ops == null) {
                return false;
            }
            int mode;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mode = ops.unsafeCheckOpNoThrow(
                        OPSTR_USAGE_STATS, Process.myUid(), context.getPackageName());
            } else {
                // Antes do Q só existe checkOpNoThrow. A diferença é que ele pode
                // lançar, mas é o mesmo app-op e a mesma resposta.
                mode = ops.checkOpNoThrow(
                        OPSTR_USAGE_STATS, Process.myUid(), context.getPackageName());
            }
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            // Sem serviço de app-ops não há como saber: tratar como não concedido
            // e a tela explicar é melhor do que fingir que a lista está vazia.
            return false;
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
        if (!hasUsagePermission(context)) {
            return new Result(new ArrayList<>(), true);
        }

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