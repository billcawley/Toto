package com.azquo.spreadsheet.zk;

import com.azquo.admin.user.UserDAO;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Created by edward on 10/01/17.
 * <p>
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

    static String interpretLockWarnings(String lockWarnings, int businessId) {
        //makes the warning more 'user-friendly'
        int pos = 0;
        while (lockWarnings.indexOf("by ", pos) > 0) {
            int startName = lockWarnings.indexOf("by ", pos);
            int endName = lockWarnings.indexOf(",", startName);
            String user = lockWarnings.substring(startName + 3, endName);
            String userName;
            try {
                userName = UserDAO.findByEmailAndBusinessId(user.trim(), businessId).getName();
            } catch (Exception e) {
                userName = user;
            }
            lockWarnings = lockWarnings.substring(0, startName + 3) + userName + lockWarnings.substring(endName).replaceFirst("T", " at ");
            pos = startName + 3 + userName.length();
        }
        return lockWarnings.substring(0, lockWarnings.lastIndexOf(":"));//this should be on every line, but usually there'll only be one line
    }

    // col heading source as in the definition, the AQL
    // It's trying to give a clue for formatting expanding
    // the size of the thing on the bottom column heading row that's repeated e.g. "sales, costs of sales, profit" - get its size unless it's permute
    // so if costs was a red column it would continue to be red as the columns expanded
    static int guessColumnsFormattingPatternWidth(LoggedInUser loggedInUser, List<List<String>> colHeadingsSource) {
        int colHeadingRow = colHeadingsSource.size();
        if (colHeadingRow == 1) return 1;
        String lastColHeading = colHeadingsSource.get(colHeadingRow - 1).get(0);
        int repeatCount = 1;
        if (!lastColHeading.startsWith(".")) {
            if (lastColHeading.toLowerCase().startsWith("permute") || lastColHeading.toLowerCase().startsWith("scale")) { // added scale as another option - this is not very satisfactory if more functions are added . . . todo factor the function names here??
                return 1;//permutes are unpredictable unless followed by a set
            }
            // so what we're saying is how big is the set at the bottom of the col definitions?
            repeatCount = CommonReportUtils.getNameQueryCount(loggedInUser, lastColHeading);
            if (repeatCount > 1) return repeatCount;
            //check that the heading above is not a set (perhaps should check all headings above).  If so, and there is a value in the bottom right of the set, then the whold headings need to be expanded.
            String headingAbove = colHeadingsSource.get(colHeadingRow-2).get(0);
            String topHeading = colHeadingsSource.get(0).get(0);
            if (headingAbove.length() > 0 && !headingAbove.equals(".")){
                int setCount = CommonReportUtils.getNameQueryCount(loggedInUser, headingAbove);
                if (setCount==1 && topHeading.length()>0 && !topHeading.equals(headingAbove)){
                    setCount = CommonReportUtils.getNameQueryCount(loggedInUser, topHeading);
                }
                if (setCount > 1 && colHeadingsSource.get(colHeadingRow - 1).get(colHeadingsSource.get(0).size()-1).length() > 0){
                    return colHeadingsSource.get(0).size();
                }
            }
        }
        return repeatCount;
    }
/*
    // as above for columns
    static int guessRowsFormattingPatternHeight(LoggedInUser loggedInUser, List<List<String>> rowHeadingsSource) {
        int colHeadingRow = colHeadingsSource.size();
        if (colHeadingRow == 1) return 1;
        String lastColHeading = colHeadingsSource.get(colHeadingRow - 1).get(0);
        int repeatCount = 1;
        if (!lastColHeading.startsWith(".")) {
            if (lastColHeading.toLowerCase().startsWith("permute")) {
                return 1;//permutes are unpredictable unless followed by a set
            }
            // so what we're saying is how big is the set at the bottom of the col definitions?
            repeatCount = CommonReportUtils.getDropdownListForQuery(loggedInUser, lastColHeading).size();
            if (repeatCount > 1) return repeatCount;
            //check that the heading above is not a set (perhaps should check all headings above).  If so, and there is a value in the bottom right of the set, then the whold headings need to be expanded.
            String headingAbove = colHeadingsSource.get(colHeadingRow-2).get(0);
            String topHeading = colHeadingsSource.get(0).get(0);
            if (headingAbove.length() > 0 && !headingAbove.equals(".")){
                int setCount = CommonReportUtils.getDropdownListForQuery(loggedInUser,headingAbove).size();
                if (setCount==1 && topHeading.length()>0 && !topHeading.equals(headingAbove)){
                    setCount = CommonReportUtils.getDropdownListForQuery(loggedInUser, topHeading).size();
                }
                if (setCount > 1 && colHeadingsSource.get(colHeadingRow - 1).get(colHeadingsSource.get(0).size()-1).length() > 0){
                    return colHeadingsSource.get(0).size();
                }
            }
        }
        return repeatCount;
    }*/

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