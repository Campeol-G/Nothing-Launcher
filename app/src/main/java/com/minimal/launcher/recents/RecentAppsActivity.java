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
     * Não precisa de Context, então pode ser campo.
     *
     * <p>O {@link DismissedStore} é o contrário: {@code new DismissedStore(this)}
     * em inicializador de campo rodaria antes de o Context ser anexado à activity,
     * e {@code getApplicationContext()} devolveria null. Ele é criado no onCreate.
     */
    private final TaskKiller killer = new ShizukuTaskKiller();

    private DismissedStore dismissedStore;

    private RecyclerView list;
    private TextView hint;
    private RecentListAdapter adapter;

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
        hint.setAlpha(0.7f);
        hint.setOnClickListener(v -> openUsageSettings());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reler a cada volta: a lista é do sistema, e o uso dos últimos minutos é
        // justamente o que muda enquanto a tela está fechada.
        RecentAppsSource.loadAsync(this, this);
    }

    // -------------------------------------------------------------- lista

    @Override
    public void onLoaded(@NonNull List<AppEntry> entries, boolean permissionMissing) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (permissionMissing) {
            showHint(R.string.recent_usage_permission_hint);
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
        hideHint();
    }

    private void showHint(int text) {
        hint.setText(text);
        hint.setVisibility(View.VISIBLE);
    }

    private void hideHint() {
        hint.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
    }

    /**
     * Abrir as configurações do app é a única forma de chegar na permissão.
     *
     * <p>Uso de dados não tem diálogo do sistema: o usuário tem que ir lá e ligar.
     * A linha de texto é o mesmo estilo das outras, então apontar para as
     * configurações é um toque nela, e não um botão com cara de botão.
     */
    private void openUsageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException noSuchScreen) {
            // ROM sem essa tela de configurações: a linha de texto já diz onde está.
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
        // A dispensa vai primeiro: ela é o que faz o app não voltar na próxima
        // vez que a tela abrir, e é ela que sobrevive à falta de Shizuku.
        // Encerrar é o que vem depois, e pode não acontecer.
        dismissedStore.dismiss(entry.packageName, System.currentTimeMillis());
        killer.kill(entry.packageName);
        shown.remove(entry);
        adapter.removeAt(position);
        if (!killer.isAvailable()) {
            showHint(R.string.recent_shizuku_missing);
        }
    }

    @Override
    public void onClearAll() {
        List<String> packages = new ArrayList<>(shown.size());
        for (AppEntry entry : shown) {
            packages.add(entry.packageName);
        }
        killer.killAll(packages);
        dismissedStore.clear();
        shown.clear();
        publish();
        if (!killer.isAvailable()) {
            showHint(R.string.recent_shizuku_missing);
        }
    }
}