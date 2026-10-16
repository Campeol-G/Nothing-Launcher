package com.minimal.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * A politica do arrasto horizontal: quando ele assume o gesto, e quando vale.
 *
 * <p>Estava dentro da View, onde nao ha como testar. Extrair para uma classe sem
 * Android e o que permite garantir que o gesto em qualquer lugar da tela nao roube
 * a rolagem da lista — que era o risco real de tirar a exigencia da borda.
 */
public class SideGesturePolicyTest {

    // Densidade do aparelho de teste: 1080px de largura a 2.156 dá ~500dp.
    private static final float DENSIDADE = 2.156f;
    private static final float LARGURA = 1080f;
    private static final float SLOP = 17f; // ~8dp

    @Test public void arrastoDeLadoNoMeioDaTelaAssume() {
        // Comecou no meio, foi curto e horizontal. Antes da mudanca a faixa lateral
        // recusava este arrasto; agora ele e o caminho normal.
        assertTrue(SideGesturePolicy.assume(200f, 10f, SLOP));
    }

    @Test public void arrastoVerticalNaoAssume() {
        // Rolar a lista e o gesto de abrir a busca. Nao e nosso.
        assertFalse(SideGesturePolicy.assume(10f, 200f, SLOP));
    }

    @Test public void arrastoDiagonalNaoAssume() {
        // Na diagonal nao da para saber de que lado a pessoa queria ir.
        assertFalse(SideGesturePolicy.assume(100f, 100f, SLOP));
    }

    @Test public void arrastoHorizontalParaOsDoisLadosAssume() {
        // Ambidestro: o gesto nao distingue direita de esquerda, e por isso o
        // comprimento e medido no valor absoluto do deslocamento.
        assertTrue(SideGesturePolicy.assume(-200f, 10f, SLOP));
        assertTrue(SideGesturePolicy.assume(200f, -10f, SLOP));
    }

    @Test public void arrastoAteOMesmoDoSlopNaoAssume() {
        // Abaixo do slop ainda e toque, nao arrasto. Sem isso um dedo tremendo na
        // tela abriria a tela de apps recentes.
        assertFalse(SideGesturePolicy.assume(SLOP, 0f, SLOP));
    }

    @Test public void percursoE8PorCentoDaLargura() {
        assertEquals(1080f * 0.08f,
                SideGesturePolicy.percursoMinimo(LARGURA, DENSIDADE), 0.5f);
    }

    @Test public void percursoTemPisoDe36Dp() {
        // Tela estreita: 8% dariam um arrasto curto demais para ser de proposito.
        float estreito = 360f * DENSIDADE; // ~360dp de largura
        assertEquals(36f * DENSIDADE, SideGesturePolicy.percursoMinimo(estreito, DENSIDADE), 0.5f);
    }

    @Test public void oPisoNaoMandaNaTelaNormal() {
        // No aparelho de teste 8% (86px) vale mais que 36dp (78px): quem manda e
        // a porcentagem, e nao o piso.
        assertEquals(LARGURA * 0.08f,
                SideGesturePolicy.percursoMinimo(LARGURA, DENSIDADE), 0.5f);
    }

    @Test public void soDisparaAcimaDoPercurso() {
        float minimo = SideGesturePolicy.percursoMinimo(LARGURA, DENSIDADE);
        assertFalse(SideGesturePolicy.dispara(minimo - 1f, minimo));
        assertTrue(SideGesturePolicy.dispara(minimo, minimo));
        assertTrue(SideGesturePolicy.dispara(minimo + 50f, minimo));
    }

    @Test public void percorrePeloValorAbsolutoDoDeslocamento() {
        // Arrastar para a esquerda tambem acumula percurso, como o da direita.
        float minimo = SideGesturePolicy.percursoMinimo(LARGURA, DENSIDADE);
        assertTrue(SideGesturePolicy.dispara(Math.abs(-200f), minimo));
    }
}