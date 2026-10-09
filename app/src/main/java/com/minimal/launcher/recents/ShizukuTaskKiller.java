package com.minimal.launcher.recents;

import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;

import java.util.List;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Encerramento por trás do Shizuku, como melhor esforço.
 *
 * <p>Três coisas que este código é, e vale dizer porque as três contrariam a
 * expectativa:
 *
 * <ul>
 *   <li><b>Nada aqui lança.</b> Shizuku parado, binder morto, ROM sem {@code am} e
 *   app de sistema que não morre com {@code force-stop} são estados esperados. O X
 *   tira o app da lista de qualquer jeito; encerrar é o bônus que pode não vir.
 *   <li><b>O retorno não significa sucesso.</b> Encerrar app de sistema não
 *   funciona, e isso não é erro. {@link TaskKiller} não devolve boolean
 *   justamente para não haver o que interpretar.
 *   <li><b>Ele encerra, não remove task.</b> O X tira o app da <em>tela</em>, e o
 *   app sai do histórico que esta tela guarda. Ele continua no recents do sistema,
 *   que é outra tela e não é nossa.
 * </ul>
 *
 * <p>A API pública do Shizuku 13.1.5 não expõe rodar comando: {@code newProcess}
 * está um nível abaixo, em {@link IShizukuService}, e o acesso a esse serviço é
 * protegido. O caminho usado aqui — {@link Shizuku#getBinder()} mais
 * {@code asInterface} — usa só os artefatos públicos que o projeto declara, sem
 * nenhuma API escondida do Android.
 */
public final class ShizukuTaskKiller implements TaskKiller {

    /**
     * A permissão que pedimos ao Shizuku.
     *
     * <p>É {@code -1}, o valor que a API usa para acesso total ao serviço. Pedimos
     * uma vez só: o pedido abre uma tela de consentimento, e repetir a cada
     * {@code onResume} seria apenas incomodar.
     */
    private static final int PERMISSION = -1;

    /** Já pedimos? A resposta do usuário é definitiva; repetir só incomoda. */
    private boolean permissionRequested;

    @Override
    public boolean isAvailable() {
        return Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void kill(@NonNull String packageName) {
        if (!Shizuku.pingBinder()) {
            return;
        }
        if (!isAvailable()) {
            requestPermissionOnce();
            return;
        }
        forceStop(packageName);
    }

    @Override
    public void killAll(@NonNull List<String> packageNames) {
        for (String packageName : packageNames) {
            kill(packageName);
        }
    }

    /**
     * {@code am force-stop} no processo do Shizuku, que roda como UID 2000.
     *
     * <p>Um processo por comando: o {@link IRemoteProcess} é destruído logo em
     * seguida, porque segurar um processo vivo só para matar outro app dá mais
     * trabalho do que repetir a criação.
     */
    private void forceStop(@NonNull String packageName) {
        IRemoteProcess process = null;
        try {
            IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
            if (service == null) {
                return;
            }
            // force-stop quer o pacote. Encerrar o processo é o que tira o app do
            // fundo; a task em si continua registrada no sistema.
            process = service.newProcess(
                    new String[]{"am", "force-stop", packageName}, null, null);
        } catch (Throwable ignored) {
            // Binder morto, permissão negada, ROM sem `am`: encerra sem efeito.
            return;
        }
        if (process == null) {
            return;
        }
        try {
            ParcelFileDescriptor stdin = process.getOutputStream();
            if (stdin != null) {
                stdin.close();
            }
        } catch (Throwable ignored) {
            // Já executou; só o pipe de comando não fechou.
        }
        try {
            process.destroy();
        } catch (Throwable ignored) {
            // O comando é de vida curta mesmo; não vale reagir.
        }
    }

    private void requestPermissionOnce() {
        if (permissionRequested) {
            return;
        }
        permissionRequested = true;
        try {
            Shizuku.requestPermission(PERMISSION);
        } catch (Throwable ignored) {
            // Sem Shizuku não há o que pedir, e a tela segue funcionando.
        }
    }
}