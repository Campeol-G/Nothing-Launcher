package com.minimal.launcher;

import android.app.Application;
import android.content.Context;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

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
        // Broadcasts do sistema: precisam de RECEIVER_EXPORTED, senão Android 13+
        // simplesmente não entrega. ContextCompat cuida da diferença de API.
        ContextCompat.registerReceiver(
                context,
                new PackageChangeReceiver(),
                PackageChangeReceiver.filter(),
                ContextCompat.RECEIVER_EXPORTED);
    }
}