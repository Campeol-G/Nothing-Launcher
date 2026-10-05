package com.minimal.launcher.apps;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.EdgeGestureLayout;
import com.minimal.launcher.R;
import com.minimal.launcher.ThemeHelper;

import java.util.List;

/**
 * Todos os apps, em ordem alfabetica, so o nome, com a busca ja focada e o
 * teclado aberto. Filter enquanto digita; se sobrar um unico resultado, o botão
 * do teclado abre esse app.
 *
 * <p>Dois modos de uso:
 * <ul>
 *   <li>{@link #MODE_BROWSE} - toque abre o app (voltar ou deslizar para baixo fecha).</li>
 *   <li>{@link #MODE_PICK} - toque devolve o app para quem chamou e a activity fecha.</li>
 * </ul>
 */
public final class AllAppsActivity extends AppCompatActivity
        implements AppListCallback, AppMenu.Listener {

    public static final String EXTRA_MODE = "com.minimal.launcher.apps.MODE";
    public static final String EXTRA_RESULT_COMPONENT = "com.minimal.launcher.apps.RESULT_COMPONENT";

    public static final int MODE_BROWSE = 0;
    public static final int MODE_PICK = 1;

    private int mode = MODE_BROWSE;

    private EdgeGestureLayout root;
    private EditText search;
    private TextView empty;
    private AppListAdapter adapter;

    public static Intent newPickIntent(@NonNull Context context) {
        return new Intent(context, AllAppsActivity.class).putExtra(EXTRA_MODE, MODE_PICK);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyNightMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_all_apps);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_BROWSE);

        root = findViewById(R.id.root);
        search = findViewById(R.id.search);
        empty = findViewById(R.id.empty);
        RecyclerView list = findViewById(R.id.all_apps_list);

        ThemeHelper.applyInsets(this, root, 26, 22, 8);
        ThemeHelper.paint(root);
        ThemeHelper.applySystemBars(this);

        List<AppEntry> all = AppRepository.get(this).getAllApps();
        adapter = new AppListAdapter(this, all, AppListAdapter.MODE_PICK, this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        // Deslizar para baixo fecha; o portão só abre no topo da lista ou fora dela.
        root.setEdgeGesture(EdgeGestureLayout.DIRECTION_DOWN, list, this::finish);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        search.setOnEditorActionListener((view, actionId, event) -> {
            boolean imeGo = actionId == EditorInfo.IME_ACTION_GO;
            boolean hardwareGo = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (imeGo || hardwareGo) {
                launchSingleMatch();
                return true;
            }
            return false;
        });

        updateEmptyState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        focusSearchAndShowKeyboard();
    }

    /**
     * Busca focada e teclado aberto, como pedido. Repetido no onResume porque o
     * teclado fecha sozinho quando o usuario sai para um app.
     */
    private void focusSearchAndShowKeyboard() {
        search.requestFocus();
        search.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null && !imm.isActive(search)) {
                imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }

    private void applyFilter(@NonNull String query) {
        adapter.filter(query);
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean isEmpty = adapter.getItemCount() == 0;
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    /** Com um unico resultado sobrando, o teclado decide: abre. */
    private void launchSingleMatch() {
        if (adapter.getItemCount() != 1) {
            return;
        }
        AppEntry match = adapter.getItems().get(0);
        if (mode == MODE_PICK) {
            // Quem chamou está escolhendo um favorito: devolver, não abrir.
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT_COMPONENT, match.component));
        } else {
            match.launch(this);
        }
        finish();
    }

    @Override
    public void onAppClick(@NonNull AppEntry entry) {
        if (mode == MODE_PICK) {
            setResult(RESULT_OK, new Intent().putExtra(EXTRA_RESULT_COMPONENT, entry.component));
            finish();
            return;
        }
        entry.launch(this);
    }

    @Override
    public boolean onAppLongClick(@NonNull View itemView, @NonNull AppEntry entry) {
        // O menu é o mesmo da tela principal; só o destino do toque muda.
        AppMenu.show(this, entry, this);
        return true;
    }

    @Override
    public void onFavoritesChanged() {
        // Nada a recarregar aqui: quem mostra favoritos é a outra tela. Fechar o
        // menu já basta.
        if (!TextUtils.isEmpty(search.getText())) {
            applyFilter(search.getText().toString());
        }
    }

    @Override
    public void onMenuDismissed() {
    }
}