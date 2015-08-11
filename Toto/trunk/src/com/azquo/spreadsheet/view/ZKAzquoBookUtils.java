package com.azquo.spreadsheet.view;

import com.azquo.admin.user.UserChoiceDAO;
import com.azquo.admin.user.UserChoice;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.*;
import org.apache.commons.lang.math.NumberUtils;
import org.zkoss.poi.ss.usermodel.*;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.*;
import org.zkoss.zss.api.model.CellStyle;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.*;

import java.rmi.RemoteException;
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

    public ZKAzquoBookUtils(SpreadsheetService spreadsheetService, UserChoiceDAO userChoiceDAO, UserRegionOptionsDAO userRegionOptionsDAO) {
        this.spreadsheetService = spreadsheetService;
        this.userChoiceDAO = userChoiceDAO;
        this.userRegionOptionsDAO = userRegionOptionsDAO;
    }

    // kind of like azquo book prepare sheet, load data bits, will aim to replicate the basics from there

    public boolean populateBook(Book book) throws Exception {
        boolean showSave = false;
        //book.getInternalBook().getAttribute(ZKAzquoBookProvider.BOOK_PATH);
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);

        Map<String, String> userChoices = new HashMap<String, String>();
        // get the user choices for the report. Can be drop down values, sorting/highlighting etc.
        // a notable point here is that the user choices don't distinguish between sheets
        List<UserChoice> allChoices = userChoiceDAO.findForUserId(loggedInUser.getUser().getId());
        for (UserChoice uc : allChoices) {
            userChoices.put(uc.getChoiceName(), uc.getChoiceValue());
        }
        String context = "";
        /* ok there was a thought of running the sheet, copying to a new, running again,
        copying to a new but this can make the logic messy and there has to be a way or resetting the
        original sheet which would be a pain. So I'm going to try my initial thought, set up all the required possibilities
        at the beginning then execute as normal, the loop below being none the wiser. Only thing : the create sheet command doesn't copy names (I mean excel names!)
        so I guess I'll try.

        // book.getInternalBook().moveSheetTo(); // will need this if we go this way.
        SSheet newSheet = book.getInternalBook().createSheet("edd_test", book.getSheetAt(0).getInternalSheet());
        List<SName> namesForSheet1 = getNamesForSheet(book.getSheetAt(0));
        for (SName name : namesForSheet1) {
            final SName newName = book.getInternalBook().createName(name.getName(), newSheet.getSheetName()); // since its in the scope of this sheet it should be ok to be the same name?
            String formula = name.getRefersToFormula();
            if (formula.contains("!")){
                formula = formula.substring(formula.indexOf("!"));
            }
            formula = newSheet.getSheetName() + formula;
            newName.setRefersToFormula(formula);
            System.out.println(newName.getName() + " - " + newName.getRefersToFormula());
        }*/



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
                if (choice != null && choice.getRowCount() == 1){
                        String userChoice = userChoices.get(choiceName);
                        sheet.getInternalSheet().getCell(choice.getRow(), choice.getColumn()).setStringValue(userChoice);
                        context += choiceName + " = " + userChoices.get(choiceName) + ";";
                 }
            }
            setDefaultChoices(loggedInUser, sheet, userChoices);//overrides any other choice
            // ok the plan here is remove all the merges then put them back in after the regions are expanded.
            List<CellRegion> merges = new ArrayList<CellRegion>(sheet.getInternalSheet().getMergedRegions());
            for (CellRegion merge : merges) {
                CellOperationUtil.unmerge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()));
            }
            // and now we want to run through all regions for this sheet, will look at the old code for this, I think it's fill region that we take cues from
            // names are per book, not sheet. Perhaps we could make names the big outside loop but for the moment I'll go by sheet - convenience function
            List<SName> namesForSheet = getNamesForSheet(sheet);
            for (SName name : namesForSheet) {
                // Old one was case insensitive - not so happy about this. Will allow it on the prefix
                if (name.getName().startsWith(azDataRegion)) { // then we have a data region to deal with here
                    String region = name.getName().substring(azDataRegion.length()); // might well be an empty string
                    UserRegionOptions userRegionOptions = userRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                    if (userRegionOptions == null) { // then get one from the sheet if we can
                        String source = "";
                        CellRegion optionsRegion = getCellRegionForSheetAndName(sheet, azOptions + region);
                        if (optionsRegion != null) {
                            source = sheet.getInternalSheet().getCell(optionsRegion.getRow(), optionsRegion.getColumn()).getStringValue();
                        }
                        userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, source);
                    }
                    // init the sort
                    fillRegion(sheet, region, userRegionOptions, loggedInUser);
                }
            }
            
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
            // now formulae should have been calculated check if anything might need to be saved
            // so similar loop to above
            for (SName name : namesForSheet) {
                if (name.getName().startsWith(azDataRegion)) {
                    String region = name.getName().substring(azDataRegion.length());
                    CellRegion displayDataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);
                    final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(region);
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
                                    if (sCell.getType() == SCell.CellType.FORMULA) {
                                        CellForDisplay cellForDisplay = sentCells.getData().get(row - startRow).get(col - startCol);
                                        if (sCell.getFormulaResultType() == SCell.CellType.NUMBER) { // then check it's value against the DB one . . .
                                              if (sCell.getNumberValue() != cellForDisplay.getDoubleValue()) {
                                                cellForDisplay.setDoubleValue(sCell.getNumberValue()); // should flag as changed
                                                showSave = true;
                                            }
                                        } else if (sCell.getFormulaResultType() == SCell.CellType.STRING){
                                            if (!sCell.getStringValue().equals(cellForDisplay.getStringValue())){
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
            addValidation(namesForSheet, sheet, loggedInUser);
        }
        loggedInUser.setContext(context);

        return showSave;
    }

    // like rangeToStringLists in azquobook

    private List<List<String>> regionToStringLists(CellRegion region, Sheet sheet) {
        List<List<String>> toReturn = new ArrayList<List<String>>();
        if (region==null) return toReturn;
        for (int rowIndex = region.getRow(); rowIndex <= region.getLastRow(); rowIndex++) {
            List<String> row = new ArrayList<String>();
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
                row.add(cell != null ? cell.getStringValue() : null);
            }
        }
        return toReturn;
    }

    private void setDefaultChoices(LoggedInUser loggedInUser, Sheet sheet, Map<String, String>userChoices){

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
                        if (defaultName!=null) {
                            String query = "`" + getRegionValue(sheet, defaultName) + "`" + defaultString.substring(dotPos);
                            try {
                                List<String> choiceOptions = spreadsheetService.getDropDownListForQuery(loggedInUser.getDataAccessToken(), query, loggedInUser.getLanguages());
                                if (choiceOptions.size() == 1){
                                    CellRegion chosen = getCellRegionForSheetAndName(sheet, choiceName + "Chosen");
                                    if (chosen!= null) {
                                        sheet.getInternalSheet().getCell(chosen.getRow(), chosen.getColumn()).setStringValue(choiceOptions.get(0));//at last!  set the option
                                    }
                                }
                            }catch(Exception e){
                                //don't bother if you cant find it.

                            }
                        }

                    }
                }

            }
        }

    }


    private String getRegionValue(Sheet sheet, CellRegion region){
        return sheet.getInternalSheet().getCell(region.getRow(), region.getColumn()).getStringValue();
    }

    private void fillRegion(Sheet sheet, String region, UserRegionOptions userRegionOptions, LoggedInUser loggedInUser) throws Exception {
        CellRegion columnHeadingsDescription = getCellRegionForSheetAndName(sheet, "az_ColumnHeadings" + region);
        CellRegion rowHeadingsDescription = getCellRegionForSheetAndName(sheet, "az_RowHeadings" + region);
        CellRegion contextDescription = getCellRegionForSheetAndName(sheet, "az_Context" + region);

        String errorMessage = null;
        if (columnHeadingsDescription != null && rowHeadingsDescription==null){
            List<List<String>> colHeadings = regionToStringLists(columnHeadingsDescription, sheet);
            List<List<CellForDisplay>>dataRegionCells = new ArrayList<List<CellForDisplay>>();
            CellRegion dataRegion = getCellRegionForSheetAndName(sheet, "az_DataRegion" + region);
            for (int rowNo=0;rowNo < dataRegion.getRowCount();rowNo++){
                List<CellForDisplay> oneRow = new ArrayList<CellForDisplay>();
                for (int colNo = 0;colNo < dataRegion.getColumnCount();colNo++){
                    oneRow.add(new CellForDisplay(false,"",0, false, rowNo, colNo));
                }
                dataRegionCells.add(oneRow);
            }

            CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(colHeadings, null,dataRegionCells, null, null,null);
            loggedInUser.setSentCells(region, cellsAndHeadingsForDisplay);
            return;
        }

        if (columnHeadingsDescription != null && rowHeadingsDescription != null) {
            try {
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = spreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), regionToStringLists(rowHeadingsDescription, sheet), regionToStringLists(columnHeadingsDescription, sheet),
                        regionToStringLists(contextDescription, sheet), userRegionOptions);
                loggedInUser.setSentCells(region, cellsAndHeadingsForDisplay);
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
                    if (cellsAndHeadingsForDisplay.getRowHeadings()!=null && (displayDataRegion.getRowCount() < cellsAndHeadingsForDisplay.getRowHeadings().size()) && displayDataRegion.getRowCount() > 2) { // then we need to expand, and there is space to do so (3 or more allocated already)
                        rowsToAdd = cellsAndHeadingsForDisplay.getRowHeadings().size() - (displayDataRegion.getRowCount());
                        int insertRow = displayDataRegion.getRow() + 2; // I think this is correct, middle row of 3?
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
                        int insertCol = displayDataRegion.getColumn() + 2; // I think this is correct, just after the second column?
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
                    int row = 0;
                    // ok there should be the right space for the headings
                    if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings()!=null) {
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

                    //← → ↑ ↓ ↔ ↕// ah I can just paste it here, thanks IntelliJ :)
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
                    if (cellsAndHeadingsForDisplay.getData()!=null) {
                        for (List<CellForDisplay> rowCellValues : cellsAndHeadingsForDisplay.getData()) {
                            int col = displayDataRegion.getColumn();
                            int localCol = 0;
                            for (CellForDisplay cellValue : rowCellValues) {
                                if (!cellValue.getStringValue().isEmpty() && !bottomColHeadings.get(localCol).equals(".")) { // then something to set. Note : if col heading ON THE DB SIDE is . then don't populate
                                    // the notable thing ehre is that ZK uses the object type to work out data type
                                    SCell cell = sheet.getInternalSheet().getCell(row, col);
                                    // logic I didn't initially implement : don't overwrite if there's a formulae in there
                                    if (cell.getType() != SCell.CellType.FORMULA) {
                                        if (cell.getCellStyle().getFont().getName().equalsIgnoreCase("Code EAN13")){ // then a special case, need to use barcode encoding
                                            cell.setValue(SpreadsheetService.prepareEAN13Barcode(cellValue.getStringValue()));// guess we'll see how that goes!
                                        } else {
                                            if (NumberUtils.isNumber(cellValue.getStringValue())) {
                                                cell.setValue(cellValue.getDoubleValue());// think that works . . .
                                            } else {
                                                cell.setValue(cellValue.getStringValue());// think that works . . .
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
        List<SName> names = new ArrayList<SName>();
        for (SName name : sheet.getBook().getInternalBook().getNames()) {
            if (name.getRefersToSheetName() != null && name.getRefersToSheetName().equals(sheet.getSheetName())) {
                names.add(name);
            }
        }
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

    public void addValidation(List<SName> namesForSheet, Sheet sheet, LoggedInUser loggedInUser) {
        if (sheet.getBook().getSheet(VALIDATION_SHEET) == null) {
            sheet.getBook().getInternalBook().createSheet(VALIDATION_SHEET);
        }
        Sheet validationSheet = sheet.getBook().getSheet(VALIDATION_SHEET);
        //validationSheet.getInternalSheet().setSheetVisible(SSheet.SheetVisible.HIDDEN);
        int numberOfValidationsAdded = 0;
        for (SName name : namesForSheet) {
            if (name.getRefersToSheetName().equals(sheet.getSheetName())) {
                if (name.getName().endsWith("Choice")) {
                    CellRegion choice = getCellRegionForSheetAndName(sheet, name.getName());
                    CellRegion chosen = getCellRegionForSheetAndName(sheet, name.getName().substring(0, name.getName().length() - "Choice".length()) + "Chosen"); // as ever I do wonder about these string literals
                    if (choice != null && chosen != null) {
                        // ok I assume choice is a single cell
                        List<String>choiceOptions = null;
                        String query= getRegionValue(sheet,choice);
                        if (query.toLowerCase().contains("default")){
                            query = query.substring(0, query.toLowerCase().indexOf("default"));
                        }
                        try{

                            if (query.startsWith("\"") || query.startsWith("“")){
                                //crude - if there is a comma in any option this will fail
                                query  = query.replace("\"","").replace("“","").replace("”","");
                                String[] choices = query.split(",");
                                for (String choice2:choices){
                                    choiceOptions.add(choice2);
                                }
                            }else{
                                choiceOptions = spreadsheetService.getDropDownListForQuery(loggedInUser.getDataAccessToken(), query, loggedInUser.getLanguages());
                            }
                            validationSheet.getInternalSheet().getCell(0, numberOfValidationsAdded).setStringValue(name.getName());
                            int row = 0;
                            for (String choiceOption : choiceOptions) {
                                row++;// like starting at 1
                                validationSheet.getInternalSheet().getCell(row, numberOfValidationsAdded).setStringValue(choiceOption);
                            }
                            Range validationValues = Ranges.range(validationSheet, 1, numberOfValidationsAdded, row, numberOfValidationsAdded);
                            //validationValues.createName("az_Validation" + numberOfValidationsAdded);
                            for (int rowNo=chosen.getRow(); rowNo < chosen.getRow() + chosen.getRowCount();rowNo++){
                                for (int colNo = chosen.getColumn(); colNo < chosen.getColumn() + chosen.getColumnCount();colNo++){

                                    Range chosenRange = Ranges.range(sheet, rowNo, colNo, rowNo, colNo);

                                    //chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "\"az_Validation" + numberOfValidationsAdded +"\"", null,
                                    chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "=" + validationValues.asString(), null,
                                            true, "title", "msg",
                                            false, Validation.AlertStyle.WARNING, "alert title", "alert msg");

                                }
                            }
/*                                    book.getInternalBook().addEventListener(
                                            new ModelEventListener() {
                                                @Override
                                                public void onEvent(ModelEvent modelEvent) {
                                                    System.out.println(modelEvent);
                                                    if (modelEvent.getName().equals("onDataValidationContentChange")){
                                                        modelEvent.getObjectId();
                                                    }
                                                }
                                            });*/
                        } catch (Exception e) {
                            Range chosenRange = Ranges.range(sheet, chosen.getRow(), chosen.getColumn(), chosen.getLastRow(), chosen.getLastColumn());
                            chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true, "{\"" + e.getMessage() + "\"}", null,
                                    true, "title", "msg",
                                    false, Validation.AlertStyle.WARNING, "alert title", "alert msg");

                        }
                        numberOfValidationsAdded++;
                    }
                }
            }
        }
    }
}