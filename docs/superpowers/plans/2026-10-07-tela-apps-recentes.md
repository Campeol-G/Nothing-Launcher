# Tela de apps recentes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Uma tela que lista os apps usados recentemente em só texto, com um X por linha que tira o app da lista, aberta por atalho do sistema e fora da lista de apps do launcher.

**Architecture:** `RecentAppsSource` lê o `UsageStatsManager` e entrega `List<AppEntry>` ordenada por uso recente. `RecentRanker` faz o trabalho puro — ordenar, deduplicar por pacote e esconder o que foi dispensado — sem nenhuma dependência de Android, e é o único ponto com teste automatizado. `DismissedStore` persiste a dispensa como `pacote -> instante`, e a comparação com `lastTimeUsed` faz o app voltar sozinho quando é reaberto. `TaskKiller` isola o Shizuku, que é melhor-esforço: o X sempre tira da lista, encerrar é o bônus.

**Tech Stack:** Java 17, Android SDK 37, minSdk 26, Gradle 9.8.0 + AGP 9.4.1. RecyclerView, `UsageStatsManager`, Shizuku 13.1.5 (`dev.rikka.shizuku:api` + `:provider`). JUnit 4.13.2 para o único teste automatizado do projeto.

**Spec:** `docs/superpowers/specs/2026-10-07-tela-apps-recentes-design.md`

## Global Constraints

- Java 17, sem Kotlin. `minSdk 26`, `compileSdk 37`, `targetSdk 37`.
- **Sem ícone e sem miniatura em toda a tela.** Nenhum `ImageView`, nenhum `loadIcon`, nenhum `getActivityIcon`. Só texto.
- **`PACKAGE_USAGE_STATS` é a única permissão nova.** Nada de serviço, nada de receptor de tela cheia, nada de acessibilidade.
- **Nenhuma classe existente muda de assinatura.** O único acréscimo em código existente é `AppRepository.executeAsync`.
- A tela **não recebe `CATEGORY_LAUNCHER`** — é isso que a mantém fora da lista de apps e da busca.
- Cor e fonte são resolvidas em runtime por `ThemeHelper.getHomeForegroundColor()` e `FontCombo.getInterfaceFont()`. Nunca hardcodar cor nem fonte.
- O `X` **sempre** tira o app da lista, para qualquer app. Encerrar é bônus e nunca écondicional do layout.
- Falha do Shizuku é caminho normal, não exceção. Nenhuma chamada de `TaskKiller` lança, e nenhum retorno é tratado como erro.
- `SharedPreferences` só é tocado por `Prefs` e pelas classes de store, no formato `\n`-joinado do `FavoritesStore`.
- Nenhum arquivo além do `.gitignore` pode conter segredo. Nada de token.

## Review Focus

Cinco entradas que a spec promete mas que nenhum teste automático vai cobrir — cada uma já está atribuída a uma task abaixo.

1. **Um app com duas tasks no UsageStats.** O `queryUsageStats` devolve um evento por task, não por app: sem dedup, o mesmo nome aparece duas vezes e o X mata a task errada. Verificado em `RecentRankerTest`.
2. **App reaberto depois de dispensado.** A dispensa é por instante, não definitiva; se o app for aberto de novo, `lastTimeUsed` passa a ser maior e ele volta sozinho, sem ação do usuário. Verificado em `RecentRankerTest`.
3. **Shizuku parado.** O X tem que continuar tirando da lista e o app não pode quebrar. Verificado no checklist manual da Task 7, item 7.
4. **App de sistema (relógio, telefone).** Tem X, sai da lista, e o processo continua vivo — `force-stop` não encerra app de sistema e isso não pode ser tratado como erro. Verificado no checklist manual da Task 7, item 10.
5. **Nome longo com mão dominante à esquerda.** O `ellipsize` tem que cortar o nome e o X não pode sair da linha nem trocar de lado. Verificado no checklist manual da Task 7, itens 5 e 12.

---

### Task 1: `RecentRanker` — a lógica pura, com teste

O filtro de dispensa é a única parte desta feature que pode dar errado de forma silenciosa: um `>=` trocado por `>` faz o app nunca voltar, e um filtro fora de ordem esconde o app errado. Por isso ele nasce puro e testado, no mesmo espírito de `AppSearch.java`, que já é a única classe "pura e sem Android" do projeto.

**Files:**
- Modify: `app/build.gradle` (bloco `dependencies`)
- Create: `app/src/main/java/com/minimal/launcher/recents/RecentRanker.java`
- Create: `app/src/test/java/com/minimal/launcher/recents/RecentRankerTest.java`

**Interfaces:**
- Consumes: nada. Esta é a primeira task.
- Produces:
  - `RecentRanker.Use` — classe aninhada com campos públicos `final String packageName` e `final long lastTimeUsed`, construtor `Use(String packageName, long lastTimeUsed)`.
  - `static List<Use> rank(@NonNull List<Use> uses, @NonNull Map<String, Long> dismissedAt)` — devolve lista imutável, mais recente primeiro, sem pacotes repetidos, sem os que têm dispensa vigente. **Não** modifica os argumentos.

- [ ] **Step 1: adicionar JUnit e o `testImplementation`**

Em `app/build.gradle`, no bloco `dependencies`, acrescentar:

```gradle
    testImplementation 'junit:junit:4.13.2'
```

- [ ] **Step 2: escrever o teste que falha**

Criar `app/src/test/java/com/minimal/launcher/recents/RecentRankerTest.java` com estes casos:

```java
public class RecentRankerTest {

    private static RecentRanker.Use use(String pkg, long when) {
        return new RecentRanker.Use(pkg, when);
    }

    @Test public void ordenaDoMaisRecenteParaOMaisAntigo() {
        List<RecentRanker.Use> result = RecentRanker.rank(
            Arrays.asList(use("a", 100L), use("b", 300L), use("c", 200L)),
            Collections.emptyMap());
        assertEquals(Arrays.asList("b", "c", "a"), packagesOf(result));
    }

    @Test public void pacoteComVariasTasksApareceUmaVezComOTimeMaisNovo() {
        List<RecentRanker.Use> result = RecentRanker.rank(
            Arrays.asList(use("a", 100L), use("a", 900L), use("a", 400L)),
            Collections.emptyMap());
        assertEquals(1, result.size());
        assertEquals(900L, result.get(0).lastTimeUsed);
    }

    @Test public void dispensaVigenteEscondeOApp() {
        // dismissedAt == lastTimeUsed esconde: foi dispensado agora.
        List<RecentRanker.Use> result = RecentRanker.rank(
            Arrays.asList(use("a", 500L)),
            Collections.singletonMap("a", 500L));
        assertTrue(result.isEmpty());
    }

    @Test public void appReabertoDepoisVoltaSozinho() {
        // dismissedAt < lastTimeUsed: o app foi usado de novo depois de dispensado.
        List<RecentRanker.Use> result = RecentRanker.rank(
            Arrays.asList(use("a", 900L)),
            Collections.singletonMap("a", 500L));
        assertEquals(1, result.size());
        assertEquals("a", result.get(0).packageName);
    }

    @Test public void dispensaDeUmAppNaoAfetaOutro() {
        List<RecentRanker.Use> result = RecentRanker.rank(
            Arrays.asList(use("a", 500L), use("b", 500L)),
            Collections.singletonMap("a", 500L));
        assertEquals(Arrays.asList("b"), packagesOf(result));
    }

    @Test public void listaVaziaDevolveListaVazia() {
        assertTrue(RecentRanker.rank(Collections.emptyList(), Collections.emptyMap()).isEmpty());
    }

    private static List<String> packagesOf(List<RecentRanker.Use> uses) {
        List<String> out = new ArrayList<>();
        for (RecentRanker.Use u : uses) out.add(u.packageName);
        return out;
    }
}
```

- [ ] **Step 3: rodar o teste para ver que falha**

Run: `gradle :app:testDebugUnitTest --tests '*RecentRankerTest'`
Expected: FAIL com "cannot find symbol: class RecentRanker"

- [ ] **Step 4: implementar `RecentRanker`**

Criar `app/src/main/java/com/minimal/launcher/recents/RecentRanker.java`. Sem import de Android — é o ponto da classe. Usar `Collections.sort` com `Comparator.comparingLong(u -> u.lastTimeUsed).reversed()`, o que deixa o mais recente primeiro; depois percorrer a lista ordenada num `LinkedHashSet<String>` para dedup por pacote, e descartar os que têm `dismissedAt >= lastTimeUsed`. Devolver `Collections.unmodifiableList(...)` sobre o resultado.

O filtro de dispensa compara por pacote, não por posição: para cada `Use`, buscar `dismissedAt.get(u.packageName)`; se ausente, o app está visível.

- [ ] **Step 5: rodar o teste para ver que passa**

Run: `gradle :app:testDebugUnitTest --tests '*RecentRankerTest'`
Expected: PASS, 6 testes

- [ ] **Step 6: commitar**

```bash
git add app/build.gradle app/src/main/java/com/minimal/launcher/recents/RecentRanker.java app/src/test/java/com/minimal/launcher/recents/RecentRankerTest.java
git commit -m "feat(recents): RecentRanker puro ordena, deduplica e aplica a dispensa

A comparacao dismissedAt >= lastTimeUsed decide se um app some da lista, e
um >= trocado por > faz o app nunca mais voltar. Isso nao da para ver
rodando na tela -- a lista simplesmente fica vazia -- entao a regra nasce
pura e testada, como AppSearch.java ja e a unica classe sem Android do
projeto."
```

---

### Task 2: `AppRepository.executeAsync` — a fila única, exposta

`RecentAppsSource` precisa da fila do `AppRepository` para não criar uma segunda, e não pode reaproveitar `searchAsync` — aquele rankeia por texto, e aqui a ordem vem de `lastTimeUsed`. Este é o único ponto tocado em código existente, e é puramente aditivo.

**Files:**
- Modify: `app/src/main/java/com/minimal/launcher/apps/AppRepository.java`

**Interfaces:**
- Consumes: nada novo.
- Produces: `public void executeAsync(@NonNull Runnable background, @NonNull Runnable onMain)` — enfileira `background` em `WORKER` e posta `onMain` em `MAIN` depois que ela terminar. Se `background` lançar `RuntimeException`, `onMain` **não** é chamado e a exceção é engolida, para uma falha de leitura não virar tela branca.

- [ ] **Step 1: implementar `executeAsync`**

Adicionar em `AppRepository`, perto de `searchAsync`. Envolver a execução de `background` em `try/catch RuntimeException`, executando `onMain` só no caminho normal. Manter a docstring no estilo do resto da classe, explicando que a fila é única de propósito.

- [ ] **Step 2: compilar**

Run: `gradle :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Nenhuma assinatura existente mudou, então quem já chamava `searchAsync` continua compilando — isso é o teste.

- [ ] **Step 3: commitar**

```bash
git add app/src/main/java/com/minimal/launcher/apps/AppRepository.java
git commit -m "feat(apps): executeAsync expoe a fila unica para fora da busca

A tela de recentes precisa da mesma fila, para nao competir com a montagem
da lista de apps, mas nao pode reaproveitar searchAsync: aquele metodo
rankeia por texto, e aqui a ordem vem de lastTimeUsed. Metodo novo e
aditivo, nenhuma assinatura existente mudou."
```

---

### Task 3: `DismissedStore` — a dispensa persiste

Sem isto o X não limpa nada: a lista é re-lida do `UsageStatsManager` a cada `onResume`, e a linha volta no próximo clique no atalho. Mesmo formato do `FavoritesStore`.

**Files:**
- Create: `app/src/main/java/com/minimal/launcher/recents/DismissedStore.java`

**Interfaces:**
- Consumes: `Prefs.NAME` (já existe).
- Produces:
  - `DismissedStore(Context context)`
  - `Map<String, Long> snapshot()` — devolve um mapa **copiado**, pacote → instante da dispensa. Devolve vazio se nada foi dispensado.
  - `void dismiss(@NonNull String packageName, long atMillis)`
  - `void clear()`

- [ ] **Step 1: implementar `DismissedStore`**

Construtor guarda `getSharedPreferences(Prefs.NAME, MODE_PRIVATE)` e um `LinkedHashMap` carregado no arranque por `parse()`, lendo a chave `dismissed_apps` do `SharedPreferences`. `snapshot()` devolve `new LinkedHashMap<>(map)`. `dismiss()` põe e chama `persist()`. `clear()` limpa e persiste. `persist()` grava o mapa como `pacote=instante` unidos por `\n`, e a leitura faz o parsing inverso, ignorando entradas malformadas em vez de lançar. Espelhar o estilo e os comentários de `FavoritesStore`.

- [ ] **Step 2: compilar**

Run: `gradle :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: commitar**

```bash
git add app/src/main/java/com/minimal/launcher/recents/DismissedStore.java
git commit -m "feat(recents): DismissedStore guarda pacote e instante da dispensa

A lista e re-lida do UsageStatsManager a cada onResume, entao tirar a linha
so da memoria nao limpa nada: ela volta no proximo atalho. Guardar o
instante, e nao um flag permanente, e o que faz o app voltar sozinho quando
o usuario abre ele de novo -- e o que impede o 'limpar tudo' de virar
apagao definitivo."
```

---

### Task 4: `TaskKiller` e `ShizukuTaskKiller`

O X tira da lista sempre; encerrar é o que ele tenta fazer por baixo. Esta task é justamente para deixar isso uma interface, e não a coisa mais difícil de desfazer depois.

**Files:**
- Modify: `app/build.gradle` (bloco `dependencies`)
- Create: `app/src/main/java/com/minimal/launcher/recents/TaskKiller.java`
- Create: `app/src/main/java/com/minimal/launcher/recents/ShizukuTaskKiller.java`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `interface TaskKiller` com `boolean isAvailable()`, `void kill(@NonNull String packageName)`, `void killAll(@NonNull List<String> packageNames)`.
  - `ShizukuTaskKiller` implementa `TaskKiller`. Construtor sem argumentos. `isAvailable()` devolve `Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PERMISSION_GRANTED`.

Quando `isAvailable()` é falso, `ShizukuTaskKiller` **pede a permissão ao Shizuku** com `Shizuku.requestPermission(ShizukuManagerApi.PERMISSION_GRANTED)` uma única vez, guardado por um flag — senão a tela pediria a cada `onResume`. Isso mostra a tela de consentimento do próprio Shizuku, é a única interação de permissão que o usuário vê, e é o motivo de `TaskKiller` ser interface e não uma chamada direta.

`kill` e `killAll` chamam `Shizuku.newProcess(...)` com `am force-stop <pacote>` e **involucram o resultado em try/catch, nunca propagando**.

- [ ] **Step 1: adicionar as dependências**

Em `app/build.gradle`, no bloco `dependencies`:

```gradle
    implementation 'dev.rikka.shizuku:api:13.1.5'
    implementation 'dev.rikka.shizuku:provider:13.1.5'
```

O `provider` é o que adquire o binder; `api` sozinho não conecta.

- [ ] **Step 2: escrever a interface `TaskKiller`**

Interface com os três métodos, javadoc curto explicando que nenhuma implementação lança e que o resultado de `kill` não indica sucesso — encerrar app de sistema é o resultado esperado, não erro.

- [ ] **Step 3: implementar `ShizukuTaskKiller`**

`isAvailable()` conforme a interface acima. `kill()` chama `Shizuku.newProcess` com `cmd: "am force-stop " + packageName` e fecha o OutputStream. `killAll()` itera chamando `kill()`. Envolver ambos em `try/catch Throwable` retornando em silêncio — Shizuku parado, binder morto ou ROM sem `am` são estados esperados, e o app já trata exceção como caminho normal em `AppEntry.launch()`.

- [ ] **Step 4: compilar**

Run: `gradle :app:assembleDebug`
Expected: BUILD SUCCESSFUL — inclusive resolvendo as duas dependências novas do Maven Central

- [ ] **Step 5: commitar**

```bash
git add app/build.gradle app/src/main/java/com/minimal/launcher/recents/TaskKiller.java app/src/main/java/com/minimal/launcher/recents/ShizukuTaskKiller.java
git commit -m "feat(recents): TaskKiller isola o Shizuku como melhor-esforco

O X tira o app da lista sempre; encerrar e o que ele tenta fazer por baixo.
Separar em interface deixa isso explicito e faz a tela funcionar com o
Shizuku parado -- o que nao encerra nada e ainda assim esvazia a lista.

Nenhuma chamada lanca e o retorno nao indica sucesso: force-stop em app de
sistema nao encerra, e isso e o resultado esperado, nao uma falha."
```

---

### Task 5: a linha com X, layout e adapter

A linha deixa de ser um `TextView`. O X precisa de área de toque de 48dp feita com padding — o glifo "X" não pode ficar enorme — e de `selectableItemBackground` próprio, para o toque no X acender só ele e não a linha inteira.

**Files:**
- Create: `app/src/main/res/layout/item_recent_app.xml`
- Create: `app/src/main/java/com/minimal/launcher/recents/RecentListAdapter.java`
- Create: `app/src/main/res/layout/activity_recent_apps.xml`

**Interfaces:**
- Consumes: `AppEntry` (existente), `Prefs.getDominantHand()` e `Prefs.ALIGN_RIGHT` (existentes), `AppRepository.getAllApps()` (existente).
- Produces:
  - `RecentListAdapter(Context context, List<AppEntry> items, Callback callback)`
  - `interface RecentListAdapter.Callback` com `void onAppClick(@NonNull AppEntry entry)`, `void onDismiss(@NonNull AppEntry entry, int position)`, `void onClearAll()`
  - `void setItems(@NonNull List<AppEntry> items)`
  - `void removeAt(int position)`

- [ ] **Step 1: criar `item_recent_app.xml`**

Raiz `LinearLayout` horizontal, `match_parent`/`wrap_content`, `paddingTop` e `paddingBottom` de `@dimen/app_row_padding`, `background="?android:attr/selectableItemBackground"`, `clickable` e `focusable`. Dois filhos:

- `TextView` id `recent_app_name`: `layout_width="0dp"`, `layout_weight="1"`, `layout_height="wrap_content"`, `includeFontPadding="false"`, `maxLines="1"`, `ellipsize="end"`, `singleLine="true"`, `elegantTextHeight="true"`.
- `TextView` id `recent_app_dismiss`: `layout_width="wrap_content"`, `layout_height="wrap_content"`, `text="@string/recent_dismiss"`, `padding` de 14dp em todos os lados (o glifo somado ao padding dá a área de toque de 48dp), `background="?android:attr/selectableItemBackground"`, `clickable="true"`, `focusable="true"`, `gravity="center"`.

- [ ] **Step 2: criar `activity_recent_apps.xml`**

Raiz `com.minimal.launcher.EdgeGestureLayout` com id `root`, igual ao de `activity_home.xml`. Dentro, um `RecyclerView` id `recent_apps_list` com `layout_width="match_parent"`, `layout_height="match_parent"`, `paddingTop="@dimen/clock_list_gap"`, `clipToPadding="false"`, `overScrollMode="never"`, `scrollbars="none"`, `contentDescription="@string/recent_list_description"`.

Sem `layout_weight`, sem cabeçalho: o `EdgeGestureLayout` é um `FrameLayout`.

- [ ] **Step 3: implementar `RecentListAdapter`**

Espelhar a estrutura de `AppListAdapter` (mesmos campos `baseTextSizePx`, `fontScale`, `textColor`, mesmo `gravityFor` lendo `Prefs.getDominantHand()`), mas com duas diferenças:

- `onCreateViewHolder` infla `item_recent_app.xml` e segura **os dois** `TextView`.
- `onBindViewHolder` aplica fonte e cor nos dois, e no `dismiss` aplica `alpha 0.7f` — a mesma cor secundária que `SettingsActivity` usa para valores. O nome recebe `textGravity`; o X fica sempre na ponta oposta, alinhado por `layout_gravity` conforme a mão dominante: mão direita → nome encosta na direita e X fica à esquerda; mão esquerda → o inverso.

O clique no nome vai para `onAppClick`. O clique no X vai para `onDismiss(entry, getBindingAdapterPosition())`. `onLongClick` no item **não** é conectado — a tela de recentes não abre menu.

- [ ] **Step 4: adicionar as strings novas em `values/strings.xml`**

`recent_list_description` = "Lista de apps recentes"; `recent_dismiss` = "×"; `recent_clear_all` = "Limpar tudo"; `recent_empty` = "Nenhum app recente"; `recent_usage_permission_hint` = "Ligue o acesso a dados de uso nas configurações do sistema para ver os apps recentes"; `recent_shizuku_missing` = "O Shizuku precisa estar rodando para encerrar apps".

- [ ] **Step 5: compilar**

Run: `gradle :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: commitar**

```bash
git add app/src/main/res/layout/item_recent_app.xml app/src/main/res/layout/activity_recent_apps.xml app/src/main/java/com/minimal/launcher/recents/RecentListAdapter.java app/src/main/res/values/strings.xml
git commit -m "feat(recents): linha com nome e X, espelhando a mao dominante

A linha deixa de ser um TextView: nome com weight=1 e ellipsize, e o X do
lado. O X tem area de toque de 48dp feita com padding, para o glifo nao
ficar enorme, e selectableItemBackground proprio -- tocar no X acende so
ele, e nao a linha inteira, que e a diferenca entre 'encerrei este' e
'abri este'.

Os Xs ficam na ponta oposta da mao dominante. Deixa-los sempre a direita
pareceria mais simples, mas com os nomes a direita eles ficariam colados
no nome num caso e soltos no meio da tela no outro."
```

---

### Task 6: `RecentAppsSource` e `RecentAppsActivity`

A tela. `RecentAppsSource` traduz UsageStats em `AppEntry` usando `RecentRanker`; a activity monta, liga o X e o "limpar tudo", e registra o atalho.

**Files:**
- Create: `app/src/main/java/com/minimal/launcher/recents/RecentAppsSource.java`
- Create: `app/src/main/java/com/minimal/launcher/recents/RecentAppsActivity.java`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `RecentRanker.rank(List<Use>, Map<String,Long>)`, `DismissedStore.snapshot()/dismiss()/clear()`, `TaskKiller`, `RecentListAdapter`, `AppRepository.executeAsync(Runnable, Runnable)`, `AppRepository.getAllApps()`, `AppEntry.fromComponent(Context, String)`, `ThemeHelper.applyNightMode/applySystemBars/applyInsets/getHomeForegroundColor`, `FontCombo.getInterfaceFont`.
- Produces: nada consumido por task posterior — esta é a última de código.

- [ ] **Step 1: implementar `RecentAppsSource`**

`static void loadAsync(Context context, Callback callback)` com `interface Callback { void onLoaded(List<AppEntry> entries, boolean permissionMissing); }`.

Dentro de `executeAsync`: chamar `UsageStatsManager.queryUsageStats(MOVE_TO_FOREGROUND, agora - 7 dias, agora)` **dentro de um `try/catch SecurityException`** — sem permissão o sistema lança, e é essa a forma pública e que funciona em toda versão de detectar a falta. Capturada a exceção, entregar `onLoaded(emptyList(), true)`.

Não usar `AppOpsManager.OPSTR_GET_USAGE_STATS`: é constante escondida, e o build falha por lint.

Sem a exceção, seguir: mapear os `UsageStats` para `List<RecentRanker.Use>`, chamar `RecentRanker.rank(uses, dismissedStore.snapshot())`, e só então cruzar com `AppRepository.getAllApps()` por pacote, montando `AppEntry` com o `AppEntry` real da lista — é esse objeto que sabe abrir o app. Pacote que não estiver na lista de apps (desinstalado, ou sem `LAUNCHER`) é ignorado sem erro. Entregar `onLoaded(entries, false)` na main thread. Todo o caminho envolvido em try/catch `RuntimeException`, como `AppRepository.rankQuietly` faz.

- [ ] **Step 2: implementar `RecentAppsActivity`**

`AppCompatActivity`, `excludeFromRecents`, sem título na tela. Em `onCreate`: `ThemeHelper.applyNightMode`, `setContentView(R.layout.activity_recent_apps)`, `ThemeHelper.applyInsets(this, root, 26, 30, 18)`, `ThemeHelper.applySystemBars(this)`, `ThemeHelper.paint(this, root, false)`, e `root.setEdgeGesture(EdgeGestureLayout.DIRECTION_DOWN, null, this::finish)`.

Instanciar os três collaborators como campos final: `private final TaskKiller killer = new ShizukuTaskKiller();`, `private final DismissedStore dismissed = new DismissedStore(this);` e o `RecentListAdapter`.

Em `onResume`: chamar `RecentAppsSource.loadAsync` e, no callback, se `permissionMissing` mostrar a linha de permissão com um `PendingIntent` para as configurações de uso do app; senão `adapter.setItems(entries + clearAllEntry)`. O botão "limpar tudo" é o **último** item da lista: um `AppEntry` sintético com `component` igual a uma constante da activity (`"minimal.launcher/CLEAR_ALL"`), reconhecível no adapter para não tentar abrir app. Ao tocar: `killer.killAll(pacotes)`, `dismissedStore.clear()`, e `adapter.setItems(...)` com a lista vazia mais o próprio botão — a lista some, o botão permanece.

No `onDismiss(entry, position)`: `dismissedStore.dismiss(entry.packageName, System.currentTimeMillis())`, `killer.kill(entry.packageName)`, `adapter.removeAt(position)`.

`onClearAll()` faz o que está acima. Nenhum dos dois caminhos trata retorno de `kill` como erro.

- [ ] **Step 3: registrar no manifesto**

Acrescentar no topo do manifesto, ao lado das outras permissões:

```xml
    <uses-permission
        android:name="android.permission.PACKAGE_USAGE_STATS"
        tools:ignore="ProtectedPermissions" />
```

`tools:ignore` é necessário porque o Lint classifica essa permissão como protegida, e o build falha sem ele.

Adicionar a activity com `android:excludeFromRecents="true"`, `android:launchMode="singleTop"`, `android:exported="true"`, o mesmo `configChanges` das outras telas, `android:theme="@style/Theme.MinimalLauncher"`. **Sem `CATEGORY_LAUNCHER`** — é o que a mantém fora da lista de apps e da busca.

Adicionar também o `ShizukuProvider` declarado pelo artefato `provider`:

```xml
        <provider
            android:name="rikka.shizuku.ShizukuProvider"
            android:authorities="${applicationId}.shizuku"
            android:enabled="true"
            android:exported="true"
            android:multiprocess="false"
            android:permission="moe.shizuku.manager.permission.API_V23" />
```

- [ ] **Step 4: compilar**

Run: `gradle :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: conferir que a tela não entrou na lista de apps**

Run: `gradle :app:assembleDebug` e abrir o APK gerado não é verificável por script. A verificação é no aparelho: `adb shell dumpsys package com.minimal.launcher | grep -i recent` deve listar a activity sem `CATEGORY_LAUNCHER` no filtro.

- [ ] **Step 6: commitar**

```bash
git add app/src/main/java/com/minimal/launcher/recents/RecentAppsSource.java app/src/main/java/com/minimal/launcher/recents/RecentAppsActivity.java app/src/main/AndroidManifest.xml
git commit -m "feat(recents): tela de apps recentes, so texto, com X e limpar tudo

RecentAppsSource traduz o UsageStatsManager em AppEntry usando RecentRanker,
e cruza com a lista de apps real para dar ao clique o componente certo.
RecentAppsActivity monta a lista, liga o X e o limpar tudo, e registra a
activity sem CATEGORY_LAUNCHER -- que e o que a mantem fora da lista de
apps do launcher e da busca, enquanto o atalho do sistema a alcanca por
intent explicito.

O 'limpar tudo' fica como a ultima linha, com o mesmo estilo das outras, e
nao tem X: ele ja E sair da lista inteira, entao o X repetiria o proprio
rotulo."
```

---

### Task 7: verificação no aparelho e README

O projeto não tem teste de integração nem emulador configurado. Esta é a task em que o resultado é julgado, e onde as cinco entradas do Review Focus que o JUnit não cobre são conferidas uma a uma.

**Files:**
- Modify: `README.md:371-373`

- [ ] **Step 1: instalar e rodar**

```bash
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: conferir a lista sem permissão de uso**

Abrir a tela pelo atalho. Esperado: lista vazia com a linha explicando onde ligar. **Sem crash.**

- [ ] **Step 3: conceder a permissão e conferir a ordem**

Conceder em Ajustes → Aplicativos → Minimal Launcher → Uso de dados → Acesso a dados de uso, reabrir a tela. Esperado: apps em ordem de uso real, e apps abertos por notificação ou link presentes.

- [ ] **Step 4: conferir clique e ripple**

Tocar no nome: abre o app certo, inclusive com várias tasks do mesmo app. Tocar no nome **não** pode acender o ripple do X.

- [ ] **Step 5: conferir nome longo**

Conferir um app com nome comprido: corta com reticências e o X não sai da linha.

- [ ] **Step 6: conferir que o X persiste e que o app volta**

Tocar no X, fechar a tela, reabrir pelo atalho. Esperado: o app **não** voltou. Abrir o app em outra tela, voltar para a de recentes. Esperado: o app voltou sozinho, sem nenhuma ação dentro da tela.

- [ ] **Step 7: conferir sem Shizuku**

Parar o Shizuku, tocar no X. Esperado: a linha some do mesmo jeito, o layout não muda, o app não quebra. É o Review Focus nº 3.

- [ ] **Step 8: conferir com Shizuku**

Subir o Shizuku, abrir alguns apps, voltar, tocar no X. Esperado: a linha some **e** o processo morre — conferir com `adb shell ps | grep <pacote>`, não só com a linha sumindo.

- [ ] **Step 9: conferir o limpar tudo**

Tocar em "Limpar tudo". Esperado: a tela esvazia, sobra só o botão, e continua vazia ao reabrir.

- [ ] **Step 10: conferir app de sistema**

Abrir o Telefone, voltar para a de recentes. Esperado: a linha tem X, some ao tocar, e `adb shell ps` mostra o processo **ainda vivo**. É o Review Focus nº 4 — e é o comportamento correto, não uma falha.

- [ ] **Step 11: conferir mão dominante invertida**

Em Configurações, pôr dominante à esquerda e reabrir a tela. Esperado: nomes e X trocaram de lado em bloco. É o Review Focus nº 5.

- [ ] **Step 12: conferir que a tela não vazou para a lista de apps**

Abrir a busca do launcher e rolar a lista de todos os apps. Esperado: "Minimal Launcher" e a tela de recentes **não** aparecem.

- [ ] **Step 13: conferir tema e fonte**

Trocar a combinação de cor e a de fonte nas Configurações, reabrir a tela. Esperado: as duas mudam junto, e o texto continua legível nos dois combos claros e escuros.

- [ ] **Step 14: atualizar o README**

Na seção "Fora do escopo, por decisão" (linha 373), tirar **múltiplas telas** da lista: a tela de recentes existe agora e é utilitária, não uma página de navegação. Deixar ícones, widgets, pastas, root e Play Store. Adicionar também uma linha na tabela de gestos registrando que a tela é aberta por atalho do sistema. Comentar no commit que o README já estava modificado por trabalho anterior da busca, e que esta é a única linha tocada aqui.

- [ ] **Step 15: commitar**

```bash
git add README.md
git commit -m "docs(readme): tira 'multiplas telas' da lista de fora de escopo

A tela de apps recentes e a terceira tela do app, e ela e utilitaria: nao
e pagina de navegacao nem pasta, e nao muda a proposta minimalista. Icons,
widgets, pastas, root e Play Store continuam fora."
```