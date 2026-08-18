package com.whereisit.app.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateTimeUtil {

    private DateTimeUtil() {
    }

    public static String format(long millis) {
        if (millis <= 0) {
            return "-";
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA);
        return formatter.format(new Date(millis));
    }
}
