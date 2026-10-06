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
 * sistema. Para a data usamos o melhor padrao disponível para o locale
 * (skeleton), que respeita a ordem de dia/mes do pais em vez de fixar "M/d".
 */
public final class ClockFormatter {

    private ClockFormatter() {
    }

    public static String time(@NonNull Context context, long millis) {
        return DateFormat.getTimeFormat(context).format(new Date(millis));
    }

    /** Data compacta (dd MMM), sempre com o nome do mês no idioma do celular. */
    public static String dateCompact(@NonNull Context context, long millis) {
        Locale locale = Locale.getDefault();
        return new java.text.SimpleDateFormat("dd MMM", locale).format(new Date(millis));
    }

    public static String date(@NonNull Context context, long millis) {
        Locale locale = Locale.getDefault();
        String skeleton = DateFormat.getBestDateTimePattern(locale, "EEEdMMM");
        return new java.text.SimpleDateFormat(skeleton, locale).format(new Date(millis));
    }
}