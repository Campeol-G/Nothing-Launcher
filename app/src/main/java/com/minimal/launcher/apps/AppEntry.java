package com.minimal.launcher.apps;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * App lançável, reduzido ao mínimo: pacote, nome exibido e componente achatado.
 * O componente é a identidade estável usada na persistência dos favoritos e no
 * reordenamento — o rótulo pode mudar ou duplicar, o componente não.
 */
public final class AppEntry {

    public final String packageName;
    public final String label;
    /** Componente achatado, no formato {@link ComponentName#flattenToString()}. */
    public final String component;

    public AppEntry(@NonNull String packageName, @NonNull String label, @NonNull String component) {
        this.packageName = packageName;
        this.label = label;
        this.component = component;
    }

    /** Reconstrói a entrada a partir do componente persistido. Retorna null se o app sumiu. */
    @Nullable
    public static AppEntry fromComponent(@NonNull Context context, @NonNull String flattened) {
        PackageManager pm = context.getPackageManager();
        ComponentName name = ComponentName.unflattenFromString(flattened);
        if (name == null) {
            return null;
        }
        try {
            return new AppEntry(
                    name.getPackageName(),
                    pm.getActivityInfo(name, 0).loadLabel(pm).toString(),
                    flattened);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Nullable
    public static AppEntry fromActivityInfo(@NonNull Context context,
                                            @NonNull android.content.pm.ResolveInfo info) {
        PackageManager pm = context.getPackageManager();
        String label;
        try {
            label = info.loadLabel(pm).toString();
        } catch (Exception e) {
            label = info.activityInfo.applicationInfo.loadLabel(pm).toString();
        }
        return new AppEntry(
                info.activityInfo.packageName,
                label,
                new ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString());
    }

    /**
     * Abre o app. Sem ícone, sem animação, sempre em task nova.
     *
     * <p>Preferimos a activity exata que o usuário favoritou; se ela não existir
     * mais, caímos no app inteiro em vez de não fazer nada.
     */
    public void launch(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        ComponentName name = ComponentName.unflattenFromString(component);

        if (name != null) {
            Intent exact = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(name);
            exact.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (start(context, exact)) {
                return;
            }
        }

        Intent fallback = pm.getLaunchIntentForPackage(packageName);
        if (fallback == null) {
            fallback = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setPackage(packageName);
        }
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(context, fallback);
    }

    private static boolean start(@NonNull Context context, @NonNull Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException ignored) {
            // App removido entre a listagem e o toque: silencioso, como esperado.
            return false;
        }
    }

    /** Abre a tela de detalhes do app nas Configurações do sistema. */
    public void showAppInfo(@NonNull Context context) {
        try {
            context.startActivity(new Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** Abre o desinstalador. Retorna false se o sistema não oferecer um. */
    public boolean uninstall(@NonNull Context context) {
        Intent intent = new Intent(
                Intent.ACTION_DELETE,
                android.net.Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AppEntry)) {
            return false;
        }
        return component.equals(((AppEntry) other).component);
    }

    @Override
    public int hashCode() {
        return component.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
        return label;
    }
}