package com.minimal.launcher.apps;

import android.content.Context;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.Prefs;
import com.minimal.launcher.R;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Lista de nomes de apps. Um adapter so, tres modos, porque os tres precisam
 * exatamente do mesmo desenho: uma linha, so o nome, fonte fina.
 *
 * <ul>
 *   <li>{@link #MODE_LAUNCH} - toque abre o app, toque longo abre o menu.</li>
 *   <li>{@link #MODE_PICK} - toque devolve o app para quem chamou.</li>
 *   <li>{@link #MODE_MANAGE} - toque marca/desmarca, toque longo comeca arrasto.</li>
 * </ul>
 */
public final class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    public static final int MODE_LAUNCH = 0;
    public static final int MODE_PICK = 1;
    public static final int MODE_MANAGE = 2;

    private final List<AppEntry> items = new ArrayList<>();
    private final List<AppEntry> source;
    private final Set<String> marked = new HashSet<>();
    private final int mode;
    @Nullable
    private final AppListCallback callback;
    private final int textGravity;
    private final float baseTextSizePx;
    private final float fontScale;

    public AppListAdapter(@NonNull Context context,
                          @NonNull List<AppEntry> source,
                          int mode,
                          @Nullable AppListCallback callback) {
        this.source = source;
        this.mode = mode;
        this.callback = callback;
        setHasStableIds(true);
        items.addAll(source);

        Prefs prefs = new Prefs(context);
        textGravity = Prefs.ALIGN_RIGHT.equals(prefs.getAlignment())
                ? Gravity.END
                : Gravity.START;
        fontScale = prefs.getFontScale();
        baseTextSizePx = context.getResources().getDimension(R.dimen.app_name_size);
    }

    @NonNull
    public List<AppEntry> getItems() {
        return items;
    }

    /** Substitui o conteudo visivel sem mexer na lista que serviu de fonte. */
    public void setItems(@NonNull List<AppEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void moveItem(int from, int to) {
        if (from < 0 || from >= items.size() || to < 0 || to >= items.size() || from == to) {
            return;
        }
        items.add(to, items.remove(from));
        notifyItemMoved(from, to);
    }

    /** Aplica o filtro da busca sobre a lista completa. */
    public void filter(@NonNull String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            setItems(source);
            return;
        }
        List<AppEntry> matches = new ArrayList<>();
        for (AppEntry entry : source) {
            if (normalize(entry.label).contains(normalized)) {
                matches.add(entry);
            }
        }
        setItems(matches);
    }

    /**
     * Marca um item como "sera removido". Só faz sentido no modo MANAGE - nos
     * outros modos o toque já tem um destino definido e nada seria riscado.
     */
    public void setMarked(@NonNull String component, boolean isMarked) {
        if (mode != MODE_MANAGE) {
            return;
        }
        boolean changed = isMarked ? marked.add(component) : marked.remove(component);
        if (changed) {
            for (int index = 0; index < items.size(); index++) {
                if (items.get(index).component.equals(component)) {
                    notifyItemChanged(index);
                    return;
                }
            }
        }
    }

    public boolean isMarked(@NonNull String component) {
        return mode == MODE_MANAGE && marked.contains(component);
    }

    @NonNull
    public List<AppEntry> getUnmarkedItems() {
        List<AppEntry> result = new ArrayList<>(items.size());
        for (AppEntry entry : items) {
            if (!marked.contains(entry.component)) {
                result.add(entry);
            }
        }
        return result;
    }

    public void clearMarks() {
        if (marked.isEmpty()) {
            return;
        }
        marked.clear();
        notifyDataSetChanged();
    }

    /** Remove acentos e caixa: "cafe" encontra "Cafe". */
    @NonNull
    private static String normalize(@NonNull String value) {
        String lower = value.toLowerCase(Locale.getDefault());
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_name, parent, false);
        return new ViewHolder((TextView) view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final AppEntry entry = items.get(position);
        TextView text = holder.text;
        text.setText(entry.label);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, baseTextSizePx * fontScale);
        text.setGravity(textGravity);

        boolean isMarked = mode == MODE_MANAGE && marked.contains(entry.component);
        text.setPaintFlags(isMarked
                ? text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                : text.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        text.setAlpha(isMarked ? 0.4f : 1f);

        // Os tres modos usam os mesmos dois callbacks; quem muda e o que a
        // activity faz com eles (abrir o app, devolver o app, marcar ou arrastar).
        text.setOnClickListener(v -> {
            if (callback != null) {
                callback.onAppClick(entry);
            }
        });
        text.setOnLongClickListener(v -> callback != null && callback.onAppLongClick(v, entry));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.hashCode();
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        ViewHolder(TextView text) {
            super(text);
            this.text = text;
            text.setSingleLine(true);
            text.setElegantTextHeight(true);
        }
    }
}