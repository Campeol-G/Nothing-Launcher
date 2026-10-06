package com.minimal.launcher.settings;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.minimal.launcher.ColorCombo;
import com.minimal.launcher.FontCombo;
import com.minimal.launcher.Prefs;
import com.minimal.launcher.R;
import com.minimal.launcher.ThemeHelper;

/**
 * Configuracoes dentro do proprio launcher, no mesmo estilo: so texto, sem
 * icones, sem barra de acao.
 *
 * <p>
 * Sao quatro opcoes e nada mais: cor de fundo, combinação de fontes, ma
 * dominante e tamanho da fonte. Cada escolha altera a tela principal, entao a
 * activity se recria em vez de existir um mecanismo de "a tela de baixo se
 * atualize".
 */
public final class SettingsActivity extends AppCompatActivity {

  private Prefs prefs;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    ThemeHelper.applyNightMode(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_settings);

    prefs = new Prefs(this);

    View root = findViewById(R.id.root);
    ThemeHelper.applyInsets(this, root, 26, 30, 18);
    ThemeHelper.applySystemBars(this);
    ThemeHelper.paint(this, root, false);

    TextView title = findViewById(R.id.settings_title);
    title.setTypeface(FontCombo.getTitleFont(this));
    title.setTextSize(TypedValue.COMPLEX_UNIT_PX,
        getResources().getDimension(R.dimen.settings_title_size));

    buildRows(findViewById(R.id.settings_rows));
  }

  private void buildRows(@NonNull LinearLayout rows) {
    rows.addView(option(getString(R.string.settings_color_combo_title),
        colorComboLabel(prefs.getColorCombo()),
        () -> chooseSingle(
            getString(R.string.settings_color_combo_title),
            colorComboLabels(),
            prefs.getColorCombo(),
            value -> {
              prefs.setColorCombo(value);
              recreate();
            })));

    rows.addView(option(getString(R.string.settings_font_combo_title),
        fontComboLabel(prefs.getFontCombo()),
        () -> chooseSingle(
            getString(R.string.settings_font_combo_title),
            fontComboLabels(),
            prefs.getFontCombo(),
            value -> {
              prefs.setFontCombo(value);
              recreate();
            })));

    rows.addView(option(getString(R.string.settings_dominant_hand_title),
        handLabel(prefs.getDominantHand()),
        () -> chooseSingle(
            getString(R.string.settings_dominant_hand_title),
            new String[] { getString(R.string.hand_left),
                getString(R.string.hand_right) },
            Prefs.ALIGN_RIGHT.equals(prefs.getDominantHand()) ? 1 : 0,
            index -> {
              prefs.setDominantHand(index == 1
                  ? Prefs.ALIGN_RIGHT
                  : Prefs.ALIGN_LEFT);
              recreate();
            })));

    rows.addView(option(getString(R.string.settings_font_size_title),
        fontSizeLabel(prefs.getFontScale()),
        () -> {
          String[] labels = new String[Prefs.FONT_SCALES.length];
          String[] keys = new String[Prefs.FONT_SCALES.length];
          for (int index = 0; index < Prefs.FONT_SCALES.length; index++) {
            labels[index] = fontSizeLabel(Prefs.FONT_SCALES[index]);
            keys[index] = String.valueOf(Prefs.FONT_SCALES[index]);
          }
          chooseSingle(getString(R.string.settings_font_size_title), labels,
              closestFontScaleIndex(prefs.getFontScale()),
              index -> {
                prefs.setFontScale(Float.parseFloat(keys[index]));
                recreate();
              });
        }));
  }

  // ---------------------------------------------------------- construcao

  /** Linha de opcao: titulo e valor atual, ambos para a esquerda. */
  private View option(String title, String valueLabel, @NonNull Runnable onClick) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL);
    row.setGravity(Gravity.START);
    row.setBackground(ripple());
    row.setClickable(true);
    row.setFocusable(true);

    row.addView(label(title, R.dimen.settings_row_size, false));

    TextView value = label(valueLabel, R.dimen.settings_summary_size, true);
    row.addView(value, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

    row.setOnClickListener(v -> onClick.run());
    return row;
  }

    private TextView label(String text, int sizeRes, boolean secondary) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimension(sizeRes));
        // Estas linhas nascem depois de ThemeHelper.paint(), que só alcança a
        // árvore que já existia. A cor entra aqui, senão herdariam o
        // textColorPrimary do tema e sumiriam sobre um combo escuro.
        view.setTextColor(ThemeHelper.getHomeForegroundColor(this));
        view.setTypeface(FontCombo.getInterfaceFont(this));
        if (secondary) {
            // Recuado, mas ainda legível: o valor atual é informação, não enfeite.
            view.setAlpha(0.7f);
        }
        return view;
    }

  /** selectableItemBackground resolvido pelo tema - nao existe como recurso. */
  @Nullable
  private android.graphics.drawable.Drawable ripple() {
    TypedValue value = new TypedValue();
    getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
    return value.resourceId != 0
        ? androidx.core.content.ContextCompat.getDrawable(this, value.resourceId)
        : null;
  }

  // -------------------------------------------------------------- dialogo

  private interface OnPicked {
    void onPicked(int index);
  }

  private void chooseSingle(String title, String[] labels, int selectedIndex,
      @NonNull OnPicked onPicked) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
          onPicked.onPicked(which);
          dialog.dismiss();
        })
        .setNegativeButton(R.string.action_cancel, null)
        .show();
  }

  // -------------------------------------------------------------- rotulos

  private String[] colorComboLabels() {
    return new String[] {
        getString(R.string.color_combo_1), getString(R.string.color_combo_2),
        getString(R.string.color_combo_3), getString(R.string.color_combo_4),
        getString(R.string.color_combo_5), getString(R.string.color_combo_6),
        getString(R.string.color_combo_7), getString(R.string.color_combo_8),
        getString(R.string.color_combo_9), getString(R.string.color_combo_10),
        getString(R.string.color_combo_11),
    };
  }

  private String colorComboLabel(int index) {
    String[] labels = colorComboLabels();
    return labels[clamp(index, labels.length)];
  }

  private String[] fontComboLabels() {
    return new String[] {
        getString(R.string.font_combo_1), getString(R.string.font_combo_2),
        getString(R.string.font_combo_3), getString(R.string.font_combo_4),
        getString(R.string.font_combo_5), getString(R.string.font_combo_6),
        getString(R.string.font_combo_7), getString(R.string.font_combo_8),
    };
  }

  private String fontComboLabel(int index) {
    String[] labels = fontComboLabels();
    return labels[clamp(index, labels.length)];
  }

  private String handLabel(String dominantHand) {
    return Prefs.ALIGN_RIGHT.equals(dominantHand)
        ? getString(R.string.hand_right)
        : getString(R.string.hand_left);
  }

  private String fontSizeLabel(float scale) {
    int best = closestFontScaleIndex(scale);
    switch (best) {
      case 0:
        return getString(R.string.font_size_small);
      case 2:
        return getString(R.string.font_size_large);
      case 3:
        return getString(R.string.font_size_huge);
      default:
        return getString(R.string.font_size_medium);
    }
  }

  private int closestFontScaleIndex(float scale) {
    int best = 0;
    float bestDistance = Math.abs(Prefs.FONT_SCALES[0] - scale);
    for (int index = 1; index < Prefs.FONT_SCALES.length; index++) {
      float distance = Math.abs(Prefs.FONT_SCALES[index] - scale);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = index;
      }
    }
    return best;
  }

  private static int clamp(int index, int length) {
    return Math.max(0, Math.min(length - 1, index));
  }
}
