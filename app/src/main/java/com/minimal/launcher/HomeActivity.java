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

import com.minimal.launcher.WallpaperHelper;
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
    private TextView clockMinutes;
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
        clockMinutes = findViewById(R.id.clock_minutes);
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

        // Logo abaixo do relógio, na lateral da ma dominante: onde o polegar
        // alcanca e com o horario em vista. A margem superior e a altura do
        // cabecalho mais uma folga curta, e o gravity top faz o resto.
        int horizontal = Prefs.ALIGN_RIGHT.equals(prefs.getDominantHand())
                ? Gravity.END : Gravity.START;
        ViewGroup.LayoutParams raw = favoritesList.getLayoutParams();
        FrameLayout.LayoutParams params = (raw instanceof FrameLayout.LayoutParams)
                ? (FrameLayout.LayoutParams) raw
                : new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.TOP | horizontal;
        params.topMargin = headerHeightPx + clockListGapPx();
        favoritesList.setLayoutParams(params);
        favoritesList.setPadding(0, 0, 0, 0);

        maxFavorites = LauncherUtils.getMaxFavorites(this);

        // Abrir a busca: arrasto para cima que comece ACIMA da faixa de gesto do
        // sistema, ou toque longo na área vazia.
        //
        // O arrasto é recusado quando nasce na faixa do sistema: o dedo sai da
        // borda inferior para trocar de app, e se o launcher respondesse ali os
        // dois gestos seriam o mesmo. Acima da faixa não há disputa — o drawer só
        // existe a partir da borda.
        root.setEdgeGesture(EdgeGestureLayout.DIRECTION_UP, favoritesList,
                () -> startActivity(new Intent(this, SearchActivity.class)));

        // O gesto horizontal abre a tela de apps recentes, na direcao dos apps: com
        // a mao direita o arrasto corre da esquerda para a direita. Ele corre
        // contra o gesto de voltar do sistema, que mora na mesma borda — aqui o
        // lancador pode tomar a decisao, e voltar a partir da tela inicial nao
        // perde nada.
        LauncherUtils.bindRecentAppsGesture(root);

        // Toque longo na area vazia tambem abre a busca: e o caminho que nao
        // depende de acertar o comeco do arrasto.
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

        // O wallpaper do aparelho, se a pessoa mandou seguir a cor. Aqui, e não na
        // inicialização do processo: o item pode ter sido ligado a qualquer momento,
        // e a cor pode ter mudado desde a última vez que a tela ficou à frente.
        WallpaperHelper.aplicarSePrecisar(this);

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
     * <p>Entra a escala efetiva, e não só a preferência, porque é ela que aparece
     * em cada {@code setTextSize}.
     *
     * <p>A orientação não entra, e antes entrava. Girar o aparelho mudava a
     * altura útil, que mudava o ajuste automático da tipografia, que fazia a
     * assinatura mudar na volta — e a tela se recreava com as medidas da
     * orientação anterior. Agora o ajuste automático mede a borda mais longa, que
     * não muda ao girar, e a rotação não passa por aqui: o manifesto tira esta
     * tela do configChanges de orientação, e o Android refaz o onCreate inteiro.
     */
    private String prefsSignature() {
        return prefs.getDominantHand() + '|'
                + LauncherUtils.getFontScale(this) + '|'
                + LauncherUtils.getFontFactor(this) + '|'
                + prefs.getColorCombo() + '|'
                + prefs.getFontCombo() + '|'
                + prefs.getClockFormat() + '|'
                + prefs.getDateFormat();
    }

    // ------------------------------------------------------------- relogio

    @Override
    public void onSystemTimeChanged() {
        updateClock();
    }

    private void updateClock() {
        long now = System.currentTimeMillis();
        boolean stacked = Prefs.CLOCK_STACKED.equals(prefs.getClockFormat());
        if (stacked) {
            clock.setText(ClockFormatter.hours(this, now));
            clockMinutes.setText(ClockFormatter.minutes(this, now));
        } else {
            clock.setText(ClockFormatter.time(this, now));
        }
        date.setText(Prefs.DATE_NUMERIC.equals(prefs.getDateFormat())
                ? ClockFormatter.dateNumeric(this, now)
                : ClockFormatter.dateExtended(this, now));
    }

    /** Folga entre o fim do relógio e o começo da lista de apps. */
    private int clockListGapPx() {
        return getResources().getDimensionPixelSize(R.dimen.clock_list_gap);
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

    /**
     * Data e relógio: fonte de cada um, e o desenho conforme o formato escolhido.
     *
     * <p>Empilhado mostra as horas em {@code clock} e os minutos em
     * {@code clock_minutes}; linha única esconde os minutos e põe o horário
     * inteiro em {@code clock}. Esconder devolve o peso ao relógio de cima, então
     * ele passa a ocupar a faixa toda sozinho.
     */
    private void applyTypography() {
        float scale = LauncherUtils.getFontScale(this);
        boolean stacked = Prefs.CLOCK_STACKED.equals(prefs.getClockFormat());
        boolean condensed = !stacked;

        clockMinutes.setVisibility(stacked ? View.VISIBLE : View.GONE);

        date.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.date_size) * scale);
        clock.setTypeface(condensed
                ? FontCombo.getClockFontCondensed(this)
                : FontCombo.getClockFont(this));
        clockMinutes.setTypeface(FontCombo.getClockFont(this));
        date.setTypeface(FontCombo.getInterfaceFont(this));
    }

    /**
     * O relógio preenche a faixa que sobra no bloco.
     *
     * <p>Os dois campos, no formato empilhado, dividem o espaço por igual: cada
     * um recebe metade do que sobraria de um horário inteiro, e como só tem dois
     * dígitos cada um esbarra muito menos na largura. Na linha única, o limite
     * continua sendo a largura — por isso a fonte condensada — e o autoSize
     * reduz para nunca cortar o texto.
     *
     * <p>O teto é a altura disponível abaixo da data, e o piso é pequeno de
     * propósito: com dois dígitos na tela é impossível o texto ficar ilegível.
     */
    private void applyClockAutosize() {
        float dateHeight = getResources().getDimension(R.dimen.date_size)
                * LauncherUtils.getFontScale(this);
        int available = Math.max(1, headerHeightPx - Math.round(dateHeight));
        int floor = ThemeHelper.dp(this, 24);

        // Empilhado: metade para cada linha. Linha única: a faixa inteira.
        boolean stacked = Prefs.CLOCK_STACKED.equals(prefs.getClockFormat());
        int ceiling = stacked ? available / 2 : available;
        ceiling = Math.max(floor + 2, ceiling);

        clock.setAutoSizeTextTypeUniformWithConfiguration(
                floor, ceiling, 2, TypedValue.COMPLEX_UNIT_PX);
        clockMinutes.setAutoSizeTextTypeUniformWithConfiguration(
                floor, ceiling, 2, TypedValue.COMPLEX_UNIT_PX);
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
