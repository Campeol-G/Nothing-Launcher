package com.minimal.launcher;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.apps.AllAppsActivity;
import com.minimal.launcher.apps.AppEntry;
import com.minimal.launcher.apps.AppListAdapter;
import com.minimal.launcher.apps.AppListCallback;
import com.minimal.launcher.apps.AppMenu;
import com.minimal.launcher.apps.FavoritesStore;
import com.minimal.launcher.services.LauncherLockService;
import com.minimal.launcher.services.LauncherNotificationListener;
import com.minimal.launcher.services.NotificationState;
import com.minimal.launcher.settings.SettingsActivity;

import java.util.List;

/**
 * Tela principal unica: relogio fixo no topo, lista de favoritos sem limite
 * abaixo, nada mais.
 *
 * <p>O relogio nao rola porque nao esta dentro de nenhum container rolavel - ele
 * e irmao da lista, nao pai dela.
 */
public final class HomeActivity extends AppCompatActivity
        implements AppListCallback, AppMenu.Listener, NotificationState.Listener,
        SystemTimeWatcher.Listener {

    private EdgeGestureLayout root;
    private TextView clock;
    private TextView date;
    private View notificationDot;
    private RecyclerView favoritesList;

    private Prefs prefs;
    private FavoritesStore favoritesStore;
    private AppListAdapter favoritesAdapter;
    private SystemTimeWatcher timeWatcher;
    private String appliedPrefs = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyNightMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        prefs = new Prefs(this);
        favoritesStore = new FavoritesStore(this);

        root = findViewById(R.id.root);
        clock = findViewById(R.id.clock);
        date = findViewById(R.id.date);
        notificationDot = findViewById(R.id.notification_dot);
        favoritesList = findViewById(R.id.favorites_list);

        ThemeHelper.applyInsets(this, root, 26, 22, 12);
        ThemeHelper.paint(root);
        ThemeHelper.applySystemBars(this);
        applyFontScale();

        favoritesList.setLayoutManager(new LinearLayoutManager(this));
        favoritesList.setPadding(0, 0, 0, ThemeHelper.dp(favoritesList, 20));
        favoritesAdapter = new AppListAdapter(this, favoritesStore.getFavorites(),
                AppListAdapter.MODE_LAUNCH, this);
        favoritesList.setAdapter(favoritesAdapter);

        // Deslizar para cima abre a lista de todos os apps; o portão do
        // EdgeGestureLayout decide se o gesto é nosso ou da lista.
        root.setEdgeGesture(EdgeGestureLayout.DIRECTION_UP, favoritesList,
                () -> startActivity(new Intent(this, AllAppsActivity.class)));
        root.setOnDoubleTapListener(this::onDoubleTap);

        View header = findViewById(R.id.header);
        View.OnLongClickListener openSettings = v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        };
        header.setOnLongClickListener(openSettings);
        root.setOnLongClickListener(openSettings);

        // O botão voltar na tela principal não faz nada.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
            }
        });

        applyListPlacement();
        appliedPrefs = prefsSignature();
        updateClock();
    }

    @Override
    protected void onStart() {
        super.onStart();
        timeWatcher = new SystemTimeWatcher(this, this);
        timeWatcher.start();
        NotificationState.setListener(this);
        refreshFavorites();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Alinhamento, fonte e posição são lidos pelo adapter e pelo layout, não
        // observados. Em vez de um mecanismo de "atualize-se", recriamos: é uma
        // tela só e a animação de transition está desligada por estilo.
        String current = prefsSignature();
        if (!current.equals(appliedPrefs)) {
            appliedPrefs = current;
            recreate();
            return;
        }

        ThemeHelper.applySystemBars(this);
        updateClock();
        updateNotificationDot(NotificationState.getCount());
    }

    /** Só as preferências que a tela principal lê na construção. */
    private String prefsSignature() {
        return prefs.getAlignment() + '|' + prefs.getVerticalPosition() + '|' + prefs.getFontScale();
    }

    @Override
    protected void onStop() {
        timeWatcher.stop();
        NotificationState.clearListener(this);
        super.onStop();
    }

    // ---------------------------------------------------------------- relógio

    @Override
    public void onSystemTimeChanged() {
        updateClock();
    }

    private void updateClock() {
        long now = System.currentTimeMillis();
        clock.setText(ClockFormatter.time(this, now));
        date.setText(ClockFormatter.date(this, now));
    }

    // ------------------------------------------------------------- preferências

    private void applyFontScale() {
        float scale = prefs.getFontScale();
        clock.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.clock_size) * scale);
        date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.date_size) * scale);
    }

    /** Centralizada no espaço livre ou ancorada perto do polegar. */
    private void applyListPlacement() {
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) favoritesList.getLayoutParams();
        params.gravity = Prefs.VPOS_BOTTOM.equals(prefs.getVerticalPosition())
                ? Gravity.BOTTOM
                : Gravity.CENTER;
        favoritesList.setLayoutParams(params);
    }

    // ---------------------------------------------------------------- favoritos

    private void refreshFavorites() {
        favoritesAdapter.setItems(favoritesStore.getFavorites());
    }

    @Override
    public void onAppClick(@NonNull AppEntry entry) {
        entry.launch(this);
    }

    @Override
    public boolean onAppLongClick(@NonNull View itemView, @NonNull AppEntry entry) {
        AppMenu.show(this, entry, this);
        return true;
    }

    @Override
    public void onFavoritesChanged() {
        refreshFavorites();
    }

    @Override
    public void onMenuDismissed() {
    }

    // ----------------------------------------------------------- notificações

    @Override
    public void onActiveNotificationsChanged(int count) {
        updateNotificationDot(count);
    }

    private void updateNotificationDot(int count) {
        boolean visible = prefs.getNotificationDot()
                && LauncherNotificationListener.isConnected()
                && count > 0;
        notificationDot.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            // O drawable e um circulo branco; o tint segue o tema.
            notificationDot.setBackgroundTintList(ColorStateList.valueOf(
                    ThemeHelper.foregroundColor(this)));
        }
    }

    // ------------------------------------------------------------------- misc

    /** Duplo toque bloqueia, se o usuário ligou e o serviço de acessibilidade está ativo. */
    private void onDoubleTap() {
        if (!prefs.getDoubleTapLock()) {
            return;
        }
        LauncherLockService.lockScreen();
    }
}