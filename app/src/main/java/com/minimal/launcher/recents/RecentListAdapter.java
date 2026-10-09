package com.minimal.launcher.recents;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.FontCombo;
import com.minimal.launcher.LauncherUtils;
import com.minimal.launcher.Prefs;
import com.minimal.launcher.R;
import com.minimal.launcher.ThemeHelper;
import com.minimal.launcher.apps.AppEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * A linha da tela de recentes: o nome do app e o X que tira ele de lá.
 *
 * <p>Este adapter é separado do {@code AppListAdapter} de propósito. Lá a linha é
 * um nome só, e esta tem duas coisas: mudar o comportamento de duas telas que já
 * funcionam por causa de uma que é nova seria a troca errada.
 *
 * <p>A linha espelha a mão dominante. Com a mão direita, que é o padrão, o nome
 * encosta na margem direita e os Xs formam uma coluna alinhada à esquerda; com a
 * mão esquerda, o inverso. Deixar o X sempre à direita pareceria mais simples, mas
 * com os nomes à direita ele ficaria colado no nome num caso e solto no meio da
 * tela no outro — quebrando o alinhamento em metade das configurações.
 */
public final class RecentListAdapter extends RecyclerView.Adapter<RecentListAdapter.ViewHolder> {

    /** O que a tela precisa saber sobre o que o usuário fez em uma linha. */
    public interface Callback {

        /** Toque no nome: abrir o app. */
        void onAppClick(@NonNull AppEntry entry);

        /** Toque no X: tirar o app da lista. */
        void onDismiss(@NonNull AppEntry entry, int position);

        /** Toque no "limpar tudo". */
        void onClearAll();
    }

    /**
     * Componente do botão "limpar tudo", que é a última linha.
     *
     * <p>Ele é um {@link AppEntry} sintético com este componente, e nenhum app real
     * o tem. Comparar o componente é o que impede o adapter de tentar abrir um app
     * nele: sem isso o toque cairia em {@code AppEntry.launch()}, que cai no
     * fallback por pacote e abriria o app errado em vez de não fazer nada.
     *
     * <p>É a única linha sem X, e o motivo é o mesmo que faz o botão existir sem
     * X: ele já É sair da lista inteira, então o X repetiria o próprio rótulo.
     */
    public static final String CLEAR_ALL_COMPONENT = "minimal.launcher/CLEAR_ALL";

    private final List<AppEntry> items = new ArrayList<>();
    @Nullable
    private final Callback callback;
    private final float baseTextSizePx;
    private final float fontScale;
    private final int textColor;

    private int textGravity;
    private boolean dismissOnStart;

    public RecentListAdapter(@NonNull Context context,
                             @NonNull List<AppEntry> source,
                             @Nullable Callback callback) {
        this.callback = callback;
        items.addAll(source);

        Prefs prefs = new Prefs(context);
        this.dismissOnStart = Prefs.ALIGN_RIGHT.equals(prefs.getDominantHand());
        this.textGravity = dismissOnStart ? Gravity.END : Gravity.START;
        this.fontScale = LauncherUtils.getFontScale(context);
        this.baseTextSizePx = context.getResources().getDimension(R.dimen.app_name_size);
        // A cor vem aqui, e não de ThemeHelper.paint(): as linhas nascem quando
        // o RecyclerView popula, depois de qualquer pintura da árvore.
        this.textColor = ThemeHelper.getHomeForegroundColor(context);
    }

    public void setItems(@NonNull List<AppEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** Tira a linha da posição dada, depois que o X foi apertado. */
    public void removeAt(int position) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        items.remove(position);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_app, parent, false);
        return new ViewHolder((LinearLayout) view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final AppEntry entry = items.get(position);
        TextView name = holder.name;
        TextView dismiss = holder.dismiss;

        name.setText(entry.label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx * fontScale);
        name.setGravity(textGravity);
        name.setTextColor(textColor);
        name.setTypeface(FontCombo.getInterfaceFont(name.getContext()));

        boolean isClearAll = CLEAR_ALL_COMPONENT.equals(entry.component);
        if (isClearAll) {
            dismiss.setVisibility(View.GONE);
        } else {
            dismiss.setVisibility(View.VISIBLE);
            // O X é informação secundária: recua sem sumir, o mesmo 0.7 que as
            // configurações usam para o valor atual de uma opção.
            dismiss.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx * fontScale);
            dismiss.setTextColor(textColor);
            dismiss.setTypeface(FontCombo.getInterfaceFont(dismiss.getContext()));
            dismiss.setAlpha(0.7f);
            applyDismissSide(holder);
            dismiss.setOnClickListener(v -> {
                if (callback != null) {
                    int current = holder.getBindingAdapterPosition();
                    if (current != RecyclerView.NO_POSITION) {
                        callback.onDismiss(entry, current);
                    }
                }
            });
        }

        name.setOnClickListener(v -> {
            if (callback == null) {
                return;
            }
            if (isClearAll) {
                callback.onClearAll();
            } else {
                callback.onAppClick(entry);
            }
        });
    }

    /**
     * Coloca o X na ponta oposta da mão dominante.
     *
     * <p>A ordem dos filhos num LinearLayout é fixa no XML, então inverter o lado do
     * X significa reordenar os dois filhos. Reordenar por posição de adapter
     * deixaria o par nome/X misturado numa lista rolável — o par se separa assim que
     * um item é reciclado.
     */
    private void applyDismissSide(@NonNull ViewHolder holder) {
        LinearLayout row = holder.row;
        int wantedIndex = dismissOnStart ? 0 : 1;
        if (row.getChildAt(wantedIndex) == holder.dismiss) {
            return;
        }
        row.removeView(holder.dismiss);
        row.addView(holder.dismiss, wantedIndex);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout row;
        final TextView name;
        final TextView dismiss;

        ViewHolder(LinearLayout row) {
            super(row);
            this.row = row;
            this.name = row.findViewById(R.id.recent_app_name);
            this.dismiss = row.findViewById(R.id.recent_app_dismiss);
            name.setElegantTextHeight(true);
        }
    }
}