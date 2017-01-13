package com.azquo.spreadsheet.zk;

import com.azquo.admin.user.UserDAO;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Created by edward on 10/01/17.
 *
 * Smaller generally stateless functions for reports that are not specific to the ZK APIs (thos would be in BookUtils)
 */
class ReportUtils {

    private static LocalDate tryDate(String maybeDate, DateTimeFormatter dateTimeFormatter) {
        try {
            return LocalDate.parse(maybeDate, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ukdf2 = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter ukdf3 = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter ukdf4 = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ukdf5 = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    static LocalDate isADate(String maybeDate) {
        if (maybeDate == null) return null;
        LocalDate date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, dateTimeFormatter);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, ukdf4);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 11 ? maybeDate.substring(0, 11) : maybeDate, ukdf3);
        if (date != null) return date;
        date = tryDate(maybeDate.length() > 8 ? maybeDate.substring(0, 8) : maybeDate, ukdf2);
        if (date != null) return date;
        return tryDate(maybeDate.length() > 10 ? maybeDate.substring(0, 10) : maybeDate, ukdf5);
    }

    static String interpretLockWarnings(String lockWarnings) {
        //makes the warning more 'user-friendly'
        int pos = 0;
        while (lockWarnings.indexOf("by ", pos) > 0) {
            int startName = lockWarnings.indexOf("by ", pos);
            int endName = lockWarnings.indexOf(",", startName);
            String user = lockWarnings.substring(startName + 3, endName);
            String userName;
            try {
                userName = UserDAO.findByEmail(user.trim()).getName();
            } catch (Exception e) {
                userName = user;
            }
            lockWarnings = lockWarnings.substring(0, startName + 3) + userName + lockWarnings.substring(endName).replaceFirst("T", " at ");
            pos = startName + 3 + userName.length();
        }
        return lockWarnings.substring(0, lockWarnings.lastIndexOf(":"));//this should be on every line, but usually there'll only be one line
    }

    // not to do with the repeat regions, perhaps could change the name
    static int getRepeatCount(LoggedInUser loggedInUser, List<List<String>> colHeadingsSource) {
        int colHeadingRow = colHeadingsSource.size();
        String lastColHeading = colHeadingsSource.get(colHeadingRow - 1).get(0);
        int repeatCount = 1;
        if (!lastColHeading.startsWith(".")) {
            if (lastColHeading.toLowerCase().startsWith("permute")) {
                return 1;//permutes are unpredictable unless followed by a set
            }
            repeatCount = CommonReportUtils.getDropdownListForQuery(loggedInUser, lastColHeading).size();
        }
        colHeadingRow--;
        //if the last line is a set, is one of the lines above also a set - if so this is a permutation
        while (colHeadingRow-- > 0) {
            String colHeading = colHeadingsSource.get(colHeadingRow).get(0);
            if (colHeading.toLowerCase().startsWith("permute(") || CommonReportUtils.getDropdownListForQuery(loggedInUser, colHeading).size() > 0) {
                return repeatCount;
            }
        }
        return repeatCount;
    }

    static boolean isHierarchy(List<List<String>> headings) {
        for (List<String> oneCol : headings) {
            for (String oneHeading : oneCol) {
                if (oneHeading.toLowerCase().contains("hierarchy ") || oneHeading.toLowerCase().contains("permute")) {
                    return true;
                }
            }
        }
        return false;
    }
}