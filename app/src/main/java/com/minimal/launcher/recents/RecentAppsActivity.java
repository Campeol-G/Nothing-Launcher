package com.minimal.launcher.recents;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.EdgeGestureLayout;
import com.minimal.launcher.FontCombo;
import com.minimal.launcher.R;
import com.minimal.launcher.ThemeHelper;
import com.minimal.launcher.apps.AppEntry;
import com.minimal.launcher.apps.AppRepository;

import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Apps usados recentemente, em só texto.
 *
 * <p>Sem ícone, sem miniatura do último momento, sem imagem nenhuma: é a mesma
 * proposta da tela inicial, e por isso reusa o estilo dela em vez de ter um
 * próprio. A única diferença na linha é o X, e ele é o botão de sair da lista —
 * encerrar o app é o que ele tenta fazer por baixo, e pode não acontecer.
 *
 * <p>A tela é aberta por atalho do sistema e não aparece na lista de apps do
 * launcher: é por não declarar {@code CATEGORY_LAUNCHER} que o launcher, que lista
 * tudo que tem essa categoria, não a mostra aqui nem na busca.
 *
 * <p>Falta a permissão de acesso a dados de uso? A tela diz como conseguir, em vez
 * de ficar vazia sem explicação — é a diferença entre "não há app recente" e "não
 * deu para ler".
 */
public final class RecentAppsActivity extends AppCompatActivity
        implements RecentListAdapter.Callback, RecentAppsSource.Callback {

    /**
     * O {@link DismissedStore} e o {@link ShizukuTaskKiller} são criados no
     * onCreate, não em inicializador de campo: um inicializador roda antes de o
     * Context ser anexado à activity, e o {@code getApplicationContext()} do store
     * devolveria null.
     */
    /** O app do Shizuku, para o aviso levar direto à tela dele. */
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    private ShizukuTaskKiller killer;
    private DismissedStore dismissedStore;

    private RecyclerView list;
    private TextView hint;
    private RecentListAdapter adapter;

    /**
     * Qual leitura a tela está mostrando.
     *
     * <p>Cada {@code onResume} enfileira uma leitura, e elas se demoram. Sem este
     * número, uma leitura antiga pode ser entregue depois do X e repor a linha que
     * o usuário acabou de tirar — o botão pareceria não funcionar. É o mesmo
     * cuidado que a busca faz com o número da consulta.
     */
    private int loadToken;

    /**
     * Os apps que a tela está mostrando, para o "limpar tudo" saber o que
     * encerrar. Não é a lista do adapter: é sem o botão do fim.
     */
    private final List<AppEntry> shown = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyNightMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_apps);

        EdgeGestureLayout root = findViewById(R.id.root);
        list = findViewById(R.id.recent_apps_list);
        hint = findViewById(R.id.recent_hint);
        dismissedStore = new DismissedStore(this);
        killer = new ShizukuTaskKiller(getPackageName());

        ThemeHelper.applyInsets(this, root, 26, 30, 18);
        ThemeHelper.applySystemBars(this);
        ThemeHelper.paint(this, root, false);

        // Deslizar para baixo fecha, como na busca.
        root.setEdgeGesture(EdgeGestureLayout.DIRECTION_DOWN, null, this::finish);

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter = new RecentListAdapter(this, new ArrayList<>(), this));

        // A linha do pé é o mesmo estilo das linhas de apps, então precisa do
        // mesmo tratamento: fonte e cor em runtime, como o adapter faz.
        hint.setTypeface(FontCombo.getInterfaceFont(this));
        hint.setTextColor(ThemeHelper.getHomeForegroundColor(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reler a cada volta: a lista é do sistema, e o uso dos últimos minutos é
        // justamente o que muda enquanto a tela está fechada.
        final int token = ++loadToken;
        // O consentimento entra assim que a tela abre, não no primeiro X: quem
        // quer a lista inteira precisa saber da dependência antes de tocar em
        // qualquer coisa.
        killer.requestPermission();
        RecentAppsSource.loadAsync(this, (entries, shizukuMissing) -> {
            // Uma leitura que já não é a atual não repõe nada: senão ela traz de
            // volta a linha que o X acabou de tirar, e o botão parece quebrado.
            if (token != loadToken || isFinishing() || isDestroyed()) {
                return;
            }
            onLoaded(entries, shizukuMissing);
        });
    }

    // -------------------------------------------------------------- lista

    @Override
    public void onLoaded(@NonNull List<AppEntry> entries, boolean shizukuMissing) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (shizukuMissing) {
            // Sem Shizuku não há lista de tasks, e a lista velha não vale nada:
            // escondê-la, senão a pessoa lê "nada aberto" e o aviso ao mesmo tempo.
            shown.clear();
            adapter.setItems(new ArrayList<>());
            list.setVisibility(View.GONE);
            showHint(R.string.recent_shizuku_required, this::openShizuku);
            return;
        }
        shown.clear();
        shown.addAll(entries);
        publish();
    }

    private void publish() {
        List<AppEntry> rows = new ArrayList<>(shown);
        rows.add(clearAllEntry());
        adapter.setItems(rows);
        list.setVisibility(View.VISIBLE);
        // Lista vazia é estado real: nada usado na janela. E ela não é acionável,
        // então não finge ser botão.
        if (shown.isEmpty()) {
            showHint(R.string.recent_empty, null);
        } else {
            hideHint();
        }
    }

    /**
     * @param actionable há algo a fazer? Então a linha abre algo. Uma linha só
     *                   informativa não pode abrir a tela errada.
     */
    private void showHint(int text, @Nullable Runnable action) {
        hint.setText(text);
        hint.setClickable(action != null);
        hint.setOnClickListener(action == null ? null : v -> action.run());
        // Esmaecida só quando é informação. Quando há algo a fazer, a linha é a
        // única instrução da tela e esmaecer a esmaece.
        hint.setAlpha(action != null ? 1f : 0.7f);
        hint.setVisibility(View.VISIBLE);
    }

    private void hideHint() {
        hint.setVisibility(View.GONE);
        hint.setOnClickListener(null);
        list.setVisibility(View.VISIBLE);
    }

    /**
     * Abrir o Shizuku é a única forma de a tela ter lista.
     *
     * <p>Não há como ler as tasks de um app de terceiro sem ele. A linha de texto é
     * o mesmo estilo das outras, então apontar para o Shizuku é um toque nela, e não
     * um botão com cara de botão.
     */
    private void openShizuku() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:" + SHIZUKU_PACKAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException noSuchScreen) {
            // ROM sem essa tela de configurações: a linha de texto já diz o que fazer.
        }
    }

    /** O botão do fim: um AppEntry com um componente que nenhum app real tem. */
    @NonNull
    private AppEntry clearAllEntry() {
        return new AppEntry("", getString(R.string.recent_clear_all),
                RecentListAdapter.CLEAR_ALL_COMPONENT);
    }

    // -------------------------------------------------------------- toques

    @Override
    public void onAppClick(@NonNull AppEntry entry) {
        entry.launch(this);
    }

    @Override
    public void onDismiss(@NonNull AppEntry entry, int position) {
        // A dispensa vai primeiro: ela é o que tira o app da lista, e é ela que
        // sobrevive à falta de Shizuku. Encerrar é o que vem depois, e pode não
        // acontecer — o app continua aberto, que é o que o X promete.
        dismissedStore.dismiss(entry.packageName);
        shown.remove(entry);
        adapter.removeAt(position);
        killOffMainThread(() -> killer.kill(entry.packageName));
    }

    @Override
    public void onClearAll() {
        List<String> packages = new ArrayList<>(shown.size());
        for (AppEntry entry : shown) {
            packages.add(entry.packageName);
        }
        dismissedStore.clear();
        shown.clear();
        publish();
        killOffMainThread(() -> killer.killAll(packages));
    }

    /**
     * Encerrar fora da main thread.
     *
     * <p>{@code newProcess} do Shizuku é transação de binder bloqueante, e o
     * "limpar tudo" faria uma por app. Na main thread isso é a tela congelada, e
     * no X simples é a linha que demora a sair. A fila do {@link AppRepository} é
     * a mesma que o resto da tela já usa, então nada compete com a leitura de uso.
     *
     * <p>Quem chama tira a linha <em>antes</em>: encerrar é o bônus, e a resposta
     * visível não pode esperar por isso.
     */
    private void killOffMainThread(@NonNull Runnable kill) {
        AppRepository.get(this).executeAsync(kill, () -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (!killer.isAvailable()) {
                // Saíram da lista mas seguem rodando: é o que o X promete.
                showHint(R.string.recent_shizuku_missing, null);
            }
        });
    }
}