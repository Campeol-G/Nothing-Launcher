package com.minimal.launcher.apps;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;

import androidx.annotation.Nullable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Fonte unica dos apps lancaveis e da resolucao dos apps padrao.
 *
 * <p>A lista completa e cacheada porque percorre todas as activities do
 * dispositivo - caro demais para repetir a cada toque. O cache e invalidado por
 * {@link PackageChangeReceiver}, ou seja, so quando algo muda.
 */
public final class AppRepository {

    private static volatile AppRepository instance;

    private final Context appContext;
    private final PackageManager packageManager;

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
        return camera != null ? camera : resolve(new Intent(MediaStore.ACTION_STILL_IMAGE_CAMERA));
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