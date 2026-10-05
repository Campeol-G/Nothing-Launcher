package com.minimal.launcher.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.EdgeGestureLayout;
import com.minimal.launcher.R;
import com.minimal.launcher.ThemeHelper;
import com.minimal.launcher.apps.AllAppsActivity;
import com.minimal.launcher.apps.AppEntry;
import com.minimal.launcher.apps.AppListAdapter;
import com.minimal.launcher.apps.AppListCallback;
import com.minimal.launcher.apps.FavoritesStore;

/**
 * Gerenciar favoritos: adicionar, remover e reordenar.
 *
 * <p>Toque marca o app para remover (fica riscado, reversivel); toque longo
 * comeca o arrasto; "Concluir" grava. Voltar tambem grava, para nada se perder
 * por acidente.
 */
public final class ManageFavoritesActivity extends AppCompatActivity
        implements AppListCallback {

    private FavoritesStore store;
    private AppListAdapter adapter;
    private TextView empty;
    private RecyclerView list;
    private ItemTouchHelper touchHelper;

    private final ActivityResultLauncher<Intent> pickApp = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                String component = result.getData()
                        .getStringExtra(AllAppsActivity.EXTRA_RESULT_COMPONENT);
                if (component == null) {
                    return;
                }
                AppEntry entry = AppEntry.fromComponent(this, component);
                if (entry == null || store.isFavorite(component)) {
                    return;
                }
                store.add(entry);
                refresh();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyNightMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_favorites);

        store = new FavoritesStore(this);

        EdgeGestureLayout root = findViewById(R.id.root);
        ThemeHelper.applyInsets(this, root, 26, 30, 14);
        ThemeHelper.applySystemBars(this);

        list = findViewById(R.id.favorites_list);
        empty = findViewById(R.id.manage_empty);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(this, store.getFavorites(), AppListAdapter.MODE_MANAGE, this);
        list.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                adapter.moveItem(viewHolder.getBindingAdapterPosition(),
                        target.getBindingAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Sem swipe: remover é por toque.
            }
        });
        touchHelper.attachToRecyclerView(list);

        findViewById(R.id.action_add).setOnClickListener(v ->
                pickApp.launch(AllAppsActivity.newPickIntent(this)));
        findViewById(R.id.action_done).setOnClickListener(v -> finish());

        refresh();
        ThemeHelper.paint(root);
    }

    private void refresh() {
        adapter.setItems(store.getFavorites());
        adapter.clearMarks();
        empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void commit() {
        store.save(adapter.getUnmarkedItems());
    }

    @Override
    protected void onPause() {
        // Voltar também grava: nada se perde por acidente.
        commit();
        super.onPause();
    }

    @Override
    public void onAppClick(@NonNull AppEntry entry) {
        adapter.setMarked(entry.component, !adapter.isMarked(entry.component));
    }

    @Override
    public boolean onAppLongClick(@NonNull View itemView, @NonNull AppEntry entry) {
        // Sem handle para arrastar (nada de icones): o proprio toque longo inicia.
        RecyclerView.ViewHolder holder = list.getChildViewHolder(itemView);
        if (holder == null) {
            return false;
        }
        if (holder.getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
            touchHelper.startDrag(holder);
        }
        return true;
    }
}