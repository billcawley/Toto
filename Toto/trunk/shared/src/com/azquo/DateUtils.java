package com.azquo;

/*

Created 19th September 2017 by EFC. Initially to factor converting between enw and old Java date classes.

 */

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

public class DateUtils {


    // bottom two lines off the net, needed as result sets don't use the new date classes
    public static LocalDateTime getLocalDateTimeFromDate(Date date) {
        if (date == null) {
            return null;
        }
        Instant instant = Instant.ofEpochMilli(date.getTime());
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public static Date getDateFromLocalDateTime(LocalDateTime date) {
        if (date == null) {
            return null;
        }
        return Date.from(date.atZone(ZoneId.systemDefault()).toInstant());
    }
}
