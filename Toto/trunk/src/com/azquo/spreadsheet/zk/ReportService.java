package com.azquo.spreadsheet.zk;

import com.azquo.TypedPair;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.spreadsheet.CommonBookUtils;
import com.azquo.spreadsheet.LoggedInUser;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.api.model.Validation;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.model.SSheet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by edward on 09/01/17.
 *
 * Will handle higher level functions required to build a report e.g. checking for permissions in names, resolving choices etc.
 * In contrast to BookUtils which should be lower level.
 */
public class ReportService {
    // should functions like this be in another class? It's not really stateless or that low level
    static final String ALLOWABLE_REPORTS = "az_AllowableReports";
    static void checkForPermissionsInSheet(LoggedInUser loggedInUser, Sheet sheet){
        //have a look for "az_AllowableReports", it's read only, getting it here seems as reasonable as anything
        Map<String, TypedPair<OnlineReport, Database>> permissionsFromReports = loggedInUser.getPermissionsFromReport() != null ? loggedInUser.getPermissionsFromReport() : new ConcurrentHashMap<>(); // cumulative permissions. Might as well make concurrent
        // a repeat call to this function - could be moved outside but I'm not too bothered about it at the moment
        List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
        for (SName sName : namesForSheet) {
            // run through every cell in any names region unlocking to I can later lock. Setting locking on a large selection seems to zap formatting, do it cell by cell
            if (sName.getName().equalsIgnoreCase(ALLOWABLE_REPORTS)) {
                CellRegion allowable = sName.getRefersToCellRegion();
                // need to detect 2nd AND 3rd column here - 2nd = db, if 3rd then last is db 2nd report and 1st name (key)
                if (allowable.getLastColumn() - allowable.getColumn() == 2) { // name, report, database
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
                            if (report != null) {
                                permissionsFromReports.put(name.toLowerCase(), new TypedPair<>(report, database));
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
                            if (report != null) {
                                permissionsFromReports.put(report.getReportName().toLowerCase(), new TypedPair<>(report, database));
                            }
                        }
                    }
                } else { // just the report
                    for (int row = allowable.getRow(); row <= allowable.getLastRow(); row++) {
                        if (!sheet.getInternalSheet().getCell(row, allowable.getColumn()).isNull()) {
                            final String reportName = sheet.getInternalSheet().getCell(row, allowable.getColumn()).getStringValue();
                            final OnlineReport report = OnlineReportDAO.findForNameAndBusinessId(reportName, loggedInUser.getUser().getBusinessId());
                            if (report != null) {
                                permissionsFromReports.put(report.getReportName().toLowerCase(), new TypedPair<>(report, DatabaseDAO.findById(loggedInUser.getUser().getDatabaseId())));
                            }
                        }
                    }
                }
                loggedInUser.setPermissionsFromReport(permissionsFromReports); // re set it in case it was null above
            }
        }
    }

    static void resolveQueries(Sheet sheet, LoggedInUser loggedInUser) {
        for (SName name : sheet.getBook().getInternalBook().getNames()) {
            if (name != null && name.getName() != null && name.getName().endsWith("Query")) {
                SCell queryCell = BookUtils.getSnameCell(name);
                // as will happen to the whole sheet later
                if (queryCell.getType() == SCell.CellType.FORMULA) {
                    //System.out.println("doing the cell thing on " + cell);
                    queryCell.getFormulaResultType();
                    queryCell.clearFormulaResultCache();
                }
                if (queryCell.getType() != SCell.CellType.ERROR && (queryCell.getType() != SCell.CellType.FORMULA || queryCell.getFormulaResultType() != SCell.CellType.ERROR)) {
                    BookUtils.setValue(queryCell, CommonBookUtils.resolveQuery(loggedInUser, queryCell.getStringValue()));
                }
            }
        }
    }

    static List<SName> addValidation(LoggedInUser loggedInUser, Book book, Map<String, List<String>> choiceOptionsMap) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        if (book.getSheet(ReportRenderer.VALIDATION_SHEET) == null) {
            book.getInternalBook().createSheet(ReportRenderer.VALIDATION_SHEET);
        }
        Sheet validationSheet = book.getSheet(ReportRenderer.VALIDATION_SHEET);
        validationSheet.getInternalSheet().setSheetVisible(SSheet.SheetVisible.HIDDEN);
        int numberOfValidationsAdded = 0;
        List<SName> dependentRanges = new ArrayList<>();
        for (SName name : book.getInternalBook().getNames()) {
            String rangeName = name.getName().toLowerCase();
            if (rangeName.toLowerCase().startsWith(ReportRenderer.AZPIVOTFILTERS) || rangeName.toLowerCase().startsWith(ReportRenderer.AZCONTEXTFILTERS)) {//the correct version should be 'az_ContextFilters'
                String[] filters = BookUtils.getSnameCell(name).getStringValue().split(",");
                SName contextChoices = book.getInternalBook().getNameByName(ReportRenderer.AZCONTEXTHEADINGS);
                if (contextChoices == null) {
                    //original name...
                    contextChoices = book.getInternalBook().getNameByName(ReportRenderer.AZPIVOTHEADINGS);
                }
                if (contextChoices != null) {
                    showChoices(loggedInUser, book, contextChoices, filters, 3);
                }
            }
            if (name.getName().toLowerCase().endsWith("choice")) {
                String choiceName = name.getName().substring(0, name.getName().length() - "choice".length());
                SCell choiceCell = BookUtils.getSnameCell(name);
                System.out.println("debug:  trying to find the region " + choiceName + "chosen");
                SName chosen = book.getInternalBook().getNameByName(choiceName + "chosen"); // as ever I do wonder about these string literals
                if (name.getRefersToCellRegion() != null && chosen != null) {
                    Sheet sheet = book.getSheet(chosen.getRefersToSheetName());
                    CellRegion chosenRegion = chosen.getRefersToCellRegion();
                    if (chosenRegion != null) {
                        List<String> choiceOptions = choiceOptionsMap.get(name.getName().toLowerCase());
                        // todo - clarify how this can ever be false?? we know there's a region for this name so
                        boolean dataRegionDropdown = !BookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(chosenRegion.getRow(), chosenRegion.getColumn(), sheet).isEmpty();
                        if (choiceCell.getType() != SCell.CellType.ERROR && (choiceCell.getType() != SCell.CellType.FORMULA || choiceCell.getFormulaResultType() != SCell.CellType.ERROR)) {
                            String query = choiceCell.getStringValue();
                            int contentPos = query.toLowerCase().indexOf(ReportRenderer.CONTENTS);
                            if ((chosenRegion.getRowCount() == 1 || dataRegionDropdown) && (choiceOptions != null || contentPos >= 0)) {// the second bit is to determine if it's in a data region, the choice drop downs are sometimes used (abused?) in such a manner, a bank of drop downs in a data region
                                if (contentPos < 0) {//not a dependent range
                                    BookUtils.setValue(validationSheet.getInternalSheet().getCell(0, numberOfValidationsAdded), name.getName());
                                    int row = 0;
                                    // yes, this can null pointer but if it does something is seriously wrong
                                    for (String choiceOption : choiceOptions) {
                                        row++;// like starting at 1
                                        SCell vCell = validationSheet.getInternalSheet().getCell(row, numberOfValidationsAdded);
                                        vCell.setCellStyle(sheet.getInternalSheet().getCell(chosenRegion.getRow(), chosenRegion.getColumn()).getCellStyle());
                                        try {
                                            Date date = df.parse(choiceOption);
                                            vCell.setDateValue(date);


                                        } catch (Exception e) {
                                            BookUtils.setValue(vCell, choiceOption);
                                        }
                                    }
                                    if (row > 0) { // if choice options is empty this will not work
                                        Range validationValues = Ranges.range(validationSheet, 1, numberOfValidationsAdded, row, numberOfValidationsAdded);
                                        //validationValues.createName("az_Validation" + numberOfValidationsAdded);
                                        for (int rowNo = chosenRegion.getRow(); rowNo < chosenRegion.getRow() + chosenRegion.getRowCount(); rowNo++) {
                                            for (int colNo = chosenRegion.getColumn(); colNo < chosenRegion.getColumn() + chosenRegion.getColumnCount(); colNo++) {
                                                Range chosenRange = Ranges.range(sheet, rowNo, colNo, rowNo, colNo);
                                                //chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "\"az_Validation" + numberOfValidationsAdded +"\"", null,
                                                chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "=" + validationValues.asString(), null,
                                                        //true, "title", "msg",
                                                        true, "", "",
                                                        false, Validation.AlertStyle.WARNING, "alert title", "alert msg");

                                            }
                                        }
                                        numberOfValidationsAdded++;
                                    } else {
                                        System.out.println("no choices for : " + choiceCell.getStringValue());
                                    }
                                } else {
                                    dependentRanges.add(name);
                                }
                            /*Unlike above where the setting of the choice has already been done we need to set the
                            existing choices here, hence why user choices is required for this. The check for userChoices not being null
                             is that it might be null when re adding the validation sheet when switching between sheets which can happen.
                             Under these circumstances I assume we won't need to re-do the filter adding. I guess need to test.
                             */
                            }
                        }// do anything in the case of excel error?

                    }
                } else {
                    SName multi = book.getInternalBook().getNameByName(choiceName + "multi"); // as ever I do wonder about these string literals
                    if (multi != null) {
                        SCell resultCell = BookUtils.getSnameCell(multi);
                        // all multi list is is a fancy way of saying to the user what is selected, e.g. all, various, all but or a list of those selected. The actual selection box is created in the composer, onclick
                        BookUtils.setValue(resultCell, ReportRenderer.multiList(loggedInUser, choiceName + "Multi", choiceCell.getStringValue()));
                    }
                }
            }
        }
        return dependentRanges;
    }

    private static void showChoices(LoggedInUser loggedInUser, Book book, SName contextChoices, String[] filters, int headingWidth) {
        Sheet cSheet = book.getSheet(contextChoices.getRefersToSheetName());
        CellRegion chRange = contextChoices.getRefersToCellRegion();
        int headingRow = chRange.getRow();
        int headingCol = chRange.getColumn();
        int headingRows = chRange.getRowCount();
        int filterCount = 0;
        //on the top of pivot tables, the options are shown as pair groups separated by a space, sometimes on two rows, also separated by a space
        for (String filter : filters) {
            filter = filter.trim();
            List<String> optionsList = CommonBookUtils.getDropdownListForQuery(loggedInUser, "`" + filter + "` children sorted");
            if (optionsList != null && optionsList.size() > 1) {
                String selected = ReportRenderer.multiList(loggedInUser, "az_" + filter, "`" + filter + "` children sorted");//leave out any with single choices
                int rowOffset = filterCount % headingRows;
                int colOffset = filterCount / headingRows;
                int chosenRow = headingRow + rowOffset;
                int chosenCol = headingCol + headingWidth * colOffset;
                if (filterCount > 0) {
                    Range copySource = Ranges.range(cSheet, headingRow, headingCol, headingRow, headingCol + 1);
                    Range copyTarget = Ranges.range(cSheet, chosenRow, chosenCol, chosenRow, chosenCol + 1);
                    CellOperationUtil.paste(copySource, copyTarget);
                    //Ranges.range(pSheet, chosenRow, chosenCol + 1).setNameName(filter + "Chosen");

                }
                BookUtils.setValue(cSheet.getInternalSheet().getCell(chosenRow, chosenCol), filter);
                if (headingWidth > 1) {
                    BookUtils.setValue(cSheet.getInternalSheet().getCell(chosenRow, chosenCol + 1), selected);
                }
                filterCount++;
            }
        }
    }
}