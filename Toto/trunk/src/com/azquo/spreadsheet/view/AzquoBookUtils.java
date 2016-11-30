package com.azquo.spreadsheet.view;

import com.azquo.TypedPair;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.user.UserChoice;
import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.memorydb.TreeNode;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by edward on 17/11/16.
 *
 * I can't remember if I'm recreating a class which existed before but if so this has a different purpose, it contains functionality that will]
 * be used by both ZK and the Excel interface.
 */
public class AzquoBookUtils {


    static List<String> getDropdownListForQuery(LoggedInUser loggedInUser, String query, List<String> languages) {
        //hack to discover a database name
        int arrowsPos = query.indexOf(">>");
        try{
            if (arrowsPos > 0) {
                Database origDatabase = loggedInUser.getDatabase();
                DatabaseServer origDatabaseServer = loggedInUser.getDatabaseServer();
                LoginService.switchDatabase(loggedInUser, query.substring(0, arrowsPos));
                List<String> toReturn = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                        .getDropDownListForQuery(loggedInUser.getDataAccessToken(), query.substring(arrowsPos + 2), languages);
                loggedInUser.setDatabaseWithServer(origDatabaseServer, origDatabase);
                return toReturn;

            }
            return RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                    .getDropDownListForQuery(loggedInUser.getDataAccessToken(), query, languages);
        } catch (Exception e){
            e.printStackTrace();
            List<String> error = new ArrayList<>();
            error.add("Error : " + e.getMessage());
            return error;
        }

    }

    public static List<String> getDropdownListForQuery(LoggedInUser loggedInUser, String query) {
        return getDropdownListForQuery(loggedInUser, query, loggedInUser.getLanguages());
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

    public static String resolveQuery(LoggedInUser loggedInUser, String query){
        try {
            RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                    .resolveQuery(loggedInUser.getDataAccessToken(), query, loggedInUser.getLanguages());// sending the same as choice but the goal here is execute server side. Generally to set an "As"
            return query;
        } catch (Exception e) {
            e.printStackTrace();
            return query +  " - Error executing query : " + getErrorFromServerSideException(e);
        }

    }

    public static String getErrorFromServerSideException(Exception e){
        Throwable t = e;
        int check = 0;
        while (t.getCause() != null && check < 20){
            t = t.getCause();
            check++;
        }
        String exceptionError = t.getMessage();
        if (exceptionError != null && exceptionError.contains("error:"))// legacy, should be removed at some point?
            exceptionError = exceptionError.substring(exceptionError.indexOf("error:"));
        return exceptionError;
    }

    public static TypedPair<Integer, String> getFullProvenanceStringForCell(LoggedInUser loggedInUser, int reportId, String region, int regionRow, int regionColumn) throws Exception {
        List<TreeNode> treeNodes = SpreadsheetService.getTreeNode(loggedInUser, reportId, region, regionRow, regionColumn, 1000);
        if (!treeNodes.isEmpty()) {
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
            if (treeNode.getChildren().size()==1){
                needsValue = false;
            }
            if (needsValue && value != null) {
                stringBuilder.append("\t");

                stringBuilder.append(treeNode.getValue());
                if (treeNode.getValueHistory() != null){
                    for (String historyItem : treeNode.getValueHistory()){
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
            if (tab==0 || needsValue){
                tab++;
            }
            for (TreeNode treeNode1 : treeNode.getChildren()) {
                resolveTreeNode(tab, stringBuilder, treeNode1);
            }
        }
    }


}
