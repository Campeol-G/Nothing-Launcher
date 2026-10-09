package com.minimal.launcher.apps;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Mantem a lista de apps e os favoritos coerentes com o que esta instalado.
 *
 * <p>Registrado uma vez, pelo {@link com.minimal.launcher.LauncherApp}. Sem ele,
 * um app desinstalado continuaria na lista dos favoritos para sempre.
 */
public final class PackageChangeReceiver extends BroadcastReceiver {

    private static volatile PackageChangeReceiver instance;

    /** Filtro com os eventos que realmente alteram a lista. */
    public static IntentFilter filter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        return filter;
    }

    @Nullable
    public static PackageChangeReceiver get() {
        return instance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        instance = this;

        AppRepository repository = AppRepository.get(context);
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
            // Só o rótulo ou a atividade habilitada mudou: o cache ainda vale,
            // mas convém reler os favoritos porque um app pode ter saído do ar.
            repository.invalidate();
            repository.preload();
            pruneFavorites(context);
            return;
        }

        repository.invalidate();
        // Recarrega em segundo plano: quem instalou ou desinstalou um app espera
        // ate a proxima busca, e nao ate a proxima tecla.
        repository.preload();

        boolean removed = Intent.ACTION_PACKAGE_REMOVED.equals(action)
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        if (removed) {
            String packageName = intent.getData() != null
                    ? intent.getData().getSchemeSpecificPart()
                    : null;
            if (packageName != null) {
                new FavoritesStore(context).removePackage(packageName);
            }
        }
    }

    /** getFavorites() reescreve a persistencia sem os componentes que sumiram. */
    private static void pruneFavorites(@NonNull Context context) {
        new FavoritesStore(context).getFavorites();
    }
}