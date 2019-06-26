package com.azquo.spreadsheet.zk;

import com.azquo.MultidimensionalListUtils;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.*;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import org.springframework.security.access.method.P;
import org.zkoss.zss.api.*;
import org.zkoss.zss.api.model.*;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.*;

import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 03/03/15.
 * <p>
 * To render the report in ZK, the online excel view
 */
public class ReportRenderer {

    // all case insensetive now so make these lower case and make the names from the reports .toLowerCase().startsWith().
    public static final String AZDATAREGION = "az_dataregion";
    public static final String AZLISTSTART = "az_ListStart";
    static final String AZDISPLAY = "az_display";
    static final String AZDRILLDOWN = "az_drilldown";
    static final String AZOPTIONS = "az_options";
    public static final String AZREPEATREGION = "az_repeatregion";
    public static final String AZREPEATSCOPE = "az_repeatscope";
    static final String AZREPEATITEM = "az_repeatitem";
    static final String AZREPEATLIST = "az_repeatlist";
    public static final String AZDISPLAYROWHEADINGS = "az_displayrowheadings";
    static final String AZDISPLAYCOLUMNHEADINGS = "az_displaycolumnheadings";
    public static final String AZCOLUMNHEADINGS = "az_columnheadings";
    public static final String AZROWHEADINGS = "az_rowheadings";
    static final String AZXML = "az_xml";
    static final String AZXMLEXTRAINFO = "az_xmlextrainfo";
    static final String AZXMLFILENAME = "az_xmlfilename";
    static final String AZSUPPORTREPORTNAME = "az_supportreportname";
    static final String AZSUPPORTREPORTFILEXMLTAG = "az_supportreportfilexmltag";
    static final String AZSUPPORTREPORTSELECTIONS = "az_supportreportselections";
    private static final String AZCONTEXT = "az_context";
    static final String AZPIVOTFILTERS = "az_pivotfilters";//old version - not to be continued
    static final String AZCONTEXTFILTERS = "az_contextfilters";
    static final String AZCONTEXTHEADINGS = "az_contextheadings";
    static final String AZPIVOTHEADINGS = "az_pivotheadings";//old version
    public static final String AZREPORTNAME = "az_reportname";
    public static final String AZIMPORTNAME = "az_importname";
    public static final String EXECUTE = "az_execute";
    public static final String PREEXECUTE = "az_preexecute";
    static final String FOLLOWON = "az_followon";
    static final String AZSAVE = "az_save";
    static final String AZREPEATSHEET = "az_repeatsheet";
    public static final String AZPDF = "az_pdf";
    static final String AZTOTALFORMAT = "az_totalformat";
    private static final String AZFASTLOAD = "az_fastload";
    static final String AZEMAILADDRESS = "az_emailaddress";
    static final String AZEMAILSUBJECT = "az_emailsubject";
    static final String AZEMAILTEXT = "az_emailtext";
    private static final String AZCURRENTUSER = "az_currentuser";
    // on an upload file, should this file be flagged as one that moves with backups and is available for non admin users to download
    public static final String AZFILETYPE = "az_filetype";


    public static boolean populateBook(Book book, int valueId) {
        return populateBook(book, valueId, false, false, null, true);
    }

    public static void populateBook(Book book, int valueId, boolean useRepeats) {
        populateBook(book, valueId, false, false, null, useRepeats);
    }


    public static boolean populateBook(Book book, int valueId, boolean useSavedValuesOnFormulae, boolean executeMode, StringBuilder errors) { // todo - make more elegant? error hack . . .
        return populateBook(book, valueId, useSavedValuesOnFormulae, executeMode, errors, true);
    }

    // todo - is it possible to extract some of this to a pre importer? Can't put it all in there . . .
    private static boolean populateBook(Book book, int valueId, boolean useSavedValuesOnFormulae, boolean executeMode, StringBuilder errors, boolean useRepeats) { // todo - make more elegant? error hack . . .

        BookUtils.removeNamesWithNoRegion(book); // should protect against some errors.


        book.getInternalBook().setAttribute(OnlineController.LOCKED, false); // by default
        long track = System.currentTimeMillis();
        String imageStoreName = "";
        boolean showSave = false;
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);

        // unlock data on every report load. Maybe make this clear to the user?
        // is the exception a concern here?
        // also clear temporary names?
        try {
            //RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearTemporaryNames(loggedInUser.getDataAccessToken());
            SpreadsheetService.unlockData(loggedInUser);
        } catch (Exception e) {
            e.printStackTrace();
        }
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        loggedInUser.setOnlineReport(OnlineReportDAO.findById(reportId));
        Map<Sheet, String> sheetsToRename = new HashMap<>(); // the repeat sheet can require renaming the first "template" sheet but this seems to trip up ZK so do it at the end after all the expanding etc
        //String context = "";
        // why a sheet loop at the outside, why not just run all the names? Need to have a think . . .
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            // I'd like to put this somewhere else but for the moment it must be per sheet to lessen the chances of overlapping repeat regions interfering with each other

            Set<String> repeatRegionTracker = new HashSet<>();
            Sheet sheet = book.getSheetAt(sheetNumber);

            SName az_CurrentUser = BookUtils.getNameByName(AZCURRENTUSER, sheet);
            if (az_CurrentUser != null){
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
            if (!loggedInUser.getUser().isDeveloper() && !loggedInUser.getUser().isAdministrator()){
                ReportService.checkForPermissionsInSheet(loggedInUser, sheet);
            }
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

            SName az_repeatSheet = BookUtils.getNameByName(AZREPEATSHEET, sheet);
            SName az_repeatItem = BookUtils.getNameByName(AZREPEATITEM, sheet);
            // second name match is if the repeat item and repeat sheet names are set global, stop them being found on subsequent runs
            if (useRepeats && az_repeatSheet != null && az_repeatSheet.getRefersToSheetName().equals(sheet.getSheetName())
                    && az_repeatItem != null && az_repeatItem.getRefersToSheetName().equals(sheet.getSheetName())) {
                SCell snameCell = BookUtils.getSnameCell(az_repeatSheet);
                if (snameCell != null && snameCell.getStringValue() != null && !snameCell.getStringValue().isEmpty()) {
                    List<String> repeatItems = CommonReportUtils.getDropdownListForQuery(loggedInUser, snameCell.getStringValue());
                    String firstItem = null; // for api purposes I need to set the initial sheet name after it's copied or I get NPEs
                    int sheetPosition = 0;
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
                                if (!name.getName().equalsIgnoreCase(AZREPEATSHEET)) { // don't copy the repeat or we'll get a recursive loop!
                                    // cloneSheet won't copy the names, need to make new ones
                                    // the new ones need to be applies to as well as refers to the new sheet
                                    SName newName = book.getInternalBook().createName(name.getName(), newSheet.getSheetName());
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
                                }
                            }
                            SName newRepeatItem = BookUtils.getNameByName(AZREPEATITEM, newSheet);
                            SCell repeatItemCell = BookUtils.getSnameCell(newRepeatItem);
                            repeatItemCell.setStringValue(repeatItem);
                            // now need to move it and rename - hopefully references e.g. names will be affected correctly?
                            book.getInternalBook().moveSheetTo(newSheet.getInternalSheet(), book.getSheetIndex(sheet) + sheetPosition);
                        }
                        sheetPosition++;
                    }
                    if (firstItem==null){
                        firstItem = sheet.getSheetName();//the repeat list is void
                    }
                    sheetsToRename.put(sheet, firstItem);
                    //need to rename current sheet now before data is loaded
                    //book.getInternalBook().setSheetName(sheet.getInternalSheet(), suggestSheetName(book, firstItem));


                    choiceOptionsMap = ChoicesService.resolveAndSetChoiceOptions(loggedInUser, sheet, regionsToWatchForMerge);
                    ReportService.resolveQueries(sheet.getBook(), loggedInUser); // after all options sorted should be ok
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
            for (SName name : namesForSheet) {
                // Old one was case insensitive - not so happy about this. Will allow it on prefixes. (fast load being set outside the loop so is there a problem with it not being found before data regions??)
                if (name.getName().equalsIgnoreCase(ReportRenderer.AZFASTLOAD)) {
                    fastLoad = true;
                }
                if (name.getName().equals("az_ImageStoreName")) {
                    imageStoreName = BookUtils.getRegionValue(sheet, name.getRefersToCellRegion());
                }
            }
            for (SName name : namesForSheet) {
                if (name.getName().toLowerCase().startsWith(AZDATAREGION)) { // then we have a data region to deal with here
                    String region = name.getName().substring(AZDATAREGION.length()); // might well be an empty string
                    if (BookUtils.getNameByName(AZROWHEADINGS + region, sheet) == null) { // if no row headings then this is an ad hoc region, save possible by default
                        showSave = true;
                    }

                    // we don't actually need to do anything with this now but we need to switch on the XML button
                    SName xmlHeadings = BookUtils.getNameByName(AZXML + region, sheet);
                    if (xmlHeadings != null){
                        sheet.getBook().getInternalBook().setAttribute(OnlineController.XMLZIP, true);
                        if (SpreadsheetService.getXMLDestinationDir() != null && !SpreadsheetService.getXMLDestinationDir().isEmpty()){
                            sheet.getBook().getInternalBook().setAttribute(OnlineController.XML, true);
                        }
                    }

                    SName optionsRegion = BookUtils.getNameByName(AZOPTIONS + region, sheet);
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
                sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED_RESULT, ReportUtils.interpretLockWarnings(lockWarnings.toString()));
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
            // returns true if data changed by formulae
            if (ReportService.checkDataChangeAndSnapCharts(loggedInUser, reportId, book, sheet, fastLoad, useSavedValuesOnFormulae)) {
                showSave = true;
            }
            // now remerge
            for (CellRegion merge : mergesToTemporarilyRemove) {
                // the boolean meant JUST horizontally, I don't know why. Hence false.
                CellOperationUtil.merge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()), false);
            }
            List<SName> dependentRanges = ChoicesService.addValidation(sheet, loggedInUser, book, choiceOptionsMap);
            if (dependentRanges.size() > 0) {
                ChoicesService.resolveDependentChoiceOptions(sheet.getSheetName().replace(" ", ""), dependentRanges, book, loggedInUser);
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
        range.sync(range1 -> {
            // this little bracketed bit is what I added to the ZK code
            if (suggestedName != null && !suggestedName.isEmpty()) {
                range1.cloneSheet(suggestSheetName(range1.getBook(), suggestedName));
            } else {
                range1.cloneSheet(suggestSheetName(range1.getBook(), range1.getSheetName()));
            }
        });
    }

    // return the error, executing reports might want it
    private static String populateRegionSet(Sheet sheet, int reportId, final String sheetName, final String region, int valueId, UserRegionOptions userRegionOptions, LoggedInUser loggedInUser, boolean quiet, Set<String> repeatRegionTracker) {
        CellRegion queryRegion = BookUtils.getCellRegionForSheetAndName(sheet,"az_query"+region);
        SName contextDescription = BookUtils.getNameByName(AZCONTEXT + region, sheet);
        if (queryRegion!=null){
            List<List<String>> contextList = replaceUserChoicesInRegionDefinition(loggedInUser,contextDescription);

            ReportService.resolveQuery(loggedInUser,sheet,queryRegion, contextList);
        }

        if (userRegionOptions.getUserLocked()) { // then put the flag on the book, remember to take it off (and unlock!) if there was an error
            sheet.getBook().getInternalBook().setAttribute(OnlineController.LOCKED, true);
        }
        SName columnHeadingsDescription = BookUtils.getNameByName(AZCOLUMNHEADINGS + region, sheet);
        SName rowHeadingsDescription = BookUtils.getNameByName(AZROWHEADINGS + region, sheet);
        String errorMessage = null;
        // make a blank area for data to be populated from, an upload in the sheet so to speak (ad hoc)
        if ((columnHeadingsDescription != null && rowHeadingsDescription == null)||(rowHeadingsDescription !=null && columnHeadingsDescription==null)) {
            List<List<String>> colHeadings = replaceUserChoicesInRegionDefinition(loggedInUser,columnHeadingsDescription);
            List<List<String>> rowHeadings= replaceUserChoicesInRegionDefinition(loggedInUser,rowHeadingsDescription);
            List<List<CellForDisplay>> dataRegionCells = new ArrayList<>();
            CellRegion dataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
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
                SName repeatList = BookUtils.getNameByName(ReportRenderer.AZREPEATLIST + region, sheet);
                SName repeatItem = BookUtils.getNameByName(ReportRenderer.AZREPEATITEM + region, sheet);
                if (repeatList != null && repeatItem != null) {
                    String repeatListText = BookUtils.getSnameCell(repeatList).getStringValue();
                    List<String> repeatListItems = CommonReportUtils.getDropdownListForQuery(loggedInUser, repeatListText);
                    if (!repeatListItems.isEmpty()) {
                        BookUtils.getSnameCell(repeatItem).setStringValue(repeatListItems.get(0));// and set the first
                    }
                }
                SName repeatList2 = BookUtils.getNameByName(ReportRenderer.AZREPEATLIST + "2" + region, sheet);
                SName repeatItem2 = BookUtils.getNameByName(ReportRenderer.AZREPEATITEM + "2" + region, sheet);
                if (repeatList2 != null && repeatItem2 != null) {
                    String repeatListText2 = BookUtils.getSnameCell(repeatList2).getStringValue();
                    List<String> repeatListItems2 = CommonReportUtils.getDropdownListForQuery(loggedInUser, repeatListText2);
                    if (!repeatListItems2.isEmpty()) {
                        BookUtils.getSnameCell(repeatItem2).setStringValue(repeatListItems2.get(0));// and set the first
                    }
                }

                List<List<String>> contextList = replaceUserChoicesInRegionDefinition(loggedInUser,contextDescription);
                // can it sort out the formulae issues?
                List<List<String>> rowHeadingList = replaceUserChoicesInRegionDefinition(loggedInUser,rowHeadingsDescription);
                //check if this is a pivot - if so, then add in any additional filter needed
                SName contextFilters = BookUtils.getNameByName(AZCONTEXTFILTERS, sheet);
                if (contextFilters == null) {
                    contextFilters = BookUtils.getNameByName(AZPIVOTFILTERS, sheet);
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
                                RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                        .getFilterListForQuery(loggedInUser.getDataAccessToken(), "`" + filter + "` children", "az_" + filter, loggedInUser.getUser().getEmail());
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

                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser, region, valueId, rowHeadingList, replaceUserChoicesInRegionDefinition(loggedInUser,columnHeadingsDescription),
                        contextList, userRegionOptions, quiet,null);
                loggedInUser.setSentCells(reportId, sheetName, region, cellsAndHeadingsForDisplay);
                // now, put the headings into the sheet!
                // might be factored into fill range in a bit
                CellRegion displayRowHeadings = BookUtils.getCellRegionForSheetAndName(sheet, AZDISPLAYROWHEADINGS + region);
                CellRegion displayColumnHeadings = BookUtils.getCellRegionForSheetAndName(sheet, AZDISPLAYCOLUMNHEADINGS + region);
                CellRegion displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);

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
                    displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);
                    // so it's NOT a repeat region. Fill the headings and populate the data!
                    if (BookUtils.getNameByName(ReportRenderer.AZREPEATREGION + region, sheet) == null
                            || BookUtils.getNameByName(ReportRenderer.AZREPEATSCOPE + region, sheet) == null
                            || BookUtils.getNameByName(ReportRenderer.AZREPEATLIST + region, sheet) == null
                            || BookUtils.getNameByName(ReportRenderer.AZREPEATITEM + region, sheet) == null) {
                        // ok there should be the right space for the headings
                        if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) {
                            int rowHeadingCols = cellsAndHeadingsForDisplay.getRowHeadings().get(0).size();
                            colsToAdd = rowHeadingCols - displayRowHeadings.getColumnCount();
                            if (colsToAdd > 0) {
                                int insertCol = displayRowHeadings.getColumn() + displayRowHeadings.getColumnCount() - 1;
                                Range insertRange = Ranges.range(sheet, 0, insertCol, maxRow, insertCol + colsToAdd - 1);
                                CellOperationUtil.insert(insertRange.toColumnRange(), Range.InsertShift.RIGHT, Range.InsertCopyOrigin.FORMAT_LEFT_ABOVE);
                                displayDataRegion = BookUtils.getCellRegionForSheetAndName(sheet, ReportRenderer.AZDATAREGION + region);
                            }
                            RegionFillerService.fillRowHeadings(loggedInUser, sheet, region, displayRowHeadings, displayDataRegion, cellsAndHeadingsForDisplay, userRegionOptions);
                        }
                        if (displayColumnHeadings != null) {
                            RegionFillerService.fillColumnHeadings(sheet, userRegionOptions, displayColumnHeadings, cellsAndHeadingsForDisplay);
                        }
                        RegionFillerService.fillData(sheet, cellsAndHeadingsForDisplay, displayDataRegion);
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
                String eMessage = "Error populating this data region : " + AZDATAREGION + region + " : " + errorMessage;
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
            CellRegion dataRegion = BookUtils.getCellRegionForSheetAndName(sheet, AZDATAREGION + region);// this function should not be called without a valid data region
            if (dataRegion != null) {
                sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching heading and context regions for this data region : " + AZDATAREGION + region);
                return "Unable to find matching heading and context regions for this data region : " + AZDATAREGION + region;
            } else {
                System.out.println("no region found for " + AZDATAREGION + region);
                return "no region found for " + AZDATAREGION + region;
            }
        }
        return null; // will it get here ever?
    }

    private static void expandDataRegionBasedOnHeadings(LoggedInUser loggedInUser, Sheet sheet, String region, CellRegion displayDataRegion, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, int maxCol, UserRegionOptions userRegionOptions) {
        // add rows
        if (cellsAndHeadingsForDisplay.getRowHeadings() != null && (displayDataRegion.getRowCount() < cellsAndHeadingsForDisplay.getRowHeadings().size()) && displayDataRegion.getRowCount() > 2) { // then we need to expand, and there is space to do so (3 or more allocated already)
            int rowsToAdd = cellsAndHeadingsForDisplay.getRowHeadings().size() - (displayDataRegion.getRowCount());
            int insertRow = displayDataRegion.getRow() + displayDataRegion.getRowCount() - 1; // last but one row
            Range copySource = Ranges.range(sheet, insertRow - 1, 0, insertRow - 1, maxCol);
            // now need to emulate the formatting based copying used for columns
            // most of the time rowsFormattingPatternHeight will be the same height as the row headings size but if the space is smaller need to do a first expand before we can do the tessalating copy paste
            int rowsFormattingPatternHeight = 1;
            // only look for the pattern if we're not using hide rows
            if (userRegionOptions.getHideRows() == 0){
                rowsFormattingPatternHeight = ReportUtils.guessColumnsFormattingPatternWidth(loggedInUser, MultidimensionalListUtils.transpose2DList(cellsAndHeadingsForDisplay.getRowHeadingsSource()));
                if (rowsToAdd < rowsFormattingPatternHeight){
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
            CellRegion displayColumnHeadings = BookUtils.getCellRegionForSheetAndName(sheet, AZDISPLAYCOLUMNHEADINGS + region);
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
            CellOperationUtil.paste(copySource, insertRange);
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

    private static List<List<String>> replaceUserChoicesInRegionDefinition(LoggedInUser loggedInUser, SName rangeName){
        List<List<String>> region = BookUtils.nameToStringLists(rangeName);
        for (int row=0;row < region.size();row++){
            for(int col=0;col < region.get(row).size();col++){
                region.get(row).set(col, CommonReportUtils.replaceUserChoicesInQuery(loggedInUser,region.get(row).get(col)));
            }
        }
        return region;
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