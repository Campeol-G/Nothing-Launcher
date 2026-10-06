package com.minimal.launcher.apps;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.minimal.launcher.FontCombo;
import com.minimal.launcher.LauncherUtils;
import com.minimal.launcher.Prefs;
import com.minimal.launcher.R;
import com.minimal.launcher.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A linha de um app: so o nome, na fonte de interface, na lateral da ma
 * dominante. Sem icone, sem badge, sem nada alem do texto.
 */
public final class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ViewHolder> {

    private final List<AppEntry> items = new ArrayList<>();
    @Nullable
    private final AppListCallback callback;
    private final float baseTextSizePx;
    private final float fontScale;
    private final int textColor;

    private int textGravity;

    public AppListAdapter(@NonNull Context context,
                          @NonNull List<AppEntry> source,
                          @Nullable AppListCallback callback) {
        this.callback = callback;
        items.addAll(source);

        Prefs prefs = new Prefs(context);
        this.textGravity = gravityFor(prefs.getDominantHand());
        this.fontScale = LauncherUtils.getFontScale(context);
        this.baseTextSizePx = context.getResources().getDimension(R.dimen.app_name_size);
        // A cor vem aqui, e não de ThemeHelper.paint(): as linhas nascem quando
        // o RecyclerView popula, depois de qualquer pintura da árvore. Sem isto
        // elas herdariam a cor do tema — quase preta num combo escuro.
        this.textColor = ThemeHelper.getHomeForegroundColor(context);
    }

    private static int gravityFor(String dominantHand) {
        return Prefs.ALIGN_RIGHT.equals(dominantHand) ? Gravity.END : Gravity.START;
    }

    public void setTextGravity(int gravity) {
        this.textGravity = gravity;
        notifyDataSetChanged();
    }

    @NonNull
    public List<AppEntry> getItems() {
        return items;
    }

    public void setItems(@NonNull List<AppEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
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
        text.setTextColor(textColor);
        text.setTypeface(FontCombo.getInterfaceFont(text.getContext()));

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
