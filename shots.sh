#!/bin/bash
# Prints das telas do Nothing Launcher, um trio por cor.
#
# A cor mora em SharedPreferences (color_combo). O sed roda dentro do aparelho,
# por um script enviado com adb push: passar o comando com aspas aninhadas pelo
# adb shell nao sobrevive a tres niveis de shell.
#
# O app e parado antes da troca: ele le a preferencia na inicializacao e nao
# ficaria sabendo da mudanca no meio. O script imprime a cor que gravou, e a
# conferimos -- print de cor errada e pior do que print nenhum.
set -e

cd "$(dirname "$0")"
PKG=com.minimal.launcher
OUT="images"
CORES="${*:-1 2 3 4 6 9 10 11}"
CONSULA="gmb"   # digits faltando: abre o Gmail, e mostra o resto que ficou de fora

mkdir -p "$OUT"
adb push /tmp/opencode/setcolor.sh /data/local/tmp/setcolor.sh >/dev/null

acorda() {
  local noite="$1"
  adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
  adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
  # A 11 (Preto OLED) so e preto de noite: de dia o proprio app a troca por
  # off-white, que e a mesma cor da 10. A duas seriam o mesmo arquivo.
  if [ "$noite" = "noite" ]; then
    adb shell cmd uimode night yes >/dev/null 2>&1 || true
  else
    adb shell cmd uimode night no >/dev/null 2>&1 || true
  fi
  sleep 1
}

muda_cor() {
  local n="$1" gravado
  adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
  gravado=$(adb shell "run-as $PKG sh /data/local/tmp/setcolor.sh $n" 2>/dev/null | tr -d '\r' | tail -1)
  local esperado=$((n - 1))
  if [ "$gravado" != "$esperado" ]; then
    echo "ERRO: cor $n nao gravou (esperado indice $esperado, veio '$gravado')" >&2
    exit 1
  fi
}

tira() {
  adb exec-out screencap -p > "$OUT/$1"
  echo "  $1 ($(wc -c < "$OUT/$1") bytes)"
}

for n in $CORES; do
  noite="dia"
  [ "$n" = "11" ] && noite="noite"
  echo "cor $n ($noite)"
  acorda "$noite"
  muda_cor "$n"

  # Inicial
  adb shell am start -W -n "$PKG/.HomeActivity" >/dev/null 2>&1 || true
  sleep 5
  tira "homescreen$n.png"

  # Busca, com o texto digitado: uma tela de busca vazia nao diz o que ela faz.
  # Nao da para abrir por `am start`: a SearchActivity nao e exportada, e o shell
  # nao pode inicia-la. O arrasto para cima, esse mesmo, e o caminho de verdade.
  adb shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  sleep 3
  adb shell input swipe 300 1800 300 1100 300 >/dev/null 2>&1 || true
  sleep 4
  adb shell input text "$CONSULA" >/dev/null 2>&1 || true
  sleep 4
  tira "searchScreen$n.png"
  adb shell input keyevent KEYCODE_BACK >/dev/null 2>&1 || true
  sleep 2

  # Apps recentes
  adb shell am start -W -n "$PKG/.recents.RecentAppsActivity" >/dev/null 2>&1 || true
  sleep 6
  tira "activitScreen$n.png"
done

# Deixa no padrao de fabrica, para o aparelho nao ficar numa cor aleatoria.
acorda dia
muda_cor 11
echo "pronto"