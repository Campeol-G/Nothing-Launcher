package com.minimal.launcher.recents;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A única regra que impede o launcher de matar a si mesmo.
 *
 * <p>Sem este teste, a falha é um crash em produção e só aparece com Shizuku
 * ligado: o launcher tem uso registrado, aparece na própria lista de recentes,
 * e o X na linha dele executa {@code am force-stop} no processo que está
 * desenhando a tela. No aparelho de teste sem Shizuku isso nunca apareceu.
 */
public class TaskKillGuardTest {

    private static final String SELF = "com.minimal.launcher";

    @Test public void naoMataOProprioLauncher() {
        assertFalse(ShizukuTaskKiller.canKill(SELF, SELF));
    }

    @Test public void mataOsOutrosApps() {
        assertTrue(ShizukuTaskKiller.canKill("com.zhiliaoapp.musically", SELF));
        assertTrue(ShizukuTaskKiller.canKill("com.android.chrome", SELF));
    }

    @Test public void pacoteVazioNaoEEncerrado() {
        assertFalse(ShizukuTaskKiller.canKill("", SELF));
    }
}