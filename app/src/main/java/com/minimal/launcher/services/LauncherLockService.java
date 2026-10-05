package com.minimal.launcher.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;

/**
 * Existe por um único motivo: {@code GLOBAL_ACTION_LOCK_SCREEN} só pode ser
 * chamado por um AccessibilityService. O duplo toque em si é detectado na
 * tela inicial, onde a detecção é confiável - aqui não seria.
 *
 * <p>Se o serviço não estiver ativo, {@link #lockScreen()} devolve false e o
 * duplo toque simplesmente não faz nada.
 */
public final class LauncherLockService extends AccessibilityService {

    private static volatile LauncherLockService instance;

    /** Atalho para as configurações do serviço. */
    public static Intent settingsIntent() {
        return new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static boolean isRunning() {
        return instance != null;
    }

    /** Trava a tela. Retorna false se o serviço não estiver ativo. */
    public static boolean lockScreen() {
        LauncherLockService service = instance;
        if (service == null) {
            return false;
        }
        return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Nada: este serviço não observa a interface, só existe pelo lock.
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onUnbind(android.content.Intent intent) {
        instance = null;
        super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }
}