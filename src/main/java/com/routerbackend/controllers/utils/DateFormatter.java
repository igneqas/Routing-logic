package com.routerbackend.controllers.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateFormatter {
    static String pattern = "yyyy-MM-dd HH:mm";
    static SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
    public static String formatDate(Date date) {
        return dateFormat.format(date);
    }
}
