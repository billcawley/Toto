package com.azquo.spreadsheet.view;

import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserChoice;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import org.apache.commons.lang.math.NumberUtils;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.*;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.*;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by cawley on 03/03/15.
 * TO manipulate the ZK book, practically speaking a lot of what's in here might be functionally similar to what is in AzquoBook
 */
public class ZKAzquoBookUtils {

    public static final String azDataRegion = "az_DataRegion";
    public static final String azOptions = "az_Options";

    final SpreadsheetService spreadsheetService;
    final UserChoiceDAO userChoiceDAO;
    final UserRegionOptionsDAO userRegionOptionsDAO;
    final RMIClient rmiClient;
    final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

    public ZKAzquoBookUtils(SpreadsheetService spreadsheetService, UserChoiceDAO userChoiceDAO, UserRegionOptionsDAO userRegionOptionsDAO, RMIClient rmiClient) {
        this.spreadsheetService = spreadsheetService;
        this.userChoiceDAO = userChoiceDAO;
        this.userRegionOptionsDAO = userRegionOptionsDAO;
        this.rmiClient = rmiClient;
    }

    public boolean populateBook(Book book) {
        return populateBook(book, false);
    }
    // kind of like azquo book prepare sheet, load data bits, will aim to replicate the basics from there

    public boolean populateBook(Book book, boolean useSavedValuesOnFormulae) {
        long track = System.currentTimeMillis();
        boolean showSave = false;
        //book.getInternalBook().getAttribute(ZKAzquoBookProvider.BOOK_PATH);
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);

        Map<String, String> userChoices = new HashMap<>();
        // get the user choices for the report. Can be drop down values, sorting/highlighting etc.
        // a notable point here is that the user choices don't distinguish between sheets
        List<UserChoice> allChoices = userChoiceDAO.findForUserId(loggedInUser.getUser().getId());
        for (UserChoice uc : allChoices) {
            userChoices.put(uc.getChoiceName(), uc.getChoiceValue());
        }
        String context = "";

        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet sheet = book.getSheetAt(sheetNumber);
            /* commenting locked for the mo
            // run through every cell unlocking to I can later lock. Setting locking on a large selection seems to zap formatting
            for (int i = 0; i <= sheet.getLastRow(); i++) {
                for (int j = 0; j < sheet.getLastColumn(i); j++){
                    Range selection =  Ranges.range(sheet, i,j);
                    CellStyle oldStyle = selection.getCellStyle();
                    EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                    newStyle.setLocked(false);
                    selection.setCellStyle(newStyle);
                }
            }*/

            // see if we can impose the user choices on the sheet
            for (String choiceName : userChoices.keySet()) {
                CellRegion choice = getCellRegionForSheetAndName(sheet, choiceName + "Chosen");
                if (choice != null && choice.getRowCount() == 1) {
                    String userChoice = userChoices.get(choiceName);
                    sheet.getInternalSheet().getCell(choice.getRow(), choice.getColumn()).setStringValue(userChoice);
                    context += choiceName + " = " + userChoices.get(choiceName) + ";";
                }
            }
            setDefaultChoices(loggedInUser, sheet, userChoices);//overrides any other choice
            /* TODO ok, after mulling the issue of selecting the first on each list automatically I think here is the place to do it
            If I don't do it here it will have to be after like UI clicks and cause the sheet to reload, here the strategy should be I think to see which choices are not set
            and to try to get the first one and set it. Depending on the structure this may take a few passes but it shouldn't be that heavy. Later.
             */

            // ok the plan here is remove all the merges then put them back in after the regions are expanded.
            List<CellRegion> merges = new ArrayList<>(sheet.getInternalSheet().getMergedRegions());
            for (CellRegion merge : merges) {
                CellOperationUtil.unmerge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()));
            }
            // and now we want to run through all regions for this sheet, will look at the old code for this, I think it's fill region that we take cues from
            // names are per book, not sheet. Perhaps we could make names the big outside loop but for the moment I'll go by sheet - convenience function
            List<SName> namesForSheet = getNamesForSheet(sheet);
            // we must resolve the options here before filling the ranges as they might feature "as" name populating queries
            Map<String, List<String>> choiceOptions = resolveChoiceOptionsAndQueries(namesForSheet, sheet, loggedInUser);
            boolean fastLoad = false; // skip some checks, initially related to saving
            for (SName name : namesForSheet) {
                // Old one was case insensitive - not so happy about this. Will allow it on the prefix
                if (name.getName().equalsIgnoreCase("az_FastLoad")) {
                    fastLoad = true;
                }
                if (name.getName().startsWith(azDataRegion)) { // then we have a data region to deal with here
                    String region = name.getName().substring(azDataRegion.length()); // might well be an empty string
                    UserRegionOptions userRegionOptions = null;
                    CellRegion optionsRegion = getCellRegionForSheetAndName(sheet, azOptions + region);
                    String source = "";
                    if (optionsRegion != null) {
                        source = sheet.getInternalSheet().getCell(optionsRegion.getRow(), optionsRegion.getColumn()).getStringValue();
                    }
                    userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, source);


                    UserRegionOptions userRegionOptions2 = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                    //only these five fields to be taken from the table.   other fields - hide_rows, sortable, row_limit, column_limit should be removed from SQL table
                    if (userRegionOptions2!=null) {
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

                    fillRegion(sheet, reportId, region, userRegionOptions, loggedInUser);
                }
            }
            System.out.println("regions populated in : " + (System.currentTimeMillis() - track) + "ms");

            // this is a pain, it seems I need to call 2 functions on each formula cell or the formula may not be calculated. ANNOYING!
            // can't do this in the fill region as formulae need to be dealt with outside
            Iterator<SRow> rowIterator = sheet.getInternalSheet().getRowIterator(); // only rows with values in them
            while (rowIterator.hasNext()) {
                Iterator<SCell> cellIterator = sheet.getInternalSheet().getCellIterator(rowIterator.next().getIndex());
                while (cellIterator.hasNext()) {
                    SCell cell = cellIterator.next();
                    if (cell.getType() == SCell.CellType.FORMULA) {
                        //System.out.println("doing the cell thing on " + cell);
                        cell.getFormulaResultType();
                        cell.clearFormulaResultCache();
                    }
                }
            }
            /* now formulae should have been calculated check if anything might need to be saved
            I'd avoided doing this but now I am it's useful for restoring values and checking for overlapping data regions.
            so similar loop to above - also we want to check for the logic of using the loaded values
            */
            if (!fastLoad) {
                for (SName name : namesForSheet) {
                    if (name.getName().startsWith(azDataRegion)) {
                        String region = name.getName().substring(azDataRegion.length());
                        CellRegion displayDataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);
                        final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, region);
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
                                        if (sCell.getType() == SCell.CellType.FORMULA) {
                                            if (sCell.getFormulaResultType() == SCell.CellType.NUMBER) { // then check it's value against the DB one . . .
                                                if (sCell.getNumberValue() != cellForDisplay.getDoubleValue()) {
                                                    if (useSavedValuesOnFormulae && !cellForDisplay.getIgnored()) { // override formula from DB, only if not ignored
                                                        sCell.setNumberValue(cellForDisplay.getDoubleValue());
                                                    } else { // the formula overrode the DB, get the value ready of saving if the user wants that
                                                        cellForDisplay.setDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                        showSave = true;
                                                    }
                                                }
                                            } else if (sCell.getFormulaResultType() == SCell.CellType.STRING) {
                                                if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())) {
                                                    if (useSavedValuesOnFormulae && !cellForDisplay.getIgnored()) { // override formula from DB
                                                        sCell.setStringValue(cellForDisplay.getStringValue());
                                                    } else { // the formula overrode the DB, get the value ready of saving if the user wants that
                                                        cellForDisplay.setStringValue(sCell.getStringValue());
                                                        showSave = true;
                                                    }
                                                }
                                            }
                                        } else {
                                            // we now want to compare in the case of non formulae changes - a value from one data region importing into another,
                                            // the other typically being of the "ad hoc" no row headings type
                                            // notably this will hit a lot of cells (all the rest)
                                            if (sCell.getType() == SCell.CellType.NUMBER) {
                                                if (sCell.getNumberValue() != cellForDisplay.getDoubleValue()) {
                                                    cellForDisplay.setDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                    showSave = true;
                                                }
                                            } else if (sCell.getType() == SCell.CellType.STRING) {
                                                if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())) {
                                                    cellForDisplay.setStringValue(sCell.getStringValue());
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
/* commenting locking for the moment
            // now protect. Doing so before seems to cause problems
            Ranges.range(sheet).protectSheet("azquo",
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
            // snap the charts - currently just top left, do bottom right also?
            for (SChart chart : sheet.getInternalSheet().getCharts()) {
                ViewAnchor oldAnchor = chart.getAnchor();
                int row = oldAnchor.getRowIndex();
                int col = oldAnchor.getColumnIndex();
                int width = oldAnchor.getWidth();
                int height = oldAnchor.getHeight();
                chart.setAnchor(new ViewAnchor(row, col, 0, 0, width, height));
            }
            // now remerge? Should work
            for (CellRegion merge : merges) {
                // I think we do want to merge horizontally (the boolean flag)
                CellOperationUtil.merge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()), true);
            }
            addValidation(namesForSheet, sheet, choiceOptions);
        }
        loggedInUser.setContext(context);
        // after stripping off some redundant exception throwing this was the only possiblity left, ignore it
        try {
            rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).clearSessionLog(loggedInUser.getDataAccessToken());
        } catch (Exception ignored){
        }
        return showSave;
    }

    // like rangeToStringLists in azquobook

    private List<List<String>> regionToStringLists(CellRegion region, Sheet sheet) {
        List<List<String>> toReturn = new ArrayList<>();
        if (region == null) return toReturn;
        for (int rowIndex = region.getRow(); rowIndex <= region.getLastRow(); rowIndex++) {
            List<String> row = new ArrayList<>();
            toReturn.add(row);
            for (int colIndex = region.getColumn(); colIndex <= region.getLastColumn(); colIndex++) {
                SCell cell = sheet.getInternalSheet().getCell(rowIndex, colIndex);
                // being paraniod?
                if (cell != null && cell.getType() == SCell.CellType.FORMULA) {
                    //System.out.println("doing the cell thing on " + cell);
                    cell.getFormulaResultType();
                    cell.clearFormulaResultCache();
                }
                // I assume non null cell has a non null sting value, do I need to check for null?
                String toShow = null;
                try {
                    toShow = cell.getStringValue();
                } catch (Exception ignored) {
                }
                row.add(toShow);
            }
        }
        return toReturn;
    }

    private void setDefaultChoices(LoggedInUser loggedInUser, Sheet sheet, Map<String, String> userChoices) {
        for (String choiceName : userChoices.keySet()) {
            CellRegion choiceList = getCellRegionForSheetAndName(sheet, choiceName + "Choice");
            if (choiceList != null) {
                String choiceString = getRegionValue(sheet, choiceList);
                int defaultPos = choiceString.indexOf("default:");
                if (defaultPos > 0) {
                    String defaultString = choiceString.substring(defaultPos + 8).trim();
                    int dotPos = defaultString.indexOf(".");
                    if (dotPos > 0) {
                        CellRegion defaultName = getCellRegionForSheetAndName(sheet, defaultString.substring(0, dotPos) + "Chosen");
                        if (defaultName != null) {
                            String query = "`" + getRegionValue(sheet, defaultName) + "`" + defaultString.substring(dotPos);
                            try {
                                List<String> choiceOptions = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                        .getDropDownListForQuery(loggedInUser.getDataAccessToken(), query, loggedInUser.getLanguages());
                                if (choiceOptions.size() == 1) {
                                    CellRegion chosen = getCellRegionForSheetAndName(sheet, choiceName + "Chosen");
                                    if (chosen != null) {
                                        sheet.getInternalSheet().getCell(chosen.getRow(), chosen.getColumn()).setStringValue(choiceOptions.get(0));//at last!  set the option
                                    }
                                }
                            } catch (Exception e) {
                                //don't bother if you cant find it.
                            }
                        }
                    }
                }
            }
        }
    }

    public static String getRegionValue(Sheet sheet, CellRegion region) {
        try{
            return sheet.getInternalSheet().getCell(region.getRow(), region.getColumn()).getStringValue();
        } catch (Exception e){
            e.printStackTrace();
            return "";
        }
    }

    private void fillRegion(Sheet sheet, int reportId,  String region, UserRegionOptions userRegionOptions, LoggedInUser loggedInUser) {
        CellRegion columnHeadingsDescription = getCellRegionForSheetAndName(sheet, "az_ColumnHeadings" + region);
        CellRegion rowHeadingsDescription = getCellRegionForSheetAndName(sheet, "az_RowHeadings" + region);
        CellRegion contextDescription = getCellRegionForSheetAndName(sheet, "az_Context" + region);

        String errorMessage = null;
        if (columnHeadingsDescription != null && rowHeadingsDescription == null) {
            List<List<String>> colHeadings = regionToStringLists(columnHeadingsDescription, sheet);
            List<List<CellForDisplay>> dataRegionCells = new ArrayList<>();
            CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);
            for (int rowNo = 0; rowNo < dataRegion.getRowCount(); rowNo++) {
                List<CellForDisplay> oneRow = new ArrayList<>();
                for (int colNo = 0; colNo < dataRegion.getColumnCount(); colNo++) {
                    oneRow.add(new CellForDisplay(false, "", 0, false, rowNo, colNo, true)); // make these ignored. Edd note : I'm not praticularly happy about this, sent data should be sent data, this is just made up . . .
                }
                dataRegionCells.add(oneRow);
            }
            CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(colHeadings, null, dataRegionCells, null, null, null);
            loggedInUser.setSentCells(reportId, region, cellsAndHeadingsForDisplay);
            return;
        }

        if (columnHeadingsDescription != null) {
            try {
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = spreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), region, regionToStringLists(rowHeadingsDescription, sheet), regionToStringLists(columnHeadingsDescription, sheet),
                        regionToStringLists(contextDescription, sheet), userRegionOptions);
                loggedInUser.setSentCells(reportId, region, cellsAndHeadingsForDisplay);
                // now, put the headings into the sheet!
                // might be factored into fill range in a bit
                CellRegion displayColumnHeadings = getCellRegionForSheetAndName(sheet, "az_DisplayColumnHeadings" + region);
                CellRegion displayRowHeadings = getCellRegionForSheetAndName(sheet, "az_DisplayRowHeadings" + region);
                CellRegion displayDataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);

                int rowsToAdd;
                int colsToAdd;

                if (displayDataRegion != null) {
                    // add rows
                    int maxCol = 0;
                    for (int i = 0; i <= sheet.getLastRow(); i++) {
                        if (sheet.getLastColumn(i) > maxCol) {
                            maxCol = sheet.getLastColumn(i);
                        }
                    }
                    if (cellsAndHeadingsForDisplay.getRowHeadings() != null && (displayDataRegion.getRowCount() < cellsAndHeadingsForDisplay.getRowHeadings().size()) && displayDataRegion.getRowCount() > 2) { // then we need to expand, and there is space to do so (3 or more allocated already)
                        rowsToAdd = cellsAndHeadingsForDisplay.getRowHeadings().size() - (displayDataRegion.getRowCount());
                        int insertRow = displayDataRegion.getRow() +displayDataRegion.getRowCount() -1; // last but one row
                        Range copySource = Ranges.range(sheet, insertRow - 1, 0, insertRow - 1, maxCol);
                        Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1, maxCol); // insert at the 3rd row - should be rows to add - 1 as it starts at one without adding anything
                        CellOperationUtil.insertRow(insertRange);
                        CellOperationUtil.paste(copySource, insertRange);
                        int originalHeight = sheet.getInternalSheet().getRow(insertRow - 1).getHeight();
                        if (originalHeight != sheet.getInternalSheet().getRow(insertRow).getHeight()) { // height may not match on insert
                            insertRange.setRowHeight(originalHeight); // hopefully set the lot in one go??
                        }
                    }
                    // add columns
                    int maxRow = sheet.getLastRow();
                    if (displayDataRegion.getColumnCount() < cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() && displayDataRegion.getColumnCount() > 2) { // then we need to expand
                        colsToAdd = cellsAndHeadingsForDisplay.getColumnHeadings().get(0).size() - (displayDataRegion.getColumnCount());
                        int insertCol = displayDataRegion.getColumn() + displayDataRegion.getColumnCount() - 1; // I think this is correct, just after the second column?
                        Range copySource = Ranges.range(sheet, 0, insertCol - 1, maxRow, insertCol - 1);
                        Range insertRange = Ranges.range(sheet, 0, insertCol, maxRow, insertCol + colsToAdd - 1); // insert just before the 3rd col
                        CellOperationUtil.insertColumn(insertRange);
                        // will this paste the lot?
                        CellOperationUtil.paste(copySource, insertRange);
                        int originalWidth = sheet.getInternalSheet().getColumn(insertCol).getWidth();
                        if (originalWidth != sheet.getInternalSheet().getColumn(insertCol).getWidth()) { // height may not match on insert
                            insertRange.setColumnWidth(originalWidth); // hopefully set the lot in one go??
                        }
                    }
                    int row;
                    // ok there should be the right space for the headings
                    if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) {
                        row = displayRowHeadings.getRow();
                        for (List<String> rowHeading : cellsAndHeadingsForDisplay.getRowHeadings()) {
                            int col = displayRowHeadings.getColumn();
                            for (String heading : rowHeading) {
                                if (heading != null && (sheet.getInternalSheet().getCell(row, col).getStringValue().isEmpty())) { // as with AzquoBook don't overwrite existing cells when it comes to headings
                                    sheet.getInternalSheet().getCell(row, col).setValue(heading);
                                }
                                col++;
                            }
                            row++;
                        }
                    }
                    //← → ↑ ↓ ↔ ↕ ah I can just paste it here, thanks IntelliJ :)
                    if (displayColumnHeadings != null) {
                        row = displayColumnHeadings.getRow();
                        for (List<String> colHeading : cellsAndHeadingsForDisplay.getColumnHeadings()) {
                            boolean columnSort = false;
                            if (row - displayColumnHeadings.getRow() == cellsAndHeadingsForDisplay.getColumnHeadings().size() - 1 && userRegionOptions.getSortable()) { // meaning last row of headings and sortable
                                columnSort = true;
                            }
                            int col = displayColumnHeadings.getColumn();
                            for (String heading : colHeading) {
                                if (columnSort) {
                                    String sortArrow = " ↕";
                                    if (heading.equals(userRegionOptions.getSortColumn())) {
                                        sortArrow = userRegionOptions.getSortColumnAsc() ? " ↑" : " ↓";
                                    }
                                    if (sheet.getInternalSheet().getCell(row, col).getStringValue().isEmpty()) {
                                        sheet.getInternalSheet().getCell(row, col).setValue(heading + sortArrow);
                                    } else {
                                        sheet.getInternalSheet().getCell(row, col).setValue(sheet.getInternalSheet().getCell(row, col).getValue() + sortArrow);
                                    }
                                    String value = sheet.getInternalSheet().getCell(row, col).getStringValue();
                                    value = value.substring(0, value.length() - 2);
                                    Range chosenRange = Ranges.range(sheet, row, col, row, col);
                                    // todo, investigate how commas would fit in in a heading name
                                    // think I'll just zap em for the mo.
                                    value = value.replace(",", "");
                                    chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true,
                                            value + " ↕," + value + " ↑," + value + " ↓", null,
                                            true, "Select Sorting", "",
                                            true, Validation.AlertStyle.WARNING, "Sort Column", "This is a sortable column, its value should not be manually altered.");
                                } else if (heading != null && sheet.getInternalSheet().getCell(row, col).getStringValue().isEmpty()) { // vanilla, overwrite if not
                                    sheet.getInternalSheet().getCell(row, col).setValue(heading);
                                }
                                col++;
                            }
                            row++;
                        }

                        // for the moment don't allow user coolum sorting (row heading sorting). Shouldn't be too difficult to add

/*                if (sortable != null && sortable.equalsIgnoreCase("all")) { // criteria from azquobook to make row heading sortable
                }*/
                    }

                    row = displayDataRegion.getRow();
                    List<String> bottomColHeadings = cellsAndHeadingsForDisplay.getColumnHeadings().get(cellsAndHeadingsForDisplay.getColumnHeadings().size() - 1); // bottom of the col headings if they are multi layered
                    if (cellsAndHeadingsForDisplay.getData() != null) {
                        for (List<CellForDisplay> rowCellValues : cellsAndHeadingsForDisplay.getData()) {
                            int col = displayDataRegion.getColumn();
                            int localCol = 0;
                            for (CellForDisplay cellValue : rowCellValues) {
                                if (!cellValue.getStringValue().isEmpty() && !bottomColHeadings.get(localCol).equals(".")) { // then something to set. Note : if col heading ON THE DB SIDE is . then don't populate
                                    // the notable thing ehre is that ZK uses the object type to work out data type
                                    SCell cell = sheet.getInternalSheet().getCell(row, col);
                                    // logic I didn't initially implement : don't overwrite if there's a formulae in there
                                    boolean hasValue = false;
                                    if (cell.getType() != SCell.CellType.FORMULA) {
                                        if (cell.getCellStyle().getDataFormat().toLowerCase().contains("mm")) {//allow users to format their own dates.  All dates on file are yyyy-MM-dd
                                            try {
                                                Date date = df.parse(cellValue.getStringValue());
                                                if (date != null) {
                                                    cell.setValue(date.getTime() / (1000 * 3600 * 24) + 25570);//convert date to days relative to 1970
                                                    hasValue = true;
                                                }
                                            } catch (Exception ignored) {
                                            }
                                        }
                                        if (!hasValue) {
                                            if (cell.getCellStyle().getFont().getName().equalsIgnoreCase("Code EAN13")) { // then a special case, need to use barcode encoding
                                                cell.setValue(SpreadsheetService.prepareEAN13Barcode(cellValue.getStringValue()));// guess we'll see how that goes!
                                            } else {
                                                if (NumberUtils.isNumber(cellValue.getStringValue())) {
                                                    cell.setValue(cellValue.getDoubleValue());// think that works . . .
                                                } else {
                                                    cell.setValue(cellValue.getStringValue());// think that works . . .
                                                }
                                            }
                                        }
                                        // see if this works for highlighting
                                        if (cellValue.isHighlighted()) {
                                            CellOperationUtil.applyFontColor(Ranges.range(sheet, row, col), "#FF0000");
                                        }
                                    /* commented for the moment, requires the overall unlock per sheet followed by the protect later
                                    if (cellValue.isLocked()){
                                        Range selection =  Ranges.range(sheet, row, col);
                                        CellStyle oldStyle = selection.getCellStyle();
                                        EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                                        newStyle.setLocked(true);
                                        selection.setCellStyle(newStyle);
                                    }*/
                                    }
                                }
                                col++;
                                localCol++;
                            }
                            row++;
                        }
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
                // maybe move to the top left? Unsure
                CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);// this function should not be called without a valid data region
                if (dataRegion != null) {
                    sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching header and context regions for this data region : az_DataRegion" + region + " : " + errorMessage);
                } else {
                    System.out.println("no region found for az_DataRegion" + region);
                }
            }
        } else {
            CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);// this function should not be called without a valid data region
            if (dataRegion != null) {
                sheet.getInternalSheet().getCell(dataRegion.getRow(), dataRegion.getColumn()).setStringValue("Unable to find matching header and context regions for this data region : az_DataRegion" + region);
            } else {
                System.out.println("no region found for az_DataRegion" + region);
            }
        }
    }

    public static List<SName> getNamesForSheet(Sheet sheet) {
        List<SName> names = new ArrayList<>();
        for (SName name : sheet.getBook().getInternalBook().getNames()) {
            if (name.getRefersToSheetName() != null && name.getRefersToSheetName().equals(sheet.getSheetName())) {
                names.add(name);
            }
        }
        Collections.sort(names, (o1, o2) -> (o1.getName().toUpperCase().compareTo(o2.getName().toUpperCase())));
        return names;
    }

    // this works out case insensitive based on the API, I've made this static for convenience? Is is a problem? Maybe the code calling it should be in here
    // todo - reslove static question!

    public static CellRegion getCellRegionForSheetAndName(Sheet sheet, String name) {
        SName toReturn = sheet.getBook().getInternalBook().getNameByName(name, sheet.getSheetName());
        if (toReturn == null) {// often may fail with explicit sheet name
            toReturn = sheet.getBook().getInternalBook().getNameByName(name);
        }
        if (toReturn != null && toReturn.getRefersToSheetName().equals(sheet.getSheetName())) {
            return toReturn.getRefersToCellRegion();
        }
        return null;
    }

    public static final String VALIDATION_SHEET = "VALIDATION_SHEET";

    // Had to split one function into two, need to evaluate choices server side before loading the regions due to names being populated by "As" clauses
    // some duplication between the two functions but not that bad I don't think

    public Map<String, List<String>> resolveChoiceOptionsAndQueries(List<SName> namesForSheet, Sheet sheet, LoggedInUser loggedInUser) {
        Map<String, List<String>> toReturn = new HashMap<>();
        // I assume
        for (SName name : namesForSheet) {
            if (name.getRefersToSheetName().equals(sheet.getSheetName())) { // why am I checking this again? A little confused
                if (name.getName().endsWith("Choice")) {
                    CellRegion choice = getCellRegionForSheetAndName(sheet, name.getName());
                    if (choice != null) {
                        // ok I assume choice is a single cell
                        List<String> choiceOptions = new ArrayList<>(); // was null, see no help in that
                        String query = getRegionValue(sheet, choice);
                        final String originalQuery = query;
                        if (query.toLowerCase().contains("default")) {
                            query = query.substring(0, query.toLowerCase().indexOf("default"));
                        }
                        try {
                            if (query.startsWith("\"") || query.startsWith("“")) {
                                //crude - if there is a comma in any option this will fail
                                query = query.replace("\"", "").replace("“", "").replace("”", "");
                                String[] choices = query.split(",");
                                Collections.addAll(choiceOptions, choices);
                            } else {
                                choiceOptions = rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())
                                        .getDropDownListForQuery(loggedInUser.getDataAccessToken(), query, loggedInUser.getLanguages());
                            }
                        } catch (Exception e) {
                            choiceOptions.add(e.getMessage());
                        }
                        toReturn.put(originalQuery, choiceOptions);
                    }
                } else if (name.getName().endsWith("Query")) {
                    CellRegion query = getCellRegionForSheetAndName(sheet, name.getName());
                    String queryString = getRegionValue(sheet, query);
                    try {
                        rmiClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp())

                                .resolveQuery(loggedInUser.getDataAccessToken(), queryString, loggedInUser.getLanguages());// sending the same as choice but the goal here is execute server side. Generally to set an "As"
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return toReturn;
    }

    public void addValidation(List<SName> namesForSheet, Sheet sheet, Map<String, List<String>> choiceCache) {
        if (sheet.getBook().getSheet(VALIDATION_SHEET) == null) {
            sheet.getBook().getInternalBook().createSheet(VALIDATION_SHEET);
        }
        Sheet validationSheet = sheet.getBook().getSheet(VALIDATION_SHEET);
        validationSheet.getInternalSheet().setSheetVisible(SSheet.SheetVisible.HIDDEN);
        int numberOfValidationsAdded = 0;
        for (SName name : namesForSheet) {
            if (name.getRefersToSheetName().equals(sheet.getSheetName())) { // why am I checking this again? A little confused
                if (name.getName().endsWith("Choice")) {
                    CellRegion choice = getCellRegionForSheetAndName(sheet, name.getName());
                    CellRegion chosen = getCellRegionForSheetAndName(sheet, name.getName().substring(0, name.getName().length() - "Choice".length()) + "Chosen"); // as ever I do wonder about these string literals
                    if (choice != null && chosen != null) {
                        // ok I assume choice is a single cell
                        String query = getRegionValue(sheet, choice);
                        List<String> choiceOptions = choiceCache.get(query);
                        validationSheet.getInternalSheet().getCell(0, numberOfValidationsAdded).setStringValue(name.getName());
                        int row = 0;
                        // yes, this can null pointer but if it does something is seriously wrong
                        for (String choiceOption : choiceOptions) {
                            row++;// like starting at 1
                            validationSheet.getInternalSheet().getCell(row, numberOfValidationsAdded).setStringValue(choiceOption);
                        }
                        if (row > 0){ // if choice options is empty this will not work
                            Range validationValues = Ranges.range(validationSheet, 1, numberOfValidationsAdded, row, numberOfValidationsAdded);
                            //validationValues.createName("az_Validation" + numberOfValidationsAdded);
                            for (int rowNo = chosen.getRow(); rowNo < chosen.getRow() + chosen.getRowCount(); rowNo++) {
                                for (int colNo = chosen.getColumn(); colNo < chosen.getColumn() + chosen.getColumnCount(); colNo++) {
                                    Range chosenRange = Ranges.range(sheet, rowNo, colNo, rowNo, colNo);
                                    //chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "\"az_Validation" + numberOfValidationsAdded +"\"", null,
                                    chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "=" + validationValues.asString(), null,
                                            true, "title", "msg",
                                            false, Validation.AlertStyle.WARNING, "alert title", "alert msg");

                                }
                            }
                            numberOfValidationsAdded++;
                        } else {
                            System.out.println("no choices for : " + query);
                        }
                    }
                }
            }
        }
    }

    /* so a user changes a drop down, we need dependant drop downs to have theiur choices blanked. Currently I assume on one sheet
    the choices are without prefix or suffix, I expect Pallet not PalletChosen. Check the choice fields for dependencies,
    zap the option in the db then recurse if necessary.
     */
    public void blankDependantChoices(LoggedInUser loggedInUser, List<String> changedChoices, Sheet sheet) {
        List<String> affectedChoices = new ArrayList<>();
        for (SName name : getNamesForSheet(sheet)) { // this should be fine? I mean getNamesForSheet.
            String nameName = name.getName();
            if (nameName.endsWith("Choice")) {
                CellRegion choice = getCellRegionForSheetAndName(sheet, name.getName());
                if (choice != null) {
                    final SCell cell = sheet.getInternalSheet().getCell(choice.getRow(), choice.getColumn());
                    if (cell.getType() == SCell.CellType.FORMULA) { // it chould be for choices
                        String query = cell.getFormulaValue(); // otherwise we'll see the result
                        for (String changed : changedChoices) {
                            if (query.toUpperCase().contains((changed + "Chosen").toUpperCase())) { // not sure if this chould be case insensetive, I'll make it so for the moment
                                // so that choice is dependant on one of the things chosen, The dropdown will change so need to zap the choice
                                String affectedChoice = nameName.substring(0, nameName.length() - "Choice".length());
                                affectedChoices.add(affectedChoice);
                                spreadsheetService.setUserChoice(loggedInUser.getUser().getId(), affectedChoice, null);
                            }
                        }
                    }
                }
            }
        }
        if (!affectedChoices.isEmpty()) {
            // then recurse
            blankDependantChoices(loggedInUser, affectedChoices, sheet);
        }
    }
}