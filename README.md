# Minimal Launcher

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
3. Escolha **Minimal Launcher**.

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
| Na busca: digitar | Mostra **um** app só, o primeiro em ordem alfabético, e quantos outros ficaram de fora |
| Na busca: tocar no resultado, ou enter | Abre esse app |
| Na busca: deslizar para baixo ou voltar | Fecha a busca |

### Por que não há arrastar para cima

Com a navegação por gestos do Android, arrastar para cima a partir da borda
inferior é do sistema: é o gesto que abre o drawer, e é também o movimento que o
polegar faz para alcançar a lista. Quando o launcher interceptava esse arrasto,
**buscar e trocar de app viravam o mesmo gesto** — e a busca disparava sem
querer no meio de outra ação.

Por isso a busca é um toque longo na área vazia, e as Configurações um toque
longo no relógio. São regiões diferentes da tela, então não há ambiguidade, e
nenhum dos dois disputa espaço com o sistema. Deslizar para baixo na busca
continua fechando: essa puxada do sistema começa na borda superior, e o arrasto
do app é decidido no meio da tela.

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
com folga sobre os 20% pedidos. Dentro dela a data fica no topo e o relógio
preenche o resto.

O relógio **não tem tamanho fixo**: usa `autoSize` com teto na altura disponível,
então ele se ajusta ao aparelho e ao formato de hora. O `clock_size` saiu do
`dimens.xml` justamente por isso.

Um limite honesto: o que trava o relógio é a **largura**, não a altura.
`21:41` ocupa cerca de 2.6 larguras de glifo, então num celular estreito o texto
esbarra na borda antes de chegar no teto de altura — nesse caso o `autoSize`
reduz e o bloco fica com respiro. Subir a fração aumenta a faixa, mas não o
glifo; para o glifo crescer seria preciso menos padding lateral nas bordas.

A lista de favoritos é centralizada no espaço **abaixo** da faixa, e não na tela
inteira: o deslocamento é um `translationY` de meia altura do cabeçalho, que é a
correção exata para centralizar no que sobra.

## Estrutura

```
app/src/main/java/com/minimal/launcher/
├── LauncherApp.java            Application: cache, favoritos iniciais, receptor de pacotes
├── HomeActivity.java           tela principal: relógio + favoritos ancorados no polegar
├── SearchActivity.java         busca, um resultado por vez
├── EdgeGestureLayout.java      resolve o conflito gesto x scroll
├── ThemeHelper.java            cores, inversão noturna, edge-to-edge, barras, insets
├── ColorCombo.java             as 10 combinações de cor
├── FontCombo.java              as 8 combinações de fonte
├── LauncherUtils.java          cálculo do limite de favoritos
├── Prefs.java                  todas as preferências
├── ClockFormatter.java         hora/data no formato do aparelho
├── SystemTimeWatcher.java      ACTION_TIME_TICK e amigos
├── apps/
│   ├── AppEntry.java           app lançável (pacote, nome, componente)
│   ├── AppRepository.java      lista de apps + resolução dos apps padrão
│   ├── FavoritesStore.java     ordem persistida dos favoritos
│   ├── AppListAdapter.java     linha de app: só o nome
│   ├── AppListCallback.java    interface de cliques
│   ├── AppMenu.java            diálogo do toque longo
│   └── PackageChangeReceiver.java
└── settings/
    └── SettingsActivity.java
```

A decisão de arquitetura e o porquê de cada escolha estão em
[`docs/superpowers/specs/2026-10-05-minimal-launcher-design.md`](docs/superpowers/specs/2026-10-05-minimal-launcher-design.md).

## Bateria

O app não declara nenhum serviço. Nenhum receptor está no manifesto. O relógio
usa `ACTION_TIME_TICK` (broadcast do sistema, 1x por minuto) em vez de timer
próprio, e a lista de apps é cacheada e invalidada só quando um pacote muda.

## Fora do escopo, por decisão

Ícones, widgets, múltiplas telas, pastas, root e publicação na Play Store.
