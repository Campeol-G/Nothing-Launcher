package com.minimal.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Resolve o conflito entre rolar uma lista e abrir outra tela com o mesmo gesto.
 *
 * <p>Regras:
 * <ul>
 *   <li>Se a lista cabe inteira na tela, o gesto vale em qualquer lugar.</li>
 *   <li>Se a lista rola, dentro dela o gesto só rola a lista.</li>
 *   <li>Dentro dela, o gesto assume quando a lista já chegou ao fim (ou ao
 *       início, no gesto para baixo) e o dedo continua na mesma direção.</li>
 *   <li>Fora da lista, o gesto sempre vale.</li>
 * </ul>
 *
 * <p>Por que uma maquina de estados e nao um {@link GestureDetector}: ele decide
 * no {@code onFling}, quando o dedo ja levantou, e nao podemos permitir que o
 * filho consuma os eventos nesse meio-tempo. Aqui o "portao" e reavaliado a cada
 * {@code MOVE}, e {@link #dispatchTouchEvent} - que a plataforma sempre chama
 * primeiro - e o que mantem essa leitura viva mesmo com a lista consumindo o
 * toque. O detector so cuida do duplo toque, opcionalmente.
 */
public class EdgeGestureLayout extends FrameLayout {

    public static final int DIRECTION_UP = 1;
    public static final int DIRECTION_DOWN = -1;
    public static final int DIRECTION_LEFT = 2;
    public static final int DIRECTION_RIGHT = -2;

    /**
     * Faixa lateral onde o gesto nasce.
     *
     * <p>48dp e o mesmo {@code SYSTEM_GESTURE_MARGIN_DP} que a app ja reserva para o
     * gesto do sistema: larga o bastante para o dedo acertar com folga, estreita
     * o bastante para nunca nascer em cima de um item da lista.
     */
    private static final int SIDE_BAND_DP = 48;

    /**
     * Percurso minimo do gesto horizontal, em dp. E o piso, nao a medida.
     *
     * <p>Quem manda e a porcentagem da largura (10%): ela da o mesmo gesto em
     * qualquer aparelho. Este piso so protege a tela estreita, onde 10% dariam um
     * arrasto curto demais para alguém acertar de proposito.
     *
     * <p>Era 18% da largura com piso de 56dp, e era longo demais: atravessar quase
     * um quinto da tela com o dedo para abrir uma lista e um gesto de leitura, nao
     * de comando. O que se pede aqui e um arrasto de polegar.
     */
    private static final int SIDE_MIN_TRAVEL_DP = 40;

    public interface OnEdgeGestureListener {
        void onEdgeGesture();
    }

    private int direction = DIRECTION_UP;
    @Nullable
    private View scrollChild;
    @Nullable
    private OnEdgeGestureListener edgeListener;

    /**
     * O segundo braço: horizontal, independente do vertical.
     *
     * <p>São dois braços porque não competem entre si. O vertical exige
     * {@code |dy| > |dx|} e o horizontal exige o contrário, então quem decide é o
     * próprio arrasto. Com um braço só, o gesto de fechar a tela acabaria engolindo
     * o de abrir a lista de recentes, ou o contrário — e o horizontal ainda teria
     * de disputar com a rolagem da lista.
     */
    private int sideDirection;
    @Nullable
    private OnEdgeGestureListener sideListener;

    /**
     * Faixa lateral onde o gesto nasce. Fora dela o arrasto e da lista.
     *
     * <p>48dp e o mesmo {@code SYSTEM_GESTURE_MARGIN_DP} que a app ja reserva para
     * o gesto do sistema: uma faixa larga o bastante para o dedo acertar com folga,
     * estreita o bastante para nunca nascer em cima de um item da lista.
     */
    private int sideStartBandPx;

    // Estado do gesto horizontal em andamento.
    private boolean sideTracking;
    private float sideTravel;
    private float sideStartX;
    private float sideStartY;
    @Nullable
    private Runnable doubleTapListener;

    /**
     * Faixa da borda inferior onde o gesto deste layout nao vale.
     *
     * <p>Com a navegacao por gestos do Android, arrastar para cima a partir da
     * borda inferior pertence ao sistema — e e o mesmo movimento que o polegar
     * faz para alcancar a lista. Sem esta exclusao, buscar e trocar de app
     * viravam o mesmo gesto, e a busca disparava no meio de outra acao.
     *
     * <p>So o gesto para cima e excluido. Para descer nao ha disputa com o
     * sistema, e a faixa ali só fazia o gesto ser descartado em silencio.
     *
     * <p>O valor vem do inset de gestos do sistema, entao respeita o aparelho: nos
     * que usam gesto, e a altura da barra de navegacao nos que usam botoes.
     */
    private int bottomGestureExclusionPx;

    private final float touchSlop;
    private final Rect childBounds = new Rect();
    private GestureDetector doubleTapDetector;

    // Estado do gesto em andamento.
    private boolean tracking;
    private boolean shouldIntercept;
    private boolean inChildAtDown;
    private boolean startedInSystemGestureZone;
    private float downX;
    private float downY;
    private float travel;
    private float lastSignedTravel;
    private float gateOpenedAtTravel;
    private float minTravelPx;

    public EdgeGestureLayout(@NonNull Context context) {
        this(context, null);
    }

    public EdgeGestureLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = android.view.ViewConfiguration.get(context).getScaledTouchSlop();
        sideStartBandPx = ThemeHelper.dp(context, SIDE_BAND_DP);
        reset();
    }

    /**
     * @param direction {@link #DIRECTION_UP} abre a busca,
     *                  {@link #DIRECTION_DOWN} fecha a tela atual.
     * @param scrollChild a lista que disputa o gesto, ou null se não houver.
     * @param listener o que fazer quando o gesto for aceito.
     */
    public void setEdgeGesture(int direction,
                              @Nullable View scrollChild,
                              @NonNull OnEdgeGestureListener listener) {
        this.direction = direction;
        this.scrollChild = scrollChild;
        this.edgeListener = listener;
    }

    /**
     * Liga (ou desliga) o gesto horizontal.
     *
     * @param direction {@link #DIRECTION_RIGHT} quando os apps encostam na direita
     *                  (e o gesto corre da esquerda para a direita), ou
     *                  {@link #DIRECTION_LEFT} no caso oposto.
     * @param listener o que fazer quando o gesto e aceito; null desliga.
     */
    public void setSideGesture(int direction, @Nullable OnEdgeGestureListener listener) {
        this.sideDirection = direction;
        this.sideListener = listener;
        if (listener == null) {
            resetSide();
        }
    }

    /**
     * Altura da faixa inferior onde o gesto nao vale. Vem dos insets do sistema.
     *
     * @see #bottomGestureExclusionPx
     */
    public void setBottomGestureExclusion(int pixels) {
        bottomGestureExclusionPx = Math.max(0, pixels);
    }

    public void setOnDoubleTapListener(@Nullable Runnable listener) {
        doubleTapListener = listener;
        doubleTapDetector = listener == null ? null : new GestureDetector(
                getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(@NonNull MotionEvent event) {
                        doubleTapListener.run();
                        return true;
                    }
                });
    }

    private void resetSide() {
        sideTracking = false;
        sideTravel = 0f;
        sideStartX = 0f;
        sideStartY = 0f;
    }

    private void reset() {
        resetSide();
        tracking = false;
        shouldIntercept = false;
        inChildAtDown = false;
        startedInSystemGestureZone = false;
        travel = 0f;
        lastSignedTravel = 0f;
        gateOpenedAtTravel = -1f;
        minTravelPx = 0f;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (doubleTapDetector != null) {
            doubleTapDetector.onTouchEvent(event);
        }
        shouldIntercept = observe(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return shouldIntercept;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (tracking) {
            return true;
        }
        boolean handled = super.onTouchEvent(event);
        // Sem isso o gesto para baixo nunca chegava a comecar. Numa tela sem
        // lista e sem view clicavel, o DOWN caia aqui e era devolvido como nao
        // tratado — e o sistema, diante de um DOWN nao tratado, para de entregar
        // os eventos seguintes a essa janela. O arrasto para cima funcionava
        // porque a lista consumia o DOWN e segurava o gesto; sozinho, na tela de
        // busca, ele perdia o resto do toque.
        if (handled || event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            return true;
        }
        // Sem tracking, devolve o default: é o que permite o toque longo na área
        // vazia ser reconhecido por quem define o listener. performClick não é
        // chamado porque clique simples na área vazia não faz nada, por projeto.
        return false;
    }

    /**
     * Máquina de estados. Devolve se este evento deve assumir o gesto.
     *
     * <p>O braço horizontal é consultado antes do vertical em todo {@code MOVE}:
     * quem está de pé é o arrasto na horizontal, e quem decide é o próprio arrasto,
     * não a ordem em que as telas registram os gestos.
     */
    private boolean observe(MotionEvent event) {
        if (sideListener != null) {
            boolean taken = observeSide(event);
            if (taken) {
                return true;
            }
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                travel = 0f;
                lastSignedTravel = 0f;
                tracking = false;
                inChildAtDown = isInsideScrollChild(downX, downY);
                startedInSystemGestureZone = startsInBottomGestureExclusion(downY);
                minTravelPx = computeMinTravel();
                gateOpenedAtTravel = gateIsOpen() ? 0f : -1f;
                return false;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getRawX() - downX;
                float dy = event.getRawY() - downY;
                // Positivo significa "a favor" do gesto escolhido.
                float signed = direction == DIRECTION_UP ? -dy : dy;
                float previousTravel = lastSignedTravel;
                lastSignedTravel = signed;

                if (!tracking) {
                    // Gesto claramente horizontal pertence a lista, nao a nos.
                    if (Math.abs(dy) < Math.abs(dx) * 1.2f) {
                        return false;
                    }
                    if (signed > touchSlop && gateIsOpen()) {
                        // O portao abriu agora: o trabalho extra e medido daqui.
                        gateOpenedAtTravel = Math.max(previousTravel, 0f);
                        tracking = true;
                        travel = signed;
                        return true;
                    }
                    return false;
                }

                travel = Math.max(travel, signed);
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean fire = tracking
                        && gateIsOpen()
                        && travel - Math.max(gateOpenedAtTravel, 0f) >= minTravelPx;
                reset();
                if (fire && edgeListener != null) {
                    edgeListener.onEdgeGesture();
                }
                return false;
            }

            case MotionEvent.ACTION_POINTER_DOWN:
                // Segunda unha: cancela, para nao ter de tratar pinza.
                reset();
                return false;

            default:
                return tracking;
        }
    }

    /**
     * O braço horizontal. Devolve true quando este evento pertence a ele.
     *
     * <p>Três decisões que explicam o comportamento:
     *
     * <ul>
     *   <li><b>Só nasce na faixa lateral.</b> Nascendo no meio da tela, o gesto
     *   disputaria a rolagem da lista com o dedo do polegar. Nascer na borda é o
     *   que torna a disputa impossível: a lista nunca começa ali.
     *   <li><b>Trava de eixo de 1.2, como a do vertical.</b> É o que impede um
     *   arrasto diagonal de acionar o gesto errado.
     *   <li><b>Só percorre, não arremessa.</b> Medir distância e não velocidade:
     *   soltar o dedo com força dispara o gesto sem querer, e o gesto errado é
     *   pior do que o gesto atrasado.
     * </ul>
     */
    private boolean observeSide(MotionEvent event) {
        float width = getWidth() > 0 ? getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sideTracking = false;
                sideTravel = 0f;
                sideStartX = event.getRawX();
                sideStartY = event.getRawY();
                return false;

            case MotionEvent.ACTION_MOVE: {
                if (sideTracking) {
                    float dx = event.getRawX() - sideStartX;
                    float signed = sideDirection == DIRECTION_RIGHT ? dx : -dx;
                    if (signed > sideTravel) {
                        sideTravel = signed;
                    }
                    return true;
                }
                float dx = event.getRawX() - sideStartX;
                float dy = event.getRawY() - sideStartY;
                // Fora da faixa lateral: o arrasto e da lista, nosso nao.
                if (!startedInSideBand(sideStartX, width)) {
                    return false;
                }
                // Trava de eixo: arrasto claramente vertical pertence ao outro
                // braço, e nao a este.
                if (Math.abs(dx) < Math.abs(dy) * 1.2f) {
                    return false;
                }
                float signed = sideDirection == DIRECTION_RIGHT ? dx : -dx;
                if (signed > touchSlop) {
                    sideTracking = true;
                    sideTravel = Math.max(signed, 0f);
                    return true;
                }
                return false;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                boolean fire = sideTracking && sideTravel >= computeSideMinTravel(width);
                resetSide();
                if (fire) {
                    sideListener.onEdgeGesture();
                }
                return false;
            }

            case MotionEvent.ACTION_POINTER_DOWN:
                // Segunda unha cancela o gesto, como no vertical.
                resetSide();
                return false;

            default:
                return sideTracking;
        }
    }

    /**
     * Percurso minimo do gesto horizontal.
     *
     * <p>Medido na largura, e nao na altura: o gesto corre de lado, e um percentual
     * da altura nao diria nada sobre o quanto falta andar. O piso existe para a tela
     * estreita; num aparelho normal quem manda e a porcentagem.
     */
    private float computeSideMinTravel(float width) {
        return Math.max(
                ThemeHelper.dp(getContext(), SIDE_MIN_TRAVEL_DP),
                width * 0.10f);
    }

    private boolean startedInSideBand(float rawX, float width) {
        return rawX <= sideStartBandPx || rawX >= width - sideStartBandPx;
    }

    private boolean isInsideScrollChild(float rawX, float rawY) {
        if (scrollChild == null) {
            return false;
        }
        if (!scrollChild.getGlobalVisibleRect(childBounds)) {
            return false;
        }
        return childBounds.contains((int) rawX, (int) rawY);
    }

    /** O toque comecou na faixa inferior que pertence ao sistema? */
    private boolean startsInBottomGestureExclusion(float rawY) {
        if (direction != DIRECTION_UP || bottomGestureExclusionPx <= 0) {
            // Quem fecha a tela arrasta para baixo, e para baixo a borda
            // inferior pertence apenas ao app.
            return false;
        }
        float height = getHeight() > 0
                ? getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        // A faixa e medida a partir da borda de baixo da tela. A janela e
        // edge-to-edge, entao a propria tela e a referencia.
        return rawY > height - bottomGestureExclusionPx;
    }

    /**
     * O portão. Fechado significa "a lista precisa do gesto agora".
     */
    private boolean gateIsOpen() {
        if (startedInSystemGestureZone) {
            // Nasceu na faixa do sistema: o app nao disputa aquele gesto.
            return false;
        }
        if (scrollChild == null) {
            return true;
        }
        boolean canScrollUp = scrollChild.canScrollVertically(-1);
        boolean canScrollDown = scrollChild.canScrollVertically(1);

        if (!canScrollUp && !canScrollDown) {
            // Coube inteira: o gesto vale em qualquer lugar da tela.
            return true;
        }
        if (!inChildAtDown) {
            // O gesto comecou fora da lista.
            return true;
        }
        // Dentro de uma lista rolavel: so na beirada.
        return direction == DIRECTION_UP ? !canScrollDown : !canScrollUp;
    }

    /**
     * Distancia minima para o gesto valer.
     *
     * <p>Fechar uma tela e um gesto curto e comum: o dedo ja esta indo embora.
     * Abrir a busca compete com o polegar rolando a lista, e ai o percurso longo
     * evita disparar por engano. Por isso o piso e a fracao de tela sao
     * diferentes por direcao: 48dp e 5% para descer, 96dp e 8% para subir.
     */
    private float computeMinTravel() {
        float height = getHeight() > 0
                ? getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        float density = getResources().getDisplayMetrics().density;
        boolean up = direction == DIRECTION_UP;
        float fromHeight = height * (up ? 0.08f : 0.05f);
        float floorDp = up ? 96f : 48f;
        return Math.max(floorDp * density, fromHeight);
    }
}