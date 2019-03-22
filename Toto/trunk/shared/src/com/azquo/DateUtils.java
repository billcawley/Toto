package com.azquo;

/*

Created 19th September 2017 by EFC. Initially to factor converting between enw and old Java date classes.

 */

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class DateUtils {


    public static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ukdf2 = DateTimeFormatter.ofPattern("d-M-yy");
    private static final DateTimeFormatter ukdf3 = DateTimeFormatter.ofPattern("d-MMM-yyyy");
    private static final DateTimeFormatter ukdf3a = DateTimeFormatter.ofPattern("d-MMM-yy");
    private static final DateTimeFormatter ukdf4 = DateTimeFormatter.ofPattern("d-M-yyyy");
    private static final DateTimeFormatter usdf2 = DateTimeFormatter.ofPattern("M-d-yy");
    private static final DateTimeFormatter usdf3 = DateTimeFormatter.ofPattern("MMM-d-yyyy");
    private static final DateTimeFormatter usdf3a = DateTimeFormatter.ofPattern("MMM-d-yy");
    private static final DateTimeFormatter usdf4 = DateTimeFormatter.ofPattern("M-d-yyyy");

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

    private static LocalDate tryDate(String maybeDate, DateTimeFormatter dateTimeFormatter) {
        try {
            return LocalDate.parse(maybeDate, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // how many years in the future will we allow on two digit dates before we take 100 years off the date
    private static long towDigitYearFutureThreshold = 10;

    public static LocalDate isADate(String maybeDate) {
        String dateToTest = maybeDate.replace("/", "-").replace(" ", "-");
        if (dateToTest.length() > 5 && dateToTest.charAt(1) == '-') dateToTest = "0" + dateToTest;
        if (dateToTest.length() > 5 && dateToTest.charAt(2)=='-' && dateToTest.charAt(4) == '-') dateToTest = dateToTest.substring(0,3) + "0" + dateToTest.substring(3);
        LocalDate date = tryDate(dateToTest.length() > 10 ? dateToTest.substring(0, 10) : dateToTest, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(dateToTest.length() > 10 ? dateToTest.substring(0, 10) : dateToTest, ukdf4);
        if (date != null) return date;
        date = tryDate(dateToTest.length() > 11 ? dateToTest.substring(0, 11) : dateToTest, ukdf3);
        if (date != null) return date;
        /* these two last ones use two digit years - Java will assume for example that 16/08/93 is 16/08/2093.
        I shall manually compensate - if the date is more than 10 years in the future then take 100 off it. This might need to be adjusted.
         */

        date = tryDate(dateToTest.length() > 8 ? dateToTest.substring(0, 8) : dateToTest, ukdf2);
        if (date!= null)
        {
            return date.isAfter(LocalDate.now().plusYears(towDigitYearFutureThreshold)) ? date.minusYears(100) : date;
        }
        date = tryDate(dateToTest, ukdf3a);
        if (date!= null)
        {
            return date.isAfter(LocalDate.now().plusYears(towDigitYearFutureThreshold)) ? date.minusYears(100) : date;
        }
        return null;
    }

    public static LocalDate isUSDate(String maybeDate) {
        String dateToTest = maybeDate.replace("/", "-").replace(" ", "-");
        if (dateToTest.length() > 5 && dateToTest.charAt(1) == '-') dateToTest = "0" + dateToTest;
        if (dateToTest.length() > 5 && dateToTest.charAt(2) == '-' && dateToTest.charAt(4) == '-') dateToTest = dateToTest.substring(0,3) + "0" + dateToTest.substring(3);
        LocalDate date = tryDate(dateToTest.length() > 10 ? dateToTest.substring(0, 10) : dateToTest, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(dateToTest.length() > 10 ? dateToTest.substring(0, 10) : dateToTest, usdf4);
        if (date != null) return date;
        date = tryDate(dateToTest.length() > 11 ? dateToTest.substring(0, 11) : dateToTest, usdf3);
        if (date != null) return date;

        /* as above compensate for formates with two digit years*/

        date = tryDate(dateToTest.length() > 11 ? dateToTest.substring(0, 11) : dateToTest, usdf3a);
        if (date!= null)
        {
            return date.isAfter(LocalDate.now().plusYears(towDigitYearFutureThreshold)) ? date.minusYears(100) : date;
        }
        date = tryDate(dateToTest.length() > 8 ? dateToTest.substring(0, 8) : dateToTest, usdf2);
        if (date!= null)
        {
            return date.isAfter(LocalDate.now().plusYears(towDigitYearFutureThreshold)) ? date.minusYears(100) : date;
        }
        return null;
    }

}
