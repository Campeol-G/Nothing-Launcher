package com.minimal.launcher.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fonte unica dos apps lancaveis e da resolucao dos apps padrao.
 *
 * <p>A lista completa e cacheada porque percorre todas as activities do
 * dispositivo - caro demais para repetir a cada toque. O cache e invalidado por
 * {@link PackageChangeReceiver}, ou seja, so quando algo muda.
 *
 * <p>Esse "caro demais" e da ordem de meio segundo, e a lista nunca pode ser
 * montada na main thread: foi exatamente ali que a busca travou na primeira
 * tecla. Por isso a montagem acontece numa thread propria, com uma fila de um
 * so lugar - assim a busca e a recarga nao competem entre si, e o resultado
 * volta para a main thread ja com a lista pronta.
 */
public final class AppRepository {

    private static volatile AppRepository instance;

    private final Context appContext;
    private final PackageManager packageManager;

    /** Fila unica: montar a lista e rankear acontecem ali, nunca na main thread. */
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "app-list");
        thread.setDaemon(true);
        return thread;
    });

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Nullable
    private List<AppEntry> cache;

    private AppRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.packageManager = this.appContext.getPackageManager();
    }

    public static AppRepository get(Context context) {
        AppRepository local = instance;
        if (local == null) {
            synchronized (AppRepository.class) {
                local = instance;
                if (local == null) {
                    local = new AppRepository(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public void invalidate() {
        synchronized (this) {
            cache = null;
        }
    }

    /**
     * Deixa a lista pronta antes de ela ser pedida.
     *
     * <p>Chamar na inicializacao do processo faz o trabalho acontecer enquanto a
     * tela inicial esta a vista, e nao enquanto o dedo do usuario ja esta
     * digitando.
     */
    public void preload() {
        WORKER.execute(() -> {
            try {
                getAllApps();
            } catch (RuntimeException ignored) {
                // A lista fica indisponivel e a busca mostra "nenhum app
                // encontrado". Um app sendo desinstalado no meio da leitura e a
                // causa realista, e ela nao pode derrubar o processo.
            }
        });
    }

    /**
     * Enfileira trabalho na fila única e entrega o resultado na main thread.
     *
     * <p>Existe para quem precisar da fila sem ser a busca: a tela de recentes
     * também monta lista em segundo plano, e o que ela não pode é criar uma fila
     * própria — duas filas fariam a montagem da lista de apps e a leitura de uso
     * competirem entre si, que é exatamente o que o comentário de {@link #WORKER}
     * diz para evitar.
     *
     * <p>Se {@code background} lançar, {@code onMain} não é chamado e a exceção
     * morre aqui. Uma falha de leitura não pode virar tela branca nem travar quem
     * pediu.
     */
    public void executeAsync(@NonNull Runnable background, @NonNull Runnable onMain) {
        WORKER.execute(() -> {
            try {
                background.run();
            } catch (RuntimeException ignored) {
                return;
            }
            MAIN.post(onMain);
        });
    }

    /**
     * Busca assincrona: o resultado chega na main thread.
     *
     * <p>A leitura da lista acontece dentro da tarefa, entao quem chama nunca
     * espera: nem pela lista ainda em construcao, nem pelo rankeamento.
     */
    public void searchAsync(@NonNull String query, @NonNull AppSearch.Callback callback) {
        WORKER.execute(() -> MAIN.post(() -> callback.onMatches(rankQuietly(query))));
    }

    /**
     * Rankeia sem derrubar a fila de quem espera.
     *
     * <p>Um app sendo desinstalado no meio da leitura e a causa realista de
     * falha aqui, e ela nao pode virar tela branca nem travar a digitacao.
     */
    @NonNull
    private List<AppEntry> rankQuietly(@NonNull String query) {
        try {
            return AppSearch.rank(query, getAllApps());
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /** Todos os apps com intent LAUNCHER, em ordem alfabetica, sem icones. */
    public List<AppEntry> getAllApps() {
        synchronized (this) {
            if (cache != null) {
                return cache;
            }
            List<AppEntry> result = loadAllApps();
            cache = result;
            return result;
        }
    }

    private List<AppEntry> loadAllApps() {
        List<ResolveInfo> resolved = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                0);
        List<AppEntry> result = new ArrayList<>(resolved.size());
        for (ResolveInfo info : resolved) {
            AppEntry entry = AppEntry.fromActivityInfo(appContext, info);
            if (entry != null) {
                result.add(entry);
            }
        }
        Collections.sort(result, byLabel());
        return Collections.unmodifiableList(result);
    }

    private static Comparator<AppEntry> byLabel() {
        // Collator ordena acentos e caixa como o usuario espera, ao contrario de
        // String.compareToIgnoreCase.
        final Collator collator = Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.SECONDARY);
        return (left, right) -> {
            int result = collator.compare(left.label, right.label);
            if (result != 0) {
                return result;
            }
            result = collator.compare(left.packageName, right.packageName);
            return result != 0 ? result : left.component.compareTo(right.component);
        };
    }

    /** Componente achatado do app de Configuracoes, ou null. */
    @Nullable
    public String resolveSettingsApp() {
        return resolve(new Intent(Settings.ACTION_SETTINGS));
    }

    /** Componente achatado de um app de camera, ou null. */
    @Nullable
    public String resolveCameraApp() {
        String camera = resolve(new Intent(MediaStore.ACTION_IMAGE_CAPTURE));
        return camera != null ? camera : resolve(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
    }

    /** Componente achatado do discador, ou null. */
    @Nullable
    public String resolveDialerApp() {
        return resolve(new Intent(Intent.ACTION_DIAL));
    }

    /**
     * Resolve por intent implicito. Intent sem handler devolve null; quem chama
     * decide o que fazer em vez de inventar um pacote fixo.
     */
    @Nullable
    private String resolve(Intent intent) {
        ResolveInfo info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        ActivityInfo activityInfo = info != null ? info.activityInfo : null;
        if (activityInfo == null) {
            return null;
        }
        return new ComponentName(activityInfo.packageName, activityInfo.name).flattenToString();
    }

    /** Existe um desinstalador para este pacote? Usado para esconder o menu. */
    public boolean canUninstall(String packageName) {
        Intent intent = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null));
        return intent.resolveActivity(packageManager) != null;
    }
}