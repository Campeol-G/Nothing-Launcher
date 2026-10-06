package com.minimal.launcher;

import android.content.Context;
import android.text.format.DateFormat;

import androidx.annotation.NonNull;

import java.util.Date;
import java.util.Locale;

/**
 * Hora e data no formato do proprio celular.
 *
 * <p>{@link DateFormat#getTimeFormat} ja vem com a preferencia de 12h/24h do
 * sistema. As datas usam o melhor padrao disponivel para o locale (skeleton),
 * que respeita a ordem de dia/mes do pais em vez de fixar "M/d".
 *
 * <p>{@link #hours} e {@link #minutes} separam a hora porque o relogio empilhado
 * precisa das duas partes em linhas diferentes. A quebra fica a cargo do locale:
 * o mesmo codigo serve para 18:02, 6:02 PM e outros formatos.
 */
public final class ClockFormatter {

    private ClockFormatter() {
    }

    public static String time(@NonNull Context context, long millis) {
        return DateFormat.getTimeFormat(context).format(new Date(millis));
    }

    /** Data numérica, como 06/10/2026 ou 10/06/2026, conforme o locale. */
    public static String dateNumeric(@NonNull Context context, long millis) {
        Locale locale = Locale.getDefault();
        String skeleton = DateFormat.getBestDateTimePattern(locale, "yMd");
        return new java.text.SimpleDateFormat(skeleton, locale).format(new Date(millis));
    }

    /**
     * Data por extenso, como "6 de outubro de 2026" ou "October 6, 2026".
     *
     * <p>Serve a quem quer a tela preenchida: o texto longo ocupa bem mais espaço
     * do que o numérico, sem precisar de um relógio maior.
     */
    public static String dateExtended(@NonNull Context context, long millis) {
        Locale locale = Locale.getDefault();
        String skeleton = DateFormat.getBestDateTimePattern(locale, "yMMMMd");
        return new java.text.SimpleDateFormat(skeleton, locale).format(new Date(millis));
    }

    /** Parte das horas, já no formato do aparelho. */
    @NonNull
    public static String hours(@NonNull Context context, long millis) {
        return timeParts(context, millis)[0];
    }

    /** Parte dos minutos, já no formato do aparelho. */
    @NonNull
    public static String minutes(@NonNull Context context, long millis) {
        return timeParts(context, millis)[1];
    }

    /**
     * Separa a hora em grupos numéricos, na ordem em que aparecem.
     *
     * <p>Funciona para qualquer formato do aparelho sem enumerate casos: o que
     * importa são os dígitos, não os separadores. "18:02" dá [18, 02]; "6:02 PM"
     * dá [6, 02].
     */
    @NonNull
    private static String[] timeParts(@NonNull Context context, long millis) {
        String[] groups = time(context, millis).split("[^0-9]+");
        int first = 0;
        while (first < groups.length && groups[first].isEmpty()) {
            first++;
        }
        String hours = first < groups.length ? groups[first] : "";
        String minutes = first + 1 < groups.length ? groups[first + 1] : "";
        return new String[]{hours, minutes};
    }
}
