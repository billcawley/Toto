package com.azquo.spreadsheet.zk;

import com.azquo.DoubleAndOrString;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.dataimport.DBCron;
import com.azquo.dataimport.ImportService;
import com.azquo.StringLiterals;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import org.apache.commons.lang.math.NumberUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import io.keikai.api.*;
import io.keikai.api.model.Book;
import io.keikai.api.model.CellData;
import io.keikai.api.model.Sheet;
import io.keikai.model.CellRegion;
import io.keikai.model.SCell;
import io.keikai.model.SName;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by edward on 09/01/17.
 * <p>
 * Breaking up the old ZKAzquoBookUtils, functions to run execute commands go in here
 */
public class ReportExecutor {

    static final String EXECUTERESULTS = "az_ExecuteResults";
    static final String OUTCOME = "az_Outcome";
    // worth explaining. One use of executing is to gather information e.g. for Ed Broking Query Validation
    // in simple terms if we see this region grab its contents
    static final String SYSTEMDATA = "az_SystemData";

    // export data from the report. A region where the data is and a region which holds the destination path
    // not so sold on the second but can write then modify
    static final String EXPORT = "az_Export";

    // now returns a book as it will need to be reloaded at the end
    // provenance id means when you select choices they will be constrained to
    public static Book runExecuteCommandForBook(LoggedInUser loggedInUser, Book book, String sourceNamedRegion) throws Exception {
        StringBuilder executeCommand = null;
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet sheet = book.getSheetAt(sheetNumber);
            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
            for (SName sName : namesForSheet) {
                if (sName.getName().equalsIgnoreCase(sourceNamedRegion)) {
                    executeCommand = new StringBuilder();
                    //;now allowing executes to be on multiple lines...
                    CellRegion region = sName.getRefersToCellRegion();
                    for (int rowNo = 0; rowNo < region.getRowCount(); rowNo++) {
                        executeCommand.append(sheet.getInternalSheet().getCell(region.getRow() + rowNo, region.getColumn()).getStringValue()).append("\n");
                    }
                    break;
                }
            }
        }
        if (executeCommand == null || (executeCommand.length() == 0)) { // just return false for the moment, no executing
            return book; // unchanged, nothing to run
        }
        //now passed through.......not needed, but left for the moment
        loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        loggedInUser.setBook(book);
        StringBuilder loops = runExecute(loggedInUser, executeCommand.toString(), null, -1, true);
        if (book.getInternalBook().getAttribute(OnlineController.BOOK_PATH) != null) { // it might be null in the case of the Excel Controller, not a problem, return null
            final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
            for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes over
                // don't move zss internal stuff, it might interfere
                if (!key.toLowerCase().contains("zss")) {
                    newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                }
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
        } else {
            return null;
        }
    }

    public static StringBuilder runExecute(LoggedInUser loggedInUser, String executeCommand, List<List<List<String>>> systemData2DArrays, int provenanceId, boolean persist) throws Exception {
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
        executeCommands(loggedInUser, commands, null, loops, systemData2DArrays, new AtomicInteger(0), provenanceId);
        // it won't have cleared while executing
        if (persist) {
            RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearSessionLog(loggedInUser.getDataAccessToken());
            SpreadsheetService.databasePersist(loggedInUser);
        }
        return loops;
    }


    // we assume cleansed of blank lines
    // now can return the outcome if there's an az_Outcome cell. Assuming a loop or list of "do"s then the String returned is the last.
    private static DoubleAndOrString executeCommands(LoggedInUser loggedInUser, List<String> commands, String exportPath, StringBuilder loopsLog, List<List<List<String>>> systemData2DArrays, AtomicInteger count, int provenanceId) throws Exception {
        String filterContext = null;
        String filterItems = null;
        DoubleAndOrString toReturn = null;
        if (commands.size() > 0 && commands.get(0) != null) {
            String firstLine = commands.get(0);
            int startingIndent = getIndent(firstLine);
            for (int i = 0; i < startingIndent; i++) {
                loopsLog.append("  ");
            }
            for (int lineNo = 0; lineNo < commands.size(); lineNo++) { // makes checking ahead easier
                String line = commands.get(lineNo);
                String trimmedLine = CommonReportUtils.replaceUserChoicesInQuery(loggedInUser, line);//replaces [choice] with chosen
                String message = "Executing: " + trimmedLine;
                System.out.println(message);
                if (trimmedLine.toLowerCase().startsWith("for each")) {
                    // gather following lines - what we'll be executing
                    int onwardLineNo = lineNo + 1;
                    List<String> subCommands = new ArrayList<>();
                    while (onwardLineNo < commands.size() && getIndent(commands.get(onwardLineNo)) > startingIndent) {
                        subCommands.add(commands.get(onwardLineNo));
                        onwardLineNo++;
                    }
                    lineNo = onwardLineNo - 1; // put line back to where it is now
                    int inPos = findString(trimmedLine, " in ");
                    String choiceName = trimmedLine.substring("for each".length(), inPos).trim();
                    if (choiceName.startsWith("`") || choiceName.startsWith("[")) {
                        choiceName = choiceName.substring(1, choiceName.length() - 1);
                    }
                    String choiceQuery = trimmedLine.substring(inPos + 4).trim();
                    loopsLog.append(choiceName).append(" : ").append(choiceQuery).append("\r\n");
                    final List<String> dropdownListForQuery = CommonReportUtils.getDropdownListForQuery(loggedInUser, choiceQuery, loggedInUser.getUser().getEmail(), false, provenanceId);
                    if (dropdownListForQuery.size() > 0 && !dropdownListForQuery.get(0).startsWith("Error :")) {
                        for (String choiceValue : dropdownListForQuery) { // run the "for" :)
                            RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).addToLog(loggedInUser.getDataAccessToken(), choiceName + " : " + choiceValue);
                            SpreadsheetService.setUserChoice(loggedInUser, choiceName.replace("`", ""), choiceValue);
                            loopsLog.append(choiceValue).append("\r\n");
                            if (!subCommands.isEmpty()) { // then we have something to run for the for each!
                                toReturn = executeCommands(loggedInUser, subCommands, exportPath, loopsLog, systemData2DArrays, count, provenanceId);
                            }
                        }
                    }
                    // if not a for each I guess we just execute? Will check for "do"
                }else if (trimmedLine.toLowerCase().startsWith("execute ")){
                    runExecuteCommandForBook(loggedInUser, loggedInUser.getBook(), trimmedLine.substring(8).trim());
                }

                else if (trimmedLine.toLowerCase().startsWith("do")) {
                    boolean debug = false;
                    if (trimmedLine.toLowerCase().endsWith("debug")) {
                        debug = true;
                        trimmedLine = trimmedLine.substring(0, trimmedLine.length() - "debug".length()).trim();
                    }
                    String reportToRun = trimmedLine.substring(2).trim();
                    Database oldDatabase = null;
                    OnlineReport onlineReport;
                    // so, first try to get the report based off permissions, if so it might override the current database
                    // note this is a BAD idea e.g. for temporary databases, todo, stop it then
                    if (loggedInUser.getPermission(reportToRun.toLowerCase()) != null) {
                        LoggedInUser.ReportDatabase permission = loggedInUser.getPermission(reportToRun.toLowerCase());
                        onlineReport = permission.getReport();
                        if (permission.getDatabase() != null) {
                            oldDatabase = loggedInUser.getDatabase();
                            loggedInUser.setDatabaseWithServer(loggedInUser.getDatabaseServer(), permission.getDatabase());
                        }
                    } else { // otherwise try a straight lookup - stick on whatever db we're currently on
                        onlineReport = OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportToRun);
                        if (onlineReport == null) {
                            onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportToRun, loggedInUser.getUser().getBusinessId());
                        }
                    }
                    if (onlineReport != null) { // need to prepare it as in the controller todo - factor?
                        loopsLog.append("Run : ").append(onlineReport.getReportName());
                        RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).addToLog(loggedInUser.getDataAccessToken(), count.incrementAndGet() + " " + loggedInUser.getUser().getName() + " Running " + onlineReport.getReportName() + " ,");
                        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk();
                        final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                        book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                        book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                        book.getInternalBook().setAttribute(OnlineController.REPORT_ID, onlineReport.getId());
                        StringBuilder errorLog = new StringBuilder();
                        final boolean save = ReportRenderer.populateBook(book, 0, false, true, errorLog); // note true at the end here - keep on logging so users can see changes as they happen
                        System.out.println("save flag : " + save);
                        if (errorLog.length() > 0) {
                            loopsLog.append(" ERROR : ").append(errorLog.toString());
                            return null;
                            // todo - how to stop all the way to the top? Can set count as -1 but this is hacky
                        }
                        ReportService.extractEmailInfo(book);
                        if (save) { // so the data was changed and if we save from here it will make changes to the DB
                            fillSpecialRegions(loggedInUser, book, onlineReport.getId());
                            for (SName name : book.getInternalBook().getNames()) {
                                if (name.getName().toLowerCase().startsWith(StringLiterals.AZDATAREGION)) { // I'm saving on all sheets, this should be fine with zk
                                    String region = name.getName().substring(StringLiterals.AZDATAREGION.length());
                                    // possibly the nosave check could be factored, in report service around line 230
                                    // this is a bit annoying given that I should be able to get the options from the sent cells but there may be no sent cells. Need to investigate this - nosave is currently being used for different databases, that's the problem
                                    SName optionsRegion = BookUtils.getNameByName(StringLiterals.AZOPTIONS + region, book.getSheet(name.getRefersToSheetName()));
                                    boolean noSave = false;
                                    if (optionsRegion != null) {
                                        String optionsSource = BookUtils.getSnameCell(optionsRegion).getStringValue();
                                        UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), onlineReport.getId(), region, optionsSource);
                                        noSave = userRegionOptions.getNoSave();
                                    }
                                    if (!noSave) {
                                        System.out.println("saving for : " + region);
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
                                toReturn = new DoubleAndOrString(outcomeCell.getNumberValue(), null);
                            } else {
                                toReturn = new DoubleAndOrString(null, outcomeCell.getStringValue());
                            }
                        }
                        // revert database if it was changed
                        if (oldDatabase != null) {
                            loggedInUser.setDatabaseWithServer(loggedInUser.getDatabaseServer(), oldDatabase);
                        }

                        SName systemDataName = book.getInternalBook().getNameByName(SYSTEMDATA);
                        if (systemDataName != null && systemData2DArrays != null) {
                            // gather debug info
                            systemData2DArrays.add(BookUtils.nameToStringLists(systemDataName));
                        }

                        //stuff added by edd, need an option for the user to see these files for debug purposes
                        if (debug) {
                            Exporter exporter = Exporters.getExporter();
                            File file = File.createTempFile("debug" + System.currentTimeMillis(), "temp.xlsx");
                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                exporter.export(book, fos);
                            }
                        }

                        // check for XML, in the context of execute we run it automatically
                        if (book.getInternalBook().getAttribute(OnlineController.XML) != null) {
                            Path destdir = Paths.get(SpreadsheetService.getXMLDestinationDir());
                            // note - I'm just grabbing the first sheet at the moment - this may need changing later
                            ReportExecutor.generateXMLFilesAndSupportingReports(loggedInUser, book.getSheetAt(0), destdir);
                        }
                        // and now check new export criteria
                        if (exportPath != null) {
                            SName export = book.getInternalBook().getNameByName(EXPORT);
                            if (export != null) { // then we have some data to read
                                boolean existsAlready = Files.exists(Paths.get(exportPath));
                                String exportFirstLine = null;
                                if (existsAlready) {
                                    try (BufferedReader br = Files.newBufferedReader(Paths.get(exportPath), StandardCharsets.UTF_8)) {
                                        exportFirstLine = br.readLine();
                                    }
                                }
                                Sheet sheet = book.getSheet(export.getRefersToSheetName());
                                try (BufferedWriter bufferedWriter = Files.newBufferedWriter(Paths.get(exportPath), StandardCharsets.UTF_8, existsAlready ? StandardOpenOption.APPEND : StandardOpenOption.CREATE)) {
                                    CellRegion refersToCellRegion = export.getRefersToCellRegion();
                                    for (int rNo = refersToCellRegion.row; rNo <= refersToCellRegion.lastRow; rNo++) {
                                        if (exportFirstLine != null && rNo == refersToCellRegion.row) {
                                            StringBuilder test = new StringBuilder();
                                            for (int cNo = refersToCellRegion.column; cNo <= refersToCellRegion.lastColumn; cNo++) {
                                                String val = "";
                                                if (sheet.getInternalSheet().getRow(rNo) != null) {
                                                    val = ImportService.getCellValue(sheet, rNo, cNo).getString();
                                                }
                                                test.append(val).append("\t");
                                            }
                                            if (test.toString().equals(exportFirstLine)) {
                                                if (rNo < refersToCellRegion.lastRow) {
                                                    rNo++;// skip the first line if it's already on file
                                                } else { // break if there's no second line to skip to
                                                    break;
                                                }
                                            }
                                        }
                                        for (int cNo = refersToCellRegion.column; cNo <= refersToCellRegion.lastColumn; cNo++) {
                                            String val = "";
                                            if (sheet.getInternalSheet().getRow(rNo) != null) {
                                                val = ImportService.getCellValue(sheet, rNo, cNo).getString();
                                            }
                                            bufferedWriter.write(val + "\t");
                                        }
                                        bufferedWriter.newLine();
                                    }
                                }
                            }
                        }
                    }
                }else if (trimmedLine.toLowerCase().startsWith("repeat until outcome")) { // new conditional logic
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
                                final DoubleAndOrString stringDouble = executeCommands(loggedInUser, subCommands, exportPath, loopsLog, systemData2DArrays, count, provenanceId);
                                if (stringDouble != null) {
                                    // ok I'm going to assume type matching - if the types don't match then forget the comparison
                                    if ((stringDouble.getString() != null && constant != null)) { // string, equals not equals comparison
                                        switch (operator) {
                                            case "=":
                                                stop = stringDouble.getString().equalsIgnoreCase(constant);
                                                break;
                                            case "!=":
                                                stop = !stringDouble.getString().equalsIgnoreCase(constant);
                                                break;
                                            default:  // error for dodgy operator??
                                                loopsLog.append("Unknown operator for repeat until outcome string : ").append(operator);
                                                break;
                                        }
                                    } else if (stringDouble.getString() == null && constant == null) { // assume numbers are set
                                        // '=', '>'. '<'. '<=' '>=' '!='
                                        switch (operator) {
                                            case "=":
                                                stop = stringDouble.getDouble() == constantDouble;
                                                break;
                                            case ">":
                                                stop = stringDouble.getDouble() > constantDouble;
                                                break;
                                            case "<":
                                                stop = stringDouble.getDouble() < constantDouble;
                                                break;
                                            case ">=":
                                                stop = stringDouble.getDouble() >= constantDouble;
                                                break;
                                            case "<=":
                                                stop = stringDouble.getDouble() <= constantDouble;
                                                break;
                                            case "!=":
                                                stop = stringDouble.getDouble() != constantDouble;
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
                            .getJsonChildren(loggedInUser.getDataAccessToken(), 0, 0, false, "edit:zapdata " + trimmedLine.substring(7), StringLiterals.DEFAULT_DISPLAY_NAME, 0);

                } else if (trimmedLine.toLowerCase().startsWith("filtercontext")) {
                    filterContext = trimmedLine.substring("filtercontext".length()).trim();
                } else if (trimmedLine.toLowerCase().startsWith("filteritems")) {
                    filterItems = trimmedLine.substring("filteritems".length()).trim();
                } else if (trimmedLine.toLowerCase().startsWith("exportpath")) { // note unlike filtercontext and filteritems this goes down the recursive levels, it's not just at this level
                    // when this is set then zap the file if it exists
                    String checkPath = trimmedLine.substring("exportpath".length()).trim();
                    int last = checkPath.lastIndexOf("\\");
                    if (checkPath.lastIndexOf("/") > last) {
                        last = checkPath.lastIndexOf("/");
                    }
                    if (last > 0) {
                        checkPath = checkPath.substring(0, last + 1) + SpreadsheetService.host + checkPath.substring(last + 1);
                    }
                    Path path = Paths.get(checkPath);
                    // new logic according to Ed Broking requirements - preface the filename with the host
                    if (Files.exists(path)) {
                        Files.delete(path);
                        // if it existed then we know it's a reasonable path
                        exportPath = checkPath;
                    } else if (Files.isDirectory(path.getParent())) { // does the directory exist
                        exportPath = checkPath;
                    }

                } else if (trimmedLine.toLowerCase().startsWith("set ")) {
                    /*
                    there are now two versions:
                    1) Set <expression> as[global]  name  -     this is creating a set
                    2) set `<name>` = <expresssion>
                        this calculates <name> from <expression> using filterItems and filterContext.
                        in <expression> if '|' is used in a term, that term is considered to be a constant.
                        e.g  filtercontext = Jan-19|`Affiliate Items` children
                             filterItems = `All orders` children
                             set `commission` = `Price` * `Commission %`|Affiliate Items`

                        in the instance above, the system first calculates the `Commission %`|`Affiliate Items` as a constant, then applies this formula to `commission` which is saved. `

                     */
                    Pattern p = Pattern.compile("" + StringLiterals.QUOTE + "[^" + StringLiterals.QUOTE + "]*" + StringLiterals.QUOTE + " ="); //`name`=
                    Matcher m = p.matcher(trimmedLine.substring(4));
                    if (m.find() && m.start() == 0) {
                        List<List<String>> colHeadings = makeNewListList("");
                        colHeadings.get(0).set(0, trimmedLine);
                        List<List<String>> rowHeadings = makeNewListList(filterItems);
                        List<List<String>> context = makeNewListList(filterContext);
                        String region = "autoexecute";
                        //note that this routine gets the data twice - once here, and once before saving.  This is unnecessary, but quicker to program for the moment.
                        UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), 0, "", "execute");//must have userRegionOptions
                        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser, region, 0, rowHeadings, colHeadings,
                                context, userRegionOptions, true);
                        loggedInUser.setSentCells(loggedInUser.getOnlineReport().getId(), region, region, cellsAndHeadingsForDisplay);
                        for (List<CellForDisplay> row : cellsAndHeadingsForDisplay.getData()) {
                            row.get(0).setNewStringValue(row.get(1).getStringValue());
                            row.get(0).setChanged();
                            row.get(0).setNewDoubleValue(row.get(1).getDoubleValue());
                        }

                        System.out.println("saving for : " + region);
                        OnlineReport onlineReport = loggedInUser.getOnlineReport();
                        // EFC note 22/02/21 - this was persisting every time, why???
                        SpreadsheetService.saveData(loggedInUser, onlineReport.getId(), onlineReport.getReportName(), region, region.toLowerCase(), false);


                    } else {

                        String result = null;
                        try {
                            result = CommonReportUtils.resolveQuery(loggedInUser, trimmedLine.substring(4), null);
                        } catch (Exception e) {
                            return new DoubleAndOrString(0.0,result + "Not found:" + trimmedLine.substring(4));
                        }

                        RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).addToLog(loggedInUser.getDataAccessToken(), result);
                    }
                } else if (trimmedLine.toLowerCase().startsWith("if ")) {
                    String result = CommonReportUtils.resolveQuery(loggedInUser, trimmedLine.substring(4), null);
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
                            toReturn = executeCommands(loggedInUser, subCommands, exportPath, loopsLog, systemData2DArrays, count, provenanceId);

                        }
                        if (lastLine.toLowerCase().equals("else")) {
                            while (lineNo < commands.size() && getIndent(commands.get(lineNo)) > startingIndent) {
                                lineNo++;
                            }
                        }
                        lineNo--;
                    } else {
                        subCommands = new ArrayList<>();
                        while (lineNo < commands.size() && getIndent(commands.get(lineNo)) > startingIndent) {
                            lastLine = commands.get(lineNo);
                            subCommands.add(lastLine);
                            lineNo++;
                        }
                        lineNo--; // put line back to where it is now
                        if (!subCommands.isEmpty()) {
                            toReturn = executeCommands(loggedInUser, subCommands, exportPath, loopsLog, systemData2DArrays, count, provenanceId);
                        }
                    }
                } else {
                    loopsLog.append("badly formed execute line  : ").append(trimmedLine);
                }
            }
        }
        loopsLog.append("\r\n");
        return toReturn;
    }

    private static List<List<String>> makeNewListList(String source) {
        List<List<String>> toReturn = new ArrayList<>();
        if (source == null) return toReturn;
        toReturn.add(Arrays.asList(source.split("\\|")));
        return toReturn;
    }

    static void fillSpecialRegions(LoggedInUser loggedInUser, Book book, int reportId) {
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith(StringLiterals.AZDATAREGION)) { // I'm saving on all sheets, this should be fine with zk
                String region = name.getName().substring(StringLiterals.AZDATAREGION.length());
                // possibly the nosave check could be factored, in report service around line 230
                // this is a bit annoying given that I should be able to get the options from the sent cells but there may be no sent cells. Need to investigate this - nosave is currently being used for different databases, that's the problem
                String sheetName = name.getRefersToSheetName();
                Sheet sheet = book.getSheet(sheetName);
                SName rowHeadings = null;
                if (sheet != null) { // it seems it can be if names are not arranged properly
                    rowHeadings = BookUtils.getNameByName(StringLiterals.AZROWHEADINGS + region, sheet);
                }
                if (rowHeadings == null) {
                    int top = name.getRefersToCellRegion().getRow();
                    int left = name.getRefersToCellRegion().getColumn();
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, sheetName, region);
                    if (cellsAndHeadingsForDisplay != null) { // apparently it can be . . .
                        List<List<CellForDisplay>> data = cellsAndHeadingsForDisplay.getData();
                        for (int rowNo = 0; rowNo < data.size(); rowNo++) {
                            for (int colNo = 0; colNo < data.get(0).size(); colNo++) {
                                String cellVal = ImportService.getCellValue(sheet, top + rowNo, left + colNo).getString();
                                if (cellVal.length() > 0) {
                                    data.get(rowNo).get(colNo).setNewStringValue(cellVal);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static int findString(String line, String toFind) throws Exception {
        //checks whether string to find is part of a name enclosed in ``
        int quotePos = 0;
        int foundPos = line.indexOf(toFind);
        if (foundPos > 0) {
            while (quotePos >= 0) {
                quotePos = line.indexOf("`", quotePos + 1);
                if (quotePos < 0 || quotePos > foundPos) return foundPos;
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
        throw (new Exception("not understood: " + line));
    }

    private static int getIndent(String s) {
        int indent = 0;
        while (indent < s.length() && s.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    // a inclined not to let two users run at the same time. I have fixed a bug relating to checking existing files but still this has little harm and should be safer
    public static synchronized int generateXMLFilesAndSupportingReports(LoggedInUser loggedInUser, Sheet selectedSheet, Path destdir) throws Exception {
        int fileCount = 0;
        // ok try to find the relevant regions
        List<SName> namesForSheet = BookUtils.getNamesForSheet(selectedSheet);
        Path azquoTempDir = Paths.get(SpreadsheetService.getHomeDir() + "/temp"); // do xml initially in here then copy across, we may need a capy of the file later to match back to errors
        // make the file pointer out here - no point in it resetting for different regions. This was highlighted when doing just xml files which are generated much faster than 1 per second
        LocalDateTime start = LocalDateTime.of(2019, Month.JANUARY, 1, 0, 0);
        LocalDateTime now = LocalDateTime.now();
        int filePointer = (int) (now.toEpochSecond(ZoneOffset.UTC) - start.toEpochSecond(ZoneOffset.UTC));
        for (SName name : namesForSheet) {
            if (name.getName().toLowerCase().startsWith(StringLiterals.AZDATAREGION)) { // then we have a data region to deal with here
                String region = name.getName().substring(StringLiterals.AZDATAREGION.length()); // might well be an empty string
                // we don't actually need to do anything with this now but we need to switch on the XML button
                SName xmlHeadings = BookUtils.getNameByName(StringLiterals.AZXML + region, selectedSheet);
                Map<String, Integer> xmlToColMap = new HashMap<>();
                if (xmlHeadings != null) {
                    Map<String, Integer> reportSelectionsColMap = new HashMap<>();
                    //todo - use azxmlflag . . .
                    SName xmlFlagName = BookUtils.getNameByName(StringLiterals.AZXMLFLAG + region, selectedSheet);
                    SName filePrefixName = BookUtils.getNameByName(StringLiterals.AZXMLFILENAME + region, selectedSheet);
                    // Ed B require the option to override the file name of the supporting report
                    SName supportReportPrefixName = BookUtils.getNameByName(StringLiterals.AZSUPPORTREPORTFILENAME + region, selectedSheet);
                    String filePrefix = null;
                    boolean multiRowPrefix = false; // a multi row prefix means the prefix will be different for each line
                    if (filePrefixName != null) {
                        if ((filePrefixName.getRefersToCellRegion().lastRow - filePrefixName.getRefersToCellRegion().row) > 0) { // only one row normally
                            multiRowPrefix = true;
                        } else {
                            SCell snameCell = BookUtils.getSnameCell(filePrefixName);
                            if (snameCell != null) {
                                // dash not allowed
                                filePrefix = snameCell.getStringValue().replace("-", "");
                            }
                        }
                    }

                    for (int col = xmlHeadings.getRefersToCellRegion().column; col <= xmlHeadings.getRefersToCellRegion().lastColumn; col++) {
                        CellData cellData = Ranges.range(selectedSheet, xmlHeadings.getRefersToCellRegion().row, col).getCellData();
//                                        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
                        //if (colCount++ > 0) bw.write('\t');
                        if (cellData != null && cellData.getFormatText().length() > 0) {
                            xmlToColMap.put(cellData.getFormatText(), col);// I assume means formatted text
                        }
                    }

                    String reportName = null;
                    // the name of the xlsx file generated
                    SName supportReportName = BookUtils.getNameByName(StringLiterals.AZSUPPORTREPORTNAME + region, selectedSheet);
                    SName supportReportSelections = BookUtils.getNameByName(StringLiterals.AZSUPPORTREPORTSELECTIONS + region, selectedSheet);
                    SName supportReportFileXMLTag = BookUtils.getNameByName(StringLiterals.AZSUPPORTREPORTFILEXMLTAG + region, selectedSheet);
                    final int reportFileXMLTagIndex = -1;
                    boolean multipleReports = false;
                    if (supportReportName != null && supportReportSelections != null) {
                        // new logic - supportReportName can be one cell as it was before *or* it can be multiple in which case I look for a matching row in it
                        if (supportReportName.getRefersToCellRegion().row == supportReportName.getRefersToCellRegion().lastRow) {
                            // then we have xlsx files to generate along side the XML files
                            SCell snameCell = BookUtils.getSnameCell(supportReportName);
                            if (snameCell != null) {
                                reportName = snameCell.getStringValue();
                            }
                        } else {
                            multipleReports = true;
                        }
                        for (int col = supportReportSelections.getRefersToCellRegion().column; col <= supportReportSelections.getRefersToCellRegion().lastColumn; col++) {
                            CellData cellData = Ranges.range(selectedSheet, supportReportSelections.getRefersToCellRegion().row, col).getCellData();
//                                        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
                            //if (colCount++ > 0) bw.write('\t');
                            if (cellData != null && cellData.getFormatText().length() > 0) {
                                reportSelectionsColMap.put(cellData.getFormatText(), col);// I assume means formatted text
                            }
                        }
                        if (supportReportFileXMLTag != null) {
                            SCell cell = BookUtils.getSnameCell(supportReportFileXMLTag);
                            // essentially a special case XML mapping. Doesn't match to a column, it will have the path to the generated report in it
                            if (cell != null) {
                                xmlToColMap.put(cell.getStringValue(), reportFileXMLTagIndex);// I assume means formatted text
                            }
                        }
                    }
                    // if there's extra info e.g. EdIT Section (not brokasure section) that needs to be hung on to to help identify data on the way back in.
                    // Adding extra fields to the XML sent to Brokasure would do this but I want to stick as closely to the spec as possible
                    SName xmlExtraInfo = BookUtils.getNameByName(StringLiterals.AZXMLEXTRAINFO + region, selectedSheet);
                    Map<String, Integer> xmlExtraInfoColMap = new HashMap<>();
                    if (xmlExtraInfo != null) {
                        for (int col = xmlExtraInfo.getRefersToCellRegion().column; col <= xmlExtraInfo.getRefersToCellRegion().lastColumn; col++) {
                            CellData cellData = Ranges.range(selectedSheet, xmlExtraInfo.getRefersToCellRegion().row, col).getCellData();
//                                        String dataFormat = sheet.getInternalSheet().getCell(r, c).getCellStyle().getDataFormat();
                            //if (colCount++ > 0) bw.write('\t');
                            if (cellData != null && cellData.getFormatText().length() > 0) {
                                xmlExtraInfoColMap.put(cellData.getFormatText(), col);// I assume means formatted text
                            }
                        }
                    }


                    boolean rootInSheet = true;

                    String rootCandidate = null;

                    for (String xmlName : xmlToColMap.keySet()) {
                        String test;
                        if (xmlName.indexOf("/", 1) > 0) {
                            test = xmlName.substring(0, xmlName.indexOf("/", 1));
                        } else {
                            test = xmlName;
                        }
                        if (rootCandidate == null) {
                            rootCandidate = test;
                        } else if (!rootCandidate.equals(test)) {
                            rootInSheet = false;
                        }
                    }

                    if (rootInSheet) { // then strip off the first tag, it will be used as root
                        for (String xmlName : new ArrayList<>(xmlToColMap.keySet())) { // copy the keys, I'm going to modify them!
                            Integer remove = xmlToColMap.remove(xmlName);
                            xmlToColMap.put(xmlName.substring(xmlName.indexOf("/", 1)), remove);
                        }
                    }

                    List<String> xmlNames = new ArrayList<>(xmlToColMap.keySet());
                    Collections.sort(xmlNames);
                    // then sort again jamming those with nested elements at the bottom
                    //xmlNames.sort(Comparator.comparingInt(s -> StringUtils.countMatches(s, "/")));
                    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    // matching brekasure example
                    transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
                    // going for one file per line as per brokersure, zip at the end
                    // tracking where we are in the xml, what elements we're in
                    for (int row = name.getRefersToCellRegion().row; row <= name.getRefersToCellRegion().lastRow; row++) {
                        boolean go = true;// the xml flag might make it false . . .
                        if (xmlFlagName != null) {
                            CellRegion refersToCellRegion = xmlFlagName.getRefersToCellRegion();
                            if (row >= refersToCellRegion.row && row <= refersToCellRegion.getLastRow()) {
                                SCell cell = selectedSheet.getInternalSheet().getCell(row, refersToCellRegion.column);
                                if (cell != null) { // not sure how it could be null
                                    if (cell.getType() != SCell.CellType.STRING || !"Y".equalsIgnoreCase(cell.getStringValue())) {
                                        go = false;
                                    }
                                }
                            }
                        }
                        if (go) {
                            //String fileName = base64int(filePointer) + ".xml";
                            // if multi row try and find a prefix value in the range
                            // also multi row will support overriding the prefixes for the support report name if applicable
                            String supportReportPrefix = null; // will be most of the time
                            if (multiRowPrefix) {
                                CellRegion refersToCellRegion = filePrefixName.getRefersToCellRegion();
                                if (row >= refersToCellRegion.row && row <= refersToCellRegion.getLastRow()) {
                                    SCell cell = selectedSheet.getInternalSheet().getCell(row, refersToCellRegion.column);
                                    if (cell != null) {
                                        // dash not allowed
                                        filePrefix = cell.getStringValue().replace("-", "");
                                    }
                                }
                                // check for a support report prefix, basically an override on the xlsx name. Same logic as the one used for the XML filename prefix
                                if (supportReportPrefixName != null){
                                    refersToCellRegion = supportReportPrefixName.getRefersToCellRegion();
                                    if (row >= refersToCellRegion.row && row <= refersToCellRegion.getLastRow()) {
                                        SCell cell = selectedSheet.getInternalSheet().getCell(row, refersToCellRegion.column);
                                        if (cell != null) {
                                            supportReportPrefix = cell.getStringValue().replace("-", "");
                                        }
                                    }
                                }
                            }

                                        /* check the file pointer is fit for purpose for xml
                                         note! I have to make the xml in azquo temp (which is not automatically cleared up - it may be manually periodically)
                                         this is because in case of an error Brokasure is NOT currently returning the original file and will have zapped it from the
                                         inbox. Hence I need a copy in temp, I'll use temp to check xml files are not duplicate names.
                                         A moot point in the case of zips but no harm
                                         */
                            String fileName = (filePrefix != null ? filePrefix : "") + eightCharInt(filePointer) + ".xml";
                            while (azquoTempDir.resolve(fileName).toFile().exists()) {
                                filePointer++;
//                                            fileName = base64int(filePointer) + ".xml";
                                fileName = (filePrefix != null ? filePrefix : "") + eightCharInt(filePointer) + ".xml";
                            }
                            // report name is not the file name, it's the name of the report in Azquo . .
                            if (multipleReports) {
                                CellRegion refersToCellRegion = supportReportName.getRefersToCellRegion();
                                if (row >= refersToCellRegion.row && row <= refersToCellRegion.getLastRow()) {
                                    SCell cell = selectedSheet.getInternalSheet().getCell(row, refersToCellRegion.column);
                                    if (cell != null) {
                                        reportName = cell.getStringValue();
                                    }
                                }
                            }

                            String reportFileName = "";
                            // we now do reports first as if a file is produced then the path to that file might be referenced by the XML
                            if (reportName != null && !reportSelectionsColMap.isEmpty()) { // I can't see how the reportSelectionsColMap could be empty and valid
                                for (String choiceName : reportSelectionsColMap.keySet()) {
                                    // dammit I forgot that! Had annoying consequences. Oh well . . .
                                    Ranges.range(selectedSheet, row, reportSelectionsColMap.get(choiceName)).notifyChange();
                                    CellData cellData = Ranges.range(selectedSheet, row, reportSelectionsColMap.get(choiceName)).getCellData();
                                    String value = "";
                                    if (cellData != null) {
                                        value = cellData.getFormatText();// I assume means formatted text
                                    }
                                    if (!value.isEmpty()) {
                                        System.out.println("Xml setting choice : " + choiceName + " value " + value);
                                        SpreadsheetService.setUserChoice(loggedInUser, choiceName, value);
                                    }
                                }
                                OnlineReport onlineReport;
                                Database oldDatabase = null;
                                if (loggedInUser.getPermission(reportName.toLowerCase()) != null) {
                                    LoggedInUser.ReportDatabase permission = loggedInUser.getPermission(reportName.toLowerCase());
                                    onlineReport = permission.getReport();
                                    if (permission.getDatabase() != null) {
                                        oldDatabase = loggedInUser.getDatabase();
                                        loggedInUser.setDatabaseWithServer(loggedInUser.getDatabaseServer(), permission.getDatabase());
                                    }
                                } else { // otherwise try a straight lookup - stick on whatever db we're currently on
                                    onlineReport = OnlineReportDAO.findForDatabaseIdAndName(loggedInUser.getDatabase().getId(), reportName);
                                    if (onlineReport == null) {
                                        onlineReport = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                                    }
                                }
                                if (onlineReport != null) { // need to prepare it as in the controller todo - factor?
                                    // now add in the support report prefix as the preference
                                    reportFileName = (supportReportPrefix != null ? supportReportPrefix : (filePrefix != null ? filePrefix : "")) + eightCharInt(filePointer) + ".xlsx";
                                    reportFileName = reportFileName.toLowerCase();
                                    // check that the xlsx doesn't already exist - if it does then bump the pointer, also need to check xml for existing files at this point
                                    while (azquoTempDir.resolve(reportFileName).toFile().exists() || azquoTempDir.resolve(fileName).toFile().exists()) {
                                        filePointer++;
                                        reportFileName = (supportReportPrefix != null ? supportReportPrefix : (filePrefix != null ? filePrefix : "")) + eightCharInt(filePointer) + ".xlsx";
                                        reportFileName = reportFileName.toLowerCase();
                                        fileName = (filePrefix != null ? filePrefix : "") + eightCharInt(filePointer) + ".xml";
                                    }

                                    String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk();
                                    final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                                    book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                                    book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                                    book.getInternalBook().setAttribute(OnlineController.REPORT_ID, onlineReport.getId());
                                    StringBuilder errorLog = new StringBuilder();
                                    // so there's an issue here - if the choices are duff and the report is empty we don't want to send the file, we want to warn the user
                                    // the easiest way is probably to check the sent cells in logged in user
                                    ReportRenderer.populateBook(book, 0, false, true, errorLog); // note true at the end here - keep on logging so users can see changes as they happen
                                    List<CellsAndHeadingsForDisplay> sentForReport = loggedInUser.getSentForReport(onlineReport.getId());
                                    boolean hasData = false;
                                    outer : for (CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay : sentForReport){
                                        if (cellsAndHeadingsForDisplay.getData().size() > 1){
                                            hasData = true;
                                            break;
                                        } else if (cellsAndHeadingsForDisplay.getData().size() == 1){// it seems permute may put in a blank row
                                            for (CellForDisplay cellForDisplay : cellsAndHeadingsForDisplay.getData().get(0)){
                                                if (!cellForDisplay.getStringValue().isEmpty() || cellForDisplay.getDoubleValue() > 0){
                                                    hasData = true;
                                                    break outer;
                                                }
                                            }
                                        }
                                    }
                                    if (!hasData){ // then we stop, no data means the sheet is probably incorrectly configured
                                        // todo - how to flag this up properly?
                                        System.out.println("*******");
                                        System.out.println("*******");
                                        System.out.println("*******");
                                        System.out.println("*******Empty sheet being generated. Skipping XML.");
                                        System.out.println("*******");
                                        System.out.println("*******");
                                        System.out.println("*******");
                                        continue;
                                    }
                                    try {
                                        for (SName sName : book.getInternalBook().getNames()) {
                                            if (sName.getRefersToSheetName() != null) {
                                                Range chosenRange = Ranges.range(book.getSheet(sName.getRefersToSheetName())
                                                        , sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()
                                                        , sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn()
                                                );
                                                chosenRange.deleteValidation();
                                            }
                                        }
                                    } catch (Exception e) { // I don't think it will exception but this is cosmetic, on the off chance it NPEs for example then carry on
                                        e.printStackTrace();// do log it though
                                    }
                                    Exporter exporter = Exporters.getExporter();
//                                                File file = zipforxmlfiles.resolve((filePrefix != null ? filePrefix : "") + base64int(filePointer) + ".xlsx").toFile();
                                    // like the xml files we now want these to have  a copy in temp too
                                    File file = azquoTempDir.resolve(reportFileName).toFile();
                                    try (FileOutputStream fos = new FileOutputStream(file)) {
                                        exporter.export(book, fos);
                                        fileCount++;
                                    }
                                    // now copy from the azquo temp dir
                                    Files.copy(azquoTempDir.resolve(reportFileName), destdir.resolve(reportFileName));
                                    if (oldDatabase != null) {
                                        loggedInUser.setDatabaseWithServer(loggedInUser.getDatabaseServer(), oldDatabase);
                                    }
                                }
                            }
                            // then I need to create a simple properties file with the extra info
                            // since we're adding Hanover as another business and there may be many more we need to know where to load data when it comes back from Brokasure
                            // the easiest way to do this is to add the database in this properties file so make one regardless of whether the XML feed spec requires it and add the database persistence name
                            // key used defined in the DB cron as that's where it's used
                            try (OutputStream output = new FileOutputStream(azquoTempDir.resolve((filePrefix != null ? filePrefix : "") + eightCharInt(filePointer) + ".properties").toString())) {
                                Properties properties = new Properties();
                                for (String propertyName : xmlExtraInfoColMap.keySet()) {
                                    Ranges.range(selectedSheet, row, xmlExtraInfoColMap.get(propertyName)).notifyChange();
                                    CellData cellData = Ranges.range(selectedSheet, row, xmlExtraInfoColMap.get(propertyName)).getCellData();
                                    String value = "";
                                    if (cellData != null) {
                                        value = cellData.getFormatText();// I assume means formatted text
                                    }
                                    if (!value.isEmpty()) {
                                        properties.put(propertyName, value);
                                    }
                                }
                                properties.put(DBCron.AZQUODATABASEPERSISTENCENAME, loggedInUser.getDatabase().getPersistenceName());
                                properties.store(output, null);
                            } catch (IOException io) {
                                io.printStackTrace();
                            }
                            //System.out.println("file name : " + fileName);
                            Document doc = docBuilder.newDocument();
                            doc.setXmlStandalone(true);
                            Element rootElement = doc.createElement(rootInSheet ? rootCandidate.replace("/", "") : "ROOT");
                            doc.appendChild(rootElement);
                            List<Element> xmlContext = new ArrayList<>();
                            for (String xmlName : xmlNames) {
                                int col = xmlToColMap.get(xmlName);
                                String value = "";
                                if (col == reportFileXMLTagIndex) {
                                    value = reportFileName;
                                } else {
                                    Ranges.range(selectedSheet, row, col).notifyChange();
                                    CellData cellData = Ranges.range(selectedSheet, row, col).getCellData();
                                    if (cellData != null) {
                                        value = cellData.getFormatText();// I assume means formatted text
                                        if (cellData.getType() == CellData.CellType.NUMERIC || (cellData.getType() == CellData.CellType.FORMULA && cellData.getResultType() == CellData.CellType.NUMERIC)) {
                                            value = value.replaceAll(",", ""); // a hack for Ed B, the formatting in the cell seems to be ignored in this respect
                                        }
                                    }
                                }
                                // note - this logic assumes he mappings are sorted
                                StringTokenizer st = new StringTokenizer(xmlName, "/");
                                int i = 0;
                                while (st.hasMoreTokens()) {
                                    String nameElement = st.nextToken();
                                    if (i >= xmlContext.size() || !xmlContext.get(i).getTagName().equals(nameElement)) {
                                        if (i < xmlContext.size()) { // it didn't match, need to chop
                                            xmlContext = xmlContext.subList(0, i);// trim the list of bits we don't need
                                        }
                                        Element element = doc.createElement(nameElement);
                                        if (xmlContext.isEmpty()) {
                                            rootElement.appendChild(element);
                                        } else {
                                            xmlContext.get(xmlContext.size() - 1).appendChild(element);
                                        }
                                        xmlContext.add(element);
                                    }
                                    i++;
                                }
                                xmlContext.get(xmlContext.size() - 1).appendChild(doc.createTextNode(value));
                            }
                            // do we want the ability to have duplicates, in this case for Deduction. SO call them deduction-1,them deduction-2 etc then zap the -1,-2 etc after the document is built
                            NodeList nodeList = doc.getElementsByTagName("*");
                            List<Node> toZap = new ArrayList<>();
                            for (int i = 0; i < nodeList.getLength(); i++) {
                                Node node = nodeList.item(i);
                                if (node.getNodeType() == Node.ELEMENT_NODE) {
                                    // do something with the current element
                                    if (node.getNodeName().contains("-") && NumberUtils.isNumber(node.getNodeName().substring(node.getNodeName().indexOf("-") + 1))) {
                                        // zap the duplicates that are empty
                                        boolean empty = true;
                                        NodeList childNodes = node.getChildNodes();
                                        for (int j = 0; j < childNodes.getLength(); j++) {
                                            if (childNodes.item(j).hasChildNodes()) {
                                                // that first criteria essentially means say it's not empty if they start adding further nested tags in tags in the duplicate tags
                                                // may need to deal with that later but for the moment this is fine
                                                if (childNodes.item(j).getFirstChild().hasChildNodes()
                                                        || (childNodes.item(j).getFirstChild().getNodeValue() != null && !childNodes.item(j).getFirstChild().getNodeValue().isEmpty())) {
                                                    empty = false;
                                                    break;
                                                }
                                            }
                                        }
                                        // can't do this here with the index for loop . . .
                                        if (empty) {
                                            toZap.add(node);
                                        } else {
                                            doc.renameNode(node, null, node.getNodeName().substring(0, node.getNodeName().indexOf("-")));
                                        }
                                    }
                                }
                            }
                            for (Node node : toZap){
                                node.getParentNode().removeChild(node); // reasonable syntax??
                            }
                            DOMSource source = new DOMSource(doc);
                            // EFC comment - I'm not forcing UTF8 here, when sending files I'll let it default to the system charset
                            BufferedWriter bufferedWriter = Files.newBufferedWriter(azquoTempDir.resolve(fileName));
                            StreamResult result = new StreamResult(bufferedWriter);
                            transformer.transform(source, result);
                            bufferedWriter.close();
                            // now copy from the azquo temp dir
                            Files.copy(azquoTempDir.resolve(fileName), destdir.resolve(fileName));
                            fileCount++;
                            filePointer++;
/*                                        if (fileCount > 8){
                                            break;
                                        }*/
                        }
                    }
                }
            }
        }
        return fileCount;
    }

    static DecimalFormat df = new DecimalFormat("00000000");

    private static String eightCharInt(int input) {
        return df.format(input);
    }

}