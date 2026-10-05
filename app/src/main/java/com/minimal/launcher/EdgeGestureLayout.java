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

    public interface OnEdgeGestureListener {
        void onEdgeGesture();
    }

    private int direction = DIRECTION_UP;
    @Nullable
    private View scrollChild;
    @Nullable
    private OnEdgeGestureListener edgeListener;
    @Nullable
    private Runnable doubleTapListener;

    private final float touchSlop;
    private final Rect childBounds = new Rect();
    private GestureDetector doubleTapDetector;

    // Estado do gesto em andamento.
    private boolean tracking;
    private boolean shouldIntercept;
    private boolean inChildAtDown;
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
        reset();
    }

    /**
     * @param direction {@link #DIRECTION_UP} abre a lista de todos os apps,
     *                  {@link #DIRECTION_DOWN} fecha a tela atual.
     * @param scrollChild a lista que disputa o gesto.
     * @param listener o que fazer quando o gesto for aceito.
     */
    public void setEdgeGesture(int direction,
                              @NonNull View scrollChild,
                              @NonNull OnEdgeGestureListener listener) {
        this.direction = direction;
        this.scrollChild = scrollChild;
        this.edgeListener = listener;
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

    private void reset() {
        tracking = false;
        shouldIntercept = false;
        inChildAtDown = false;
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
        // Sem tracking, devolve o default: é o que permite o toque longo na área
        // vazia ser reconhecido por quem define o listener. performClick não é
        // chamado porque clique simples na área vazia não faz nada, por projeto.
        return super.onTouchEvent(event);
    }

    /** Máquina de estados. Devolve se este evento deve assumir o gesto. */
    private boolean observe(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getRawX();
                downY = event.getRawY();
                travel = 0f;
                lastSignedTravel = 0f;
                tracking = false;
                inChildAtDown = isInsideScrollChild(downX, downY);
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

    private boolean isInsideScrollChild(float rawX, float rawY) {
        if (scrollChild == null) {
            return false;
        }
        if (!scrollChild.getGlobalVisibleRect(childBounds)) {
            return false;
        }
        return childBounds.contains((int) rawX, (int) rawY);
    }

    /**
     * O portão. Fechado significa "a lista precisa do gesto agora".
     */
    private boolean gateIsOpen() {
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

    /** 8% da altura, com piso e teto para funcionar bem em telas altas e baixas. */
    private float computeMinTravel() {
        float height = getHeight() > 0
                ? getHeight()
                : getResources().getDisplayMetrics().heightPixels;
        float density = getResources().getDisplayMetrics().density;
        float fromHeight = height * 0.08f;
        float min = Math.min(160f * density, 96f * density);
        return Math.max(min, fromHeight);
    }
}