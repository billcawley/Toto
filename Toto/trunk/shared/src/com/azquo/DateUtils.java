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

import static java.time.temporal.ChronoUnit.DAYS;

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
    private static final LocalDate start = LocalDate.of(1899, 12, 30);

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
    private static final long towDigitYearFutureThreshold = 10;

    public static LocalDate isADate(String maybeDate) {
        if (maybeDate.contains(" - ")) return null;//date ranges are not dates
        String dateToTest = maybeDate.replace("/", "-").replace(" ", "-");
        if (dateToTest.length() > 5 && dateToTest.charAt(1) == '-') dateToTest = "0" + dateToTest;
        if (dateToTest.length() > 5 && dateToTest.charAt(2) == '-' && dateToTest.charAt(4) == '-')
            dateToTest = dateToTest.substring(0, 3) + "0" + dateToTest.substring(3);
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
        if (date != null) {
            return date.isAfter(LocalDate.now().plusYears(towDigitYearFutureThreshold)) ? date.minusYears(100) : date;
        }
        date = tryDate(dateToTest, ukdf3a);
        if (date != null) {
            return date.isAfter(LocalDate.now().plusYears(towDigitYearFutureThreshold)) ? date.minusYears(100) : date;
        }
        return null;
    }

    public static long excelDate(LocalDate date) {
        if (date == null) {
            return 0;
        }
        return start.until(date, DAYS);

    }

    public static LocalDate isUSDate(String maybeDate) {
        String dateToTest = maybeDate.replace("/", "-").replace(" ", "-");
        if (dateToTest.length() > 5 && dateToTest.charAt(1) == '-') dateToTest = "0" + dateToTest;
        if (dateToTest.length() > 5 && dateToTest.charAt(2) == '-' && dateToTest.charAt(4) == '-')
            dateToTest = dateToTest.substring(0, 3) + "0" + dateToTest.substring(3);
        LocalDate date = tryDate(dateToTest.length() > 10 ? dateToTest.substring(0, 10) : dateToTest, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(dateToTest.length() > 10 ? dateToTest.substring(0, 10) : dateToTest, usdf4);
        if (date != null) return date;
        date = tryDate(dateToTest.length() > 11 ? dateToTest.substring(0, 11) : dateToTest, usdf3);
        if (date != null) return date;

        /* as above compensate for formates with two digit years*/

        date = tryDate(dateToTest.length() > 11 ? dateToTest.substring(0, 11) : dateToTest, usdf3a);
        if (date != null) {
            return date.isAfter(LocalDate.now().plusYears(towDigitYearFutureThreshold)) ? date.minusYears(100) : date;
        }
        date = tryDate(dateToTest.length() > 8 ? dateToTest.substring(0, 8) : dateToTest, usdf2);
        if (date != null) {
            return date.isAfter(LocalDate.now().plusYears(towDigitYearFutureThreshold)) ? date.minusYears(100) : date;
        }
        return null;
    }

    public static String standardizeDate(String oldDate) {
        //dates may be stored in many forms - this attempts to standardize them to yyyy-mm-dd hh:mm:ss
        //IT CANNOT DETECT US DATES!
        if (oldDate.length() < 8 || oldDate.charAt(4) == '-') {
            return oldDate;
        }
        String newDate = oldDate;
        if (oldDate.charAt(2) == '-' || oldDate.charAt(2) == '/' || oldDate.charAt(2) == '.') {
            String monthDay = oldDate.substring(3, 5) + "-" + oldDate.substring(0, 2);
            if (oldDate.length() == 8 || oldDate.charAt(8) == ' ') {
                newDate = "20" + oldDate.substring(6, 8) + "-" + monthDay;
                if (oldDate.length() > 9) {
                    newDate += oldDate.substring(8);
                }
            } else {
                if (oldDate.length() == 10 || oldDate.charAt(10) == ' ') {
                    newDate = oldDate.substring(6, 10) + "-" + monthDay;
                }
                if (oldDate.length() > 11) {
                    newDate += oldDate.substring(10);
                }
            }
        }
        return newDate;
    }

    public static String toDate(String intString) {
        int days = (int) Double.parseDouble(intString);
        LocalDate date = start.plus(days, DAYS);
        return dateTimeFormatter.format(date);
    }
}
