# Minimal Launcher

Launcher Android nativo de uso pessoal. Uma tela, sem ícones, sem widgets, sem
páginas extras, sem animação de transição. Fundo sólido desenhado pelo app
(nunca o wallpaper do sistema), tipografia `sans-serif-light`, lista de favoritos
sem limite.

O nome é provisório: está em `app/src/main/res/values/strings.xml` → `app_name`,
e o `applicationId` está em `app/build.gradle`.

## Requisitos

| Item | Versão |
|---|---|
| JDK | 17+ (o build foi feito com 27) |
| Android SDK | platform **android-37.0** (Android 17) e build-tools 36 |
| Gradle | 9.8 (já vem pelo wrapper) |
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

Ou abra a pasta no Android Studio e deixe ele gerar o wrapper
(`gradle/wrapper/gradle-wrapper.jar`) na primeira sincronização.

Instalar direto pelo terminal:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Definir como launcher padrão

Depois de instalado:

1. Toque e segure em qualquer área vazia da tela inicial → **Configurações**.
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

## Permissões opcionais

Ambas são desligadas por padrão e só o launcher pede, na própria tela de
Configurações. Nenhuma delas é pedida na instalação.

### Indicador de notificação

1. Configurações do launcher → ative **Indicador de notificação**.
2. Se aparecer a linha **Acesso a notificações**, toque nela.
3. Em *Acesso a notificações*, ligue **Minimal Launcher**.

Sem a permissão o ponto simplesmente não aparece; nada trava.

### Duplo toque para bloquear

1. Configurações do launcher → ative **Duplo toque para bloquear**.
2. Um diálogo explica que é preciso o serviço de acessibilidade → **Abrir
   configurações**.
3. Em *Acessibilidade*, ative **Minimal Launcher — bloqueio por toque duplo**.

Notas honestas sobre esse recurso:

- Só um `AccessibilityService` pode travar a tela (`GLOBAL_ACTION_LOCK_SCREEN`).
- Essa ação existe a partir do **Android 9 (API 28)**. Em Android 8 o duplo
  toque não faz nada, e o app não avisa de forma enganosa.
- Se o serviço for desligado, o duplo toque simplesmente não faz nada.

## Como usar

| Gesto / ação | Resultado |
|---|---|
| Tocar no nome | Abre o app |
| Toque longo no nome | Favoritar / Remover dos favoritos, Informações do app, Desinstalar |
| Deslizar para cima | Abre a lista de todos os apps |
| Na lista de todos os apps, um resultado só + enter do teclado | Abre esse app |
| Voltar ou deslizar para baixo | Fecha a lista de todos os apps |
| Toque longo no relógio ou em área vazia | Abre as Configurações |
| Duplo toque na tela principal | Trava a tela (se o serviço estiver ativo) |

Sobre o conflito entre rolar a lista e o gesto:

- Se os favoritos **cabem** na tela, o gesto para cima funciona em qualquer
  lugar.
- Se a lista **rola**, deslizar para cima dentro dela só rola a lista. O gesto
  assume quando a lista já chegou ao fim e o dedo continua subindo, ou quando o
  gesto começou fora da lista (sobre o relógio, por exemplo).

## Estrutura

```
app/src/main/java/com/minimal/launcher/
├── LauncherApp.java            Application: cache, favoritos iniciais, receptor de pacotes
├── HomeActivity.java           tela principal
├── EdgeGestureLayout.java      resolve o conflito gesto x scroll
├── ThemeHelper.java            tema, cores, edge-to-edge, barras, insets
├── Prefs.java                  todas as preferências
├── ClockFormatter.java         hora/data no formato do aparelho
├── SystemTimeWatcher.java      ACTION_TIME_TICK e amigos
├── apps/
│   ├── AppEntry.java           app lançável (pacote, nome, componente)
│   ├── AppRepository.java      lista de apps + resolução dos apps padrão
│   ├── FavoritesStore.java     ordem persistida dos favoritos
│   ├── AppListAdapter.java     um adapter, três modos
│   ├── AppListCallback.java    interface de cliques
│   ├── AppMenu.java            diálogo do toque longo
│   ├── AllAppsActivity.java    todos os apps + busca (modos browse e pick)
│   └── PackageChangeReceiver.java
├── settings/
│   ├── SettingsActivity.java
│   └── ManageFavoritesActivity.java
└── services/
    ├── LauncherNotificationListener.java
    ├── LauncherLockService.java
    └── NotificationState.java
```

A decisão de arquitetura e o porquê de cada escolha estão em
[`docs/superpowers/specs/2026-10-05-minimal-launcher-design.md`](docs/superpowers/specs/2026-10-05-minimal-launcher-design.md).

## Bateria

Os dois únicos serviços do app existem porque o sistema exige:

- `LauncherNotificationListener` só roda se o usuário ligar o acesso a
  notificações nas Configurações do sistema.
- `LauncherLockService` só roda se o usuário ativar o serviço de acessibilidade.

Nenhum receptor está no manifesto. O relógio usa `ACTION_TIME_TICK` (broadcast do
sistema, 1x por minuto) em vez de timer próprio.

## Fora do escopo, por decisão

Ícones, widgets, múltiplas telas, pastas, root e publicação na Play Store.