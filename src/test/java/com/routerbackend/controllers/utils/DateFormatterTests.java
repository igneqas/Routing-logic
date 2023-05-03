package com.routerbackend.controllers.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;

public class DateFormatterTests {

    @Test
    public void formatDate_correctlyFormatsDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(123 + 1900, Calendar.MAY, 1, 10, 10, 10);
        String formattedDate = DateFormatter.formatDate(calendar.getTime());
        Assert.assertEquals("2023-05-01 10:10", formattedDate);
    }
}
