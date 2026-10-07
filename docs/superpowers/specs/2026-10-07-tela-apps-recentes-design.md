# Tela de apps recentes — Design

Data: 2026-10-07
Status: aprovado (arquitetura discutida antes de gerar o código)

## Objetivo

Uma tela que lista os apps usados recentemente, no mesmo estilo do launcher:
só texto, sem ícones, sem miniatura do último momento, sem imagem nenhuma.

Cada linha traz o nome do app e um **X** ao lado, que encerra aquele app.
O botão de limpar tudo encerra todos de uma vez, e aparece como mais uma linha
de texto no mesmo layout — não como botão de sistema flutuante.

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

`ActivityManager.getAppTasks()` **não** devolve a lista de recents do usuário. A
documentação é explícita — *"For non-system callers, this will only return
information related to the calling app."* Um launcher que chama isso recebe
apenas as próprias tasks. O `getRecentTasks()` que devolveria foi removido na
API 21.

Isso vale inclusive com Shizuku ligado: em modo ADB o Shizuku roda como UID
2000 (shell), que **não tem** a permissão `REAL_GET_TASKS`. O app de referência
`tymwitko/Recents`, que faz exatamente isto, tem no README *"sorted by last
launch time"* — ele usa `UsageStatsManager` para ordenar, e Shizuku só para
encerrar. Por isso as duas peças entram juntas aqui, cada uma com seu papel:

| Peça | Entrega | Custa |
|---|---|---|
| `UsageStatsManager` | A **lista e a ordem** — quais apps foram usados recentemente | Uma permissão, concedida em Ajustes |
| Shizuku | A **ação** — encerrar o app / remover a task | Instalar e rodar o Shizuku |

Limitação honesta: mesmo com Shizuku, apps de sistema (relógio, telefone,
configurações) não são encerrados por `force-stop`. O app de referência avisa a
mesma limitação — e por isso as linhas de app de sistema não têm X, que seria
um botão que não faz nada.

## Mapa de pacotes e classes

Tudo novo fica em `com.minimal.launcher.recents`. Nenhuma classe existente muda
de assinatura — o único acréscimo é **um** método novo em `AppRepository`,
aditivo, detalhado no fluxo de dados. É o critério para o resto do launcher não
sentir nada.

| Classe | Responsabilidade |
|---|---|
| `RecentAppsActivity` | A tela. Monta a lista, trata clique, X e limpar tudo. |
| `RecentAppsSource` | Lê o `UsageStatsManager` e devolve `List<AppEntry>` em ordem de uso. |
| `RecentListAdapter` | Adapter **novo**, só desta tela: nome + X por linha. |
| `TaskKiller` | Interface de encerrar apps: um, ou todos. |
| `ShizukuTaskKiller` | Implementação via Shizuku. Cuida também do caso "Shizuku parado". |

`item_app_name.xml` e `AppListAdapter` **não** são usados nesta tela e não são
alterados. A tela inicial e a busca continuam com o adapter de uma coluna.

O botão de "limpar tudo" entra na **mesma** lista que os apps, como última
posição. Ele é um `AppEntry` sintético cujo `component` é uma constante que
nenhum app real tem, e `RecentAppsActivity` o reconhece por essa constante — é
o que impede o adapter de tentar abrir um app nele. Sem esse reconhecimento, o
toque cairia no `AppEntry.launch()`, que cai no fallback por pacote e abriria o
app errado em vez de não fazer nada.

## Fluxo de dados

```
RecentAppsActivity.onResume()
  └─ RecentAppsSource.loadAsync()
       ├─ UsageStatsManager não concedido → lista vazia + dica
       ├─ queryUsageStats(MOVE_TO_FOREGROUND)
       ├─ ordena por lastTimeUsed desc
       ├─ cruza com AppRepository.getAllApps() para achar o AppEntry
       ├─ deduplica por pacote (UsageStats tem um evento por task, não por app)
       └─ descarta apps de sistema (X não teria o que fazer)
  └─ adapter.setItems(lista)
```

A consulta acontece na **fila única do `AppRepository`** (`WORKER`), nunca na
main thread — o mesmo motivo que já está documentado em `AppRepository`: ler
todos os apps custa meio segundo, e foi exatamente na main thread que a busca
travou na primeira tecla.

`RecentAppsSource` **não** cria thread nova, e **não** reaproveita `searchAsync`
— aquele método rankeia por texto, e aqui a ordem não vem de texto nenhum, vem
de `lastTimeUsed`. O que ele precisa é da mesma fila com o mesmo contrato de
volta para a main thread. Para isso, `AppRepository` ganha **um método novo**:

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

## A linha: nome e X

`item_recent_app.xml`, layout novo, exclusivo desta tela:

```
┌──────────────────────────────────────────┐
│ LinearLayout horizontal, padding 10dp    │
│                                          │
│  ┌──────────────┐          ┌──────┐       │
│  │ nome do app  │          │  X   │       │
│  │ weight=1     │          │      │       │
│  │ ellipsize=end│          │      │       │
│  └──────────────┘          └──────┘       │
└──────────────────────────────────────────┘
```

O nome tem `layout_width=0dp` e `weight=1`, com `ellipsize=end`: nomes longos
cortam, e não empurram o X para fora da linha.

O X é um `TextView` com área de toque de 48dp — o mínimo do Android — feita
com `padding`, para o alvo ficar confortável sem que o glifo "X" fique enorme.
Glifo, não ícone: um caractere na fonte de interface, sem drawable, na mesma
cor do nome e com `alpha` um pouco menor, para que o nome continue sendo a
informação principal. Na cor secundária que o `SettingsActivity` já usa para
valores (`alpha 0.7f`).

O X tem o seu próprio `selectableItemBackground`: tocar no X acende só ele, e
não a linha inteira — o que faz a diferença entre "encerrei este" e "abri este".

**Alinhamento.** A linha espelha a mão dominante, como o resto do app:

| Mão dominante | Nome | X |
|---|---|---|
| direita (padrão) | encosta na margem direita | coluna alinhada à esquerda |
| esquerda | encosta na margem esquerda | coluna alinhada à direita |

Nos dois casos todos os nomes ficam encostados no mesmo lado e todos os Xs
formam uma coluna. Deixar o X sempre à direita pareceria mais simples, mas com
os nomes à direita ele ficaria colado no nome num caso e perdido no meio da
tela no outro — quebrando o alinhamento em metade das configurações. O
`RecentListAdapter` resolve isso com a mesma `gravityFor` que `AppListAdapter`
já usa.

## Aparência

Sem ícone, sem miniatura, sem imagem — o requisito central.

O nome é o `AppNameText` de `themes.xml`: 22sp, `includeFontPadding=false`,
`selectableItemBackground`. Fonte e cor são aplicadas em runtime
(`FontCombo.getInterfaceFont()` e `ThemeHelper.getHomeForegroundColor()`) — não
vêm do XML, porque dependem do combo escolhido e porque `ThemeHelper.paint()`
só alcança views que já existem quando é chamado, e as linhas nascem depois,
quando o RecyclerView popula.

O "limpar tudo" é **a última linha da lista**, sem X, com o mesmo `AppNameText`.
Sem cor de destaque, sem background próprio. Distinto das linhas de apps só pelo
rótulo. Tocar encerra todos os apps da lista.

A tela não tem título. `SearchActivity` e `SettingsActivity` têm título porque
são telas de consulta; esta é uma lista, como a tela inicial, e a tela inicial
não tem. A lista começa com padding superior de `clock_list_gap` (20dp) — o
mesmo respiro que separa o relógio da lista na tela inicial. Insets de sistema
saem de `ThemeHelper.applyInsets`, como nas outras telas.

Fechar: gesto para baixo pelo `EdgeGestureLayout`, como em `SearchActivity`. O
botão voltar do sistema também funciona.

## Encerrar apps, e o que acontece sem Shizuku

O X **sempre** aparece e **sempre** significa "encerrar este app". Não há
variação de layout conforme a configuração — a tela não muda de cara porque o
Shizuku está ou não ligado.

Quando o Shizuku não está disponível, o toque no X mostra uma linha de texto
explicando que o Shizuku precisa estar rodando. É o comportamento honesto:
não esconde o botão, não finge que encerrou, e não faz a lista mudar de
aparência. O mesmo vale para o "limpar tudo".

`TaskKiller` cobre duas granularidades, porque encerrar um app e encerrar
todos não são o mesmo trabalho:

```java
public interface TaskKiller {
    boolean isAvailable();
    void kill(@NonNull String packageName);
    void killAll(@NonNull List<String> packageNames);
}
```

`ShizukuTaskKiller` implementa as duas com `am force-stop` por pacote, e remove
as tasks. `isAvailable()` responde se o binder foi adquirido. Nenhuma chamada
lança: falha do Shizuku é um estado esperado, não uma exceção — e o app já
trata exceção como caminho normal em `AppEntry.launch()` e
`AppRepository.rankQuietly()`.

Dependências, ambas no Maven Central:

```gradle
implementation "dev.rikka.shizuku:api:13.1.5"
implementation "dev.rikka.shizuku:provider:13.1.5"
```

O `provider` é o que acquire o binder; sem ele, `api` sozinho não conecta.

Consequência aceita: o Shizuku precisa estar instalado e rodando. Em modo ADB
ele morre a cada reboot e precisa de `adb` para voltar. Isso é OK para uso
pessoal e foi decisão consciente — o alternativa era o X não existir sem
Shizuku, e aí o botão não estaria lá na maior parte do tempo.

## Permissões e manifesto

- `PACKAGE_USAGE_STATS` — permissão de acesso especial, não de runtime. O
  usuário concede em Ajustes → Aplicativos → Minimal Launcher → Uso de dados →
  Acesso a dados de uso. Não há diálogo do sistema para pedir isso; a tela
  detecta que não tem a permissão e mostra uma linha de texto explicando onde
  ligar, com atalho para as configurações do app. Sem permissão: lista vazia,
  nenhum crash.
- Shizuku não pede nenhuma permissão ao usuário: ele pergunta ao app na
 primeira chamada, e o usuário aceita numa tela do próprio Shizuku.
- `ShizukuProvider` no manifesto, declarado pelo `provider`.
- `RecentAppsActivity`: `excludeFromRecents=true`, `launchMode=singleTop`,
  `configChanges` igual ao das outras telas, tema `Theme.MinimalLauncher`.
- **A activity não recebe `CATEGORY_LAUNCHER`.** É isso que a mantém fora da
  lista de apps do launcher e da tela de busca. O atalho do sistema a alcança
  por `explicit intent` (o usuário aponta para o componente), que não depende de
  filtro de categoria.

## Verificação

`gradle assembleDebug` verde **ou** não há claim de pronto. O projeto não tem
testes automatizados — `AppSearch.java` é a única classe pura e testável, e esta
mudança não a toca. A verificação é manual, no dispositivo:

1. Sem a permissão de uso: a tela abre, lista vazia, linha explicando como
   conceder. Sem crash.
2. Concedida: a lista sai na ordem de uso real, e apps abertos por notificação
   ou link aparecem — é a diferença em relação a rastrear só o launcher.
3. Tocar no nome abre o app certo, inclusive quando há várias tasks do mesmo app.
4. Tocar no nome **não** toca por alto o X: o ripple acende só o X.
5. Nome longo corta com reticências e o X não sai da linha.
6. Com Shizuku rodando: o X encerra o app — confirmado pelo `ps` ou pelo
   `dumpsys`, não só pela linha sumindo.
7. Sem Shizuku: o X mostra a linha explicando, o layout não muda, o app não
   crasha.
8. "Limpar tudo" encerra todos os apps da lista e esvazia a tela.
9. Apps de sistema aparecem na lista **sem** X.
10. Mão dominante invertida: nomes e X trocam de lado em bloco.
11. A tela **não** aparece na busca nem na lista de apps.
12. Ajustes de fonte e cor continuam valendo aqui: trocar o combo na tela de
    configurações muda esta tela também.

## Fora do escopo

Ícones, widgets, pastas, root, publicação na Play Store. E também:

- **Interceptar o gesto de recents.** É a alternativa real ao atalho, e está
  fora por causa do piscar do sistema e da fragilidade em atualização de ROM —
  não por ser impossível.
- **Encerrar apps de sistema.** Nem com Shizuku. Por isso essas linhas não têm X.
- **Rastrear histórico próprio do launcher.** Descartado: é superficial
  (perde tudo que não passou pelo launcher) e o `UsageStats` resolve melhor, com
  uma permissão a mais que o usuário não nota.
- **Fazer o Shizuku iniciar sozinho no boot.** Ele morre a cada reboot e o
  usuário precisa reativar; automatizar isso exigiria root.
- **Swipe para remover linha individual.** A remoção é pelo X.