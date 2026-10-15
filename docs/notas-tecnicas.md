# Notas técnicas

O detalhamento de como o app foi construído: as armadilhas do Android encontradas
no caminho, e por quê cada decisão é como é.

O [README](../README.md) tem o essencial para instalar e usar. Este arquivo é para
quem quer mexer no código.

Conteúdo trazido do README original, antes dele ser enxuto para o estado alpha.

---

# Nothing Launcher

Launcher Android nativo de uso pessoal. Uma tela, sem ícones, sem widgets, sem
páginas extras, sem animação de transição. Fundo sólido desenhado pelo app
(nunca o wallpaper do sistema) e uma lista de favoritos que cabe na tela, sem
rolagem.

O nome é provisório: está em `app/src/main/res/values/strings.xml` → `app_name`,
e o `applicationId` está em `app/build.gradle`.

## Requisitos

| Item | Versão |
|---|---|
| JDK | 17+ (o build foi feito com 27) |
| Android SDK | platform **android-37.0** (Android 17) e build-tools 36 |
| Gradle | 9.8 |
| AGP | 9.4.1 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 37 |

## Compilar

O `local.properties` desta máquina aponta para `/home/chico/Android/Sdk`. Se o SDK
estiver em outro lugar, edite a linha `sdk.dir`.

```bash
cd ~/Projects/launcher

# APK de depuração
gradle assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Versão assinada (precisa de uma chave; rode release depois)
gradle assembleRelease
```

Instalar direto pelo terminal:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Definir como launcher padrão

Depois de instalado:

1. Toque e segure em qualquer área vazia da tela inicial → **Busca**.
   Para as **Configurações**, toque e segure no relógio ou na data.
   (Se ainda não for o launcher, abra o app pelo ícone no drawer do sistema.)
2. Ou: **Configurações do sistema → Apps → Aplicativo de início padrão**.
   Em Android 12+: **Configurações → Apps → Apps padrão → App de início**.
3. Escolha **Nothing Launcher**.

Atalho pelo terminal:

```bash
adb shell cmd package set-home-activity com.minimal.launcher/.HomeActivity
```

Como o app também responde a `android.intent.action.APPLICATION_PREFERENCES`,
alguns aparelhos mostram um atalho extra nas Configurações do sistema.

## Permissões

Nenhuma. O app não pede acesso a notificações nem usa serviço de
acessibilidade, e nada disso é opcional — simplesmente não existe.

As notificações continuam funcionando normalmente: elas são tratadas pelo
sistema. O launcher apenas esconde as barras e deixa o usuário puxá-las quando
quiser, e é aí que as notificações aparecem como sempre.

## Como usar

| Gesto / ação | Resultado |
|---|---|
| Tocar no nome | Abre o app |
| Toque longo no nome | Favoritar / Remover dos favoritos, Informações do app, Desinstalar |
| Toque longo na área vazia | Abre a busca |
| Toque longo no relógio ou na data | Abre as Configurações |
| Na busca: digitar | Mostra **um** app só, o que melhor responde ao texto, e quantos outros ficaram de fora |
| Na busca: tocar no resultado, ou enter | Abre esse app — inclusive com o texto pela metade: `tk` abre o TikTok |
| Na busca: deslizar para baixo ou voltar | Fecha a busca |
| Na busca: o teclado sumir | Fecha a busca: sem teclado não há busca |
| Na tela de recentes: tocar no nome | Abre o app |
| Na tela de recentes: tocar no X | Tira o app da lista e tenta encerrá-lo |
| Na tela de recentes: tocar em "Limpar tudo" | Esvazia a lista e tenta encerrar tudo |
| Na tela de recentes: deslizar para baixo ou voltar | Fecha |

### Por que o arrasto para cima não colide com o sistema

Com a navegação por gestos do Android, arrastar para cima a partir da borda
inferior pertence ao sistema: é o gesto que abre o drawer, e é também o
movimento que o polegar faz para alcançar a lista. Quando o launcher
interceptava esse arrasto, **buscar e trocar de app viravam o mesmo gesto**.

O arrasto para cima continua existindo, mas só vale quando **começa acima** da
faixa reservada ao sistema. A faixa é o maior valor entre o inset de gestos, o da
barra de navegação e um piso de **48dp** — o inset reportado costuma medir só a
barra, uns 24dp, e é menor que a área que o sistema trata como gesto. Medido no
aparelho: a exclusão cobre os 126px de baixo, e o arrasto a partir de y=2280
sobe.

Acima da faixa não há disputa, porque o drawer do sistema só existe a partir da
borda.

O **toque longo** na área vazia continua sendo o caminho que não depende de
acertar o começo do arrasto. E as Configurações são um toque longo no relógio,
que é uma região diferente da tela.

Deslizar para baixo na busca continua fechando: essa puxada do sistema começa na
borda superior, e o arrasto do app é decidido no meio da tela. Quem preferir não
usar gesto algum tem o botão voltar do sistema e o Enter do teclado.

A faixa reservada ao sistema **só vale para o arrasto para cima**. Ela existe
porque esse arrasto disputa o gesto de ir para a casa; para descer não há
disputa nenhuma, e a faixa ali só fazia o gesto ser descartado em silêncio.

### Por que o deslizar para baixo não funcionava

Numa tela sem lista e sem view clicável — que é o caso da busca — nenhum filho
consome o `ACTION_DOWN`. Ele caía no layout e era devolvido como não tratado, e
**o sistema, diante de um DOWN não tratado, para de entregar os eventos
seguintes àquela janela**. O arrasto perdia o resto do toque antes de começar.

Na tela inicial isso não aparecia: o `RecyclerView` consome o DOWN e segura o
gesto inteiro, que é por isso que o arrasto para cima sempre funcionou. Em
`EdgeGestureLayout.onTouchEvent` o DOWN agora é consumido pelo próprio layout
quando mais ninguém o consome.

Fechar uma tela também é um gesto curto, e o limite de percurso é menor que o de
abrir: 48dp e 5% da altura para descer, 96dp e 8% para subir. Abrir a busca
divide o gesto com o polegar rolando a lista, e ali o percurso longo evita
disparo por engano.

### O teclado é precondição da busca

A tela de busca só existe com o teclado, e isso virou regra explícita em três
lugares:

1. **Ele sobe junto.** Não basta pedir `showSoftInput`: sem
   `stateAlwaysVisible` no manifesto, o sistema responde ao pedido com
   `Unspecified window will hide input` e esconde o teclado de novo. Medido no
   aparelho antes e depois.
2. **Ele volta se a janela ganhar foco**, porque o `showSoftInput` pedido antes
   do foco da janela é descartado.
3. **Se ele sumir depois de ter estado visível, a tela fecha.** Vale para o
   botão voltar — que primeiro esconde o teclado — e para o gesto de descer no
   próprio teclado. Duas guardas impedem que vire corrida: o teclado precisa ter
   estado visível, e a activity precisa estar em primeiro plano com foco, senão
   abrir um app (que também fecha o teclado) fecharia a busca junto.

### Latência de entrada na busca

Duas coisas levavam a primeira tecla a esperar quase um segundo, e as duas foram
tiradas do caminho da digitação:

- **A lista de apps era montada na main thread, na primeira tecla.** Ler todos os
  apps instalados (`queryIntentActivities` + o rótulo de cada um) custa meio
  segundo. Agora ela é montada numa thread própria, e o pedido da busca entra
  nessa mesma fila: quem digita nunca espera, nem pela lista, nem pelo
  ranqueamento.
- **Cada tecla renormalizava ~200 rótulos**, com `replaceAll` compilando um regex
  por rótulo. O rótulo normalizado passa a ser calculado uma vez, quando o app é
  construído, e a busca virou comparação de string.

A lista também é **pré-carregada** na inicialização do processo: o trabalho
acontece enquanto a tela inicial está à vista, não quando o dedo já está
digitando. E recarrega em segundo plano quando um app é instalado ou removido.
Medido no aparelho: partida a frio do processo, digitando logo em seguida, o
quadro mais longo foi de 87ms e a mediana de 11ms — antes, a thread principal
ficava bloqueada por centenas de ms na primeira tecla.

O teclado é pedido de imediato e repetido no primeiro desenho da tela, quando a
janela já está pronta. Não há delay artificial: um `postDelayed` de 200ms que
existia aqui custava 200ms de latência toda vez que alguém queria trocar de app,
somados à espera do toque longo.

### A busca acha o app com o texto pela metade

O enter não exige o nome completo. O ranqueamento está em
[`apps/AppSearch.java`](app/src/main/java/com/minimal/launcher/apps/AppSearch.java),
é puro e não tem nada de Android: nome igual, nome que começa com o texto, uma
palavra do nome que começa com o texto, nome que contém o texto e, por último, o
texto aparecendo na ordem pulando letras — `tk` acha TikTok, `gmb` acha Gmail.

A tela e o enter usam **o mesmo** ranqueamento: o que está escrito na tela é o que
o enter abre, inclusive quando a busca não achou nada — nesse caso a tela diz
`Nenhum app encontrado` e o enter não faz nada. A última regra exige dois
caracteres no mínimo: com um só quase todo nome do aparelho casaria e a busca
viraria loteria.

### A fonte se ajusta ao aparelho

A base é 22sp para os apps e 54sp para o relógio, e por cima dela há duas
multiplicações, em `LauncherUtils`:

1. **Automática, pela altura da tela** — uma referência de 800dp vale 1.0; uma
   tela de 698dp recebe 1.15 (é nelas que 22sp parece pequeno); uma de 914dp
   recebe 0.92, para o relógio não virar um outdoor. O limite é 0.92 a 1.30.
2. **A preferência das Configurações** — os quatro passos multiplicam o
   automático. Quem não mexe fica só no automático.

Densidade de tela não entra no cálculo: sp já é independente de pixels, então o
ajuste é sobre o *tamanho físico da tela*, não sobre a resolução.

Girar o aparelho refaz a conta: a escala efetiva faz parte da assinatura de
preferências da tela inicial, e uma rotação que a mude recria a activity.

### O limite de favoritos

A tela inicial mostra apenas o que cabe. O número é calculado a partir da altura
da tela **e da escala tipográfica efetiva** — aumentar a fonte reduz o número de
linhas, como deve ser. O teto é 5 e 14; na prática quase todo aparelho alto com
fonte média chega nos 14, ocupando uns 75% da altura da tela. O resto é o bloco
do relógio e o respiro em volta da lista, que é centralizada.

Se preferir o bloco colado embaixo, é uma palavra em `HomeActivity`:
`Gravity.CENTER_VERTICAL` → `Gravity.BOTTOM`.

Favoritos que não cabem **não são apagados**: continuam guardados e aparecem na
busca. Se a lista já estiver cheia, o toque longo avisa em vez de adicionar.

Com poucos favoritos a tela parece vazia — isso é conteúdo, não layout: o
primeiro uso semeia três apps (Configurações, Câmera, Telefone). Adicione mais
pelo toque longo na busca.

## Aparência

Cores e fontes são combinações fechadas, definidas nos arquivos da raiz do
projeto:

| Arquivo | O que define |
|---|---|
| `colors.md` | 10 combinações de fundo + texto da tela inicial |
| `searchScreen.md` | Cores da tela de busca (fundo, texto, placeholder, borda) |
| `fonts.md` | 8 combinações de fontes (interface, relógio, títulos) |

Os dados vivem em `ColorCombo.java` e `FontCombo.java`. O valor em
`CURRENT_COLOR_COMBO_INDEX` / `CURRENT_FONT_COMBO_INDEX` é o **padrão** quando o
usuário nunca escolheu nada — mudar o número no código e recompilar já troca o
visual inteiro do app. O mesmo também dá para fazer pelas Configurações, que é
o jeito normal de usar.

O padrão de fábrica é o combo **11. Preto OLED**, porque o preto puro é o que
uma tela OLED gasta menos e o que mais combina com um launcher de tela única.

Ele é também o único combo **escuro por natureza**, e por isso tem duas
variantes: **à noite** é `#000000` puro; **de dia** é off-white (`#F2F0E9`).
Preto sob luz do dia não é discomfortável, é escuro demais — e a busca recebe a
mesma troca. Os outros dez combos são claros por definição, e a inversão noturna
deles é que produz a versão escura.

Fora do OLED, a inversão noturna nega o canal RGB de cada cor: o fundo claro
vira escuro e o texto escuro vira claro. O texto principal mantém contraste de
**7:1 ou mais** em todas as combinações, de dia e de noite; o placeholder da
busca é puxado em direção à cor de texto para não cair abaixo de 4.5:1.

### Uma armadilha que o código registra

`ThemeHelper.paint()` só alcança as views que **já existem** quando é chamado,
no `onCreate`. As linhas do RecyclerView e as linhas criadas em código nas
Configurações nascem depois, e ficariam com a cor do tema (`textColorPrimary`,
quase preto) — invisíveis sobre um combo escuro. Por isso as duas classes
recebem a cor no ponto onde criam a view: `AppListAdapter.onBindViewHolder` e
`SettingsActivity.label()`.

As fontes vivem em `res/font/` como `.ttf` e são carregadas por
`ResourcesCompat`; se um recurso falhar, o combo cai na fonte do sistema em vez
de quebrar a tela.

## O bloco de hora e data

A faixa de cima ocupa **28% da altura da tela** (`LauncherUtils.getHeaderHeightPx`),
com folga sobre os 20% pedidos. Dentro dela ficam a data e o relógio.

O relógio **não tem tamanho fixo**: usa `autoSize` com teto na altura disponível,
e o `clock_size` saiu do `dimens.xml` por isso.

### Os dois formatos do relógio

Um horário de cinco caracteres esbarra na **largura** antes da altura: `18:02`
ocupa cerca de 2.55 larguras de glifo, e sobra quase metade da faixa sem uso.
Por isso existem dois formatos, ambos medidos no aparelho:

| Formato | Fonte | Altura do relógio | Quando |
|---|---|---|---|
| **Empilhado** (padrão) | Roboto Flex | **~598px** (duas linhas de ~299) | Padrão. Duas linhas de dois dígitos: cada uma esbarra muito menos na largura |
| **Linha única** | Roboto Condensed | ~579px | Quem prefere o horário numa linha só |

A linha única só ficou grande com a fonte condensada: a Roboto Condensed é da
mesma família da Flex, então o desenho continua coerente. Sem ela, o mesmo
formato ficaria em ~370px.

O `autoSize` divide a faixa por igual no formato empilhado e usa a faixa inteira
na linha única, e sempre reduz antes de cortar o texto — vale para `9:41`,
`18:02` e `6:02 PM`.

Duas armadilhas do `TextView` que custaram tempo aqui, e que valem registrar:

- `android:singleLine="true"` **também** liga `ellipsize=END` e
  `horizontallyScrolling`. Com os dois, o `autoSize` deixa de encolher e o
  horário aparece cortado como `19:...`. Só `android:maxLines="1"`, mais
  `ellipsize="none"`.
- O `LayoutParams` do `header` é de `FrameLayout`, não de `LinearLayout`, porque
  quem decide essa classe é o **pai** — o `EdgeGestureLayout`. Passar a errada
  estoura com `ClassCastException` dentro de `FrameLayout.onMeasure`, e o app não
  abre. O compilador aceita, porque `setLayoutParams` recebe qualquer
  `ViewGroup.LayoutParams`.

### Os dois formatos da data

| Formato | Exemplo |
|---|---|
| **Por extenso** (padrão) | `6 de outubro de 2026` |
| **Numérica** | `06/10/2026` |

O numérico respeita a ordem do país: `10/06/2026` nos EUA, `06/10/2026` no
Brasil. O por extenso ocupa bem mais espaço, o que também ajuda a preencher a
faixa.

### Onde ficam essas escolhas

Cor de fundo, fontes, mão dominante, formato do relógio, formato da data e
tamanho da fonte. Tudo isso entra na assinatura de preferências da tela
principal, e a mudança recria a activity.

A lista de favoritos fica **logo abaixo do relógio**, com uma folga curta
(`clock_list_gap`), e não centralizada na tela: o gravity é `top` e a distância
vem de uma margem superior igual à altura do cabeçalho mais a folga.

## Estrutura

```
app/src/main/java/com/minimal/launcher/
├── LauncherApp.java            Application: cache, favoritos iniciais, receptor de pacotes
├── HomeActivity.java           tela principal: relógio + favoritos ancorados no polegar
├── SearchActivity.java         busca, um resultado por vez
├── EdgeGestureLayout.java      resolve o conflito gesto x scroll, e faz o DOWN
│                               ser consumido quando ninguém mais consome
├── ThemeHelper.java            cores, inversão noturna, edge-to-edge, barras, insets
├── ColorCombo.java             as 10 combinações de cor
├── FontCombo.java              as 8 combinações de fonte
├── LauncherUtils.java          cálculo do limite de favoritos
├── Prefs.java                  todas as preferências
├── ClockFormatter.java         hora/data no formato do aparelho
├── SystemTimeWatcher.java      ACTION_TIME_TICK e amigos
├── apps/
│   ├── AppEntry.java           app lançável (pacote, nome, componente, nome normalizado)
│   ├── AppRepository.java      lista de apps, fila única, resolução dos apps padrão
│   ├── AppSearch.java          ranqueamento da busca: puro, sem Android
│   ├── FavoritesStore.java     ordem persistida dos favoritos
│   ├── AppListAdapter.java     linha de app: só o nome
│   ├── AppListCallback.java    interface de cliques
│   ├── AppMenu.java            diálogo do toque longo
│   └── PackageChangeReceiver.java
├── recents/
│   ├── RecentAppsActivity.java tela de apps recentes: só texto, com X
│   ├── RecentAppsSource.java   dumpsys → AppEntry, fora da main thread
│   ├── ShizukuTaskLister.java  roda o dumpsys pelo shell do Shizuku
│   ├── TaskListParser.java     parser do dumpsys: puro, sem Android
│   ├── RecentRanker.java       ordem, dedup e dispensa: puro, sem Android
│   ├── RecentListAdapter.java  linha com nome e X, espelhando a mão dominante
│   ├── DismissedStore.java     pacote → instante da dispensa
│   ├── TaskKiller.java         interface de encerrar, sem Android
│   └── ShizukuTaskKiller.java  a implementação, como melhor esforço
└── settings/
    └── SettingsActivity.java
```

A decisão de arquitetura e o porquê de cada escolha estão em
[`docs/superpowers/specs/2026-10-05-minimal-launcher-design.md`](docs/superpowers/specs/2026-10-05-minimal-launcher-design.md).

## Bateria

O app não declara nenhum serviço. Nenhum receptor está no manifesto. O relógio
usa `ACTION_TIME_TICK` (broadcast do sistema, 1x por minuto) em vez de timer
próprio, e a lista de apps é cacheada e invalidada só quando um pacote muda.

A fila de apps é uma thread só, com uma tarefa por vez, e ela é usada em três
momentos: na inicialização do processo, quando um pacote muda e a cada tecla
digitada. Todas as tarefas são curtas e nenhuma é periódica — nenhuma delas roda
sozinha esperando por alguma coisa.

## Fora do escopo, por decisão

Ícones, widgets, pastas, root e publicação na Play Store.

A tela de apps recentes é a terceira tela do app, e ela não conta como "múltiplas
telas" neste sentido: é uma lista de consulta, não uma página de navegação, não
tem pasta, não tem ícone e não muda a proposta minimalista. Ícones continuam fora
de qualquer tela.

## A tela de apps recentes

Só texto: os nomes, na ordem em que o sistema devolve as tasks, mais um X por linha. Sem ícone, sem
miniatura do último momento, sem imagem nenhuma — a mesma proposta da tela
inicial, e por isso o mesmo estilo de linha.

**Ela não substitui a tela de recents do Android.** A tela de recents mora dentro
do SystemUI e não tem API pública para um app de terceiros substituí-la. Esta é
uma tela à parte, aberta por atalho do sistema (duplo toque no power, segurar
volume, ou o gesto que a ROM oferecer). Interceptar o gesto de recents com um
`AccessibilityService` é possível e foi considerado, mas o recents do sistema
pisca antes da nossa tela, exige permissão de acessibilidade e quebra em
atualização de ROM.

**A lista vem das tasks abertas de verdade, via Shizuku.** Um app comum não tem
acesso a isso: `getRunningTasks` devolve só a si mesmo e `getAppTasks` também —
medido no aparelho durante o desenvolvimento. O Shizuku empresta os privilégios de
shell, e a lista sai do `dumpsys activity recents`, filtrando os tipos `home` e
`recents` e o nosso próprio pacote.

Isso corrigiu uma ilusão que a versão anterior tinha: com `UsageStatsManager` a
lista era um **histórico de uso**, e apps abertos por notificação ou por link
apareciam nela como se estivessem abertos. Agora o que está na lista está aberto.

O `lastActiveTime` de cada task é o que faz a dispensa funcionar (ver abaixo), e é
o mesmo relógio monotônico de `SystemClock.elapsedRealtime()` — conferido no
aparelho: 249.353s de uptime contra 249.172s de `lastActiveTime`.

**O X significa sair da lista**, sempre, para qualquer app. Quando o app pode ser
encerrado, ele também é; quando não pode, só sai. Um X que fizesse uma coisa
nuns apps e outra nos demais seria pior do que nenhum X, porque o usuário não
teria como saber qual é qual antes de tocar. Encerrar usa Shizuku, e é melhor
esforço: sem ele a tela continua limpa, e o que não acontece é o encerramento.

A dispensa guarda o **instante** em que o app foi tirado, não um flag. A task
é a mesma antes e depois de o app ser reaberto — mesmo id, mesmo pacote —, e só
o `lastActiveTime` muda. Sem o instante não há como distinguir "a task é de antes
do X" de "a pessoa reabriu depois do X", e o app ficava fora da lista para sempre. É essa
comparação com o último uso que faz o app voltar sozinho quando é aberto de novo,
e o que impede o "limpar tudo" de virar um apagão que o usuário não desfaz.

A tela é registrada **sem `CATEGORY_LAUNCHER`**. É isso que a mantém fora da
lista de apps e da busca, já que as duas listam o que tem essa categoria; o atalho
do sistema a alcança por intent explícito, que não depende de filtro.
