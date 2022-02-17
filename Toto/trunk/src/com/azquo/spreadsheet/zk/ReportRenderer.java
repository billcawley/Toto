package com.azquo.spreadsheet.zk;

import com.azquo.ExternalConnector;
import com.azquo.MultidimensionalListUtils;
import com.azquo.RowColumn;
import com.azquo.StringLiterals;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.onlinereport.*;
import com.azquo.admin.user.*;
import com.azquo.dataimport.ImportService;
import com.azquo.dataimport.ImportTemplate;
import com.azquo.dataimport.ImportTemplateDAO;
import com.azquo.dataimport.ImportTemplateData;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.github.rcaller.rstuff.RCaller;
import com.github.rcaller.rstuff.RCode;
import org.apache.commons.lang.math.NumberUtils;
import io.keikai.api.*;
import io.keikai.api.model.*;
import io.keikai.api.model.Sheet;
import io.keikai.model.*;
import org.apache.poi.ss.usermodel.Cell;

import java.io.*;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.azquo.dataimport.ImportService.dbPath;
import static com.azquo.dataimport.ImportService.importTemplatesDir;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 03/03/15.
 * <p>
 * To render the report in Keikai, the online excel view
 */
public class ReportRenderer {

    public static boolean populateBook(Book book, int valueId) throws Exception {
        return populateBook(book, valueId, false, false, null, true);
    }

    public static void populateBook(Book book, int valueId, boolean useRepeats) throws Exception {
        populateBook(book, valueId, false, false, null, useRepeats);
    }

    public static boolean populateBook(Book book, int valueId, boolean useSavedValuesOnFormulae, boolean executeMode, StringBuilder errors) throws Exception { // todo - make more elegant? error hack . . .
        return populateBook(book, valueId, useSavedValuesOnFormulae, executeMode, errors, true);
    }

    // note there is a post import keikai option which could speed big data inserts but it does this by not recalculating and that's kinda what we need to do
    // when resolving e.g. choices etc before even populating data  so probably a moot point but worth noting
    private static boolean populateBook(Book book, int valueId, boolean useSavedValuesOnFormulae, boolean executeMode, StringBuilder errors, boolean useRepeats) throws Exception { // todo - make more elegant? error hack . . .
        BookUtils.removeNamesWithNoRegion(book); // should protect against some errors.
        //BookUtils.localiseNames(book);  //bug in ZK - will not extend ranges when rows inserted
        book.getInternalBook().setAttribute(OnlineController.LOCKED, false); // by default
        long track = System.currentTimeMillis();
        String imageStoreName = "";
        boolean showSave = false;
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        // unlock data on every report load. Maybe make this clear to the user?
        // is the exception a concern here?
        // also clear temporary names? Apparently not - discuss with WFC
        try {
            //RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearTemporaryNames(loggedInUser.getDataAccessToken());
            SpreadsheetService.unlockData(loggedInUser);
        } catch (Exception e) {
            e.printStackTrace();
        }
        loadExternalData(book,loggedInUser);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        loggedInUser.setOnlineReport(OnlineReportDAO.findById(reportId));
        Map<Sheet, String> sheetsToRename = new HashMap<>(); // the repeat sheet can require renaming the first "template" sheet but this seems to trip up ZK so do it at the end after all the expanding etc
        //String context = "";
        // why a sheet loop at the outside, why not just run all the names? Need to have a think . . .
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            // I'd like to put this somewhere else but for the moment it must be per sheet to lessen the chances of overlapping repeat regions interfering with each other

            Set<String> repeatRegionTracker = new HashSet<>();
            Sheet sheet = book.getSheetAt(sheetNumber);
            SName az_CurrentUser = BookUtils.getNameByName(StringLiterals.AZCURRENTUSER, sheet);
            if (az_CurrentUser != null) {
                BookUtils.getSnameCell(az_CurrentUser).setStringValue(loggedInUser.getUser().getEmail());
            }

            // might give a little speed increase until the notifys get going . . .
            Ranges.range(sheet).setAutoRefresh(false);

            // check we're not hitting a validation sheet we added!
            if (sheet.getSheetName().endsWith(ChoicesService.VALIDATION_SHEET)) {
                continue;
            }
            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);

            // options and validation now sorted per sheet
            // ignore permissions for developers and admins. A notable todo : zap permissions when reports/databses are edited or deleted
            //if (!loggedInUser.getUser().isDeveloper() && !loggedInUser.getUser().isAdministrator()){
            ReportService.checkForPermissionsInSheet(loggedInUser, sheet, reportId);
            // }
            /* with the protect sheet command below commented this is currently pointless. I think ZK had some misbehavior while locked so leave this for the moment.
            int maxRow = sheet.getLastRow();
            int maxCol = 0;
            for (int row = 0; row <= maxRow; row++) {
                if (maxCol < sheet.getLastColumn(row)) {
                    maxCol = sheet.getLastColumn(row);
                }
            }
            for (int row = 0; row <= maxRow; row++) {
                for (int col = 0; col <= maxCol; col++) {
                    Range selection = Ranges.range(sheet, row, col);
                    CellStyle oldStyle = selection.getCellStyle();
                    EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                    newStyle.setLocked(false);
                    selection.setCellStyle(newStyle);
                }
            }*/
            // we must resolve the options here before filling the ranges as they might feature "as" name populating queries
            List<CellRegion> regionsToWatchForMerge = new ArrayList<>();
            // it will return the properly resolved choice options map as well as flagging the regions to merge by adding to the list
            Map<String, List<String>> choiceOptionsMap = ChoicesService.resolveAndSetChoiceOptions(loggedInUser, sheet, regionsToWatchForMerge);
            ReportService.resolveQueries(sheet.getBook(), loggedInUser); // after all options sorted should be ok

            // start repeat sheet stuff after choices and queries
            // az_RepeatSheet will have a valid name query in it, get the set of names (use choice query for this), then starting with the initial
            // sheet populate a sheet for each valie in az_RepeatItem starting with the first sheet and adding directly after

            SName az_repeatSheet = BookUtils.getNameByName(StringLiterals.AZREPEATSHEET, sheet);
            SName az_repeatItem = BookUtils.getNameByName(StringLiterals.AZREPEATITEM, sheet);
            // second name match is if the repeat item and repeat sheet names are set global, stop them being found on subsequent runs
            if (useRepeats && az_repeatSheet != null && az_repeatSheet.getRefersToSheetName().equals(sheet.getSheetName())
                    && az_repeatItem != null && az_repeatItem.getRefersToSheetName().equals(sheet.getSheetName())) {
                SCell snameCell = BookUtils.getSnameCell(az_repeatSheet);
                if (snameCell != null && snameCell.getStringValue() != null && !snameCell.getStringValue().isEmpty()) {
                    List<String> repeatItems = CommonReportUtils.getDropdownListForQuery(loggedInUser, snameCell.getStringValue());
                    String firstItem = null; // for api purposes I need to set the initial sheet name after it's copied or I get NPEs
                    int sheetPosition = 0;
                    if (repeatItems.size() == 0) {
                        BookUtils.deleteSheet(book, sheetNumber);
                        sheetNumber--;
                        continue;
                    } else {
                        for (String repeatItem : repeatItems) {
                            if (firstItem == null) { // set the repeat tiem on this sheet
                                SCell repeatItemCell = BookUtils.getSnameCell(az_repeatItem);
                                repeatItemCell.setStringValue(repeatItem);
                                firstItem = repeatItem;
                            } else { // make a new one and copy names
                                Range sheetRange = Ranges.range(sheet);
                                // our modified version of the function
                                CopySheet(sheetRange, repeatItem);
                                Sheet newSheet = book.getSheetAt(book.getNumberOfSheets() - 1);// it will be the latest
                                for (SName name : namesForSheet) {
                                    SName newName = book.getInternalBook().getNameByName(name.getName(), newSheet.getSheetName());
                                    if (!name.getName().equalsIgnoreCase(StringLiterals.AZREPEATSHEET)) { // don't copy the repeat or we'll get a recursive loop!
                                        // cloneSheet won't copy the names, need to make new ones
                                        // the new ones need to be applies to as well as refers to the new sheet
                                        String newFormula;
                                        if (newSheet.getSheetName().contains(" ") && !name.getRefersToFormula().startsWith("'")) { // then we need to add quotes
                                            newFormula = name.getRefersToFormula().replace(sheet.getSheetName() + "!", "'" + newSheet.getSheetName() + "'!");
                                        } else {
                                            if (name.getRefersToFormula().startsWith("'")) {
                                                newFormula = name.getRefersToFormula().replace("'" + sheet.getSheetName() + "'!", "'" + newSheet.getSheetName() + "'!");
                                            } else {
                                                newFormula = name.getRefersToFormula().replace(sheet.getSheetName() + "!", newSheet.getSheetName() + "!");
                                            }
                                        }
                                        newName.setRefersToFormula(newFormula);
                                    } else {
                                        book.getInternalBook().deleteName(newName); // now Keikai is automatically copying all names then we need to zap the repeat sheet to stop recursion
                                    }
                                }
                                SName newRepeatItem = BookUtils.getNameByName(StringLiterals.AZREPEATITEM, newSheet);
                                SCell repeatItemCell = BookUtils.getSnameCell(newRepeatItem);
                                repeatItemCell.setStringValue(repeatItem);
                                // now need to move it and rename - hopefully references e.g. names will be affected correctly?
                                book.getInternalBook().moveSheetTo(newSheet.getInternalSheet(), book.getSheetIndex(sheet) + sheetPosition);
                            }
                            sheetPosition++;
                        }
                        if (firstItem == null) {
                            firstItem = sheet.getSheetName();//the repeat list is void
                        }
                        sheetsToRename.put(sheet, firstItem);
                        //need to rename current sheet now before data is loaded
                        //book.getInternalBook().setSheetName(sheet.getInternalSheet(), suggestSheetName(book, firstItem));


                        choiceOptionsMap = ChoicesService.resolveAndSetChoiceOptions(loggedInUser, sheet, regionsToWatchForMerge);
                        ReportService.resolveQueries(sheet.getBook(), loggedInUser); // after all options sorted should be ok
                    }
                }
            }


            // ok the plan here is remove merges that might be adversely affected by regions expanding then put them back in after the regions are expanded.
            List<CellRegion> mergesToTemporarilyRemove = new ArrayList<>(sheet.getInternalSheet().getMergedRegions());
            Iterator<CellRegion> it = mergesToTemporarilyRemove.iterator();
            while (it.hasNext()) {
                CellRegion merge = it.next();
                if (regionsToWatchForMerge.contains(merge)) {
                    CellOperationUtil.unmerge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()));
                } else {
                    it.remove(); // a merge we just leave alone
                }
            }
            // and now we want to run through all regions for this sheet
            // todo - should fastload be poassed down to stop range notify changes? What is being used to replace the formula result cache clear - I guess depends on performance
            boolean fastLoad = false; // skip some checks, initially related to saving
            boolean skipChartSnap = false; // skip some checks, initially related to saving
            for (SName name : namesForSheet) {
                // Old one was case insensitive - not so happy about this. Will allow it on prefixes. (fast load being set outside the loop so is there a problem with it not being found before data regions??)
                if (name.getName().equalsIgnoreCase(StringLiterals.AZFASTLOAD)) {
                    fastLoad = true;
                }
                if (name.getName().equalsIgnoreCase(StringLiterals.AZSKIPCHARTSNAP)) {
                    skipChartSnap = true;
                }
                if (name.getName().equals("az_ImageStoreName")) {
                    imageStoreName = BookUtils.getRegionValue(sheet, name.getRefersToCellRegion());
                }
            }
            for (SName name : namesForSheet) {
                if (name.getName().toLowerCase().startsWith(StringLiterals.AZDATAREGION)) { // then we have a data region to deal with here
                    String region = name.getName().substring(StringLiterals.AZDATAREGION.length()); // might well be an empty string
                    if (BookUtils.getNameByName(StringLiterals.AZROWHEADINGS + region, sheet) == null) { // if no row headings then this is an ad hoc region, save possible by default
                        showSave = true;
                    }

                    // we don't actually need to do anything with this now but we need to switch on the XML button
                    SName xmlHeadings = BookUtils.getNameByName(StringLiterals.AZXML + region, sheet);
                    if (xmlHeadings != null) {
                        sheet.getBook().getInternalBook().setAttribute(OnlineController.XMLZIP, true);
                        if (SpreadsheetService.getXMLDestinationDir() != null && !SpreadsheetService.getXMLDestinationDir().isEmpty()) {
                            sheet.getBook().getInternalBook().setAttribute(OnlineController.XML, true);
                        }
                    }

                    SName optionsRegion = BookUtils.getNameByName(StringLiterals.AZOPTIONS + region, sheet);
                    String optionsSource = "";
                    if (optionsRegion != null) {
                        SCell optionsCell = BookUtils.getSnameCell(optionsRegion);
                        optionsSource = optionsCell.getStringValue();
                    }
                    // better way to combine user region options from the sheet and report database
                    UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, optionsSource);
                    // UserRegionOptions from MySQL will have limited fields filled
                    UserRegionOptions userRegionOptions2 = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                    // only these five fields are taken from the table
                    if (userRegionOptions2 != null) {
                        if (userRegionOptions.getSortColumn() == null) {
                            userRegionOptions.setSortColumn(userRegionOptions2.getSortColumn());
                            userRegionOptions.setSortColumnAsc(userRegionOptions2.getSortColumnAsc());
                        }
                        if (userRegionOptions.getSortRow() == null) {
                            userRegionOptions.setSortRow(userRegionOptions2.getSortRow());
                            userRegionOptions.setSortRowAsc(userRegionOptions2.getSortRowAsc());
                        }

                        userRegionOptions.setHighlightDays(userRegionOptions2.getHighlightDays());
                    }
                    if (loggedInUser.getHighlightDays() > 0) {
                        userRegionOptions.setHighlightDays(loggedInUser.getHighlightDays());
                    }
                    String databaseName = userRegionOptions.getDatabaseName();
                    // fairly simple addition to allow multiple databases on the same report
                    // todo - support when saving . . .
                    if (databaseName != null) { // then switch database, fill and switch back!
                        Database origDatabase = loggedInUser.getDatabase();
                        DatabaseServer origServer = loggedInUser.getDatabaseServer();
                        try {
                            LoginService.switchDatabase(loggedInUser, databaseName);
                            String error = populateRegionSet(sheet, reportId, sheet.getSheetName(), region, valueId, userRegionOptions, loggedInUser, executeMode, repeatRegionTracker); // in this case execute mode is telling the logs to be quiet
                            if (errors != null && error != null) {
                                if (errors.length() > 0) {
                                    errors.append("\n");
                                }
                                errors.append("ERROR : ").append(error);
                            }
                        } catch (Exception e) {
                            String eMessage = "Unknown database " + databaseName + " for region " + region;
                            BookUtils.setValue(sheet.getInternalSheet().getCell(0, 0), eMessage);
                        }
                        loggedInUser.setDatabaseWithServer(origServer, origDatabase);
                    } else {
                        String error = populateRegionSet(sheet, reportId, sheet.getSheetName(), region, valueId, userRegionOptions, loggedInUser, executeMode, repeatRegionTracker);
                        if (errors != null && error != null) {
                            if (errors.length() > 0) {
                                errors.append("\n");
                            }
                            errors.append("ERROR : ").append(error);
                        }
                    }
                }
                if (name.getName().toLowerCase().equals(StringLiterals.AZMENUSPEC)) { // then we have a MENU to deal with here
                    List<List<String>> menuSpec = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, name);
                    int menuLines = 0;
                    for (List<String> submenu: menuSpec){
                        String submenuText = submenu.get(0);
                        String submenuName = submenu.get(1);
                        List<MenuItem> menuItems= MenuItemDAO.findForNameAndBusinessId(submenuName.toLowerCase(Locale.ROOT),loggedInUser.getBusiness().getId());
                        if (menuItems.size()>0){
                            menuLines += 2 & menuItems.size() + 3;
                        }
                    }
                    SName menuRange =  BookUtils.getNameByName(StringLiterals.AZMENU, sheet);
                    if (menuRange!=null) {
                        int firstRow = menuRange.getRefersToCellRegion().getRow();
                        int rowCount = menuRange.getRefersToCellRegion().getRowCount();
                        int firstCol = menuRange.getRefersToCellRegion().getColumn();
                        Range titleModel = Ranges.range(sheet, firstRow, 0, firstRow + 1, book.getMaxColumns());
                        Range itemModel = Ranges.range(sheet, firstRow + 2, 0, firstRow + 3, book.getMaxColumns());
                        if (rowCount < menuLines) {
                            Range insertRange = Ranges.range(sheet, firstRow + rowCount - 1, 0, firstRow + menuLines - 1, 1); // insert at the 3rd row - should be rows to add - 1 as it starts at one without adding anything
                            CellOperationUtil.insertRow(insertRange);
                        }
                        int rowNo = firstRow;
                        for (List<String> submenu : menuSpec) {
                            String submenuText = submenu.get(0);
                            String submenuName = submenu.get(1);
                            String submenuExplanation = submenu.get(2);
                            List<MenuItem> menuItems = MenuItemDAO.findForNameAndBusinessId(submenuName, loggedInUser.getBusiness().getId());
                            if (menuItems.size() > 0) {
                                if (firstRow< rowNo){
                                    CellOperationUtil.paste(titleModel, Ranges.range(sheet,rowNo, 0, rowNo + 1, book.getMaxColumns()));
                                }
                                sheet.getInternalSheet().getCell(rowNo++, firstCol).setStringValue(submenuText);
                                sheet.getInternalSheet().getCell(rowNo++, firstCol + 2).setStringValue(submenuExplanation);
                                for (MenuItem menuItem:menuItems){
                                    if (rowNo > firstRow + 2){
                                        CellOperationUtil.paste(itemModel, Ranges.range(sheet,rowNo, 0, rowNo + 1, book.getMaxColumns()));
                                    }
                                    OnlineReport or = OnlineReportDAO.findById(menuItem.getReportId());
                                    String menuItemName= menuItem.getMenuItemName();
                                    if (menuItemName.length()==0){
                                        menuItemName = or.getReportName();
                                    }
                                    String iframe = menuItem.getIframe();
                                    if (iframe!=null && iframe.length()>0){
                                        loggedInUser.setIFrameLookup(menuItemName.toLowerCase(Locale.ROOT), iframe.trim());

                                    }
                                    sheet.getInternalSheet().getCell(rowNo++, firstCol + 1).setStringValue(menuItemName);
                                    sheet.getInternalSheet().getCell(rowNo++, firstCol + 2).setStringValue(menuItem.getExplanation());
                                    Database db = DatabaseDAO.findById(menuItem.getId());
                                    loggedInUser.setReportDatabasePermission(menuItemName,or,db,true);

                                }
                                rowNo++;//blank row between menus
                            }
                        }
                    }


                }
            }
            // after loading deal with lock stuff
            final List<CellsAndHeadingsForDisplay> sentForReport = loggedInUser.getSentForReport(reportId);
            StringBuilder lockWarnings = new StringBuilder();
            for (CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay : sentForReport) {
                if (cellsAndHeadingsForDisplay.getLockResult() != null) {
                    if (lockWarnings.length() == 0) {
                        lockWarnings.append("Data on this sheet is locked\n");
                    }
                    lockWarnings.append("by ").append(cellsAndHeadingsForDisplay.getLockResult());
                }
            }
            if (lockWarnings.length() > 0) {
                sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED_RESULT, ReportUtils.interpretLockWarnings(lockWarnings.toString(), loggedInUser.getUser().getBusinessId()));
                // any locks applied need to be let go
                if (sheet.getBook().getInternalBook().getAttribute(OnlineController.LOCKED).equals(Boolean.TRUE)) { // I think that's allowed, it was locked then unlock and switch the flag
                    try {
                        SpreadsheetService.unlockData(loggedInUser);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED, false);
                }
            }
            // 19/10/20. Initial R implementation, may require modifying later to increase capacity
            for (SName name : namesForSheet) {
                // Old one was case insensitive - not so happy about this. Will allow it on prefixes. (fast load being set outside the loop so is there a problem with it not being found before data regions??)
                if (name.getName().startsWith(StringLiterals.AZRQUERY)) {
                    SName rDataName = BookUtils.getNameByName(StringLiterals.AZRDATA + name.getName().substring(StringLiterals.AZRQUERY.length()), sheet);
                    if (rDataName != null) {
                        RCaller caller = RCaller.create();
                        RCode code = RCode.create();

                        List<List<String>> rData = BookUtils.nameToStringLists(rDataName);
                        List<List<String>> transposed = MultidimensionalListUtils.transpose2DList(rData);
                        StringBuilder headings = new StringBuilder();
                        for (List<String> row : transposed) {
                            boolean first = true;
                            List<Double> asDoubles = new ArrayList<>();
                            String heading = null;
                            for (String value : row) {
                                if (first) {
                                    if (value.startsWith(".")) {
                                        value = value.substring(1);
                                    }
                                    heading = value;
                                } else {
                                    if (value.isEmpty()) {
                                        asDoubles.add(0.0);
                                    } else {
                                        if (NumberUtils.isNumber(value)) {
                                            asDoubles.add(Double.parseDouble(value));
                                        } else {
                                            break;
                                        }
                                    }
                                }
                                first = false;
                            }
                            if (asDoubles.size() == (row.size() - 1)) { // then we can use numbers
                                // apparently we have to array copy here hhhhngh
                                double[] target = new double[asDoubles.size()];
                                for (int i = 0; i < target.length; i++) {
                                    target[i] = asDoubles.get(i);
                                }
                                code.addDoubleArray(heading, target);
                            } else {
                                // apparently we have to array copy here hhhhngh
                                String[] target = new String[row.size() - 1];
                                for (int i = 0; i < target.length; i++) {
                                    target[i] = row.get(i + 1);
                                }
                                code.addStringArray(heading, target);
                            }
                            headings.append(heading).append(",");
                        }
                        code.addRCode("BaseFrame <- as.data.frame(cbind(" + headings.substring(0, headings.length() - 1) + "))");
                        String query = BookUtils.getSnameCell(name).getStringValue();
                        String[] rcommands = query.split("\\n");
                        for (String rcommand : rcommands) {
                            code.addRCode(rcommand);
                        }
                        caller.setRCode(code);
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        caller.redirectROutputToStream(stream);
//                        caller.runAndReturnResult("result");
                        caller.runOnly();
                        String finalString = new String(stream.toByteArray());
                        finalString = finalString.replace("Output:", "");
                        String[] lines = finalString.split("\\n");
                        for (int i = 0; i < lines.length; i++) {
                            sheet.getInternalSheet().getCell(name.getRefersToCellRegion().row + 1 + i, name.getRefersToCellRegion().column).setStringValue(lines[i]);
                        }
                        //System.out.println(finalString);


//                        String[] result = caller.getParser().getAsStringArray("azquoresult");
//                        System.out.println(result[0]);
//                        System.out.println(result);
/*                        for (String rName : caller.getParser().getNames()){
                            System.out.println(rName);
                            System.out.println(caller.getParser().getType(rName));

                        }*/

/*                        double[] arr = new double[]{1.0, 2.0, 3.0};
                        code.addDoubleArray("myarr", arr);
                        code.addRCode("avg <- mean(myarr)");
                        caller.setRCode(code);
                        caller.runAndReturnResult("avg");
                        double[] result = caller.getParser().getAsDoubleArray("avg");
                        System.out.println(result[0]);*/

                    }
                }
            }


            System.out.println("regions populated in : " + (System.currentTimeMillis() - track) + "ms");
// commenting just this line for the mo
            // now protect. Doing so before seems to cause problems
/*            Ranges.range(sheet).protectSheet("azquo",
                    true, //allowSelectingLockedCells
                    true, //allowSelectingUnlockedCells,
                    true, //allowFormattingCells
                    true, //allowFormattingColumns
                    true, //allowFormattingRows
                    true, //allowInsertColumns
                    true, //allowInsertRows
                    true, //allowInsertingHyperlinks
                    true, //allowDeletingColumns
                    true, //boolean allowDeletingRows
                    true, //allowSorting
                    true, //allowFiltering
                    true, //allowUsingPivotTables
                    true, //drawingObjects
                    true  //boolean scenarios
            );*/
            // all data for that sheet should be populated
            // returns true if data changed by formulaeRanges.range(sheet).notifyChange();
            if (ReportService.checkDataChangeAndSnapCharts(loggedInUser, reportId, book, sheet, fastLoad, skipChartSnap, useSavedValuesOnFormulae)) {
                showSave = true;
            }
            // now remerge
            for (CellRegion merge : mergesToTemporarilyRemove) {
                // the boolean meant JUST horizontally, I don't know why. Hence false.
                CellOperationUtil.merge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()), false);
            }
            if (book.getInternalBook().getAttribute("novalidations") == null) { // hack to stop them being added for PDF
                List<SName> dependentRanges = ChoicesService.addValidation(sheet, loggedInUser, book, choiceOptionsMap);
                if (dependentRanges.size() > 0) {
                    ChoicesService.resolveDependentChoiceOptions(sheet.getSheetName().replace(" ", ""), dependentRanges, book, loggedInUser);
                }
            }
        }
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet s = book.getSheetAt(sheetNumber);
            if (s.getSheetName().startsWith("-")) {
                book.getInternalBook().moveSheetTo(s.getInternalSheet(), book.getNumberOfSheets() - 1);
            }
        }

        for (Map.Entry<Sheet, String> sheetNewName : sheetsToRename.entrySet()) {
            Sheet sheet = sheetNewName.getKey();
            String newName = sheetNewName.getValue();
            String oldName = sheet.getSheetName();
            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
            book.getInternalBook().setSheetName(sheet.getInternalSheet(), suggestSheetName(book, newName));
            // and we need to sort the names
            for (SName name : namesForSheet) {
                String newFormula;
                if (newName.contains(" ") && !name.getRefersToFormula().startsWith("'")) { // then we need to add quotes
                    newFormula = name.getRefersToFormula().replace(oldName + "!", "'" + newName + "'!");
                } else {
                    if (name.getRefersToFormula().startsWith("'")) {
                        newFormula = name.getRefersToFormula().replace("'" + oldName + "'!", "'" + newName + "'!");
                    } else {
                        newFormula = name.getRefersToFormula().replace(oldName + "!", newName + "!");
                    }
                }
                name.setRefersToFormula(newFormula);
            }
            //and the sent cells maps to realign them to the renamed sheets
            for (CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay : loggedInUser.getSentForReportAndSheet(reportId, oldName)) {
                String region = cellsAndHeadingsForDisplay.getRegion();
                loggedInUser.setSentCells(reportId, newName, region, cellsAndHeadingsForDisplay);
                //should we zap the old map?  does not do any harm?

            }
        }
        //
        loggedInUser.setImageStoreName(imageStoreName);
        // we won't clear the log in the case of execute
        if (!executeMode) {
            // after stripping off some redundant exception throwing this was the only possibility left, ignore it
            try {
                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearSessionLog(loggedInUser.getDataAccessToken());
            } catch (Exception ignored) {
            }
        }
        return showSave;
    }

    // edd modifying a version of ZK code to allow a suggested new sheet name
    private static String suggestSheetName(Book book, String suggestedName) {
        if (book.getSheet(suggestedName) == null) {
            return suggestedName;
        }
        int num = 1;
        String name = null;
        Pattern pattern = Pattern.compile("(.*) \\(([0-9]+)\\)$");
        Matcher matcher = pattern.matcher(suggestedName);
        if (matcher.find()) {
            suggestedName = matcher.group(1);
            num = Integer.parseInt(matcher.group(2));
        }

        int i = 0;

        for (int length = book.getNumberOfSheets(); i <= length; ++i) {
            StringBuilder var10000 = (new StringBuilder()).append(suggestedName).append(" (");
            ++num;
            String n = var10000.append(num).append(")").toString();
            if (book.getSheet(n) == null) {
                name = n;
                break;
            }
        }
        return name;
    }

    private static void CopySheet(Range range, String suggestedName) {
        if (suggestedName.length()> 31){
            suggestedName = suggestedName.substring(0,31);
            if (range.getBook().getSheet(suggestedName)!=null){
                int i = 0;
                for (i=0;i<10;i++){//more than 9 - anything goes!
                    if (range.getBook().getSheet(suggestedName.substring(0,30)+ i)==null){
                        suggestedName = suggestedName.substring(0,30) + i;
                        break;
                    }
                }
            }
        }
        final String chosenName = suggestedName;
        range.sync(range1 -> {
            // this little bracketed bit is what I added to the ZK code
            if (chosenName != null && !chosenName.isEmpty()) {
                range1.cloneSheet(suggestSheetName(range1.getBook(), chosenName));
            } else {
                range1.cloneSheet(suggestSheetName(range1.getBook(), range1.getSheetName()));
            }
        });
    }

    // return the error, executing reports might want it
    private static String populateRegionSet(Sheet sheet, int reportId, final String sheetName, final String region, int valueId, UserRegionOptions userRegionOptions, LoggedInUser loggedInUser, boolean quiet, Set<String> repeatRegionTracker) throws Exception {
        CellRegion queryRegion = BookUtils.getCellRegionForSheetAndName(sheet, "az_query" + region);
        SName contextDescription = BookUtils.getNameByName(StringLiterals.AZCONTEXT + region, sheet);
        if (queryRegion != null) {
            List<List<String>> contextList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, contextDescription);
            ReportService.resolveQuery(loggedInUser, sheet, queryRegion, contextList);
        }

        if (userRegionOptions.getUserLocked()) { // then put the flag on the book, remember to take it off (and unlock!) if there was an error
            sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED, true);
        }
        SName columnHeadingsDescription = BookUtils.getNameByName(StringLiterals.AZCOLUMNHEADINGS + region, sheet);
        SName rowHeadingsDescription = BookUtils.getNameByName(StringLiterals.AZROWHEADINGS + region, sheet);
        String errorMessage = null;
        // make a blank area for data to be populated from, an upload in the sheet so to speak (ad hoc)
        if ((columnHeadingsDescription != null && rowHeadingsDescription == null) || (rowHeadingsDescription != null && columnHeadingsDescription == null)) {
            List<List<String>> colHeadings = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, columnHeadingsDescription);
            List<List<String>> rowHeadings = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, rowHeadingsDescription);
            List<List<CellForDisplay>> dataRegionCells = new ArrayList<>();
            CellRegion dataRegion = BookUtils.getCellRegionForSheetAndName(sheet, StringLiterals.AZDATAREGION + region);
            for (int rowNo = 0; rowNo < dataRegion.getRowCount(); rowNo++) {
                List<CellForDisplay> oneRow = new ArrayList<>();
                for (int colNo = 0; colNo < dataRegion.getColumnCount(); colNo++) {
                    oneRow.add(new CellForDisplay(false, "", 0, false, rowNo, colNo, true, false, null, 0)); // make these ignored. Edd note : I'm not particularly happy about this, sent data should be sent data, this is just made up . . .
                }
                dataRegionCells.add(oneRow);
            }
            // note the col headings source is going in here as is without processing as in the case of ad-hoc it is not dynamic (i.e. an Azquo query), it's import file column headings
            CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(region, colHeadings, rowHeadings, null, null, dataRegionCells, null, null, null, 0, userRegionOptions.getRegionOptionsForTransport(), null);// todo - work out what to do with the timestamp here! Might be a moot point given now row headings
            loggedInUser.setSentCells(reportId, sheetName, region, cellsAndHeadingsForDisplay);
            if (userRegionOptions.getPreSave()) {
                ReportService.checkDataChangeAndSnapCharts(loggedInUser, reportId, sheet.getBook(), sheet, false, true, false);
                // see comment on other pre save b-t - should we just pass through online report?
                SpreadsheetService.saveData(loggedInUser, reportId, loggedInUser.getOnlineReport().getReportName(), sheet.getSheetName(), region, false);
            }
            return null;
        }
        if (columnHeadingsDescription != null) {
            try {
                /* hack introduced by Edd 22/06/2017. For repeat regions I had assumed that the headings/context didn't rely on the repeat items hence I could do preparatory work here
                (making space) without the repeat times. It seems headings might alter according to the item but NOTE! if the number of headings changes due to the item this will
                 go wrong later as the code that prepares the space assumes heading numbers will stay consistent. Anyway the hack is jamming the first repeat item in so we can make
                 this getCellsAndHeadingsForDisplay call without problems if it relies on it. Could the following little chunk be factored with the coed in fillDataForRepeatRegions? Not sure.

                 Have added repeat item 2 as well
                  */
                SName repeatList = BookUtils.getNameByName(StringLiterals.AZREPEATLIST + region, sheet);
                SName repeatItem = BookUtils.getNameByName(StringLiterals.AZREPEATITEM + region, sheet);
                if (repeatList != null && repeatItem != null) {
                    String repeatListText = BookUtils.getSnameCell(repeatList).getStringValue();
                    List<String> repeatListItems = CommonReportUtils.getDropdownListForQuery(loggedInUser, repeatListText);
                    if (!repeatListItems.isEmpty()) {
                        BookUtils.getSnameCell(repeatItem).setStringValue(repeatListItems.get(0));// and set the first
                    }
                }
                SName repeatList2 = BookUtils.getNameByName(StringLiterals.AZREPEATLIST + "2" + region, sheet);
                SName repeatItem2 = BookUtils.getNameByName(StringLiterals.AZREPEATITEM + "2" + region, sheet);
                if (repeatList2 != null && repeatItem2 != null) {
                    String repeatListText2 = BookUtils.getSnameCell(repeatList2).getStringValue();
                    List<String> repeatListItems2 = CommonReportUtils.getDropdownListForQuery(loggedInUser, repeatListText2);
                    if (!repeatListItems2.isEmpty()) {
                        BookUtils.getSnameCell(repeatItem2).setStringValue(repeatListItems2.get(0));// and set the first
                    }
                }

                List<List<String>> contextList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, contextDescription);
                // can it sort out the formulae issues?
                List<List<String>> rowHeadingList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, rowHeadingsDescription);
                //check if this is a pivot - if so, then add in any additional filter needed
                SName contextFilters = BookUtils.getNameByName(StringLiterals.AZCONTEXTFILTERS, sheet);
                if (contextFilters == null) {
                    contextFilters = BookUtils.getNameByName(StringLiterals.AZPIVOTFILTERS, sheet);
                }
                // a comma separated list of names
                if (contextFilters != null) {
                    String[] filters = BookUtils.getSnameCell(contextFilters).getStringValue().split(",");
                    for (String filter : filters) {
                        filter = filter.trim();
                        try {
                            //List<String> possibleOptions = getDropdownListForQuery(loggedInUser, "`" + filter + "` children");
                            // the names selected off the pivot filter are jammed into a name with "az_" before the filter name
                            List<String> choiceOptions = CommonReportUtils.getDropdownListForQuery(loggedInUser, "`az_" + filter + "` children");
                            // This means if it's not empty and not full ignore the filter. Works as the permute function, which needs to be used with pivots,
                            // will constrain by this set, if created, falling back on the set it's passed if not. Means what's in the permute will be a subset of the filters
                            // does this mean a double check? That it's constrained by selection here in context and again by the permute? I suppose no harm.
                            //if (possibleOptions.size() != choiceOptions.size() && choiceOptions.size() > 0) {
                            if (choiceOptions.size() == 1 && choiceOptions.get(0).startsWith("Error")) {
                                //THis should create the required bits server side . . .
                                CommonReportUtils.getFilterListForQuery(loggedInUser, "`" + filter + "` children", "az_" + filter);
                                choiceOptions = CommonReportUtils.getDropdownListForQuery(loggedInUser, "`az_" + filter + "` children");

                            }
                            if (choiceOptions.size() > 0) {//conditional should now be irrelevant
                                List<String> additionalContext = new ArrayList<>();
                                additionalContext.add("az_" + filter);
                                // so add it to the context. Could perhaps be one list that's added? I suppose it doesn't really matter.
                                contextList.add(additionalContext);
                            }
                        } catch (Exception e) {
                            //ignore - no choices yet made
                            // todo, make this a bit more elegant?
                        }
                    }
                }

                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser, region, valueId, rowHeadingList,
                        BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, columnHeadingsDescription),
                        contextList, userRegionOptions, quiet);
                loggedInUser.setSentCells(reportId, sheetName, region, cellsAndHeadingsForDisplay);
                // now, put the headings into the sheet!
                // might be factored into fill range in a bit
                CellRegion displayRowHeadings = BookUtils.getCellRegionForSheetAndName(sheet, StringLiterals.AZDISPLAYROWHEADINGS + region);
                CellRegion displayColumnHeadings = BookUtils.getCellRegionForSheetAndName(sheet, StringLiterals.AZDISPLAYCOLUMNHEADINGS + region);
                CellRegion displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, StringLiterals.AZDATAREGION + region);

                int colsToAdd;
                int maxRow = sheet.getLastRow();
                int maxCol = 0;
                for (int row = 0; row <= maxRow; row++) {
                    if (maxCol < sheet.getLastColumn(row)) {
                        maxCol = sheet.getLastColumn(row);
                    }
                }

                if (displayDataRegion != null) {

                    expandDataRegionBasedOnHeadings(loggedInUser, sheet, region, displayDataRegion, cellsAndHeadingsForDisplay, maxCol, userRegionOptions);
                    // these re loadings are because the region may have changed
                    // why reload displayDataRegion but not displayRowHeadings for example? todo - check, either both need reloading or both don't - this isn't a biggy it's just to do with name references which now I think about it probably don't need reloading but it's worth checking and being consistent
                    displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, StringLiterals.AZDATAREGION + region);
                    // so it's NOT a repeat region. Fill the headings and populate the data!
                    if (BookUtils.getNameByName(StringLiterals.AZREPEATREGION + region, sheet) == null
                            || BookUtils.getNameByName(StringLiterals.AZREPEATSCOPE + region, sheet) == null
                            || BookUtils.getNameByName(StringLiterals.AZREPEATLIST + region, sheet) == null
                            || BookUtils.getNameByName(StringLiterals.AZREPEATITEM + region, sheet) == null) {
                        // ok there should be the right space for the headings
                        if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) {
                            int rowHeadingCols = cellsAndHeadingsForDisplay.getRowHeadings().get(0).size();
                            colsToAdd = rowHeadingCols - displayRowHeadings.getColumnCount();
                            if (colsToAdd > 0) {
                                int insertCol = displayRowHeadings.getColumn() + displayRowHeadings.getColumnCount() - 1;
                                Range insertRange = Ranges.range(sheet, 0, insertCol, maxRow, insertCol + colsToAdd - 1);
                                CellOperationUtil.insert(insertRange.toColumnRange(), Range.InsertShift.RIGHT, Range.InsertCopyOrigin.FORMAT_LEFT_ABOVE);
                                displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, StringLiterals.AZDATAREGION + region);
                            }
                            RegionFillerService.fillRowHeadings(loggedInUser, sheet, region, displayRowHeadings, displayDataRegion, cellsAndHeadingsForDisplay, userRegionOptions);
                        }
                        if (displayColumnHeadings != null) {
                            RegionFillerService.fillColumnHeadings(sheet, userRegionOptions, displayColumnHeadings, cellsAndHeadingsForDisplay);
                        }
                        RegionFillerService.fillData(sheet, cellsAndHeadingsForDisplay, displayDataRegion, userRegionOptions);
                        // without this multi step formulae e.g. in headings won't resolve. If this is a performance issue might need to pass through fast load.
                        // does this make later clear formulae result caches or indeed the lot redundant?? todo - investigate!
                        BookUtils.notifyChangeOnRegion(sheet, displayDataRegion);
                    } else {
                        // the more complex function that deals with repeat regions - it now notably does the headings
                        RegionFillerService.fillDataForRepeatRegions(loggedInUser, reportId, sheet, region, userRegionOptions, displayRowHeadings, displayColumnHeadings, displayDataRegion, rowHeadingsDescription, columnHeadingsDescription, contextDescription, maxRow, valueId, quiet, repeatRegionTracker);
                    }
                }
            } catch (RemoteException re) {
                // is printing the stack trace going to jam the logs unnecessarily?
                Throwable t = re.detail.getCause();
                if (t != null) {
                    errorMessage = t.getLocalizedMessage();
                    t.printStackTrace();
                } else {
                    errorMessage = re.getMessage();
                    re.printStackTrace();
                }
            } catch (Exception e) {
                errorMessage = e.getMessage();
                e.printStackTrace();
            }
            if (errorMessage != null) {
                // maybe move to the top left? Unsure - YES!
                int rowNo = 0;
                int colNo = 0;
                while (sheet.getInternalSheet().getColumn(colNo).isHidden() && colNo < 100) colNo++;
                while (sheet.getInternalSheet().getRow(rowNo).isHidden() && rowNo < 100) rowNo++;
                String eMessage = "Error populating this data region : " + StringLiterals.AZDATAREGION + region + " : " + errorMessage;
                sheet.getInternalSheet().getCell(rowNo, colNo).setStringValue(eMessage);
                /*
                CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);// this function should not be called without a valid data region
                if (dataRegion != null) {
                    sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching heading and context regions for this data region : az_DataRegion" + region + " : " + errorMessage);
                } else {
                    System.out.println("no region found for az_DataRegion" + region);
                }
                */
                return eMessage;
            }
        } else {
            CellRegion dataRegion = BookUtils.getCellRegionForSheetAndName(sheet, StringLiterals.AZDATAREGION + region);// this function should not be called without a valid data region
            if (dataRegion != null) {
                sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching heading and context regions for this data region : " + StringLiterals.AZDATAREGION + region);
                return "Unable to find matching heading and context regions for this data region : " + StringLiterals.AZDATAREGION + region;
            } else {
                System.out.println("no region found for " + StringLiterals.AZDATAREGION + region);
                return "no region found for " + StringLiterals.AZDATAREGION + region;
            }
        }
        if (userRegionOptions.getPreSave()) {
            ReportService.checkDataChangeAndSnapCharts(loggedInUser, reportId, sheet.getBook(), sheet, false, true, false);
            // I'm using the logged in user to get the report and the report name - should this be used for report id? Or pass through the report?
            SpreadsheetService.saveData(loggedInUser, reportId, loggedInUser.getOnlineReport().getReportName(), sheet.getSheetName(), region, false);
        }
        // EFC note : as described for the `temporary categorisation` but every time?
        return null; // will it get here ever?
    }

    private static void expandDataRegionBasedOnHeadings(LoggedInUser loggedInUser, Sheet sheet, String region, CellRegion displayDataRegion, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, int maxCol, UserRegionOptions userRegionOptions) {
        // add rows
        if (cellsAndHeadingsForDisplay.getRowHeadings() != null
                && (displayDataRegion.getRowCount() < cellsAndHeadingsForDisplay.getRowHeadings().size())
                && displayDataRegion.getRowCount() > 2
        && !userRegionOptions.getCsvDownload()) { // then we need to expand, and there is space to do so (3 or more allocated already)
            int rowsToAdd = cellsAndHeadingsForDisplay.getRowHeadings().size() - (displayDataRegion.getRowCount());
            int insertRow = displayDataRegion.getRow() + displayDataRegion.getRowCount() - 1; // last but one row
            Range copySource = Ranges.range(sheet, insertRow - 1, 0, insertRow - 1, maxCol);
            // now need to emulate the formatting based copying used for columns
            // most of the time rowsFormattingPatternHeight will be the same height as the row headings size but if the space is smaller need to do a first expand before we can do the tessalating copy paste
            int rowsFormattingPatternHeight = 1;
            // only look for the pattern if we're not using hide rows
            if (userRegionOptions.getHideRows() == 0) {
                rowsFormattingPatternHeight = ReportUtils.guessColumnsFormattingPatternWidth(loggedInUser, MultidimensionalListUtils.transpose2DList(cellsAndHeadingsForDisplay.getRowHeadingsSource()));
                if (rowsFormattingPatternHeight % rowsToAdd != 0) {//if the pattern cannot be repeated exactly, assume normal operation
                    rowsFormattingPatternHeight = 1;
                }
            }
            // make it up to a multiple of the pattern if it isn't
            if (rowsFormattingPatternHeight > 1 && rowsFormattingPatternHeight < cellsAndHeadingsForDisplay.getRowHeadings().size()) {
                int copyCount = cellsAndHeadingsForDisplay.getRowHeadings().size() / rowsFormattingPatternHeight;
                if (rowsFormattingPatternHeight > displayDataRegion.getRowCount()) {
                    rowsToAdd = rowsFormattingPatternHeight - displayDataRegion.getRowCount();
                    Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1, maxCol); // insert just before the last col
                    CellOperationUtil.insertRow(insertRange);
                    // will this paste the lot?
                    CellOperationUtil.paste(copySource, insertRange);
                    insertRow += rowsToAdd;
                    rowsToAdd = rowsFormattingPatternHeight * (copyCount - 1);
                }
            }
            // now the insert as normal
            //found a bug in ZK - lines to fudge it
            Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1, maxCol); // insert at the 3rd row - should be rows to add - 1 as it starts at one without adding anything
//            if (rowsToAdd < 5000) {// ZK siesed when 16000 rows were added to a spreadsheet with 200 columns
            CellOperationUtil.insertRow(insertRange);

//            CellOperationUtil.insert(insertRange, Range.InsertShift.DOWN, Range.InsertCopyOrigin.FORMAT_NONE); // don't copy any formatting . . . a problem with hidden rows!
            // this is hacky, the bulk insert above will have pushed the bottom row down and in many cases we want it back where it was for teh pattern to be pasted properly
            if (rowsFormattingPatternHeight > 1) {
                //cut back the last column to it's original position, and shift the insert range one column to the right
                CellOperationUtil.cut(Ranges.range(sheet, insertRow + rowsToAdd, 0, insertRow + rowsToAdd, maxCol)
                        , Ranges.range(sheet, insertRow, 0, insertRow, maxCol));
                // so the next row after the first section to copy until the end bit
                insertRange = Ranges.range(sheet, insertRow + 1, 0, insertRow + rowsToAdd, maxCol);
                copySource = Ranges.range(sheet, displayDataRegion.getRow(), 0, displayDataRegion.getRow() + rowsFormattingPatternHeight - 1, maxCol);// should be the section representing the pattern we want to copy (with the last row restored to where it was)
            }
//            CellOperationUtil.pasteSpecial(copySource, insertRange, Range.PasteType.FORMULAS, Range.PasteOperation.NONE, false, false);
            CellOperationUtil.paste(copySource, insertRange);
            int originalHeight = sheet.getInternalSheet().getRow(insertRow - 1).getHeight();
            if (originalHeight != sheet.getInternalSheet().getRow(insertRow).getHeight()) { // height may not match on insert
                insertRange.setRowHeight(originalHeight); // hopefully set the lot in one go??
            }
            boolean hidden = sheet.getInternalSheet().getRow(insertRow - 1).isHidden();
            if (hidden) {
                for (int row = insertRange.getRow(); row <= insertRange.getLastRow(); row++) {
                    sheet.getInternalSheet().getRow(row).setHidden(true);
                }
            }
            //}
        }
        // add columns
        int maxRow = sheet.getLastRow();
        int colsToShow = cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size();
        if (displayDataRegion.getColumnCount() < colsToShow && displayDataRegion.getColumnCount() > 1 && displayDataRegion.getColumnCount() < colsToShow) { // then we need to expand
            int colsToAdd = cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() - (displayDataRegion.getColumnCount());
            int topRow = 0;
            CellRegion displayColumnHeadings = BookUtils.getCellRegionForSheetAndName(sheet, StringLiterals.AZDISPLAYCOLUMNHEADINGS + region);
            if (displayColumnHeadings != null) {
                topRow = displayColumnHeadings.getRow();
            }
            int insertCol = displayDataRegion.getColumn() + displayDataRegion.getColumnCount() - 1; // I think this is correct, just after the second column?
            Range copySource = Ranges.range(sheet, topRow, insertCol - 1, maxRow, insertCol - 1);
            // most of the time columnsFormattingPatternWidth will be the same as the column headings size but if it's not it might be used
            int columnsFormattingPatternWidth = ReportUtils.guessColumnsFormattingPatternWidth(loggedInUser, cellsAndHeadingsForDisplay.getColHeadingsSource());
            if (columnsFormattingPatternWidth > 1 && columnsFormattingPatternWidth < cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size()) {//the column headings have been expanded because the top left element is a set.  Check for secondary expansion, then copy the whole region
                int copyCount = cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() / columnsFormattingPatternWidth;
                if (columnsFormattingPatternWidth > displayDataRegion.getColumnCount()) {
                    colsToAdd = columnsFormattingPatternWidth - displayDataRegion.getColumnCount();
                    Range insertRange = Ranges.range(sheet, topRow, insertCol, maxRow, insertCol + colsToAdd - 1); // insert just before the last col
                    CellOperationUtil.insertColumn(insertRange);
                    // will this paste the lot?
                    CellOperationUtil.paste(copySource, insertRange);
                    insertCol += colsToAdd;
                    colsToAdd = columnsFormattingPatternWidth * (copyCount - 1);
                }
            }
            Range insertRange = Ranges.range(sheet, topRow, insertCol, maxRow, insertCol + colsToAdd - 1); // insert just before the last col, except for permuted headings
            CellOperationUtil.insertColumn(insertRange);
            if (columnsFormattingPatternWidth > 1 && columnsFormattingPatternWidth < colsToShow) {
                //cut back the last column to it's original position, and shift the insert range one column to the right
                CellOperationUtil.cut(Ranges.range(sheet, topRow, insertCol + colsToAdd, maxRow, insertCol + colsToAdd), Ranges.range(sheet, topRow, insertCol, maxRow, insertCol));
                insertRange = Ranges.range(sheet, topRow, insertCol + 1, maxRow, insertCol + colsToAdd);
                copySource = Ranges.range(sheet, topRow, displayDataRegion.getColumn(), maxRow, displayDataRegion.getColumn() + columnsFormattingPatternWidth - 1);
            }
            // will this paste the lot?
            Range paste = CellOperationUtil.paste(copySource, insertRange);

            if (columnsFormattingPatternWidth > 1){
                //..needs to be done twice - not sure why.
                paste = CellOperationUtil.pasteSpecial(copySource, insertRange, Range.PasteType.COLUMN_WIDTHS,Range.PasteOperation.NONE, false,false);
                insertRange = Ranges.range(sheet,topRow, displayDataRegion.getColumn() + columnsFormattingPatternWidth, maxRow, displayDataRegion.getColumn()+ 2 * columnsFormattingPatternWidth-1);
                paste = CellOperationUtil.pasteSpecial(copySource, insertRange,Range.PasteType.COLUMN_WIDTHS, Range.PasteOperation.NONE,false,false);
            }
            int originalWidth = sheet.getInternalSheet().getColumn(insertCol - 1).getWidth();
            if (originalWidth != sheet.getInternalSheet().getColumn(insertCol).getWidth()) { // height may not match on insert
                insertRange.setColumnWidth(originalWidth); // hopefully set the lot in one go??
            }
            boolean hidden = sheet.getInternalSheet().getColumn(insertCol - 1).isHidden();
            if (hidden) {
                for (int col = insertRange.getColumn(); col <= insertRange.getLastColumn(); col++) {
                    sheet.getInternalSheet().getColumn(col).setHidden(true);
                }
            }
        }
    }

    private static void loadExternalData(Book book, LoggedInUser loggedInUser)throws Exception{
        for (SName name:book.getInternalBook().getNames()){
            int nameRow = -1;
            int connectorRow =  -1;
            int sqlRow = -1;
            if (name.getName().toLowerCase(Locale.ROOT).startsWith(StringLiterals.AZIMPORTDATA)){
                List<List<String>> importdataspec = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, name);
                int cols = importdataspec.get(0).size();
                int rows = importdataspec.size();
                for (int rowNo = 0;rowNo < rows;rowNo++){
                    String heading = importdataspec.get(rowNo).get(0).toLowerCase(Locale.ROOT);
                    if (heading.equals("sheet/range name")) nameRow = rowNo;
                    if (heading.equals("connector")) connectorRow = rowNo;
                    if (heading.equals("sql")) sqlRow = rowNo;
                }
                if (nameRow < 0 || connectorRow < 0 || sqlRow < 0){
                    return;
                }
                for (int col=1;col<cols;col++) {
                    String rangeName = importdataspec.get(nameRow).get(col).toLowerCase(Locale.ROOT);
                    if (rangeName.length() == 0){
                        break;
                    }
                    String connectorName = importdataspec.get(connectorRow).get(col).toLowerCase(Locale.ROOT);
                    String sqlName = importdataspec.get(sqlRow).get(col);
                    boolean found = false;
                    Sheet sheet = null;
                    int startRow = 0;
                    int startCol = 0;
                    int rowCount = 0;
                    int colCount = 0;
                    for (int i=0;i<book.getNumberOfSheets();i++){
                        sheet = book.getSheetAt(i);
                        if (sheet.getSheetName().toLowerCase(Locale.ROOT).equals(rangeName)){
                            found = true;
                            break;
                        }
                    }
                    if (!found){
                        sheet = null;
                        SName sourceName = book.getInternalBook().getNameByName(rangeName);
                        if (sourceName !=null){
                              sheet = book.getSheet(sourceName.getRefersToSheetName());
                              startRow = sourceName.getRefersToCellRegion().getRow();
                              startCol = sourceName.getRefersToCellRegion().getColumn();
                              rowCount = sourceName.getRefersToCellRegion().getRowCount();
                              colCount = sourceName.getRefersToCellRegion().getCellCount();
                              return;
                        }
                    }

                    List<List<String>> data =getExternalData(loggedInUser,rangeName,connectorName,sqlName);
                    if (data==null || data.size()==0){
                        return;
                    }
                    if(rowCount==0 || rowCount > data.size()){
                        rowCount = data.size();
                    }
                    if (colCount == 0 || colCount > data.get(0).size()){
                        colCount =data.get(0).size();
                    }
                    for (int rowNo = 0; rowNo < rowCount; rowNo++){
                        List<String> dataline = data.get(rowNo);
                        for (int colNo = 0;colNo < colCount; colNo++){;
                              sheet.getInternalSheet().getCell(rowNo + startRow, colNo + startCol).setStringValue(dataline.get(colNo));

                        }
                    }
                 }
            }


        }

    }

   private static List<List<String>> getExternalData(LoggedInUser loggedInUser, String rangeName, String connectorName, String sql)throws Exception {
       List<List<String>> toReturn = new ArrayList<>();
       if (connectorName.length() == 0) {
           char delimiter = ',';
           ImportTemplate it = ImportTemplateDAO.findForNameAndBusinessId(rangeName, loggedInUser.getBusiness().getId());
           if (it == null) {
               return null;
           }
           String path = SpreadsheetService.getHomeDir() + dbPath + loggedInUser.getBusinessDirectory() + importTemplatesDir + it.getFilenameForDisk();
           try (BufferedReader br = Files.newBufferedReader(Paths.get(path), StandardCharsets.ISO_8859_1)) { // iso shouldn't error while UTF8 can . . .
               // grab the first line to check on delimiters
               String firstLine = br.readLine();
               if (firstLine == null || firstLine.length() == 0) {
                   br.close();
                   throw new Exception(it.getFilename() + ": Unable to read any data (perhaps due to an empty file)");
               }
               if (firstLine.contains("|")) {
                   delimiter = '|';
               }
               if (firstLine.contains("\t")) {
                   delimiter = '\t';
               }
               br.close();
               CsvMapper csvMapper = new CsvMapper();
               csvMapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
               CsvSchema schema = csvMapper.schemaFor(String[].class)
                       .withColumnSeparator(delimiter)
                       .withLineSeparator("\n");
               if (delimiter == '\t') {
                   schema = schema.withoutQuoteChar();
               }
               MappingIterator<String[]> lineIterator = (csvMapper.readerFor(String[].class).with(schema).readValues(new File(path)));
               while (lineIterator.hasNext()){
                   toReturn.add(new ArrayList<>(Arrays.asList(lineIterator.next())));

               }
           } catch (Exception e) {
               throw new Exception(it.getFilename() + ": Unable to read " + it.getFilename());
           }
       }else{
           return ExternalConnector.getData(connectorName, sql);
       }
       return toReturn;
   }


        /*  THIS MAY BE USEFUL FOR CREATING 'EXCEL STYLE' RANGES  (e.g. $AB$99)

    private static String numberColToStringCol(int col) {
        if (col < 26) return Character.toString((char)(65 + col));
        int left = col / 26;
        int right = col - left * 26;
        return Character.toString((char)(64 + left)) + Character.toString((char)(65 + right));
    }

    private static String numberRangeToStringRange(int topRow, int bottomRow, int leftCol, int rightCol) {
        return "$" + numberColToStringCol(leftCol) + "$" + (topRow + 1) + ":$" + numberColToStringCol(rightCol) + "$" + (bottomRow + 1);
    }
    */
}