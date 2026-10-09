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
    }

    private final List<AppEntry> items = new ArrayList<>();
    @Nullable
    private final Callback callback;
    private final float baseTextSizePx;
    private final float fontScale;
    private final int textColor;
    private final int paddingPx;
    private final float dismissTextSizePx;

    private int textGravity;
    /** O X fica na ponta, e o nome encosta na ponta oposta. */
    private boolean dismissOnEnd;

    public RecentListAdapter(@NonNull Context context,
                             @NonNull List<AppEntry> source,
                             @Nullable Callback callback) {
        this.callback = callback;
        items.addAll(source);

        Prefs prefs = new Prefs(context);
        // Mão direita: nomes encostam na esquerda e o X fica na ponta direita.
        // São opostos de propósito — juntos, o dedo tinha que acertar o nome
        // exato para abrir e o X para fechar; separados, a área entre eles toda
        // abre o app e o X vira um alvo fixo.
        this.dismissOnEnd = Prefs.ALIGN_RIGHT.equals(prefs.getDominantHand());
        // O nome encosta na ponta OPOSTA ao X.
        this.textGravity = dismissOnEnd ? Gravity.START : Gravity.END;
        this.fontScale = LauncherUtils.getFontScale(context);
        this.paddingPx = context.getResources().getDimensionPixelSize(R.dimen.recent_row_padding);
        this.dismissTextSizePx = context.getResources().getDimension(R.dimen.recent_dismiss_size);
        this.baseTextSizePx = context.getResources().getDimension(R.dimen.recent_app_name_size);
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
        name.setPadding(0, paddingPx, 0, paddingPx);
        name.setGravity(textGravity);
        name.setTextColor(textColor);
        name.setTypeface(FontCombo.getInterfaceFont(name.getContext()));

        {
            dismiss.setVisibility(View.VISIBLE);
            // O X é informação secundária: recua sem sumir, o mesmo 0.7 que as
            // configurações usam para o valor atual de uma opção.
            dismiss.setTextSize(TypedValue.COMPLEX_UNIT_PX, dismissTextSizePx * fontScale);
            dismiss.setTextColor(textColor);
            dismiss.setTypeface(FontCombo.getInterfaceFont(dismiss.getContext()));
            dismiss.setAlpha(0.7f);
            // Sem isto o leitor de tela anuncia o glifo pelo nome: "multiplicação".
            dismiss.setContentDescription(
                    dismiss.getContext().getString(R.string.recent_dismiss_description));
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

        // O clique é na LINHA, não no nome: com uma mão só, exigir que o dedo
        // acerte o texto e não o espaço vazio ao lado seria exigir precisão que
        // o polegar não tem. O X consome o toque dele porque é clicável e fica
        // por cima do listener da linha.
        holder.row.setOnClickListener(v -> {
            if (callback != null) {
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
        int wantedIndex = dismissOnEnd ? 1 : 0;
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