package com.minimal.launcher.apps;

import android.view.View;

import androidx.annotation.NonNull;

/** O que a tela precisa saber sobre o que o usuario fez em um item da lista. */
public interface AppListCallback {

    /** Toque curto. */
    void onAppClick(@NonNull AppEntry entry);

    /**
     * Toque longo.
     *
     * @return true se o item consumiu o toque (a lista deve abrir o menu).
     */
    boolean onAppLongClick(@NonNull View itemView, @NonNull AppEntry entry);
}