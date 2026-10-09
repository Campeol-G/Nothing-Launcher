package com.minimal.launcher.recents;

import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import moe.shizuku.server.IRemoteProcess;
import moe.shizuku.server.IShizukuService;
import rikka.shizuku.Shizuku;

/**
 * Lê a lista de tasks pelo shell do Shizuku.
 *
 * <p>{@code dumpsys activity recents} é impresso pela própria
 * {@code ActivityTaskManager}: é o mesmo dado que enche a tela de recents do
 * sistema. Rodar um comando e ler a saída evita qualquer API escondida, que seria
 * frágil e fora do espírito deste app.
 *
 * <p>Nada aqui lança. Shizuku parado e binder morto são estados esperados: quem
 * chama trata null como "não deu para ler" e a tela avisa.
 */
final class ShizukuTaskLister implements RecentAppsSource.TaskLister {

    private static final String COMMAND = "dumpsys activity recents";
    /** Um dumpsys de recents grande é de poucos KB. O teto é só para não vazar. */
    private static final int MAX_BYTES = 512 * 1024;

    @Override
    @Nullable
    public String readTasks() {
        IRemoteProcess process = null;
        try {
            if (!Shizuku.pingBinder() || !ShizukuTaskKiller.isPermissionGranted()) {
                return null;
            }
            IShizukuService service = IShizukuService.Stub.asInterface(Shizuku.getBinder());
            if (service == null) {
                return null;
            }
            process = service.newProcess(new String[]{"dumpsys", "activity", "recents"}, null, null);
            return drain(process);
        } catch (Throwable ignored) {
            // Binder morto, permissão negada, ROM sem `dumpsys`: estado esperado.
            return null;
        } finally {
            closeQuietly(process);
        }
    }

    /** Lê a saída toda e fecha os descritores. */
    @Nullable
    private static String drain(@NonNull IRemoteProcess process) {
        ParcelFileDescriptor stdout = null;
        try {
            stdout = process.getInputStream();
            if (stdout == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            try (InputStream in = new FileInputStream(stdout.getFileDescriptor())) {
                int read;
                while ((read = in.read(chunk)) > 0) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() > MAX_BYTES) {
                        break;
                    }
                }
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            // Saída truncada ou ilegível: o parser devolve vazio e a tela avisa.
            return null;
        } finally {
            closeDescriptorQuietly(stdout);
        }
    }

    private static void closeDescriptorQuietly(@Nullable ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (Throwable ignored) {
            // Já fechado, ou binder morto. Não vale reagir.
        }
    }

    /** O comando é de vida curta: não vale segurar processo para depois. */
    private static void closeQuietly(@Nullable IRemoteProcess process) {
        if (process == null) {
            return;
        }
        try {
            process.destroy();
        } catch (Throwable ignored) {
            // Já morreu sozinho, que é o caso comum.
        }
    }
}