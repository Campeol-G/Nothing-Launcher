# Minimal Launcher — Design

Data: 2026-10-05
Status: aprovado (arquitetura discutida antes de gerar o código)

## Objetivo

Launcher Android nativo de uso pessoal. Uma única tela principal sem ícones,
sem widgets, sem páginas extras e sem animações chamativas. Fundo sólido
desenhado pelo app, tipografia fina (`sans-serif-light`), lista de favoritos
ilimitada rolável com relógio fixo no topo, gesto de deslizar para cima para a
lista de todos os apps, configurações dentro do próprio launcher.

Não será publicado na Play Store. Sem ícones de apps, sem widgets, sem múltiplas
telas/páginas, sem pastas, sem root.

## Ambiente e toolchain

| Item | Valor | Motivo |
|---|---|---|
| Linguagem | Java 17 | Toda a API necessária é do framework, sem wrapper Kotlin-only. Kotlin não traz ganho aqui. |
| Gradle | 9.8.0 | Já instalado na máquina; suportado pelo AGP 9.4.1. |
| AGP | 9.4.1 | Última estável. |
| compileSdk / targetSdk | 37 | `android-37.0` = Android 17, `PreviewSdkInt=0` (estável). |
| minSdk | 26 | Requisito do usuário. Cobre adaptive icons e dispensando flags de compatibilidade. |
| Dependências | `appcompat`, `recyclerview`, `core` | Só o necessário: night mode, listas e insets. Sem Material, sem Glide. |
| Package | `com.minimal.launcher` | `applicationId` provisório, fácil de trocar. |

`app_name` é o único ponto de troca do nome visível do app.

## Mapa de pacotes e classes

Pacote raiz `com.minimal.launcher`, quatro subpacotes, uma responsabilidade
por classe.

### `/` — núcleo da tela principal

| Classe | Responsabilidade |
|---|---|
| `LauncherApp` | `Application`. Inicializa o repositório, semeia os 3 favoritos padrão e registra o `PackageChangeReceiver`. |
| `HomeActivity` | Launcher (`CATEGORY_HOME` + `LAUNCHER`, `singleTask`). Compõe relógio fixo + lista de favoritos + indicador de notificação. Back é no-op. Duplo toque bloqueia. Toque longo no relógio/área vazia abre configurações. |
| `EdgeGestureLayout` | `FrameLayout` que resolve o conflito do gesto vertical. Decide intercepção com base em três predicados e dispara o callback. Também alimenta um `GestureDetector` opcional para o duplo toque. |
| `ThemeHelper` | Aplica tema (sistema/preto/branco), cores de fundo/foreground, edge-to-edge, esconder barras, insets. |
| `ClockFormatter` | `android.text.format.DateFormat.getTimeFormat` (respeita 12h/24h) + `DateFormat.getBestDateTimePattern` para a data. |
| `SystemTimeWatcher` | Receptor dinâmico de `ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, `ACTION_DATE_CHANGED`. |
| `Prefs` | Acesso tipado ao `SharedPreferences`. Fonte única de verdade das preferências. |
| `FavoritesStore`? | Não — ver `apps`. |

### `apps/` — dados de app e listas

| Classe | Responsabilidade |
|---|---|
| `AppEntry` | Imutável: `packageName`, `label`, `component` achatado. Sabe se lançar a si mesmo. |
| `AppRepository` | Singleton. Cache da lista de todos os apps (ordenada por `Collator`) e resolução dos apps padrão por intent implícito. |
| `FavoritesStore` | Ordem persistida dos favoritos: `get`/`isFavorite`/`add`/`remove`/`move`/`ensureSeeded`. Poda componentes que sumiram. |
| `AppListAdapter` | Um adapter, três modos: `LAUNCH`, `PICK`, `MANAGE`. |
| `AppListCallback` | Interface de callbacks da lista. |
| `AllAppsActivity` | Lista alfabética + busca focada com teclado aberto. Modos `BROWSE` e `PICK`. |
| `PackageChangeReceiver` | `PACKAGE_ADDED`/`REMOVED`/`REPLACED`/`CHANGED` → invalida cache e poda favoritos. |
| `AppMenu` | Diálogo do toque longo: favoritar/remover, informações, desinstalar. |

### `settings/`

| Classe | Responsabilidade |
|---|---|
| `SettingsActivity` | Tela de configurações no mesmo estilo, sem ícones. Filtro `APPLICATION_PREFERENCES` declarado. |
| `ManageFavoritesActivity` | Reordenar (arrastar), marcar para remover, adicionar via `AllAppsActivity` em modo `PICK`. |

### `services/`

| Classe | Responsabilidade |
|---|---|
| `LauncherNotificationListener` | `NotificationListenerService`. Conta notificações ativas. `isConnected()` para a UI. |
| `LauncherLockService` | `AccessibilityService`. Único propósito: `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)`. |
| `NotificationState` | Ponte estática entre o listener e a activity. Sem `LocalBroadcastManager`, sem polling. |

## Resolução do conflito de scroll

Regra pedida: com a lista de favoritos coubendo inteira, o gesto funciona em
qualquer lugar da tela. Com a lista rolável, deslizar para cima **dentro** dela
apenas rola a lista; o gesto abre todos os apps quando (a) a lista já está no
fim e o usuário continua puxando para cima, ou (b) o gesto começou fora da lista.

Implementado em `EdgeGestureLayout`, com uma máquina de estados explícita em vez
de `GestureDetector` (precisamos de controle sobre o instante em que a "porta"
abre):

- Todo evento passa por `dispatchTouchEvent` → `observe(ev)`, então a decisão
  é tomada mesmo quando a `RecyclerView` consome o toque.
- No `ACTION_DOWN` registra-se `inChildAtDown` (o ponto caiu dentro dos bounds
  globais da lista?) e `gateOpenedAtTravel = 0`.
- O portão é reavaliado a cada `MOVE`:
  - `UP`: `!listaRolável || !inChildAtDown || listaNoFim`
  - `DOWN`: `!listaRolável || !inChildAtDown || listaNoInicio`
- Quando o portão fecha→abre no meio do gesto, `gateOpenedAtTravel` recebe o
  deslocamento atual. Assim "continuar deslizando depois do fim" exige trabalho
  extra real (48dp), e não dispara só porque a lista chegou ao fim.
- `onInterceptTouchEvent` devolve `true` apenas quando vamos assumir o gesto; a
  `RecyclerView` recebe `ACTION_CANCEL` e para de rolar.
- Disparo no `ACTION_UP` quando `travel - gateOpenedAtTravel` passa do limiar
  (8% da altura, entre 72dp e 160dp) **e** o portão continua aberto.
- Fora do `RecyclerView` não é necessário `OnItemTouchListener`: `super`
  `dispatchTouchEvent` da activity sempre entrega os eventos ao layout raiz.

O mesmo componente fecha `AllAppsActivity` para baixo, com o portão invertido
(`listaNoInicio`).

## Decisões

1. **Duplo toque** é detectado pelo `GestureDetector` dentro de `HomeActivity`
   (100% confiável na própria UI). O `AccessibilityService` existe apenas
   porque só ele pode chamar `performGlobalAction`. Se não estiver ativo, o
   gesto não faz nada e as configurações explicam como ativar.
2. **Indicador de notificação**: ponto de 6dp centralizado abaixo da data.
   Contagem atualizada por callback do `NotificationState`, apenas quando a
   permissão de acesso a notificações está concedida; caso contrário o
   indicador fica desligado sem quebrar nada.
3. **Tema**: `Theme.AppCompat.DayNight.NoActionBar` + `AppCompatDelegate`.
   `THEME_SYSTEM` é o valor neutro (não mexe no delegate), `THEME_DARK`
   força `MODE_NIGHT_YES`, `THEME_LIGHT` força `MODE_NIGHT_NO`.
4. **Fundo**: nunca usa o wallpaper. `windowBackground` preto e a raiz pinta
   preto/branco em runtime.
5. **Persistência**: `SharedPreferences`. Os favoritos são gravados como uma
   única string com componentes separados por `\n` — `StringSet` do
   `SharedPreferences` é um conjunto sem ordem, e ordem é requisito.
6. **Sem animações**: `android:windowAnimationStyle = @null`.
7. **Bateria**: nenhum serviço em segundo plano além dos dois, e ambos só fazem
   trabalho quando o usuário liga a opção correspondente. O
   `NotificationListenerService` só é habilitado pelo usuário nas Configurações
   do sistema.

## Permissões e manifesto

- `QUERY_ALL_PACKAGES` + `<queries>` para `MAIN`/`LAUNCHER` e para
  `ACTION_DELETE` com esquema `package`. Uso pessoal.
- `LauncherLockService`: `BIND_ACCESSIBILITY_SERVICE`, `exported=true`, metadado
  `res/xml/accessibility_service_config.xml`.
- `LauncherNotificationListener`: `BIND_NOTIFICATION_LISTENER_SERVICE`,
  `exported=true`. Nenhuma permissão de runtime é necessária — o usuário
  concede em Configurações → Acesso a notificações.
- `HomeActivity`: `launchMode=singleTask`, `stateNotNeeded`,
  `excludeFromRecents`, `clearTaskOnLaunch`, categorias `HOME` + `DEFAULT` +
  `LAUNCHER`.
- `SettingsActivity`: `exported=true` com
  `android.intent.action.APPLICATION_PREFERENCES`.
- Receptores de pacote e de tempo usam `RECEIVER_EXPORTED` (com fallback por
  `Build.VERSION`) porque são broadcasts do sistema.

## Fora do escopo

Ícones, widgets, múltiplas telas/páginas, pastas, root, publicação na Play Store.

## Verificação

O projeto é compilado de verdade com o SDK local (`gradle assembleDebug`) antes
de ser entregue como "pronto". Sem build verde, não há claim de pronto.