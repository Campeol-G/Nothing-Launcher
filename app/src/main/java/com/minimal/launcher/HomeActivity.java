package com.minimal.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.apps.AppEntry;
import com.minimal.launcher.apps.AppListAdapter;
import com.minimal.launcher.apps.AppListCallback;
import com.minimal.launcher.apps.AppMenu;
import com.minimal.launcher.apps.FavoritesStore;
import com.minimal.launcher.settings.SettingsActivity;

import java.util.List;

/**
 * Tela unica do launcher: hora e data no topo, depois os apps escolhidos pelo
 * usuario, escritos sem icone e ancorados perto do polegar da ma dominante.
 *
 * <p>A lista nunca rola. O que sobra do limite vai para a busca, que e o
 * destino do gesto de deslizar para cima.
 *
 * <p>O relogio e a data ficam fora do RecyclerView, fixos no topo: nenhum
 * container rolavel os envolve, entao eles nao rolam de jeito nenhum.
 */
public final class HomeActivity extends AppCompatActivity
        implements AppListCallback, AppMenu.Listener, SystemTimeWatcher.Listener {

    private EdgeGestureLayout root;
    private LinearLayout header;
    private TextView clock;
    private TextView date;
    private RecyclerView favoritesList;

    private Prefs prefs;
    private FavoritesStore favoritesStore;
    private AppListAdapter favoritesAdapter;
    private SystemTimeWatcher timeWatcher;
    private int maxFavorites;
    private int headerHeightPx;

    /** Preferencias lidas na construcao. Quando mudam, a tela e recriada. */
    private String appliedPrefs = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyNightMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        prefs = new Prefs(this);
        favoritesStore = new FavoritesStore(this);

        root = findViewById(R.id.root);
        header = findViewById(R.id.header);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        favoritesList = findViewById(R.id.favorites_list);

        ThemeHelper.applyInsets(this, root, 26, 22, 12);
        ThemeHelper.paint(this, root, false);
        ThemeHelper.applySystemBars(this);

        applyHeaderHeight();
        applyTypography();
        applyClockAutosize();

        favoritesAdapter = new AppListAdapter(this, visibleFavorites(), this);
        favoritesList.setLayoutManager(new LinearLayoutManager(this));
        favoritesList.setAdapter(favoritesAdapter);

        // Centralizada na vertical, na lateral da ma dominante: onde o polegar
        // alcanca sem o olhar ter que descer ate o rodape da tela.
        int horizontal = Prefs.ALIGN_RIGHT.equals(prefs.getDominantHand())
                ? Gravity.END : Gravity.START;
        ViewGroup.LayoutParams raw = favoritesList.getLayoutParams();
        FrameLayout.LayoutParams params = (raw instanceof FrameLayout.LayoutParams)
                ? (FrameLayout.LayoutParams) raw
                : new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_VERTICAL | horizontal;
        favoritesList.setLayoutParams(params);
        favoritesList.setPadding(0, 0, 0, 0);

        // O gravity acima centraliza na tela inteira, mas o topo pertence ao
        // bloco do relógio. Deslocar meia altura do cabeçalho é a correção exata
        // para centralizar no espaço que sobra abaixo dele.
        favoritesList.setTranslationY(headerHeightPx / 2f);

        maxFavorites = LauncherUtils.getMaxFavorites(this);

        // Sem gesto de arrastar para a busca, de propósito.
        //
        // Com a navegação por gestos do Android, arrastar para cima a partir da
        // borda inferior pertence ao sistema: é o gesto que abre o drawer, e é
        // também o movimento que o polegar faz para alcançar a lista. Quando o
        // app o interceptava, buscar e trocar de app viravam o mesmo gesto — e a
        // busca disparava sem querer no meio de outra ação.
        //
        // Busca e Configurações foram para toque longo, em regiões diferentes da
        // tela: o corpo abre a busca, o relógio abre as Configurações.
        root.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        });

        View header = findViewById(R.id.header);
        header.setOnLongClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        });

        // Na tela inicial o voltar nao faz nada: e a ultima activity do app.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
            }
        });

        appliedPrefs = prefsSignature();
        updateClock();
    }

    @Override
    protected void onStart() {
        super.onStart();
        timeWatcher = new SystemTimeWatcher(this, this);
        timeWatcher.start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Alinhamento, fonte, cor e o proprio limite de favoritos mudam o desenho
        // da tela. Recriar e mais barato do que um mecanismo de "atualize-se" em
        // uma tela so, e a transicao esta desligada por estilo.
        String current = prefsSignature();
        if (!current.equals(appliedPrefs)) {
            appliedPrefs = current;
            recreate();
            return;
        }

        ThemeHelper.applySystemBars(this);
        updateClock();
        // Favoritos podem ter sido adicionados na busca ou removidos no menu.
        favoritesAdapter.setItems(visibleFavorites());
    }

    @Override
    protected void onStop() {
        timeWatcher.stop();
        super.onStop();
    }

    /**
     * Preferências e medidas que mudam o desenho da tela inicial.
     *
     * <p>Entra a escala efetiva, e não só a preferência: girar o aparelho muda
     * a altura útil e portanto o ajuste automático da tipografia.
     */
    private String prefsSignature() {
        return prefs.getDominantHand() + '|'
                + LauncherUtils.getFontScale(this) + '|'
                + LauncherUtils.getFontFactor(this) + '|'
                + prefs.getColorCombo() + '|'
                + prefs.getFontCombo();
    }

    // ------------------------------------------------------------- relogio

    @Override
    public void onSystemTimeChanged() {
        updateClock();
    }

    private void updateClock() {
        long now = System.currentTimeMillis();
        clock.setText(ClockFormatter.time(this, now));
        date.setText(ClockFormatter.dateCompact(this, now));
    }

    // ------------------------------------------------- bloco de hora e data

    /**
     * O bloco de cima ocupa uma fração fixa da tela.
     *
     * <p>Precisa ser em pixels e não em {@code wrap_content}: o relógio usa
     * autoSize dentro do espaço que sobra no bloco, e o autoSize só funciona se a
     * altura for conhecida.
     *
     * <p>Os LayoutParams são de {@link FrameLayout} porque é o pai — o
     * EdgeGestureLayout — que decide essa classe, e não o header, que por acaso
     * é um LinearLayout. Passar LinearLayout.LayoutParams aqui estoura com
     * ClassCastException dentro de {@code FrameLayout.onMeasure}.
     */
    private void applyHeaderHeight() {
        headerHeightPx = LauncherUtils.getHeaderHeightPx(this);
        ViewGroup.LayoutParams raw = header.getLayoutParams();
        FrameLayout.LayoutParams params = (raw instanceof FrameLayout.LayoutParams)
                ? (FrameLayout.LayoutParams) raw
                : new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, headerHeightPx);
        params.height = headerHeightPx;
        header.setLayoutParams(params);
    }

    /** Data e relógio: fonte de cada um e o tamanho do relógio. */
    private void applyTypography() {
        float scale = LauncherUtils.getFontScale(this);
        date.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.date_size) * scale);
        clock.setTypeface(FontCombo.getClockFont(this));
        date.setTypeface(FontCombo.getInterfaceFont(this));
    }

    /**
     * O relógio preenche a faixa que sobra no bloco.
     *
     * <p>O teto é a altura disponível abaixo da data, mas na prática o que limita
     * é a <b>largura</b>: "21:41" usa cerca de 2.6 larguras de glifo, então num
     * celular estreito o texto esbarra na borda antes de chegar no teto de
     * altura. O autoSize reduz nesse caso, e é ele que mantém "9:41", "21:41" e
     * "9:41 AM" dentro da tela sem cortar nem estourar as margins.
     *
     * <p>Se o relógio parecesse pequeno demais num aparelho específico, o ajuste
     * é aqui: subir a fração em {@code LauncherUtils} aumenta a faixa, mas não o
     * glifo — para o glifo crescer é preciso menos padding lateral.
     */
    private void applyClockAutosize() {
        float dateHeight = getResources().getDimension(R.dimen.date_size)
                * LauncherUtils.getFontScale(this);
        int available = Math.max(1, headerHeightPx - Math.round(dateHeight));
        clock.setAutoSizeTextTypeUniformWithConfiguration(
                ThemeHelper.dp(this, 24), available, 2, TypedValue.COMPLEX_UNIT_PX);
    }

    // -------------------------------------------------------- favoritos

    /**
     * Os favoritos que cabem na tela. O que exceder o limite nao e apagado: fica
     * guardado e continua alcancavel pela busca.
     */
    @NonNull
    private List<AppEntry> visibleFavorites() {
        List<AppEntry> all = favoritesStore.getFavorites();
        return all.size() <= maxFavorites
                ? all
                : all.subList(0, maxFavorites);
    }

    @Override
    public void onAppClick(@NonNull AppEntry entry) {
        entry.launch(this);
    }

    @Override
    public boolean onAppLongClick(@NonNull View itemView, @NonNull AppEntry entry) {
        AppMenu.show(this, entry, this, maxFavorites);
        return true;
    }

    @Override
    public void onFavoritesChanged() {
        favoritesAdapter.setItems(visibleFavorites());
    }

    @Override
    public void onMenuDismissed() {
    }
}
