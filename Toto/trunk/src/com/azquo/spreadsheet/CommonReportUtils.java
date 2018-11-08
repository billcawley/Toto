package com.azquo.spreadsheet;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.user.UserChoice;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.StringLiterals;
import com.azquo.rmi.RMIClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by edward on 17/11/16.
 * <p>
 * Contains functionality that will be used by both ZK and the Excel interface.
 */
public class CommonReportUtils {

    public static List<String> getDropdownListForQuery(LoggedInUser loggedInUser, String query, String user, boolean justUser) {
        //hack to discover a database name
        query = replaceUserChoicesInQuery(loggedInUser, query);
        int arrowsPos = query.indexOf(">>");
        try {
            if (arrowsPos > 0) {
                Database origDatabase = loggedInUser.getDatabase();
                DatabaseServer origDatabaseServer = loggedInUser.getDatabaseServer();
                LoginService.switchDatabase(loggedInUser, query.substring(0, arrowsPos));
                List<String> toReturn = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                        .getDropDownListForQuery(loggedInUser.getDataAccessToken(), query.substring(arrowsPos + 2), user, justUser);
                loggedInUser.setDatabaseWithServer(origDatabaseServer, origDatabase);
                return toReturn;
            }
            return RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                    .getDropDownListForQuery(loggedInUser.getDataAccessToken(), query, user, justUser);
        } catch (Exception e) {
            e.printStackTrace();
            List<String> error = new ArrayList<>();
            error.add("Error : " + e.getMessage().substring(e.getMessage().lastIndexOf("Exception:")).trim());
            return error;
        }
    }

    public static int getNameQueryCount(LoggedInUser loggedInUser, String query) {
        // todo comment that this means there's one figure
        if (query.toLowerCase().startsWith("count(")){
            return 1;
        }
        try {
            return RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp()).getNameQueryCount(loggedInUser.getDataAccessToken(), query, loggedInUser.getUser().getEmail());
        } catch (Exception e){
            // for the moment be "quiet", this function used to help formatting
            e.printStackTrace();
            return 0;
        }
    }
    public static List<String> getDropdownListForQuery(LoggedInUser loggedInUser, String querye) {
        return getDropdownListForQuery(loggedInUser,querye,null);
    }



        public static List<String> getDropdownListForQuery(LoggedInUser loggedInUser, String query, String fieldName) {// WFC added fieldname taken from the spreadsheet (<fieldName>Choice) to pick chosen values from user selection list
        if (fieldName!=null) {
            String selectionList = loggedInUser.getUser().getSelections();
            if (selectionList!=null) {
                String[] selections = selectionList.split(";");
                for (String selection : selections) {
                    int equalPos = selection.indexOf("=");
                    if (equalPos > 0) {
                        if (selection.substring(0, equalPos).equalsIgnoreCase(fieldName)) {
                            List<String> toReturn = new ArrayList<>();
                            toReturn.add(selection.substring(equalPos + 1));
                            return toReturn;
                        }
                    }
                }
            }
        }

        return getDropdownListForQuery(loggedInUser, query, loggedInUser.getUser().getEmail(), false);
    }

    public static Map<String, String> getUserChoicesMap(LoggedInUser loggedInUser) {
        // get the user choices for the report. Can be drop down values, sorting/highlighting etc.
        // a notable point here is that the user choices don't distinguish between sheets
        Map<String, String> userChoices = new HashMap<>();
        List<UserChoice> allChoices = UserChoiceDAO.findForUserId(loggedInUser.getUser().getId());
        for (UserChoice uc : allChoices) {
            userChoices.put(uc.getChoiceName().toLowerCase(), uc.getChoiceValue()); // make case insensitive
        }
        return userChoices;
    }

    public static String replaceUserChoicesInQuery(LoggedInUser loggedInUser, String query){
        if (query.contains("[")) {//items in [] will be replaced by user choices
            Map<String,String> userChoices = getUserChoicesMap(loggedInUser);
            int pos=query.indexOf("[");

            while (pos >= 0) {
                int endPos = query.indexOf("]", pos);
                if (endPos < 0) break;
                String userChoice = query.substring(pos + 1, endPos);
                String userChoiceBasic = userChoice;
                if (userChoice.startsWith("az_")) {
                    userChoiceBasic = userChoice.substring(3);
                }
                String replacement = userChoices.get(userChoiceBasic.toLowerCase());
                if (replacement != null){
                    query = query.substring(0,pos) + replacement + query.substring(endPos + 1);
                    pos = pos + replacement.length();

                }else{
                    pos = endPos + 1;
                }
                pos = query.indexOf("[", pos);


            }

        }
        return query;
    }

    public static String resolveQuery(LoggedInUser loggedInUser, String query) {
        query = replaceUserChoicesInQuery(loggedInUser, query);
        try {
            boolean result =  RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                    .resolveQuery(loggedInUser.getDataAccessToken(), query, loggedInUser.getUser().getEmail());// sending the same as choice but the goal here is execute server side. Generally to set an "As"
            if (result) return "true";
            return "false";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error executing: " + query + " -  : " + getErrorFromServerSideException(e);
        }
    }

    public static String getErrorFromServerSideException(Exception e) {
        Throwable t = e;
        int check = 0;
        while (t.getCause() != null && check < 20) {
            t = t.getCause();
            check++;
        }
        return t.getMessage();
    }

/*    public static TypedPair<Integer, String> getFullProvenanceStringForCell(LoggedInUser loggedInUser, int reportId, String region, int regionRow, int regionColumn) throws Exception {
        final ProvenanceDetailsForDisplay provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, reportId, region, regionRow, regionColumn, 1000);
        if (provenanceDetailsForDisplay.getProcenanceForDisplayList() != null) {
            final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, region);
            StringBuilder colRowContext = new StringBuilder();
            colRowContext.append("COLUMN");
            for (List<String> colHeadingsRow : sentCells.getColumnHeadings()) {
                colRowContext.append("\t" + colHeadingsRow.get(regionColumn));
            }
            colRowContext.append("\n\tROW ");
            for (String rowItem : sentCells.getRowHeadings().get(regionRow)) {
                colRowContext.append("\t" + rowItem);
            }
            colRowContext.append("\n\tCONTEXT ");
            for (List<String> contextRow : sentCells.getContextSource()) {
                for (String context : contextRow) {
                    colRowContext.append("\t" + context);
                }
            }
            colRowContext.append("\n\n");
            StringBuilder toShow = new StringBuilder();
            for (TreeNode TreeNode : treeNodes) {
                resolveTreeNode(0, toShow, TreeNode);
            }
            // that int was what was in the code before however much it makes sense (or not!)
            return new TypedPair<>(getLastValueInt(treeNodes.get(0)), toShow.toString());
        }
        return null;
    }

    private static int getLastValueInt(com.azquo.memorydb.TreeNode treeNode) {
        if (treeNode.getHeading() != null && !treeNode.getChildren().isEmpty()) { // then assume we have items too!
            return getLastValueInt(treeNode.getChildren().get(treeNode.getChildren().size() - 1));
        }
        return treeNode.getValueId();
    }

    private static void resolveTreeNode(int tab, StringBuilder stringBuilder, com.azquo.memorydb.TreeNode treeNode) {
        for (int i = 0; i < tab; i++) {
            stringBuilder.append("\t");
        }
        boolean needsValue = true;
        if (treeNode.getName() != null) {
            stringBuilder.append(treeNode.getName());
            String value = treeNode.getValue();
            if (treeNode.getChildren().size() == 1) {
                needsValue = false;
            }
            if (needsValue && value != null) {
                stringBuilder.append("\t");

                stringBuilder.append(treeNode.getValue());
                if (treeNode.getValueHistory() != null) {
                    for (String historyItem : treeNode.getValueHistory()) {
                        stringBuilder.append("\n");
                        for (int i = 0; i < tab; i++) {
                            stringBuilder.append("\t");
                        }
                        stringBuilder.append("\t\tHistory\t" + historyItem); // out one further
                    }
                }
            }
            stringBuilder.append("\n");
        }
        if (treeNode.getHeading() != null) { // then assume we have items too!
            stringBuilder.append(treeNode.getHeading());
            //stringBuilder.append("\n");
            if (tab == 0 || needsValue) {
                tab++;
            }
            for (TreeNode treeNode1 : treeNode.getChildren()) {
                resolveTreeNode(tab, stringBuilder, treeNode1);
            }
        }
    } */
}