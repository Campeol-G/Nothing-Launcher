#!/usr/bin/env python3
"""Confere a cor de fundo de cada print contra a paleta do ColorCombo.

Um print de cor errada e pior do que print nenhum: no README ele affirmaria
uma cor que o app nao mostrou. A cor e lida do proprio arquivo, num pixel de
canto, e comparada com a paleta -- o script nao confia no nome do arquivo.
"""
import sys
from PIL import Image

# HOMES e SEARCH_BARS de ColorCombo.java, na ordem do arquivo (indice 0 = combo 1).
HOME = {1: "F4EFE3", 2: "E8DDC8", 3: "E6E5E0", 4: "CCD3D6", 6: "E7D8C3",
        9: "C9DEEB", 10: "F2F0E9", 11: "F2F0E9"}
SEARCH = {1: "E8E1D3", 2: "DCD0BA", 3: "D8D7D2", 4: "C0C9CD", 6: "D9C8B0",
          9: "BBD6E6", 10: "E5E2D9", 11: "E5E2D9"}
# A 11 e tirada de noite, porque de dia o app troca o OLED por off-white e ela
# viraria uma copia da 10.
HOME[11] = "000000"
SEARCH[11] = "000000"

PONTOS = [(4, 4), (8, 700), (540, 400), (1075, 1200)]


def cor_de_fundo(caminho):
    img = Image.open(caminho).convert("RGB")
    w, h = img.size
    for x, y in PONTOS:
        px = img.getpixel((min(x, w - 1), min(y, h - 1)))
        return "%02X%02X%02X" % px
    return "??????"


def main(diretorio="images"):
    erros = 0
    for n in sorted(HOME):
        esperado_home = HOME[n]
        esperado_search = SEARCH[n]
        # A busca tem paleta propria: fundo dela nao e o da tela inicial.
        casos = [("homescreen", {HOME[n]}),
                 ("activitScreen", {HOME[n]}),
                 ("searchScreen", {SEARCH[n]})]
        for prefixo, aceitos in casos:
            caminho = f"{diretorio}/{prefixo}{n}.png"
            try:
                achado = cor_de_fundo(caminho)
            except FileNotFoundError:
                print(f"  FALTA   {caminho}")
                erros += 1
                continue
            if achado in aceitos:
                print(f"  ok      {prefixo}{n}.png  #{achado}")
            else:
                esperado = "/".join(sorted("#" + c for c in aceitos))
                print(f"  ERRADO  {prefixo}{n}.png  veio #{achado}, esperado {esperado}")
                erros += 1
    print(f"\n{erros} arquivo(s) com a cor errada ou faltando.")
    return 1 if erros else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:]))