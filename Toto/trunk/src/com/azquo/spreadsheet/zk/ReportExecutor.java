package com.azquo.spreadsheet.zk;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.dataimport.ImportService;
import com.azquo.StringLiterals;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.OnlineController;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by edward on 09/01/17.
 * <p>
 * Breaking up the old ZKAzquoBookUtils, functions to run execute commands go in here
 */
public class ReportExecutor {

    private static final String EXECUTERESULTS = "az_ExecuteResults";
    private static final String OUTCOME = "az_Outcome";

    // now returns a book as it will need to be reloaded at the end
    public static Book runExecuteCommandForBook(Book book, String sourceNamedRegion) throws Exception {
        String executeCommand = null;
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet sheet = book.getSheetAt(sheetNumber);
            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
            for (SName sName : namesForSheet) {
                if (sName.getName().equalsIgnoreCase(sourceNamedRegion)) {
                    executeCommand = sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()).getStringValue();
                    break;
                }
            }
        }
        if (executeCommand == null || executeCommand.isEmpty()) { // just return false for the moment, no executing
            return book; // unchanged, nothing to run
        }
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        StringBuilder loops = runExecute(loggedInUser, executeCommand);

        final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
        for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes overt
            newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
        }
        ReportRenderer.populateBook(newBook, 0);
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet sheet = book.getSheetAt(sheetNumber);
            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
            for (SName sName : namesForSheet) {
                if (sName.getName().equalsIgnoreCase(EXECUTERESULTS)) {
                    final SCell cell = sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn());
                    BookUtils.setValue(cell, loops.toString());
                }
            }
        }
        return newBook;
    }

  public static StringBuilder runExecute(LoggedInUser loggedInUser, String executeCommand) throws Exception{
      List<String> commands = new ArrayList<>();
      StringTokenizer st = new StringTokenizer(executeCommand, "\n");
      while (st.hasMoreTokens()) {
          String line = st.nextToken();
          if (!line.trim().isEmpty()) {
              commands.add(line);
          }
      }
      //RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearTemporaryNames(loggedInUser.getDataAccessToken());
      RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).addToLog(loggedInUser.getDataAccessToken(), "Starting execute");
      StringBuilder loops = new StringBuilder();
      executeCommands(loggedInUser, commands, loops, new AtomicInteger(0));
      // it won't have cleared while executing
      RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearSessionLog(loggedInUser.getDataAccessToken());
      SpreadsheetService.databasePersist(loggedInUser);
      return loops;
  }


    // we assume cleansed of blank lines
    // now can return the outcome if there's an az_Outcome cell. Assuming a loop or list of "do"s then the String returned is the last.
    private static TypedPair<String, Double> executeCommands(LoggedInUser loggedInUser, List<String> commands, StringBuilder loopsLog, AtomicInteger count) throws Exception {
        TypedPair<String, Double> toReturn = null;
        if (commands.size() > 0 && commands.get(0) != null) {
            String firstLine = commands.get(0);
            int startingIndent = getIndent(firstLine);
            for (int i = 0; i < startingIndent; i++) {
                loopsLog.append("  ");
            }
            for (int lineNo = 0; lineNo < commands.size(); lineNo++) { // makes checking ahead easier
                String line = commands.get(lineNo);
                String trimmedLine = line.trim();
                if (trimmedLine.toLowerCase().startsWith("for each")) {
                    // gather following lines - what we'll be executing
                    int onwardLineNo = lineNo + 1;
                    List<String> subCommands = new ArrayList<>();
                    while (onwardLineNo < commands.size() && getIndent(commands.get(onwardLineNo)) > startingIndent) {
                        subCommands.add(commands.get(onwardLineNo));
                        onwardLineNo++;
                    }
                    lineNo = onwardLineNo - 1; // put line back to where it is now
                    if (!subCommands.isEmpty()) { // then we have something to run for the for each!
                        int inPos = findString(trimmedLine," in ");
                        String choiceName = trimmedLine.substring("for each".length(), inPos). trim();
                        if (choiceName.startsWith("`") || choiceName.startsWith("[")){
                            choiceName = choiceName.substring(1,choiceName.length()-1);
                        }
                        String choiceQuery = trimmedLine.substring(inPos + 4).trim();
                        loopsLog.append(choiceName).append(" : ").append(choiceQuery).append("\r\n");
                        final List<String> dropdownListForQuery = CommonReportUtils.getDropdownListForQuery(loggedInUser, choiceQuery);
                        for (String choiceValue : dropdownListForQuery) { // run the for :)
                            RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).addToLog(loggedInUser.getDataAccessToken(), choiceName + " : " + choiceValue);
                            SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceName.replace("`", ""), choiceValue);
                            toReturn = executeCommands(loggedInUser, subCommands, loopsLog, count);

                        }
                    }
                    // if not a for each I guess we just execute? Will check for "do"
                } else if (trimmedLine.toLowerCase().startsWith("do")) {
                    String reportToRun = trimmedLine.substring(2).trim();
                    Database oldDatabase = null;
                    OnlineReport onlineReport;
                    // so, first try to get the report based off permissions, if so it might override the current database
                    if (loggedInUser.getPermissionsFromReport()!=null && loggedInUser.getPermissionsFromReport().get(reportToRun.toLowerCase()) != null){
                        onlineReport = loggedInUser.getPermissionsFromReport().get(reportToRun.toLowerCase()).getFirst();
                        if (loggedInUser.getPermissionsFromReport().get(reportToRun.toLowerCase()).getSecond() != null){
                            oldDatabase = loggedInUser.getDatabase();
                            loggedInUser.setDatabaseWithServer(loggedInUser.getDatabaseServer(), loggedInUser.getPermissionsFromReport().get(reportToRun.toLowerCase()).getSecond());
                        }
                    } else { // otherwise try a straight lookup - stick on whatever db we're currently on
                        onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportToRun, loggedInUser.getUser().getBusinessId());
                    }
                    if (onlineReport != null) { // need to prepare it as in the controller todo - factor?

                        loopsLog.append("Run : ").append(onlineReport.getReportName());
                        RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).addToLog(loggedInUser.getDataAccessToken(), count.incrementAndGet() + " " + loggedInUser.getUser().getName() + " Running  " + onlineReport.getReportName() + " ,");
                        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk();
                        final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                        book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                        book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                        book.getInternalBook().setAttribute(OnlineController.REPORT_ID, onlineReport.getId());
                        StringBuilder errorLog = new StringBuilder();
                        final boolean save = ReportRenderer.populateBook(book, 0, false, true, errorLog); // note true at the end here - keep on logging so users can see changes as they happen
                        if (errorLog.length() > 0){
                            loopsLog.append(" ERROR : " + errorLog.toString());
                            return null;
                            // todo - how to stop all the way to the top? Can set count as -1 but this is hacky
                        }
                        if (save) { // so the data was changed and if we save from here it will make changes to the DB
                            for (SName name : book.getInternalBook().getNames()) {
                                if (name.getName().toLowerCase().startsWith(ReportRenderer.AZDATAREGION)) { // I'm saving on all sheets, this should be fine with zk
                                    String region = name.getName().substring(ReportRenderer.AZDATAREGION.length());
                                    // possibly the nosave check could be factored, in report service around line 230
                                    // this is a bit annoying given that I should be able to get the options from the sent cells but there may be no sent cells. Need to investigate this - nosave is currently being used for different databases, that's the problem
                                    SName optionsRegion = book.getInternalBook().getNameByName(ReportRenderer.AZOPTIONS + region);
                                    String optionsSource = "";
                                    boolean noSave = false;
                                    if (optionsRegion != null) {
                                        optionsSource = BookUtils.getSnameCell(optionsRegion).getStringValue();
                                        UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), onlineReport.getId(), region, optionsSource);
                                        noSave = userRegionOptions.getNoSave();
                                    }
                                    if (!noSave) {
                                        SpreadsheetService.saveData(loggedInUser, onlineReport.getId(), onlineReport.getReportName(), name.getRefersToSheetName(), region.toLowerCase(), false); // to not persist right now
                                    }
                                }
                            }
                            AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
                        }
                        // here we try to get an outcome to return
                        SName outcomeName = book.getInternalBook().getNameByName(OUTCOME); // I assume only one
                        if (outcomeName != null) {
                            final CellRegion refersToCellRegion = outcomeName.getRefersToCellRegion();
                            SCell outcomeCell = book.getInternalBook().getSheetByName(outcomeName.getRefersToSheetName()).getCell(refersToCellRegion.getRow(), refersToCellRegion.getColumn());
                            // ok now I think I need to be careful of the cell type
                            if ((outcomeCell.getType() == SCell.CellType.FORMULA && outcomeCell.getFormulaResultType() == SCell.CellType.NUMBER) || outcomeCell.getType() == SCell.CellType.NUMBER) { // I think a decent enough way to number detect?
                                toReturn = new TypedPair<>(null, outcomeCell.getNumberValue());
                            } else {
                                toReturn = new TypedPair<>(outcomeCell.getStringValue(), null);
                            }
                        }
                        // revert database if
                        if (oldDatabase != null){
                            loggedInUser.setDatabaseWithServer(loggedInUser.getDatabaseServer(),oldDatabase);
                        }
                    }
                } else if (trimmedLine.toLowerCase().startsWith("repeat until outcome")) { // new conditional logic
                    // similar to the for each - gather sub commands
                    String condition = trimmedLine.substring("repeat until outcome".length()).trim();
                    if (condition.contains(" ")) {
                        String operator = condition.substring(0, condition.indexOf(" "));
                        String constant = condition.substring(condition.indexOf(" ") + 1);
                        double constantDouble = 0;
                        if (constant.startsWith("`") && constant.endsWith("`")) {
                            constant = constant.replace("`", "");
                        } else { // try for a number
                            try {
                                constantDouble = Double.parseDouble(constant.replace(",", "")); // zap the commas to try to parse
                                constant = null; // null the string if we have a parsed number
                            } catch (Exception ignored) {
                            }
                        }
                        int onwardLineNo = lineNo + 1;
                        List<String> subCommands = new ArrayList<>();
                        while (onwardLineNo < commands.size() && getIndent(commands.get(onwardLineNo)) > startingIndent) {
                            subCommands.add(commands.get(onwardLineNo));
                            onwardLineNo++;
                        }
                        lineNo = onwardLineNo - 1; // put line back to where it is now
                        int counter = 0;
                        // todo - max clause
                        int limit = 10_000;
                        if (!subCommands.isEmpty()) { // then we have something to run for the for each!
                            boolean stop = true; // make the default be to stop e.g. in the case of bad syntax or whatever . . .
                            do {
                                counter++;
                                final TypedPair<String, Double> stringDoubleTypedPair = executeCommands(loggedInUser, subCommands, loopsLog, count);
                                if (stringDoubleTypedPair != null) {
                                    // ok I'm going to assume type matching - if the types don't match then forget the comparison
                                    if ((stringDoubleTypedPair.getFirst() != null && constant != null)) { // string, equals not equals comparison
                                        switch (operator) {
                                            case "=":
                                                stop = stringDoubleTypedPair.getFirst().equalsIgnoreCase(constant);
                                                break;
                                            case "!=":
                                                stop = !stringDoubleTypedPair.getFirst().equalsIgnoreCase(constant);
                                                break;
                                            default:  // error for dodgy operator??
                                                loopsLog.append("Unknown operator for repeat until outcome string : ").append(operator);
                                                break;
                                        }
                                    } else if (stringDoubleTypedPair.getFirst() == null && constant == null) { // assume numbers are set
                                        // '=', '>'. '<'. '<=' '>=' '!='
                                        switch (operator) {
                                            case "=":
                                                stop = stringDoubleTypedPair.getSecond() == constantDouble;
                                                break;
                                            case ">":
                                                stop = stringDoubleTypedPair.getSecond() > constantDouble;
                                                break;
                                            case "<":
                                                stop = stringDoubleTypedPair.getSecond() < constantDouble;
                                                break;
                                            case ">=":
                                                stop = stringDoubleTypedPair.getSecond() >= constantDouble;
                                                break;
                                            case "<=":
                                                stop = stringDoubleTypedPair.getSecond() <= constantDouble;
                                                break;
                                            case "!=":
                                                stop = stringDoubleTypedPair.getSecond() != constantDouble;
                                                break;
                                            default:
                                                loopsLog.append("Unknown operator for repeat until outcome number : ").append(operator);
                                        }

                                    } else { // mismatched types, error message?
                                        loopsLog.append("Mismatched String/Number for repeat until outcome : ").append(trimmedLine);
                                    }
                                } else {
                                    loopsLog.append("Null return on execute for repeat until outcome : ").append(trimmedLine);
                                }
                            } while (!stop && counter < limit);
                        }
                    } else {
                        loopsLog.append("badly formed repeat until outcome : ").append(trimmedLine);
                    }
                } else if (trimmedLine.toLowerCase().startsWith("delete ")) {
                    // zapdata is put through to the name query parser - this is not great practice really . . . .
                  RMIClient.getServerInterface(loggedInUser.getDatabaseServer().getIp())
                            .getJsonChildren(loggedInUser.getDataAccessToken(),0, 0, false, "edit:zapdata " + trimmedLine.substring(7), StringLiterals.DEFAULT_DISPLAY_NAME, 0);

                } else if (trimmedLine.toLowerCase().startsWith("set ")) {
                    String result = CommonReportUtils.resolveQuery(loggedInUser, trimmedLine.substring(4));
                    if (result.toLowerCase().startsWith("error")) {
                        throw (new Exception(result));
                    }
                }else if (trimmedLine.toLowerCase().startsWith("if ")) {
                    String result = CommonReportUtils.resolveQuery(loggedInUser, trimmedLine.substring(4));
                    if (result.toLowerCase().startsWith("error")) {
                        throw (new Exception(result));
                    }
                    String lastLine = "";
                    lineNo++;
                    List<String> subCommands = new ArrayList<>();
                    while (lineNo < commands.size() && getIndent(commands.get(lineNo)) > startingIndent) {
                        lastLine = commands.get(lineNo);
                        subCommands.add(lastLine);
                        lineNo++;
                    }
                    lineNo--; // put line back to where it is now

                    if (result.equals("true")) {
                        if (!subCommands.isEmpty()) {
                            toReturn = executeCommands(loggedInUser, subCommands, loopsLog, count);

                        }
                        if (lastLine.toLowerCase().equals("else")) {
                            while (lineNo < commands.size() && getIndent(commands.get(lineNo)) > startingIndent) {
                                lineNo++;
                            }
                        }
                        lineNo--;

                    }else{
                        subCommands = new ArrayList<>();
                        while (lineNo < commands.size() && getIndent(commands.get(lineNo)) > startingIndent) {
                            lastLine = commands.get(lineNo);
                            subCommands.add(lastLine);
                            lineNo++;
                        }
                        lineNo--; // put line back to where it is now
                        if (!subCommands.isEmpty()) {
                            toReturn = executeCommands(loggedInUser, subCommands, loopsLog, count);

                        }

                    }


                }else{
                        loopsLog.append("badly formed execute line  : ").append(trimmedLine);
                }
            }
        }
        loopsLog.append("\r\n");
        return toReturn;
    }

    private static int findString(String line, String toFind)throws Exception{
        //checks whether string to find is part of a name enclosed in ``
        int quotePos = 0;
        int foundPos = line.indexOf(toFind);
        if (foundPos > 0 ) {
            while (quotePos >= 0) {
                quotePos = line.indexOf("`", quotePos + 1);
                if (quotePos < 0 || quotePos > foundPos)  return foundPos;
                int endPos = line.indexOf("`", quotePos + 1);
                if (endPos < 0) quotePos = endPos;
                else {
                    if (foundPos < endPos) {
                        foundPos = line.indexOf(toFind, endPos);
                        if (foundPos < 0) endPos = -2;
                    }
                    quotePos = endPos + 1;
                }
            }
        }
        throw(new Exception("not understood: " + line));
     };

    private static int getIndent(String s) {
        int indent = 0;
        while (indent < s.length() && s.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }
}