package com.minimal.launcher.apps;

import android.content.Context;
import android.content.SharedPreferences;

import com.minimal.launcher.Prefs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Ordem dos favoritos.
 *
 * <p>Guarda tudo em uma única string separada por {@code \n} dentro do
 * {@link SharedPreferences}: {@code StringSet} é um conjunto sem ordem, e ordem
 * é requisito aqui.
 */
public final class FavoritesStore {

    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_SEEDED = "favorites_seeded";
    private static final String SEPARATOR = "\n";

    private final Context appContext;
    private final SharedPreferences prefs;
    private final AppRepository repository;

    public FavoritesStore(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = this.appContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        this.repository = AppRepository.get(this.appContext);
    }

    /**
     * Favoritos legíveis, na ordem salva. Componentes que não existem mais são
     * removidos da persistência na leitura, para a lista não crecer sozinha.
     */
    public List<AppEntry> getFavorites() {
        List<String> raw = readRaw();
        List<AppEntry> result = new ArrayList<>(raw.size());
        List<String> survivors = new ArrayList<>(raw.size());
        for (String flattened : raw) {
            AppEntry entry = AppEntry.fromComponent(appContext, flattened);
            if (entry != null) {
                result.add(entry);
                survivors.add(flattened);
            }
        }
        if (survivors.size() != raw.size()) {
            writeRaw(survivors);
        }
        return result;
    }

    /** Só os componentes, sem tocar no PackageManager. Usado por isFavorite/remove. */
    public List<String> getFavoriteComponents() {
        return Collections.unmodifiableList(readRaw());
    }

    public boolean isFavorite(String component) {
        return readRaw().contains(component);
    }

    public void add(AppEntry entry) {
        List<String> raw = readRaw();
        if (raw.contains(entry.component)) {
            return;
        }
        raw.add(entry.component);
        writeRaw(raw);
    }

    public void remove(String component) {
        List<String> raw = readRaw();
        if (raw.remove(component)) {
            writeRaw(raw);
        }
    }

    public void save(List<AppEntry> entries) {
        List<String> raw = new ArrayList<>(entries.size());
        for (AppEntry entry : entries) {
            if (!raw.contains(entry.component)) {
                raw.add(entry.component);
            }
        }
        writeRaw(raw);
    }

    public void move(int from, int to) {
        List<String> raw = readRaw();
        if (from < 0 || from >= raw.size() || to < 0 || to >= raw.size() || from == to) {
            return;
        }
        raw.add(to, raw.remove(from));
        writeRaw(raw);
    }

    /** Some com todos os favoritos de um pacote. Usado quando ele e desinstalado. */
    public void removePackage(String packageName) {
        String prefix = packageName + "/";
        List<String> raw = readRaw();
        List<String> survivors = new ArrayList<>(raw.size());
        for (String component : raw) {
            if (!component.startsWith(prefix)) {
                survivors.add(component);
            }
        }
        if (survivors.size() != raw.size()) {
            writeRaw(survivors);
        }
    }

    /** Roda uma única vez, na primeira execução: Configurações, Câmera e Telefone. */
    public void ensureSeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            return;
        }
        prefs.edit().putBoolean(KEY_SEEDED, true).apply();

        List<String> seeds = new ArrayList<>(3);
        addIfResolvable(seeds, repository.resolveSettingsApp());
        addIfResolvable(seeds, repository.resolveCameraApp());
        addIfResolvable(seeds, repository.resolveDialerApp());

        if (!seeds.isEmpty()) {
            writeRaw(seeds);
        }
    }

    private void addIfResolvable(List<String> seeds, String component) {
        if (component != null && !seeds.contains(component)
                && AppEntry.fromComponent(appContext, component) != null) {
            seeds.add(component);
        }
    }

    private List<String> readRaw() {
        String joined = prefs.getString(KEY_FAVORITES, null);
        if (joined == null || joined.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> raw = new ArrayList<>(Arrays.asList(joined.split(SEPARATOR, -1)));
        raw.remove("");
        return raw;
    }

    private void writeRaw(List<String> raw) {
        prefs.edit().putString(KEY_FAVORITES, String.join(SEPARATOR, raw)).apply();
    }
}