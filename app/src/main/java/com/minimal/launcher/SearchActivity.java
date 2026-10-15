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

import java.util.List;

/**
 * Busca por qualquer app instalado, com um unico resultado por vez.
 *
 * <p>Conforme o usuario digita, a tela mostra so o app que melhor responde ao
 * texto, e quantos outros ficaram de fora. O resultado mostrado e o mesmo que o
 * enter abre: a tela e o enter usam o mesmo ranqueamento, que esta em
 * {@link com.minimal.launcher.apps.AppSearch}. E por isso que o enter funciona
 * com o texto pela metade: "tk" encontra TikTok.
 *
 * <p>Como so um app aparece, a linha "+N outros" conta quantos ficaram de fora.
 * Sem ela a busca pareceria quebrada quando o primeiro resultado não é o que a
 * pessoa procurava.
 *
 * <p>O teclado nao e um detalhe desta tela, e precondicao dela: nao existe
 * busca sem teclado. Por isso ele e pedido na abertura, repetido enquanto a
 * janela nao ganhar foco, e a tela fecha se ele sumir.
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

    /**
     * Qual consulta o resultado na tela responde.
     *
     * <p>A busca e assincrona, entao a resposta de "tk" pode chegar depois da
     * de "tikt". O numero da consulta separa as duas: uma resposta velha e
     * descartada em vez de sobrescrever o que o usuario esta vendo agora.
     */
    private int queryToken;

    /** A activity esta em primeiro plano? So interessa quando o teclado some. */
    private boolean resumed;

    /** O teclado ja esteve visivel nesta tela? Antes disso, ele nao "sumiu". */
    private boolean keyboardSeen;
    /** A tela já está saindo. Dois caminhos podem pedir a saída quase juntos. */
    private boolean saindo;

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

        // O teclado e precondicao da tela, entao a tela precisa saber quando ele
        // vai embora.
        ThemeHelper.applyInsets(this, root, 26, 22, 10, this::onKeyboardVisibilityChanged);
        ThemeHelper.applySystemBars(this);

        int foreground = ThemeHelper.getSearchForegroundColor(this);
        int subdued = ThemeHelper.getSearchPlaceholderColor(this);

        ThemeHelper.paint(this, root, true);

        // A tela sobe de baixo, como quem puxa uma gaveta. Sem isto, ela aparecia de
        // repente no meio de um gesto: o dedo subia e a tela simplesmente estava la.
        // Rotação não repete a animação — a tela já estava lá antes.
        if (savedInstanceState == null) {
            SlideAnimation.entrarDeBaixo(root);
        }

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
        // Este gesto e seguro com a navegacao por gestos: a puxada do sistema
        // para abrir a gaveta de notificacoes comeca na borda superior, e o
        // arrasto do app e decidido aqui, no meio da tela. A faixa reservada ao
        // gesto do sistema nao entra aqui — ela existe para o arrasto para cima,
        // que disputa o home. Quem preferir nao usar gesto algum tem o botao
        // voltar do sistema e o Enter do teclado.
        root.setEdgeGesture(EdgeGestureLayout.DIRECTION_DOWN, null, this::fecharDeslizando);

        // O botão voltar do sistema fecha a activity na hora, sem passar pela
        // animação. Aqui ele passa pelo mesmo caminho dos outros dois.
        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        fecharDeslizando();
                    }
                });

        // Na busca o gesto horizontal abre a tela de apps recentes. O custo: o
        // arrasto que fecharia a busca indo para tras vira "abrir recentes". Fechar
        // continua pelo botao voltar e pelo gesto de descer, que ja existem.
        LauncherUtils.bindRecentAppsGesture(root);

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
        resumed = true;
        focusSearchAndShowKeyboard();
    }

    @Override
    protected void onPause() {
        resumed = false;
        // A tela de apps recentes pode ter sido aberta pelo arrasto horizontal, e a
        // busca continua viva atrás dela. Sem fechar o teclado aqui, ele aparece por
        // cima da tela nova — e é a pior coisa que pode acontecer num launcher, cujo
        // fundo é sólido e sem nada por cima.
        hideKeyboard();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // A janela nem sempre tem foco no primeiro onResume, e um showSoftInput
        // pedido antes disso e descartado. Repetir aqui e o que faz o teclado
        // abrir junto com a tela.
        if (hasFocus) {
            focusSearchAndShowKeyboard();
        }
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

    /**
     * Pede o teclado, sempre.
     *
     * <p>Aqui estava a causa do teclado não subir junto com a tela: o pedido era
     * guardado por {@code imm.isActive(view)}, e essa resposta é {@code true}
     * quando a activity já é servida pela IME — mesmo com o teclado escondido.
     * O pedido era descartado antes de sair. Como nesta tela o teclado é
     * precondição, não há o que guardar: {@code showSoftInput} não faz nada se o
     * teclado já estiver aberto.
     */
    private void showKeyboard() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) {
            imm.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Fecha o teclado.
     *
     * <p>Separado de {@code showKeyboard} porque os dois lados precisam andar
     * juntos: a tela abre com o teclado e, qualquer que seja o caminho de saída —
     * arrasto lateral, arrasto para baixo, botão voltar — ela fecha sem ele.
     */
    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null && search != null) {
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
    }

    // ------------------------------------------------------------- teclado

    /**
     * O teclado sumiu: a tela vai embora junto.
     *
     * <p>Sem teclado esta tela nao tem funcao — quem abriu a busca queria
     * escrever. Vale tanto para o gesto de descer no teclado, que o proprio
     * sistema reconhece, quanto para o botao voltar, que primeiro esconde o
     * teclado antes de chegar aqui.
     *
     * <p>Duas condicoes impedem que isso vire uma corrida: o teclado precisa ter
     * estado visivel (antes disso ele so ainda nao chegou), e a activity precisa
     * estar em primeiro plano com foco (assim abrir um app, que tambem fecha o
     * teclado, nao fecha a busca junto).
     */
    private void onKeyboardVisibilityChanged(boolean visible) {
        if (visible) {
            keyboardSeen = true;
            return;
        }
        if (!keyboardSeen || !resumed || !hasWindowFocus()) {
            return;
        }
        fecharDeslizando();
    }

    /**
     * Fecha a tela descendo, e só então fechando de verdade.
     *
     * <p>Todos os caminhos de saída passam por aqui: o botão voltar, o arrasto para
     * baixo e o teclado sumindo. A animação tem que ser a última coisa a acontecer
     * antes do {@code finish}, senão o Android corta a tela no meio do deslize.
     *
     * <p>A guarda existe porque dois caminhos podem pedir a saída quase juntos — o
     * botão voltar primeiro esconde o teclado, e o sumiço do teclado também pede
     * para fechar. Sem ela, a tela animationava duas vezes.
     */
    private void fecharDeslizando() {
        if (saindo) {
            return;
        }
        saindo = true;
        SlideAnimation.sairParaBaixo(root, this::finish);
    }

    // -------------------------------------------------------------- resultado

    /**
     * Pede o resultado para o texto atual.
     *
     * <p>A busca acontece fora da main thread: ler todos os apps instalados
     * custa meio segundo, e foi justamente na main thread que a primeira tecla
     * ficava esperando. A resposta vem para a main thread e so vale se ainda for
     * a consulta que o usuario esta digitando.
     */
    private void update(@NonNull String query) {
        int token = ++queryToken;
        AppRepository.get(this).searchAsync(query, matches -> {
            if (token != queryToken || isFinishing() || isDestroyed()) {
                return;
            }
            show(matches);
        });
    }

    /** Desenha o resultado ranqueado: um app e a contagem do que ficou de fora. */
    private void show(@NonNull List<AppEntry> matches) {
        if (matches.isEmpty()) {
            match = null;
            resultBlock.setVisibility(View.GONE);
            emptyHint.setVisibility(View.VISIBLE);
            emptyHint.setText(TextUtils.isEmpty(search.getText())
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

    /** Abre o app que a tela esta mostrando. */
    private void launchMatch() {
        if (match != null) {
            match.launch(this);
        }
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
