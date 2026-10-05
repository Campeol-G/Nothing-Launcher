package com.minimal.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Mantem relogio e data em dia sem timer proprio.
 *
 * <p>{@code ACTION_TIME_TICK} chega uma vez por minuto e e o unico jeito de
 * manter a tela correta sem gastar bateria com um Runnable. Os demais eventos
 * existem porque o usuario muda 12h/24h, fuso ou data enquanto a tela principal
 * esta aberta.
 */
public final class SystemTimeWatcher {

    public interface Listener {
        void onSystemTimeChanged();
    }

    private final Context appContext;
    private final Listener listener;
    private final BroadcastReceiver receiver;
    @Nullable
    private IntentFilter filter;

    public SystemTimeWatcher(@NonNull Context context, @NonNull Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                SystemTimeWatcher.this.listener.onSystemTimeChanged();
            }
        };
    }

    public void start() {
        if (filter != null) {
            return;
        }
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        // Broadcasts do sistema: precisam de RECEIVER_EXPORTED a partir da API 33.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            appContext.registerReceiver(receiver, filter);
        }
    }

    public void stop() {
        if (filter == null) {
            return;
        }
        try {
            appContext.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
            // Ja desmarcado: nada a fazer.
        }
        filter = null;
    }
}