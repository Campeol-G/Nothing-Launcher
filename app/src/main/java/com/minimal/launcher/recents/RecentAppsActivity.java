package com.minimal.launcher.recents;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.EdgeGestureLayout;
import com.minimal.launcher.FontCombo;
import com.minimal.launcher.LauncherUtils;
import com.minimal.launcher.Prefs;
import com.minimal.launcher.R;
import com.minimal.launcher.SlideAnimation;
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
        implements RecentListAdapter.Callback, RecentAppsSource.Callback,
        EdgeGestureLayout.SideGestureBlocker {

    /**
     * O {@link DismissedStore} e o {@link ShizukuTaskKiller} são criados no
     * onCreate, não em inicializador de campo: um inicializador roda antes de o
     * Context ser anexado à activity, e o {@code getApplicationContext()} do store
     * devolveria null.
     */
    /** O app do Shizuku, para o aviso levar direto à tela dele. */
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    /**
     * Quanto da linha o dedo precisa percorrer para ela ser apagada.
     *
     * <p>Metade, que é o padrão do {@link ItemTouchHelper}, é o valor de um gesto
     * de comando: atravessar metade da tela para confirmar algo que a pessoa já
     * decidiu é pedir precisão de outro tipo. Um terço é o ponto em que o dedo já
     * foi longe o bastante para ter pegado a intenção e perto o bastante para a
     * linha ainda não ter saído da tela.
     *
     * <p>Não é tão baixo que um arrasto de rolagem apague: o que entra na conta
     * antes disso é o slop do sistema na horizontal, que é a mesma trava que
     * protege a rolagem da lista.
     */
    private static final float LIMIAR_APAGAR = 0.35f;

    /**
     * De que lado a tela entra, quando ela foi aberta por arrasto.
     *
     * <p>Guarda o lado pelo qual ela entra, e não a direção do dedo: o dedo vai
     * para a direita e a tela vem da esquerda. Quem converte é quem constrói o
     * intent.
     */
    private static final String EXTRA_LADO = "minimal.launcher/LADO";
    private static final int LADO_NENHUM = 0;
    private static final int LADO_DA_ESQUERDA = 1;
    private static final int LADO_DA_DIREITA = 2;

    /**
     * O intent que abre a tela vinda de um arrasto.
     *
     * @param dragDirecao {@link EdgeGestureLayout#DIRECTION_RIGHT} para o dedo ter
     *                    ido para a direita; a tela entra pela esquerda.
     */
    @NonNull
    public static Intent intentEnteringFrom(@NonNull Context context, int dragDirecao) {
        return new Intent(context, RecentAppsActivity.class)
                .putExtra(EXTRA_LADO, dragDirecao == EdgeGestureLayout.DIRECTION_RIGHT
                        ? LADO_DA_ESQUERDA : LADO_DA_DIREITA);
    }

    private ShizukuTaskKiller killer;
    private Prefs prefs;
    private DismissedStore dismissedStore;

    private EdgeGestureLayout root;
    private RecyclerView list;
    private TextView hint;
    private TextView clearAll;
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
     * De que lado a tela entrou, para o voltar sair pelo mesmo lado.
     *
     * <p>Guardado mesmo quando a entrada não é animada: abrir por atalho do sistema
     * também precisa saber para que lado a tela deve ir embora.
     */
    private int ladoDeEntrada = LADO_NENHUM;

    /**
     * Os apps que a tela está mostrando, para o "limpar tudo" saber o que
     * encerrar. Não é a lista do adapter: é sem o botão do fim.
     */
    private final List<AppEntry> shown = new ArrayList<>();

    /**
     * De onde a lista está na tela, para converter o toque em coordenada dela.
     *
     * <p>Reaproveitado porque {@link #temAppEm} é chamado a cada toque e a lista
     * não anda entre um {@code DOWN} e o arrasto que ele começa.
     */
    private final int[] origemDaLista = new int[2];

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.applyNightMode(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recent_apps);

        EdgeGestureLayout raiz = findViewById(R.id.root);
        root = raiz;
        list = findViewById(R.id.recent_apps_list);
        hint = findViewById(R.id.recent_hint);
        clearAll = findViewById(R.id.recent_clear_all);
        prefs = new Prefs(this);
        dismissedStore = new DismissedStore(this);
        killer = new ShizukuTaskKiller(getPackageName());

        ThemeHelper.applyInsets(this, root, 26, 30, 18);
        ThemeHelper.applySystemBars(this);
        ThemeHelper.paint(this, root, false);

        // Deslizar para baixo fecha, como na busca.
        root.setEdgeGesture(EdgeGestureLayout.DIRECTION_DOWN, null, this::finish);

        // Aqui o arrasto horizontal fecha, e para os dois lados. E o inverso exato
        // do gesto que abriu a tela, entao quem chegou na tela por um gesto sai
        // pela mesma dobradura.
        root.setSideGesture(this::sairParaO);

        // ...mas só onde não há app. Em cima de uma linha o mesmo arrasto é o do
        // app, e ele sai da lista. Decidido no DOWN, não a cada MOVE: o dono do
        // gesto é de onde o dedo partiu.
        root.setSideGestureBlocker(this);

        // O voltar do Android fecha a tela, e ela sai pelo mesmo lado em que
        // entrou. Sem isto o voltar é um corte seco no meio de um launcher que
        // desliza — e a tela de recentes é a única em que o gesto é o caminho
        // principal, então o corte aparecia logo depois de usar o gesto.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Entrou pela esquerda, sai pela direita: a dobra é a mesma das
                // duas pontas. Aberta por atalho do sistema, sem lado, sai pela
                // direita.
                sairParaO(ladoDeEntrada == LADO_DA_DIREITA
                        ? EdgeGestureLayout.DIRECTION_LEFT
                        : EdgeGestureLayout.DIRECTION_RIGHT);
            }
        });

        // Se veio por arrasto, a tela entra deslizando pelo lado oposto ao dedo.
        // Sem isso (aberta por atalho do sistema) nao ha animacao nenhuma.
        ladoDeEntrada = getIntent().getIntExtra(EXTRA_LADO, LADO_NENHUM);
        if (savedInstanceState == null) {
            animarEntrada(getIntent());
        }

        LinearLayoutManager linhas = new LinearLayoutManager(this);
        // A lista se levanta do botão, e não desce do topo. Sem isto o primeiro app
        // nasce colado no status bar — o ponto mais distante do polegar, para a tela
        // cujo botão fica embaixo.
        linhas.setStackFromEnd(true);
        list.setLayoutManager(linhas);
        list.setAdapter(adapter = new RecentListAdapter(this, new ArrayList<>(), this));

        // O arrasto que apaga a linha. Ele vive no RecyclerView, e é por isso que a
        // tela precisa recusar o gesto dele: os dois escutam o mesmo arrasto, e sem
        // a recusa o app sairia da lista e a tela fecharia no mesmo gesto.
        ligarArrastoParaApagar();

        // O pé e a dica usam o mesmo estilo das linhas de apps, então precisam do
        // mesmo tratamento: fonte e cor em runtime, como o adapter faz.
        int foreground = ThemeHelper.getHomeForegroundColor(this);
        float scale = LauncherUtils.getFontScale(this);
        for (TextView footer : new TextView[]{clearAll, hint}) {
            footer.setTypeface(FontCombo.getInterfaceFont(this));
            footer.setTextColor(foreground);
        }
        clearAll.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.recent_clear_all_size) * scale);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(R.dimen.recent_hint_size) * scale);

        // O "Limpar tudo" é uma view fixa, e não uma linha: fica no mesmo lugar
        // sempre, com a mesma área de toque, sem depender de rolagem.
        clearAll.setOnClickListener(v -> onClearAll());
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
            clearAll.setVisibility(View.GONE);
            showHint(R.string.recent_shizuku_required, this::openShizuku);
            return;
        }
        shown.clear();
        shown.addAll(entries);
        publish();
    }

    private void publish() {
        // A lista não tem mais a linha do "Limpar tudo": ele é uma view fixa no
        // pé, e ela existe para a lista poder rolar por cima.
        adapter.setItems(shown);
        list.setVisibility(View.VISIBLE);
        atualizarPe();
    }

    /**
     * O pé da tela, que é uma função da lista e nada mais.
     *
     * <p>Com app na lista, o "Limpar tudo" é a única ação da tela e fica embaixo.
     * Sem nenhum, ele some — limpar uma lista vazia não é ação, é um botão que
     * mente — e a frase assume o meio da tela, que é onde ela é lida.
     *
     * <p>Fica separada de {@link #publish()} porque quem apaga uma linha chega
     * aqui pelo {@code onDismiss}, e republicar a lista ali cancelaria a animação
     * de saída da linha que o dedo acabou de arrastar.
     */
    private void atualizarPe() {
        if (shown.isEmpty()) {
            // Lista vazia é estado real: nada aberto. E ela não é acionável, então
            // não finge ser botão.
            clearAll.setVisibility(View.GONE);
            showHint(R.string.recent_empty, null);
        } else {
            clearAll.setVisibility(View.VISIBLE);
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

    // -------------------------------------------------------------- toques

    @Override
    public void onAppClick(@NonNull AppEntry entry) {
        entry.launch(this);
    }

    @Override
    public void onDismiss(@NonNull AppEntry entry, int position) {
        // A dispensa vai primeiro: ela é o que tira o app da lista, e é ela que
        // sobrevive à falta de Shizuku. Encerrar é o que vem depois, e pode não
        // acontecer — o app continua aberto, que é o que o X e o arrasto prometem.
        // O instante é o do relogio monotonic: e ele que diz a tela se a task e de
        // antes ou de depois da dispensa. Um app reaberto pela pessoa tem task mais
        // nova que a dispensa e volta para a lista sozinho.
        dismissedStore.dismiss(entry.packageName, android.os.SystemClock.elapsedRealtime());
        shown.remove(entry);
        adapter.removeAt(position);
        // A linha que saiu pode ter sido a última: o pé e uma função da lista.
        atualizarPe();
        killOffMainThread(() -> killer.kill(entry.packageName));
    }

    // -------------------------------------------------------------- gesto

    /**
     * O arrasto que apaga a linha, para os dois lados.
     *
     * <p>É o mesmo caminho do X, e é proposital que os dois existam: o X é o alvo
     * que o dedo acha sem olhar, e o arrasto é o gesto curto para quem já sabe
     * que a linha está lá. Os telefones que fazem swipe para dispensar fazem os
     * dois, e o mesmo dedo prefere o atalho quando já está na linha.
     *
     * <p>Só dispensa, não arrasta: a ordem das linhas é a do sistema, que a lista
     * inteira reflete, e um reordenamento aqui seria uma ordem que não existe em
     * lugar nenhum. Por isso o primeiro argumento do callback é zero.
     *
     * <p>O limiar é menor que o do gesto que fecha a tela. Apagar é uma ação de
     * leitura — a pessoa já decidiu, o dedo só confirma — e esperar metade da linha
     * seria pedir precisão de comando. Não é tão baixo que um arrasto de rolagem
     * apague: {@link ItemTouchHelper} só conta o que passa do slop na horizontal.
     */
    private void ligarArrastoParaApagar() {
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direcao) {
                // A posição vem do holder, não do gesto: entre o arrasto e este
                // callback a lista pode ter rolado, e um índice guardado antes
                // apontaria para a linha errada.
                int posicao = viewHolder.getBindingAdapterPosition();
                if (posicao == RecyclerView.NO_POSITION || posicao >= shown.size()) {
                    return;
                }
                onDismiss(shown.get(posicao), posicao);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return LIMIAR_APAGAR;
            }
        }).attachToRecyclerView(list);
    }

    /**
     * O arrasto lateral é desta tela, ou de um app?
     *
     * <p>Responde ao {@code DOWN}, quando ainda se sabe de onde o dedo partiu.
     * Sob uma linha, o arrasto é dela; no vazio de cima, nas margens e no rodapé,
     * não há app, e o gesto fecha a tela como em qualquer outra.
     *
     * <p>{@link #findChildViewUnder} devolve {@code null} justamente no que não é
     * linha: o vazio que sobra acima da lista, porque ela cresce de baixo para
     * cima, e o respiro do {@code padding}, porque a busca do RecyclerView desconta
     * o padding antes de olhar. Não é preciso medir nada aqui.
     */
    @Override
    public boolean temAppEm(float rawX, float rawY) {
        list.getLocationOnScreen(origemDaLista);
        return list.findChildViewUnder(rawX - origemDaLista[0], rawY - origemDaLista[1]) != null;
    }

    /**
     * A tela entrando deslizando pelo lado oposto ao do dedo.
     *
     * <p>Arrastou para a direita, a tela vem da esquerda — o olho já sabe para que
     * lado a coisa vai, e a animação só confirma. É o mesmo princípio do teclado: a
     * coisa nova nasce perto de onde o dedo já estava.
     *
     * <p>Sem animação quando não há arrasto na origem. Uma tela que abre deslizando
     * sozinha, sem gesto nenhum atrás, é só enfeite.
     */
    private void animarEntrada(@NonNull Intent deOnde) {
        ladoDeEntrada = deOnde.getIntExtra(EXTRA_LADO, LADO_NENHUM);
        if (ladoDeEntrada == LADO_NENHUM) {
            return;
        }
        SlideAnimation.entrarDoLado(root,
                ladoDeEntrada == LADO_DA_ESQUERDA
                        ? SlideAnimation.LADO_ESQUERDO : SlideAnimation.LADO_DIREITO);
    }

    /**
     * A tela saindo na direção do dedo.
     *
     * <p>O gesto de fechar vai pelo mesmo caminho que o de abrir, ao contrário: o
     * dedo vai para a direita e a tela vai com ele.
     */
    private void sairParaO(int direcao) {
        SlideAnimation.sairParaOLado(root, direcao, this::finish);
    }

    /** O pé da tela chama isto. Não vem do adapter: o botão saiu da lista. */
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