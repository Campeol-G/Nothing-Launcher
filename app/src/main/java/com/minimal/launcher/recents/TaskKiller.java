package com.minimal.launcher.recents;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Encerrar apps. Só isso — o X da tela de recentes é o que tira o app da lista.
 *
 * <p>Encerrar é o que o X tenta fazer por baixo, e é o que pode não acontecer:
 * app de sistema não morre com {@code force-stop}, e sem Shizuku rodando nada é
 * encerrado. Nenhuma das duas coisas é falha, e é por isso que esta interface é
 * bem mais burra do que parece precisar ser.
 */
public interface TaskKiller {

    /** Dá para pedir encerramento agora? Não pendura isso na tela: só decide se pede. */
    boolean isAvailable();

    /** Encerrar um app. Melhor esforço: some, não lança, e não devolve sucesso. */
    void kill(@NonNull String packageName);

    /** Encerrar vários. Melhor esforço, como {@link #kill}. */
    void killAll(@NonNull List<String> packageNames);
}