package com.minimal.launcher;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.minimal.launcher.apps.AppEntry;
import com.minimal.launcher.apps.AppMenu;
import com.minimal.launcher.apps.AppRepository;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Busca por qualquer app instalado, com um unico resultado por vez.
 *
 * <p>Conforme o usuario digita, a tela mostra so o primeiro app que corresponde
 * em ordem alfabetica. Digitar mais uma letra troca o resultado. Os favoritos
 * tambem aparecem aqui: o que eles gains e acesso direto, nao exclusao.
 *
 * <p>Como so um app aparece, a linha "+N outros" conta quantos ficaram de fora.
 * Sem ela a busca pareceria quebrada quando o primeiro resultado não é o que a
 * pessoa procurava.
 */
public final class SearchActivity extends AppCompatActivity implements AppMenu.Listener {

    private EdgeGestureLayout root;
    private View searchBar;
    private EditText search;
    private View resultBlock;
    private TextView result;
    private TextView more;
    private TextView emptyHint;
    private TextView footer;

    /** O app que o resultado atual representa. Fonte de verdade do clique. */
    @Nullable
    private AppEntry match;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyNightMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        root = findViewById(R.id.root);
        searchBar = findViewById(R.id.search_bar_container);
        search = findViewById(R.id.search);
        resultBlock = findViewById(R.id.search_result_block);
        result = findViewById(R.id.search_result);
        more = findViewById(R.id.search_more);
        emptyHint = findViewById(R.id.search_empty_hint);
        footer = findViewById(R.id.search_footer);

        ThemeHelper.applyInsets(this, root, 26, 22, 10);
        ThemeHelper.applySystemBars(this);

        int foreground = ThemeHelper.getSearchForegroundColor(this);
        int subdued = ThemeHelper.getSearchPlaceholderColor(this);

        ThemeHelper.paint(this, root, true);

        searchBar.setBackground(ThemeHelper.createRoundedRectDrawable(this,
                ThemeHelper.getSearchBackgroundColor(this),
                ThemeHelper.getSearchBarBorderColor(this)));

        float scale = LauncherUtils.getFontScale(this);
        search.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.search_size) * scale);
        result.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.search_result_size) * scale);
        emptyHint.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.search_size) * scale);
        more.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.settings_summary_size) * scale);
        footer.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.settings_summary_size) * scale);

        // A barra tem cores proprias: as pintadas pela arvore nao valem aqui.
        search.setTextColor(foreground);
        search.setHintTextColor(subdued);
        search.setTypeface(FontCombo.getSearchFont(this));
        result.setTypeface(FontCombo.getTitleFont(this));
        emptyHint.setTypeface(FontCombo.getInterfaceFont(this));
        more.setTypeface(FontCombo.getInterfaceFont(this));
        footer.setTypeface(FontCombo.getInterfaceFont(this));

        // O contador e o rodape sao informacao secundaria: recuam sem sumir.
        more.setTextColor(subdued);
        footer.setTextColor(subdued);
        footer.setAlpha(0.55f);

        // Deslizar para baixo fecha a busca.
        //
        // Este gesto é seguro com a navegação por gestos: a puxada do sistema
        // para abrir a gaveta de notificações começa na borda superior, e o
        // arrasto do app é decidido aqui, no meio da tela. Quem preferir não
        // usar gesto algum tem o botão voltar do sistema e o Enter do teclado.
        root.setEdgeGesture(EdgeGestureLayout.DIRECTION_DOWN, null, this::finish);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                update(s.toString());
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
                launchMatch();
                return true;
            }
            return false;
        });

        result.setOnClickListener(v -> launchMatch());
        result.setOnLongClickListener(v -> {
            AppEntry current = match;
            if (current != null) {
                AppMenu.show(this, current, this, LauncherUtils.getMaxFavorites(this));
            }
            return true;
        });

        update("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        focusSearchAndShowKeyboard();
    }

    /**
     * Busca focada e teclado aberto, sem delay artificial.
     *
     * <p>Aqui já houve um {@code postDelayed} de 200ms "para a janela assentar",
     * e ele custava 200ms de latência toda vez que alguém queria trocar de app.
     * O teclado é pedido de imediato e repetido no primeiro desenho da tela, que
     * é quando a janela já está pronta: assim cobre os dois casos sem esperar.
     */
    private void focusSearchAndShowKeyboard() {
        search.requestFocus();
        showKeyboard();
        search.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    private boolean done;

                    @Override
                    public void onGlobalLayout() {
                        if (done || isFinishing() || isDestroyed()) {
                            return;
                        }
                        done = true;
                        showKeyboard();
                        search.getViewTreeObserver()
                                .removeOnGlobalLayoutListener(this);
                    }
                });
    }

    private void showKeyboard() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null && !imm.isActive(search)) {
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    // -------------------------------------------------------------- resultado

    /** Recalcula o unico resultado a partir do texto atual. */
    private void update(@NonNull String query) {
        List<AppEntry> matches = findMatches(query);

        if (matches.isEmpty()) {
            match = null;
            resultBlock.setVisibility(View.GONE);
            emptyHint.setVisibility(View.VISIBLE);
            emptyHint.setText(TextUtils.isEmpty(query)
                    ? getString(R.string.search_idle_hint)
                    : getString(R.string.search_no_results));
            return;
        }

        match = matches.get(0);
        emptyHint.setVisibility(View.GONE);
        resultBlock.setVisibility(View.VISIBLE);
        result.setText(match.label);

        int others = matches.size() - 1;
        if (others == 0) {
            more.setVisibility(View.GONE);
        } else {
            more.setVisibility(View.VISIBLE);
            more.setText(others == 1
                    ? getString(R.string.search_more_one)
                    : getString(R.string.search_more_many, others));
        }
    }

    /**
     * Apps que correspondem, em ordem alfabetica. Remove acentos e caixa antes
     * de comparar: "cafe" encontra "Cafe".
     */
    @NonNull
    private List<AppEntry> findMatches(@NonNull String query) {
        String normalized = normalize(query);
        List<AppEntry> matches = new ArrayList<>();
        if (normalized.isEmpty()) {
            return matches;
        }
        for (AppEntry entry : AppRepository.get(this).getAllApps()) {
            if (normalize(entry.label).contains(normalized)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private void launchMatch() {
        if (match != null) {
            match.launch(this);
        }
    }

    @NonNull
    private static String normalize(@NonNull String value) {
        String lower = value.toLowerCase(Locale.getDefault());
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    // ------------------------------------------------------------------ menu

    @Override
    public void onFavoritesChanged() {
        // O resultado exibido continua valendo; so muda o que o menu mostra.
    }

    @Override
    public void onMenuDismissed() {
    }
}
