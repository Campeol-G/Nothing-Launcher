package com.minimal.launcher.apps;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.minimal.launcher.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu do toque longo. Mesmo menu na lista de favoritos e na lista de todos os
 * apps: nada de ícone, nada de submenu, no maximo quatro linhas de texto.
 */
public final class AppMenu {

    /** O que a tela faz depois de uma escolha do menu. */
    public interface Listener {
        /** Favoritos mudaram: a lista de origem precisa se recarregar. */
        void onFavoritesChanged();

        /** A tela pode simplesmente fechar, se quiser. */
        void onMenuDismissed();
    }

    private static final int ACTION_FAVORITE = 0;
    private static final int ACTION_INFO = 1;
    private static final int ACTION_UNINSTALL = 2;

    private AppMenu() {
    }

    public static void show(@NonNull final Context context,
                            @NonNull final AppEntry entry,
                            @Nullable final Listener listener) {
        final FavoritesStore favorites = new FavoritesStore(context);
        final boolean isFavorite = favorites.isFavorite(entry.component);
        final boolean canUninstall = AppRepository.get(context).canUninstall(entry.packageName);

        List<String> labels = new ArrayList<>(3);
        final List<Integer> actions = new ArrayList<>(3);

        labels.add(context.getString(isFavorite
                ? R.string.menu_remove_favorite
                : R.string.menu_add_favorite));
        actions.add(ACTION_FAVORITE);

        labels.add(context.getString(R.string.menu_app_info));
        actions.add(ACTION_INFO);

        if (canUninstall) {
            labels.add(context.getString(R.string.menu_uninstall));
            actions.add(ACTION_UNINSTALL);
        }

        if (actions.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(context)
                .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                    int action = actions.get(which);
                    switch (action) {
                        case ACTION_FAVORITE:
                            if (isFavorite) {
                                favorites.remove(entry.component);
                            } else {
                                favorites.add(entry);
                            }
                            if (listener != null) {
                                listener.onFavoritesChanged();
                            }
                            break;
                        case ACTION_INFO:
                            entry.showAppInfo(context);
                            break;
                        case ACTION_UNINSTALL:
                            entry.uninstall(context);
                            break;
                        default:
                            break;
                    }
                })
                .setOnDismissListener(dialog -> {
                    if (listener != null) {
                        listener.onMenuDismissed();
                    }
                })
                .show();
    }
}