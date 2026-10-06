package com.minimal.launcher;

import android.graphics.Color;

/**
 * Combinacoes de cores do app (fundo + texto), conforme colors.md.
 *
 * <p>Cada uma das combinacoes define um visual completo para a tela inicial: um
 * fundo confortavel e um texto sobre ele.
 *
 * <p>São dois tipos de combinação:
 *
 * <ul>
 *   <li><b>Claras</b> (1 a 10): valem como escritas de dia. A versao noturna é
 *       a inversao dos canais RGB — fundo claro vira escuro, texto escuro
 *       vira claro.</li>
 *   <li><b>Escura</b> (11, Preto OLED): vale como escrita a noite, que é quando
 *       o preto puro importa num painel OLED. De dia ela tem uma variante clara
 *       propria, porque preto sob luz do dia não é discomfortavel — é escuro
 *       demais para a tela.</li>
 * </ul>
 *
 * <p><b>Configuração manual para testes:</b> mude
 * {@link #CURRENT_COLOR_COMBO_INDEX}. Pelo app, em Configuracoes > Cor de fundo.
 */
public final class ColorCombo {

    /** Indice da combinacao ativa: 10 = Preto OLED (padrao). */
    public static final int CURRENT_COLOR_COMBO_INDEX = 10;

    /** Quantas combinacoes existem. */
    public static final int COUNT = 11;

    /** Combinacoes noturnas: cada entrada = [fundo, texto]. */
    private static final int[][] HOMES = {
        {Color.parseColor("#F4EFE3"), Color.parseColor("#2F2A22")}, // 1. Creme + Marrom
        {Color.parseColor("#E8DDC8"), Color.parseColor("#342C24")}, // 2. Bege + Marrom Escuro
        {Color.parseColor("#E6E5E0"), Color.parseColor("#292929")}, // 3. Cinza Claro + Grafite
        {Color.parseColor("#CCD3D6"), Color.parseColor("#1F2A30")}, // 4. Aço + Grafite frio
        {Color.parseColor("#D9E2D0"), Color.parseColor("#283329")}, // 5. Verde Sálvia + Verde Escuro
        {Color.parseColor("#E7D8C3"), Color.parseColor("#332A20")}, // 6. Areia + Marrom
        {Color.parseColor("#F1E7C8"), Color.parseColor("#302B20")}, // 7. Amarelo Pálido + Marrom
        {Color.parseColor("#E8D5D2"), Color.parseColor("#39272A")}, // 8. Rosa Queimado Claro + Vinho Escuro
        {Color.parseColor("#C9DEEB"), Color.parseColor("#123243")}, // 9. Azul Céu + Azul Petróleo
        {Color.parseColor("#F2F0E9"), Color.parseColor("#1E1E1C")}, // 10. Off-White + Preto Suave
        {Color.parseColor("#000000"), Color.parseColor("#F2F0E9")}, // 11. Preto OLED
    };

    /**
     * Variante diurna dos combos escuros por natureza. Vazio para os claros, que
     * de dia já valem como escritos.
     */
    private static final int[][] HOMES_DAY = {
        null, null, null, null, null, null, null, null, null, null,
        {Color.parseColor("#F2F0E9"), Color.parseColor("#1E1E1C")}, // 11 de dia
    };

    /** Combinacoes noturnas da busca: [fundo, texto, placeholder, borda]. */
    private static final int[][] SEARCH_BARS = {
        {Color.parseColor("#E8E1D3"), Color.parseColor("#3A342B"), Color.parseColor("#71695D"), Color.parseColor("#D8CFBE")},
        {Color.parseColor("#DCD0BA"), Color.parseColor("#40362B"), Color.parseColor("#736859"), Color.parseColor("#CCBDA5")},
        {Color.parseColor("#D8D7D2"), Color.parseColor("#30302F"), Color.parseColor("#70706C"), Color.parseColor("#C9C8C3")},
        {Color.parseColor("#C0C9CD"), Color.parseColor("#22303A"), Color.parseColor("#5E6C74"), Color.parseColor("#A2AFB4")},
        {Color.parseColor("#CBD6C1"), Color.parseColor("#303A30"), Color.parseColor("#687265"), Color.parseColor("#BAC7AF")},
        {Color.parseColor("#D9C8B0"), Color.parseColor("#3C3025"), Color.parseColor("#75685A"), Color.parseColor("#C8B69D")},
        {Color.parseColor("#E6D9B5"), Color.parseColor("#393226"), Color.parseColor("#756B58"), Color.parseColor("#D4C59D")},
        {Color.parseColor("#DCC4C1"), Color.parseColor("#433033"), Color.parseColor("#79686A"), Color.parseColor("#CDAEAA")},
        {Color.parseColor("#BBD6E6"), Color.parseColor("#183B4C"), Color.parseColor("#5B7A8A"), Color.parseColor("#9CBECB")},
        {Color.parseColor("#E5E2D9"), Color.parseColor("#282825"), Color.parseColor("#6D6C66"), Color.parseColor("#D2CFC5")},
        {Color.parseColor("#000000"), Color.parseColor("#F2F0E9"), Color.parseColor("#8A8A85"), Color.parseColor("#2E2E2E")},
    };

    /** Variante diurna da busca para os combos escuros por natureza. */
    private static final int[][] SEARCH_BARS_DAY = {
        null, null, null, null, null, null, null, null, null, null,
        {Color.parseColor("#E5E2D9"), Color.parseColor("#282825"), Color.parseColor("#6D6C66"), Color.parseColor("#D2CFC5")},
    };

    private ColorCombo() {
    }

    /** Este combo é escuro por natureza, e por isso tem variante diurna? */
    public static boolean isDarkNative(int index) {
        return index >= 0 && index < HOMES_DAY.length && HOMES_DAY[index] != null;
    }

    public static int[][] getCombos() {
        return HOMES;
    }

    public static int[][] getCombosDay() {
        return HOMES_DAY;
    }

    public static int[][] getSearchBars() {
        return SEARCH_BARS;
    }

    public static int[][] getSearchBarsDay() {
        return SEARCH_BARS_DAY;
    }
}
