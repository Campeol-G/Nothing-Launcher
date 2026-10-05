package com.minimal.launcher.services;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Contador de notificações ativas. Só existe enquanto o usuário concede acesso
 * a notificações nas Configurações do sistema - desligado, este servico nem
 * existe e o indicador nao aparece. A barra de notificação do sistema nunca é
 * tocada aqui.
 */
public final class LauncherNotificationListener extends NotificationListenerService {

    private static volatile boolean connected;

    /** O usuário concedeu acesso a notificações? */
    public static boolean isConnected() {
        return connected;
    }

    @Override
    public void onListenerConnected() {
        connected = true;
        recount();
    }

    @Override
    public void onListenerDisconnected() {
        connected = false;
        NotificationState.publish(0);
    }

    @Override
    public void onDestroy() {
        connected = false;
        NotificationState.publish(0);
        super.onDestroy();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        recount();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification notification) {
        recount();
    }

    private void recount() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            NotificationState.publish(active == null ? 0 : active.length);
        } catch (SecurityException e) {
            // A permissão foi revogada entre a conexão e agora.
            connected = false;
            NotificationState.publish(0);
        }
    }
}