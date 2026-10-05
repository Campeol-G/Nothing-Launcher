package com.minimal.launcher;

import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;

import com.minimal.launcher.apps.AppRepository;
import com.minimal.launcher.apps.FavoritesStore;
import com.minimal.launcher.apps.PackageChangeReceiver;

/**
 * Ponto unico de inicializacao: cache de apps, favoritos da primeira execucao e
 * o unico receptor de broadcasts de pacote do processo.
 */
public final class LauncherApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppRepository.get(this);
        new FavoritesStore(this).ensureSeeded();
        registerPackageWatcher(this);
    }

    /**
     * Sem este receptor, um app desinstalado ficaria na lista de favoritos para
     * sempre. Registrado aqui, e nao por activity, para nao depender de qual tela
     * esta aberta.
     */
    private static void registerPackageWatcher(Context context) {
        IntentFilter filter = PackageChangeReceiver.filter();
        PackageChangeReceiver receiver = new PackageChangeReceiver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }
}