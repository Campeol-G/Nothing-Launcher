# Tela de apps recentes — Design

Data: 2026-10-07
Status: aprovado (arquitetura discutida antes de gerar o código)

## Objetivo

Uma tela que lista os apps usados recentemente, no mesmo estilo do launcher:
só texto, sem ícones, sem miniatura do último momento, sem imagem nenhuma. O
botão de limpar tudo aparece como mais uma linha de texto, no mesmo layout das
linhas de apps — não como um botão de sistema flutuante.

A tela é aberta por um atalho do sistema, e fica **fora** da lista de apps do
launcher: o launcher continua listando apps de verdade, nunca a si mesmo.

## O que foi descartado, e por quê

A intenção original era trocar a tela de "apps recentes" do próprio Android — a
que abre pelo botão de recents da barra de navegação. Isso não é possível sem root.
A tela de recents mora dentro do `com.android.wm.shell.taskview.TaskView`, no
SystemUI, e não existe API pública para um app de terceiros substituí-la. O
`ActivityManager.getRecentTasks()`, que faria isso, foi removido de apps de
terceiros na API 21.

Restaram três caminhos, todos verificados:

**Atalho do sistema** — escolhido. A activity vira alvo de atalho e o usuário
atribui em Ajustes (duplo toque no power, segurar volume, ou o gesto que a ROM
oferecer). Abre direto, sem piscar nada, sem permissão especial, sem hack. Não
é o botão de recents, mas é um botão do sistema de verdade.

**Interceptar o gesto de recents via AccessibilityService** — possível, e
descartado por agora, não por ser impossível. Um serviço de acessibilidade
vigia `TYPE_WINDOW_STATE_CHANGED`, e quando o SystemUI abre o recents, fecha e
abre a nossa tela no lugar. Os custos: o recents do sistema **pisca** antes da
nossa tela (o serviço reage depois, não impede), o usuário precisa ativar em
Ajustes → Acessibilidade, e a abordagem quebra em atualização de ROM. Fica como
evolução futura se o atalho do sistema incomodar.

**App Prediction API** — não serve. É a via oficial para o launcher alimentar o
sistema com previsões, mas controla só a *fileira de previsões*, não a lista de
cards. Não entrega a tela descrita.

## Fonte da lista: UsageStats, e por que Shizuku não faz isso

Uma correção que muda o desenho: `ActivityManager.getAppTasks()` **não** devolve
a lista de recents do usuário. A documentação é explícita — *"For non-system
callers, this will only return information related to the calling app."* Um
launcher que chama isso recebe apenas as próprias tasks. O `getRecentTasks()`
que devolveria foi removido na API 21.

Isso vale inclusive com Shizuku ligado: em modo ADB o Shizuku roda como UID
2000 (shell), que **não tem** a permissão `REAL_GET_TASKS`. O app de referência
`tymwitko/Recents`, que faz exatamente isto, tem no README *"sorted by last
launch time"* — ele usa `UsageStatsManager` para ordenar, e Shizuku só para o
botão de matar.

As duas peças não são alternativas concorrentes. São metades diferentes:

| Peça | Entrega | Custa |
|---|---|---|
| `UsageStatsManager` | A **ordem** — quais apps foram usados recentemente | Uma permissão, concedida em Ajustes |
| Shizuku | A **ação** — encerrar o processo / remover a task | Instalar e rodar o Shizuku |

O app de recentes funciona sem Shizuku: a lista aparece e o "limpar tudo" só
esvazia a lista. Com Shizuku, o mesmo botão encerra os apps de verdade. Essa
degradação é deliberada — a tela nunca quebra por falta de Shizuku.

Limitação honesta: mesmo com Shizuku, apps de sistema (relógio, telefone,
configurações) não são encerrados por `force-stop`. O app de referência avisa
a mesma limitação.

## Mapa de pacotes e classes

Tudo novo fica em `com.minimal.launcher.recents`. Nenhuma classe existente
muda de assinatura — o único acréscimo é **um** método novo em
`AppRepository`, aditivo, detalhado no fluxo de dados. É o critério para o
resto do launcher não sentir nada.

| Classe | Responsabilidade |
|---|---|
| `RecentAppsActivity` | A tela. Monta a lista, trata clique, monta o botão de limpar. |
| `RecentAppsSource` | Lê o `UsageStatsManager` e devolve `List<AppEntry>` em ordem de uso. |
| `TaskKiller` | Interface de "encerrar tudo". |
| `TaskKillerUnavailable` | Implementação que não faz nada. Usada sem Shizuku. |
| `ShizukuTaskKiller` | Implementação via Shizuku. Entra numa fase posterior. |

`RecentAppsActivity` **reusa** `AppListAdapter`, `AppListCallback` e
`item_app_name.xml` sem nenhuma alteração — o adapter já aceita qualquer
`List<AppEntry>` no construtor, e é exatamente o que se quer aqui: as linhas
desta tela são idênticas às da tela inicial.

O botão de "limpar tudo" entra na **mesma** `List<AppEntry>` que os apps, como
a última posição. Ele é um `AppEntry` sintético cujo `component` é uma
constante que nenhum app real tem, e `RecentAppsActivity` o reconhece por essa
constante para não tentar abrir um app. A alternativa — um `View` separado
abaixo da lista — criaria dois mecanismos de linha na mesma tela, e a segunda
forma não receberia o estilo da primeira sem repetir à mão fonte, cor e padding.

Cuidado real: `AppEntry` não é `@Nullable`-friendly para este caso, e o
adapter não valida. Se o botão sumir mas a lista não for repovoada, o clique
tentará abrir um componente inexistente — `AppEntry.launch()` já cai no
fallback por pacote, então não quebra, mas abre o app errado. A tela sempre
repovoa as duas coisas juntas, no mesmo `setItems`.

## Fluxo de dados

```
RecentAppsActivity.onResume()
  └─ RecentAppsSource.loadAsync()
       ├─ UsageStatsManager não concedido → lista vazia + dica
       ├─ queryUsageStats(MOVE_TO_FOREGROUND)
       ├─ ordena por lastTimeUsed desc
       ├─ cruza com AppRepository.getAllApps() para achar o AppEntry
       └─ deduplica por pacote (UsageStats tem um evento por task, não por app)
  └─ adapter.setItems(lista)
```

A consulta acontece na **fila única do `AppRepository`** (`WORKER`), nunca na
main thread — o mesmo motivo que já está documentado em `AppRepository`: ler
todos os apps custa meio segundo, e foi exatamente na main thread que a busca
travou na primeira tecla.

`RecentAppsSource` **não** cria thread nova, e **não** reaproveita
`searchAsync` — aquele método rankeia por texto, e aqui a ordem não vem de
texto nenhum, vem de `lastTimeUsed`. O que ele precisa é da mesma fila com o
mesmo contrato de volta para a main thread. Para isso, `AppRepository` ganha
**um método novo**:

```java
/** Enfileira trabalho na fila única e entrega o resultado na main thread. */
public void executeAsync(@NonNull Runnable background, @NonNull Runnable onMain)
```

É a única alteração em classe existente, e é aditiva: quem já chama
`searchAsync` continua idêntico. Manter a fila única é o ponto — duas filas
fariam a montagem da lista de apps e a leitura de uso competirem entre si,
que é exatamente o que o comentário de `WORKER` diz para evitar.

Cruzar com `AppRepository.getAllApps()` é o que dá o `AppEntry` completo (com o
componente achatado, que `launch()` precisa). O `UsageStatsManager` devolve só
o pacote; sem o cruzamento, clicar na linha não abriria o app certo.

Apps em UsageStats que não estão mais instalados, ou que não são lançáveis,
somem do cruzamento. Não viram erro.

## Aparência

Sem ícone, sem miniatura, sem imagem — o requisito central.

A linha é `item_app_name.xml`: um `TextView` com o estilo `AppNameText` de
`themes.xml`, 22sp, `includeFontPadding=false`, padding vertical de 10dp,
`selectableItemBackground` no toque. Fonte e cor são aplicadas em runtime pelo
adapter (`FontCombo.getInterfaceFont()` e
`ThemeHelper.getHomeForegroundColor()`) — não vêm do XML, porque dependem do
combo escolhido e porque `ThemeHelper.paint()` só alcança views que já existem
quando é chamado, e as linhas nascem depois, quando o RecyclerView popula.

O botão de "limpar tudo" é **a última linha da lista**, com o mesmo
`AppNameText`, o mesmo tamanho e a mesma fonte. Sem cor de destaque, sem
background próprio, sem ícone. Distinto das linhas de apps só pelo rótulo — é
isso que "no mesmo layout do app" quer dizer. Tocar esvazia a lista.

A tela tem título? Não. `SearchActivity` e `SettingsActivity` têm título porque
são telas de consulta; esta é uma lista, como a tela inicial, e a tela inicial
não tem.

Sem título, a lista começa no topo com padding superior de `clock_list_gap`
(20dp) — o mesmo respiro que separa o relógio da lista na tela inicial, então
a primeira linha não gruda no status bar. Insets de sistema e de teclado saem
de `ThemeHelper.applyInsets`, como nas outras telas.

Fechar: gesto para baixo pelo `EdgeGestureLayout`, como em `SearchActivity`. O
botão voltar do sistema também funciona.

## Permissões e manifesto

- `PACKAGE_USAGE_STATS` — permissão de acesso especial, não de runtime. O
  usuário concede em Ajustes → Aplicativos → Minimal Launcher → Uso de dados →
  Acesso a dados de uso. Não há diálogo do sistema para pedir isso; a tela
  detecta que não tem a permissão e mostra uma linha de texto explicando onde
  ligar, com um atalho para as configurações do app. Sem permissão: lista vazia,
  nenhum crash.
- `RecentAppsActivity`: `excludeFromRecents=true`, `launchMode=singleTop`,
  `configChanges` igual ao das outras telas, tema `Theme.MinimalLauncher`.
- **A activity não recebe `CATEGORY_LAUNCHER`.** É isso que a mantém fora da
  lista de apps do launcher e da tela de busca. O atalho do sistema a alcança
  por `explicit intent` (o usuário aponta para o componente), que não depende de
  filtro de categoria.
- Nada de notificação de uso na tela inicial: o pedido foi uma lista de recentes,
  não um atalho para abrir. Sem permissão nova além de `PACKAGE_USAGE_STATS`.

## Fases

**Fase 1 — a tela.** `RecentAppsActivity`, `RecentAppsSource`, layout,
`TaskKiller` + `TaskKillerUnavailable`. Abre pelo atalho, lista por UsageStats,
o botão de limpar esvazia a lista. Compila e funciona sem Shizuku e sem
dependência nova.

**Fase 2 — Shizuku.** `ShizukuTaskKiller` implementando `TaskKiller`, com
`am force-stop` por pacote e remoção das tasks. Entra como dependência real
(`dev.rikka.shizuku:api`) e troca a implementação usada pela tela. A separação
existe para que a fase 2 não redesenhe a fase 1.

## Verificação

`gradle assembleDebug` verde **ou** não há claim de pronto. O projeto não tem
testes automatizados — `AppSearch.java` é a única classe pura e testável, e esta
mudança não a toca. A verificação é manual, no dispositivo:

1. Sem a permissão concedida: a tela abre, lista vazia, linha explicando como
   conceder. Sem crash.
2. Concedida: a lista sai na ordem de uso real, e apps abertos por notificação
   ou link aparecem — é a diferença em relação a rastrear só o launcher.
3. Tocar numa linha abre o app certo, inclusive quando há várias tasks do mesmo
   app.
4. "Limpar tudo" esvazia a lista e o texto some com ela.
5. A tela **não** aparece na busca nem na lista de apps.
6. Ajustes de fonte, cor e mão dominante continuam valendo aqui: trocar o combo
   na tela de configurações muda esta tela também.
7. Sem Shizuku: o botão de limpar não derruba nada e o app segue funcionando.

## Fora do escopo

Ícones, widgets, pastas, root, publicação na Play Store. E também:

- **Interceptar o gesto de recents.** É a alternativa real ao atalho, e está
  fora por causa do piscar do sistema e da fragilidade em atualização de ROM —
  não por ser impossível.
- **Encerrar apps de sistema.** Nem com Shizuku.
- **Rastrear histórico próprio do launcher.** Descartado: é superficial
  (perde tudo que não passou pelo launcher) e o `UsageStats` resolve melhor, com
  uma permissão a mais que o usuário não nota.
- **Swipe para remover linha individual.** A tela não tem gesto próprio de
  remover; a remoção é pelo botão de limpar.