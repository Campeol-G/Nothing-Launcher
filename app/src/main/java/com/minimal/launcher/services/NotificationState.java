package com.minimal.launcher.services;

/**
 * Quantas notificações estão ativas, publicado por
 * {@link LauncherNotificationListener} para a {@link com.minimal.launcher.HomeActivity}.
 *
 * <p>Um unico observador por vez (a tela principal), entao nao ha necessidade de
 * lista de ouvintes nem de LocalBroadcastManager. Houve da API 34 para nao
 * vazar Contexto entre Activities.
 */
public final class NotificationState {

    /** Recebe o novo valor sempre na thread principal. */
    public interface Listener {
        void onActiveNotificationsChanged(int count);
    }

    private static final Object LOCK = new Object();

    private static int count;
    private static Listener listener;

    private NotificationState() {
    }

    public static int getCount() {
        synchronized (LOCK) {
            return count;
        }
    }

    public static boolean hasNotifications() {
        return getCount() > 0;
    }

    /** Chamado pelo servico. Pode vir de qualquer thread. */
    public static void publish(final int newCount) {
        final Listener current;
        synchronized (LOCK) {
            count = newCount;
            current = listener;
        }
        if (current == null) {
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> {
                    if (current == listener) {
                        current.onActiveNotificationsChanged(newCount);
                    }
                });
    }

    public static void setListener(Listener newListener) {
        final int current;
        synchronized (LOCK) {
            listener = newListener;
            current = count;
        }
        if (newListener == null) {
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(
                () -> {
                    if (newListener == listener) {
                        newListener.onActiveNotificationsChanged(current);
                    }
                });
    }

    public static void clearListener(Listener oldListener) {
        synchronized (LOCK) {
            if (listener == oldListener) {
                listener = null;
            }
        }
    }
}