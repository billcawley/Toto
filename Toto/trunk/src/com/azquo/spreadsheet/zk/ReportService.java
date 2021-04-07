package com.azquo.spreadsheet.zk;

import com.azquo.DateUtils;
import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.util.AzquoMailer;
import org.zkoss.zk.ui.util.Clients;
import io.keikai.api.Ranges;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.model.*;
import io.keikai.ui.Spreadsheet;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Created by edward on 09/01/17.
 * <p>
 * Will handle higher level functions required to build a report e.g. checking for permissions in names, resolving choices etc.
 * In contrast to BookUtils which should be lower level.
 */
public class ReportService {
    // should functions like this be in another class? It's not really stateless or that low level
    static final String ALLOWABLE_REPORTS = "az_AllowableReports";
    static final String ALLOWABLE_FORMS = "az_AllowableForms";
    private static final String REDUNDANT = "redundant";

    static void checkForPermissionsInSheet(LoggedInUser loggedInUser, Sheet sheet, int reportId) {
        //have a look for "az_AllowableReports", it's read only, getting it here seems as reasonable as anything
        // a repeat call to this function - could be moved outside but I'm not too bothered about it at the moment
        List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
        //current report is always allowable...
        SName sReportName  = BookUtils.getNameByName(ReportRenderer.AZREPORTNAME, sheet);
        if (sReportName==null){
            return;
        }
        String thisReportName = BookUtils.getSnameCell(sReportName).getStringValue();
        // todo - null pointer when no database for user? Force db to be set? Or allow it not to?
        OnlineReport or = OnlineReportDAO.findById(reportId);
        //System.out.println("adding a report to permissions : " + or);
        loggedInUser.setReportDatabasePermission(null, or, loggedInUser.getDatabase());
        for (SName sName : namesForSheet) {
            // run through every cell in any names region unlocking to I can later lock. Setting locking on a large selection seems to zap formatting, do it cell by cell
            if (sName.getName().equalsIgnoreCase(ALLOWABLE_REPORTS)) {
                CellRegion allowable = sName.getRefersToCellRegion();
                // need to detect 2nd AND 3rd column here - 2nd = db, if 3rd then last is db 2nd report and 1st name (key)
                if (allowable.getLastColumn() - allowable.getColumn() >= 2) { // name, report, database
                    for (int row = allowable.getRow(); row <= allowable.getLastRow(); row++) {
                        if (!sheet.getInternalSheet().getCell(row, allowable.getColumn()).isNull()) {
                            String name = sheet.getInternalSheet().getCell(row, allowable.getColumn()).getStringValue();
                            final String reportName = sheet.getInternalSheet().getCell(row, allowable.getColumn() + 1).getStringValue();
                            final OnlineReport report = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                            final String databaseName = sheet.getInternalSheet().getCell(row, allowable.getColumn() + 2).getStringValue();
                            Database database = DatabaseDAO.findForNameAndBusinessId(databaseName, loggedInUser.getUser().getBusinessId());
                            if (database == null) {
                                database = DatabaseDAO.findById(loggedInUser.getUser().getDatabaseId());
                            }
                            if (report != null && !reportName.equals(thisReportName)) {
                                loggedInUser.setReportDatabasePermission(name.toLowerCase(), report, database);
                            }
                        }
                    }
                } else if (allowable.getLastColumn() - allowable.getColumn() == 1) { // report, database
                    for (int row = allowable.getRow(); row <= allowable.getLastRow(); row++) {
                        if (!sheet.getInternalSheet().getCell(row, allowable.getColumn()).isNull()) {
                            final String reportName = sheet.getInternalSheet().getCell(row, allowable.getColumn()).getStringValue();
                            final OnlineReport report = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                            final String databaseName = sheet.getInternalSheet().getCell(row, allowable.getColumn() + 1).getStringValue();
                            Database database = DatabaseDAO.findForNameAndBusinessId(databaseName, loggedInUser.getUser().getBusinessId());
                            if (database == null) {
                                database = DatabaseDAO.findById(loggedInUser.getUser().getDatabaseId());
                            }
                            if (report != null && database!=null) {
                                loggedInUser.setReportDatabasePermission(null, report, database);
                            }
                        }
                    }
                } else { // just the report
                    for (int row = allowable.getRow(); row <= allowable.getLastRow(); row++) {
                        if (!sheet.getInternalSheet().getCell(row, allowable.getColumn()).isNull()) {
                            final String reportName = sheet.getInternalSheet().getCell(row, allowable.getColumn()).getStringValue();
                            final OnlineReport report = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                            if (report != null) {
                                loggedInUser.setReportDatabasePermission(null, report, DatabaseDAO.findById(loggedInUser.getUser().getDatabaseId()));
                            }
                        }
                    }
                }
            }
            // a simple list for the mo
            if (sName.getName().equalsIgnoreCase(ALLOWABLE_FORMS)) {
                CellRegion allowable = sName.getRefersToCellRegion();
                Set<String> formNames = new HashSet<>();
                for (int row = allowable.getRow(); row <= allowable.getLastRow(); row++) {
                    if (!sheet.getInternalSheet().getCell(row, allowable.getColumn()).isNull()) {
                        final String formName = sheet.getInternalSheet().getCell(row, allowable.getColumn()).getStringValue();
                        if (formName != null && !formName.isEmpty()){
                            formNames.add(formName.toLowerCase());
                        }
                    }
                }
                loggedInUser.setFormPermissions(formNames);
            }
        }
        //System.out.println("permissions : " + loggedInUser.getReportIdDatabaseIdPermissions());
    }

    static void resolveQueries(Book book, LoggedInUser loggedInUser) {
        List<SName> names = new ArrayList<>(book.getInternalBook().getNames());
        names.sort(Comparator.comparing(SName::getName)); // they may be anyway but be sure
        for (SName name : book.getInternalBook().getNames()) {
            if (name != null && name.getName() != null && name.getName().endsWith("Query")) {
                Sheet sheet = book.getSheet(name.getRefersToSheetName());
                //adjusted by WFC to allow a whole range to be the query.
                resolveQuery(loggedInUser, sheet, name.getRefersToCellRegion(), null);
            }
        }
    }

    static void resolveQuery(LoggedInUser loggedInUser, Sheet sheet, CellRegion cellRegion, List<List<String>> contextSource) {
        for (int row = 0; row < cellRegion.getRowCount(); row++) {
            for (int col = 0; col < cellRegion.getColumnCount(); col++) {
                SCell queryCell = sheet.getInternalSheet().getCell(cellRegion.getRow() + row, cellRegion.getColumn() + col);
                if (queryCell.getType() != SCell.CellType.ERROR && (queryCell.getType() != SCell.CellType.FORMULA || (queryCell.getFormulaResultType() != SCell.CellType.ERROR && queryCell.getFormulaResultType() == SCell.CellType.STRING))
                        && queryCell.getType() != SCell.CellType.NUMBER && queryCell.getStringValue().length() > 0) {
                    // hack - on resolving a forumlae if the formula is a string but formatted as number get stirng can error unless you do this
                    if (queryCell.getType() == SCell.CellType.FORMULA) {
                        queryCell.clearFormulaResultCache();
                    }
                    String queryResult = CommonReportUtils.resolveQuery(loggedInUser, queryCell.getStringValue(), contextSource);
                    if (queryResult != null && queryResult.toLowerCase().startsWith("error")) {
                        BookUtils.setValue(queryCell, queryCell.getStringValue() + " - " + queryResult);
                        Ranges.range(sheet, queryCell.getRowIndex(), queryCell.getColumnIndex()).notifyChange(); //
                    }
                }
            }
        }
    }

    private static final DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // make ZK resolve formulae and the, assuming not fast save check for formulae changing data. Finally snap the charts.
    // todo - this was only supposed to be called once but now due to the pre save check it can be called more than once. Not sure if it's the biggest thing but
    static boolean checkDataChangeAndSnapCharts(LoggedInUser loggedInUser, int reportId, Book book, Sheet sheet, boolean skipSaveCheck, boolean skipChartSnap, boolean useSavedValuesOnFormulae) {
        boolean showSave = false;
            /* heck if anything might need to be saved
            I'd avoided doing this but now I am it's useful for restoring values and checking for overlapping data regions.
            so similar loop to above - also we want to check for the logic of using the loaded values
            */
        // by a negative as we want to imply that the default is to check the save
        if (!skipSaveCheck) {
            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
            for (SName name : namesForSheet) {
                if (name.getName().toLowerCase().startsWith(ReportRenderer.AZDATAREGION)) {
                    String region = name.getName().substring(ReportRenderer.AZDATAREGION.length());
                    CellRegion displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, ReportRenderer.AZDATAREGION + region);
                    final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, sheet.getSheetName(), region);
                    if (displayDataRegion != null && sentCells != null) {
                        int startRow = displayDataRegion.getRow();
                        int endRow = displayDataRegion.getLastRow();
                        int startCol = displayDataRegion.getColumn();
                        int endCol = displayDataRegion.getLastColumn();
                        for (int row = startRow; row <= endRow; row++) {
                            for (int col = startCol; col <= endCol; col++) {
                                SCell sCell = sheet.getInternalSheet().getCell(row, col);
                                if (sentCells.getData() != null && sentCells.getData().size() > row - name.getRefersToCellRegion().getRow() // as ever check ranges of the data region vs actual data sent.
                                        && sentCells.getData().get(row - name.getRefersToCellRegion().getRow()).size() > col - name.getRefersToCellRegion().getColumn()) {
                                    CellForDisplay cellForDisplay = sentCells.getData().get(row - startRow).get(col - startCol);
                                    if (cellForDisplay.getSelected()) {
                                        book.getInternalBook().setAttribute(OnlineController.CELL_SELECT, row + "," + col);
                                    }
                                    if (sCell.getType() == SCell.CellType.FORMULA) {
                                        sCell.clearFormulaResultCache(); // this, apparently, is necessary. Tripped up on a hefty formula for oilfields, saving some headings
                                        try {
                                            if (sCell.getFormulaResultType() == SCell.CellType.NUMBER) { // then check it's value against the DB one . . .
                                                // zerosaved means that if the heading  name has the relevant attribute
                                                if ((sCell.getNumberValue() == 0 && (sentCells.getZeroSavedRowIndexes() != null && sentCells.getZeroSavedRowIndexes().contains(row - startRow))
                                                        || (sentCells.getZeroSavedColumnIndexes() != null && sentCells.getZeroSavedColumnIndexes().contains(col - startCol)))
                                                        || sCell.getNumberValue() != cellForDisplay.getDoubleValue()) {
                                                    if (useSavedValuesOnFormulae && !cellForDisplay.getIgnored()) { // override formula from DB, only if not ignored
                                                        sCell.setNumberValue(cellForDisplay.getDoubleValue());
                                                    } else { // the formula overrode the DB, get the value ready of saving if the user wants that
                                                        // this code for string date setting was below but makes no sense if the number is set also so put it in here. Either or.
                                                        if (sCell.getCellStyle().getDataFormat().toLowerCase().contains("m")) {
                                                            if (sCell.getNumberValue() > 0) {
                                                                String dateValue = df.format(DateUtils.getLocalDateTimeFromDate(sCell.getDateValue()));
                                                                if (!cellForDisplay.getStringValue().equals(dateValue)){
                                                                    cellForDisplay.setNewStringValue(dateValue);//set a string value as our date for saving purposes
                                                                    showSave = true;
                                                                }
                                                            }
                                                        } else {
                                                            cellForDisplay.setNewDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                            showSave = true;
                                                        }
                                                    }
                                                }
                                            } else if (sCell.getFormulaResultType() == SCell.CellType.STRING) {
                                                if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())) {
                                                    if (useSavedValuesOnFormulae && !cellForDisplay.getIgnored()) { // override formula from DB
                                                        BookUtils.setValue(sCell, cellForDisplay.getStringValue());
                                                    } else { // the formula overrode the DB, get the value ready of saving if the user wants that
                                                        cellForDisplay.setNewStringValue(sCell.getStringValue());
                                                        showSave = true;
                                                    }
                                                }
                                            }
                                        } catch (Exception e){
                                            System.out.println("Error on cell " + sCell.getReferenceString());
                                            e.printStackTrace();
                                        }
                                    } else {
                                        // we now want to compare in the case of non formulae changes - a value from one data region importing into another,
                                        // the other typically being of the "ad hoc" no row headings type
                                        // notably this will hit a lot of cells (all the rest)
                                        String cellString = BookUtils.getCellString(sheet, row, col);
                                        if (sCell.getType() == SCell.CellType.NUMBER) {
                                            if (sCell.getNumberValue() != cellForDisplay.getDoubleValue()) {
                                                cellForDisplay.setNewStringValue(cellString);//to cover dates as well as numbers -EFC, I don't really understand but I'm moving this inside the conditional
                                                cellForDisplay.setNewDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                showSave = true;
                                            }
                                        } else if (sCell.getType() == SCell.CellType.STRING) {
                                            if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())) {
                                                cellForDisplay.setNewStringValue(sCell.getStringValue());
                                                showSave = true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
            for (SChart chart : sheet.getInternalSheet().getCharts()) {
                ViewAnchor oldAnchor = chart.getAnchor();
                int row = oldAnchor.getRowIndex();
                int col = oldAnchor.getColumnIndex();
                int width = oldAnchor.getWidth();
                int height = oldAnchor.getHeight();
                if (!skipChartSnap) {
                    chart.setAnchor(new ViewAnchor(row, col, 0, 0, width, height));
                } else {
                    chart.setAnchor(new ViewAnchor(row, col, oldAnchor.getXOffset(), oldAnchor.getYOffset(), width, height));
                }
            }
        return showSave;
    }

    public static String save(Spreadsheet ss, LoggedInUser loggedInUser) throws Exception {
        final Book book = ss.getBook();
        return save(book, loggedInUser);
    }

    // factored off from the command controller
    public static String save(Book book, LoggedInUser loggedInUser) throws Exception {
        // todo - provenance?
        long time = System.currentTimeMillis();
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        OnlineReport onlineReport = OnlineReportDAO.findById(reportId);
        String error = null;
        int savedItems = 0;
        StringBuilder redundant = new StringBuilder();
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith(ReportRenderer.AZDATAREGION.toLowerCase())) { // I'm saving on all sheets, this should be fine with zk
                String region = name.getName().substring(ReportRenderer.AZDATAREGION.length());
                // todo - factor this chunk? - about to put it in the execute code
                // this is a bit annoying given that I should be able to get the options from the sent cells but there may be no sent cells. Need to investigate this - nosave is currently being used for different databases, that's the problem
                SName optionsRegion = BookUtils.getNameByName(ReportRenderer.AZOPTIONS + region, book.getSheet(name.getRefersToSheetName()));
                String optionsSource;
                boolean noSave = false;
                if (optionsRegion != null) {
                    optionsSource = BookUtils.getSnameCell(optionsRegion).getStringValue();
                    UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, optionsSource);
                    noSave = userRegionOptions.getNoSave();
                }
                if (!noSave) {
                    ReportExecutor.fillSpecialRegions(loggedInUser, book, reportId);
                    extractEmailInfo(book);
                    final String result = SpreadsheetService.saveData(loggedInUser, reportId, onlineReport != null ? onlineReport.getReportName() : "", name.getRefersToSheetName(), region.toLowerCase());
                    if (!result.startsWith("true")) {
                        Clients.evalJavaScript("alert(\"Save error : " + result + "\")");
                        error = result;
                    } else {
                        String count = result.substring(5);
                        if (result.contains(REDUNDANT)) {
                            redundant.append(count.substring(count.indexOf(REDUNDANT)));
                            count = count.substring(0, count.indexOf(REDUNDANT)).trim();
                        }
                        try {
                            savedItems += Integer.parseInt(count.trim());
                        } catch (Exception ignored) {

                        }
                    }
                }
            }
            // deal with repeats, annoying!
            if (name.getName().toLowerCase().startsWith(ReportRenderer.AZREPEATSCOPE)) { // then try to find the "sub" regions. todo, lower/upper case? Consistency . . .
                String region = name.getName().substring(ReportRenderer.AZREPEATSCOPE.length());
                final SName repeatRegion = book.getInternalBook().getNameByName(ReportRenderer.AZREPEATREGION + region);
                if (repeatRegion != null) {
                    int regionRows = repeatRegion.getRefersToCellRegion().getRowCount();
                    int regionCols = repeatRegion.getRefersToCellRegion().getColumnCount();
                    // integer division is fine will give the number of complete region rows and cols ( rounds down)
                    int repeatRows = name.getRefersToCellRegion().getRowCount() / regionRows;
                    int repeatCols = name.getRefersToCellRegion().getColumnCount() / regionCols;
                    for (int row = 0; row < repeatRows; row++) {
                        for (int col = 0; col < repeatCols; col++) {
                            //region + "-" + repeatRow + "-" + repeatColumn
                            if (loggedInUser.getSentCells(reportId, repeatRegion.getRefersToSheetName(), region.toLowerCase() + "-" + row + "-" + col) != null) { // the last ones on the repeat scope might be blank
                                final String result = SpreadsheetService.saveData(loggedInUser, reportId, onlineReport != null ? onlineReport.getReportName() : "", repeatRegion.getRefersToSheetName(), region.toLowerCase() + "-" + row + "-" + col);
                                if (!result.startsWith("true")) {
                                    Clients.evalJavaScript("alert(\"Save error : " + result + "\")");
                                } else {
                                    try {
                                        savedItems += Integer.parseInt(result.substring(5));
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // repeat sheet save, ergh . . .
            if (error != null) {
                break; // stop looping through the names if a save failed
            }
        }
        // new thing, look for followon, guess we need an instance of ZK azquobook utils
        // need to show readout like executing todo. On that topic could the executing loading screen say "running command?" or something similar?
        AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
        System.out.println("Save time ms : " + (System.currentTimeMillis() - time));
        if (error == null) {
            error = "Success: " + savedItems + " values saved";
            if (redundant.length() > 0) {
                error += " - " + redundant;
            }
            Map<String, String> params = new HashMap<>();
            params.put("Report", (onlineReport != null ? onlineReport.getReportName() : ""));
            loggedInUser.userLog("Save",  params);
            ReportExecutor.runExecuteCommandForBook(book, ReportRenderer.FOLLOWON); // that SHOULD do it. It will fail gracefully in the vast majority of times there is no followon
            // unlock here makes sense think, if duff save probably leave locked
            SpreadsheetService.unlockData(loggedInUser);
            return error;
        } else {
            return error;
        }
    }

    static void extractEmailInfo(Book book) {
        // EFC - it's exceptioning - going to try catch it for the mo
        try {
            for (SName name : book.getInternalBook().getNames()) {
                if (name.getName().toLowerCase().startsWith(ReportRenderer.AZEMAILADDRESS)) {
                    String region = name.getName().substring(ReportRenderer.AZEMAILADDRESS.length());
                    SName emailSubjectName = book.getInternalBook().getNameByName(ReportRenderer.AZEMAILSUBJECT + region);
                    SName emailTextName = book.getInternalBook().getNameByName(ReportRenderer.AZEMAILTEXT + region);
                    String emailAddress = BookUtils.getSnameCell(name).getStringValue();
                    if (emailAddress.length() > 0 && emailSubjectName != null && emailTextName != null) {
                        AzquoMailer.sendEMail(emailAddress, null, BookUtils.getSnameCell(emailSubjectName).getStringValue(), BookUtils.getSnameCell(emailTextName).getStringValue(), null, null);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Email exception : " + e.getMessage());
        }
    }
}