package com.azquo.spreadsheet.zk;

import com.azquo.admin.user.UserRegionOptions;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import org.zkoss.zss.api.CellOperationUtil;
import org.zkoss.zss.api.Range;
import org.zkoss.zss.api.Ranges;
import org.zkoss.zss.api.model.*;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by edward on 12/01/17.
 * <p>
 * Break off fill code from the old Azquo Book Utils to put in here
 */

class RegionFillerService {
    // as it says. Need to consider the factoring here given the number of parameters passed
    static void fillRowHeadings(LoggedInUser loggedInUser, Sheet sheet, String region, CellRegion displayRowHeadings
            , CellRegion displayDataRegion, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, int cloneCols) {
        int rowHeadingsColumn = displayRowHeadings.getColumn();
        boolean isHierarchy = ReportUtils.isHierarchy(cellsAndHeadingsForDisplay.getRowHeadingsSource());
        int row = displayRowHeadings.getRow();
        int lineNo = 0;
        // why -1?
        int lastTotalRow = row - 1;
        int totalRow = -1;
        List<String> lastRowHeadings = new ArrayList<>(cellsAndHeadingsForDisplay.getRowHeadings().size());
        for (List<String> rowHeading : cellsAndHeadingsForDisplay.getRowHeadings()) {
            int col = displayRowHeadings.getColumn();
            int startCol = col;
            for (String heading : rowHeading) {
                if (heading != null && !heading.equals(".") && (sheet.getInternalSheet().getCell(row, col).getType() != SCell.CellType.STRING || sheet.getInternalSheet().getCell(row, col).getStringValue().isEmpty())) { // as with AzquoBook don't overwrite existing cells when it comes to headings
                    SCell cell = sheet.getInternalSheet().getCell(row, col);
                    cell.setValue(heading);
                    if (lineNo > 0 && lastRowHeadings.get(col - startCol) != null && lastRowHeadings.get(col - startCol).equals(heading)) {
                        //disguise the heading by making foreground colour = background colour
                        Range selection = Ranges.range(sheet, row, col, row, col);
                        CellOperationUtil.applyFontColor(selection, sheet.getInternalSheet().getCell(row, col).getCellStyle().getBackColor().getHtmlColor());
                    }
                }
                col++;
            }
            lineNo++;
            if (isHierarchy && lineNo > 1) {
                int sameValues;
                for (sameValues = 0; sameValues < lastRowHeadings.size(); sameValues++) {
                    if (!rowHeading.get(sameValues).equals(lastRowHeadings.get(sameValues))) {
                        break;
                    }
                }
                //format the row headings for hierarchy.  Each total level has a different format.   clear visible names in all but on eheading
                if (sameValues < rowHeading.size() - 1) {
                    int totalCount = rowHeading.size() - sameValues - 1;
                    //this is a total line
                    int selStart = displayRowHeadings.getColumn();
                    int selEnd = displayDataRegion.getColumn() + displayDataRegion.getColumnCount() - 1 + cloneCols;
                    SCell lineFormat = BookUtils.getSnameCell(sheet.getBook().getInternalBook().getNameByName("az_totalFormat" + totalCount + region));
                    Range selection = Ranges.range(sheet, row - 1, selStart, row - 1, selEnd);
                    Range headingRange = Ranges.range(sheet, row - 1, selStart, row - 1, selStart + displayRowHeadings.getColumnCount() - 1);
                    if (lineFormat == null) {
                        CellOperationUtil.applyFontBoldweight(selection, Font.Boldweight.BOLD);
                    } else {
                        if (totalCount == 1 && totalRow == -1) {
                            totalRow = lineFormat.getRowIndex();
                        }
                        if (totalCount == 1 && totalRow > displayRowHeadings.getRow()) {
                            //copy the total row and the line above to the current position, then copy the line above into the intermediate rows
                            int dataStart = displayDataRegion.getColumn();
                            int copyRows = row - lastTotalRow - 2;
                            if (copyRows > 2) {
                                copyRows = 2;
                            }
                            Range copySource = Ranges.range(sheet, totalRow - 2, dataStart, totalRow, selEnd);
                            Range destination = Ranges.range(sheet, row - copyRows - 1, dataStart, row - 1, selEnd);
                            int tempStart = selEnd + 10;
                            Range tempPos = Ranges.range(sheet, totalRow - 2, tempStart, totalRow, tempStart + selEnd - selStart);
                            if (copyRows == 1) {
                                //delete the middle line
                                CellOperationUtil.delete(Ranges.range(sheet, totalRow - 1, tempStart, totalRow - 1, tempStart + selEnd - selStart), Range.DeleteShift.UP);
                                tempPos = Ranges.range(sheet, totalRow - 2, tempStart, totalRow - 1, tempStart + selEnd - selStart);
                            }
                            CellOperationUtil.paste(copySource, tempPos);
                            CellOperationUtil.cut(tempPos, destination);
                            // is this dangerous? Could it corrupt another part of the sheet?
                            if (row - lastTotalRow > 4) {
                                //zap cells above, and fill in cells below
                                CellOperationUtil.delete(Ranges.range(sheet, lastTotalRow + 1, dataStart, row - 4, selEnd), Range.DeleteShift.UP);
                                CellOperationUtil.insert(Ranges.range(sheet, lastTotalRow + 2, dataStart, row - 3, selEnd), Range.InsertShift.DOWN, Range.InsertCopyOrigin.FORMAT_NONE);
                                CellOperationUtil.paste(Ranges.range(sheet, lastTotalRow + 1, dataStart, lastTotalRow + 1, selEnd), Ranges.range(sheet, lastTotalRow + 2, dataStart, row - 3, selEnd));
                            }
                        }
                        CellOperationUtil.applyBackColor(selection, lineFormat.getCellStyle().getBackColor().getHtmlColor());
                        CellOperationUtil.applyFontColor(selection, lineFormat.getCellStyle().getFont().getColor().getHtmlColor());
                        Range formatRange = Ranges.range(sheet.getBook().getSheet(lineFormat.getSheet().getSheetName()), lineFormat.getRowIndex(), lineFormat.getColumnIndex());
                        CellOperationUtil.pasteSpecial(formatRange, headingRange, Range.PasteType.FORMATS, Range.PasteOperation.NONE, false, false);
                        if (sameValues > 0) {
                            Range blanks = Ranges.range(sheet, row - 1, rowHeadingsColumn, row - 1, rowHeadingsColumn + sameValues - 1);
                            CellOperationUtil.applyFontColor(blanks, sheet.getInternalSheet().getCell(row - 1, rowHeadingsColumn).getCellStyle().getBackColor().getHtmlColor());
                        }
                        SCell first = sheet.getInternalSheet().getCell(row, rowHeadingsColumn);
                        //the last line of the pivot table shows the set name of the first set, which is not very useful!
                        try {
                            if (first.getStringValue().startsWith("az_")) {
                                first.setStringValue("TOTAL");
                            }
                        } catch (Exception ignored) {
                        }
                    }
                    if (row > displayRowHeadings.getRow()) {
                        Ranges.range(sheet, row - 1, selStart + sameValues + 1, row - 1, selStart + displayRowHeadings.getColumnCount() - 1).clearContents();
                    }
                    lastTotalRow = row - 1;
                }
                if (sameValues > 0) {
                    Range selection = Ranges.range(sheet, row, rowHeadingsColumn, row, rowHeadingsColumn + sameValues - 1);
                    //CellOperationUtil.clearStyles(selection);
                    CellOperationUtil.applyFontColor(selection, sheet.getInternalSheet().getCell(row, rowHeadingsColumn).getCellStyle().getBackColor().getHtmlColor());
                }
            }
            lastRowHeadings = rowHeading;
            row++;
        }
        if (isHierarchy) {
            //paste in row headings
            String rowHeadingsString = cellsAndHeadingsForDisplay.getRowHeadingsSource().get(0).get(0);
            if (rowHeadingsString.toLowerCase().startsWith("permute(")) {
                String[] rowHeadings = rowHeadingsString.substring("permute(".length(), rowHeadingsString.length() - 1).split(",");
                int hrow = displayRowHeadings.getRow() - 1;
                int hcol = rowHeadingsColumn;
                for (String rowHeading : rowHeadings) {
                    rowHeading = rowHeading.replace("`", "").trim();
                    if (rowHeading.contains(" sorted")) { // maybe factor the string literal? Need to make it work for the mo
                        rowHeading = rowHeading.substring(0, rowHeading.indexOf(" sorted")).trim();
                    }
                    String colHeading = ChoicesService.multiList(loggedInUser, "az_" + rowHeading, "`" + rowHeading + "` children");
                    if (colHeading == null || colHeading.equals("[all]")) colHeading = rowHeading;
                    BookUtils.setValue(sheet.getInternalSheet().getCell(hrow, hcol++), colHeading);
                }

            }
        }
    }

    //← → ↑ ↓ ↔ ↕ ah I can just paste it here, thanks IntelliJ :)
    static void fillColumnHeadings(Sheet sheet, UserRegionOptions userRegionOptions, CellRegion displayColumnHeadings
            , CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay) {
        int row = displayColumnHeadings.getRow();
        // ignore row is for when the number of rows making up the column headings form the server is greater than the number of rows it's going to fit in
        // in which case use the bottom rows of the column headings
        int ignoreRow = cellsAndHeadingsForDisplay.getColumnHeadings().size() - displayColumnHeadings.getRowCount();
        for (List<String> colHeading : cellsAndHeadingsForDisplay.getColumnHeadings()) {
            String lastHeading = "";
            if (--ignoreRow < 0) {
                boolean columnSort = false;
                if (row - displayColumnHeadings.getRow() == displayColumnHeadings.getRowCount() - 1 && userRegionOptions.getSortable()) { // meaning last row of headings and sortable
                    columnSort = true;
                }
                int col = displayColumnHeadings.getColumn();
                for (String heading : colHeading) {
                    if (heading.equals(lastHeading) || heading.equals(".")) {
                        heading = "";
                    } else {
                        lastHeading = heading;
                    }

                    SCell sCell = sheet.getInternalSheet().getCell(row, col);
                    if (sCell.getType() != SCell.CellType.STRING && sCell.getType() != SCell.CellType.NUMBER) {
                        BookUtils.setValue(sCell, "");
                    } else {//new behaviour - if there's a filled in heading, this can be used to detect the sort.
                        try {
                            if (sCell.getStringValue().length() > 0) {
                                heading = sCell.getStringValue();
                            }
                        } catch (Exception e) {//to catch the rare cases where someone has hardcoded a year into the headings
                            if (sCell.getNumberValue() > 0) {
                                heading = sCell.getNumberValue() + "";
                                if (heading.length() > 5 && heading.endsWith(".0")) {
                                    heading = heading.substring(0, heading.length() - 2);
                                }
                            }
                        }
                    }
                    if (columnSort && !heading.equals(".")) { // don't sort "." headings they are probably derived in the spreadsheet
                        String sortArrow = " ↕";
                        if ((((col - displayColumnHeadings.getColumn()) + 1) + "").equals(userRegionOptions.getSortColumn())) {
                            sortArrow = userRegionOptions.getSortColumnAsc() ? " ↑" : " ↓";
                        }
                        if (!sCell.getStringValue().contains(sortArrow)) {
                            if (sCell.getType() == SCell.CellType.STRING && sCell.getStringValue().isEmpty()) {
                                sCell.setValue(heading + sortArrow);
                            } else {
                                sCell.setValue(sCell.getValue() + sortArrow);
                            }
                        }
                        String value = sCell.getStringValue();
                        value = value.substring(0, value.length() - 2);
                        Range chosenRange = Ranges.range(sheet, row, col, row, col);
                        // todo, investigate how commas would fit in in a heading name
                        // think I'll just zap em for the mo.
                        value = value.replace(",", "");
                        chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true,
                                value + " ↕," + value + " ↑," + value + " ↓", null,
                                true, "Select Sorting", "",
                                true, Validation.AlertStyle.WARNING, "Sort Column", "This is a sortable column, its value should not be manually altered.");
                    } else {
                        if (heading != null && (!sCell.getType().equals(SCell.CellType.NUMBER) && (sCell.isNull() || sCell.getStringValue().length() == 0))) { // vanilla, overwrite if not
                            sCell.setValue(heading);
                        }
                    }
                    col++;
                }
                row++;
            }
        }
        // for the moment don't allow user column sorting (row heading sorting). Shouldn't be too difficult to add
/*                if (sortable != null && sortable.equalsIgnoreCase("all")) { // criteria from azquobook to make row heading sortable
                }*/
    }

    // a lot of objets passed. Mainly to deal with the repeat scope stuff
    static void fillData(LoggedInUser loggedInUser, int reportId, Sheet sheet, String region, UserRegionOptions userRegionOptions, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, CellRegion displayDataRegion
            , SName rowHeadingDescription, SName columnHeadingsDescription, SName contextDescription, int maxCol, int valueId, boolean quiet) throws Exception {
        // the repeat code used to be split, having examined it this doesn't really make sense, it can all be dealt with in a block after
        // note - this means the repeatRegion may have been expanded but I think this makes sense - before if it wasn't big enough it would break
        // won't be properly tested until we need it again
        // the region to be repeated, will contain headings and an item which changes for each repetition
        SName repeatRegion = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATREGION + region);
        // the target space we can repeat into. Can expand down but not across
        SName repeatScope = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATSCOPE + region);
        // the list of items we can repeat over
        SName repeatList = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATLIST + region);
        // the cell we'll put the items in the list in
        SName repeatItem = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATITEM + region);

        if (repeatRegion != null && repeatScope != null && repeatList != null && repeatItem != null) { // then the repeat thing
            int repeatRegionWidth = repeatRegion.getRefersToCellRegion().getColumnCount();
            int repeatScopeWidth = repeatScope.getRefersToCellRegion().getColumnCount();
            int repeatRegionHeight = repeatRegion.getRefersToCellRegion().getRowCount();
            int repeatScopeHeight = repeatScope.getRefersToCellRegion().getRowCount();
            int repeatColumns = repeatScopeWidth / repeatRegionWidth;
            // we'll need the relative location of the item to populate it in each instance
            int rootRow = repeatRegion.getRefersToCellRegion().getRow();
            int rootCol = repeatRegion.getRefersToCellRegion().getColumn();
            int repeatItemRowOffset = repeatItem.getRefersToCellRegion().getRow() - repeatRegion.getRefersToCellRegion().getRow();
            int repeatItemColumnOffset = repeatItem.getRefersToCellRegion().getColumn() - repeatRegion.getRefersToCellRegion().getColumn();

            String repeatListText = BookUtils.getSnameCell(repeatList).getStringValue();
            List<String> repeatListItems = CommonReportUtils.getDropdownListForQuery(loggedInUser, repeatListText);
            // so the expansion within each region will be dealt with by fill region internally but out here I need to ensure that there's enough space for the regions unexpanded
            int repeatRowsRequired = repeatListItems.size() / repeatColumns;
            if (repeatListItems.size() % repeatColumns > 0) {
                repeatRowsRequired++;
            }
            int rowsRequired = repeatRowsRequired * repeatRegionHeight;
            if (rowsRequired > repeatScopeHeight) {
                // slight copy paste from above, todo factor?
                int rowsToAdd = rowsRequired - repeatScopeHeight;
                int insertRow = repeatScope.getRefersToCellRegion().getRow() + repeatScope.getRefersToCellRegion().getRowCount() - 1; // last row, inserting here shifts the row down - should it be called last row? -1 on the row count as rows are inclusive. Rows 3-4 is not 1 row it's 2!
//                int insertRow = repeatScope.getRefersToCellRegion().getLastRow(); // last row (different API call, was using the firt row plus the row count - 1, a simple mistake?)
                Range copySource = Ranges.range(sheet, insertRow - 1, 0, insertRow - 1, maxCol);
                Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1, maxCol); // insert at the 3rd row - rows to add - 1 as rows are inclusive
                CellOperationUtil.insertRow(insertRange);
                CellOperationUtil.paste(copySource, insertRange);
                int originalHeight = sheet.getInternalSheet().getRow(insertRow - 1).getHeight();
                if (originalHeight != sheet.getInternalSheet().getRow(insertRow).getHeight()) { // height may not match on insert
                    insertRange.setRowHeight(originalHeight); // hopefully set the lot in one go??
                }
                // and do hidden
                boolean hidden = sheet.getInternalSheet().getRow(insertRow - 1).isHidden();
                if (hidden) {
                    for (int row = insertRange.getRow(); row <= insertRange.getLastRow(); row++) {
                        sheet.getInternalSheet().getRow(row).setHidden(true);
                    }
                }
            }
            // so the space should be prepared for multi - now straight into
            int repeatColumn = 0;
            int repeatRow = 0;
            // work out where the data region is relative to the repeat region, we wait until now as it may have been expanded
            int repeatDataRowOffset = displayDataRegion.getRow() - repeatRegion.getRefersToCellRegion().getRow();
            int repeatDataColumnOffset = displayDataRegion.getColumn() - repeatRegion.getRefersToCellRegion().getColumn();
            int repeatDataLastRowOffset = displayDataRegion.getLastRow() - repeatRegion.getRefersToCellRegion().getRow();
            int repeatDataLastColumnOffset = displayDataRegion.getLastColumn() - repeatRegion.getRefersToCellRegion().getColumn();

            Range copySource = Ranges.range(sheet, rootRow, rootCol, repeatRegion.getRefersToCellRegion().getLastRow(), repeatRegion.getRefersToCellRegion().getLastColumn());
            for (String item : repeatListItems) {
                Range insertRange = Ranges.range(sheet, rootRow + (repeatRegionHeight * repeatRow), rootCol + (repeatRegionWidth * repeatColumn), rootRow + (repeatRegionHeight * repeatRow) + repeatRegionHeight - 1, rootCol + (repeatRegionWidth * repeatColumn) + repeatRegionWidth - 1);
                if (repeatRow > 0 || repeatColumn > 0) { // no need to paste over the first
                    CellOperationUtil.paste(copySource, insertRange);
                }
                // and set the item
                BookUtils.setValue(sheet.getInternalSheet().getCell(rootRow + (repeatRegionHeight * repeatRow) + repeatItemRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatItemColumnOffset), item);
                repeatColumn++;
                if (repeatColumn == repeatColumns) { // zap if back to the first column
                    repeatColumn = 0;
                    repeatRow++;
                }
            }
            // reset tracking among the repeat regions
            repeatColumn = 0;
            repeatRow = 0;
            // and now do the data, separate loop otherwise I'll be copy/pasting data
            for (String item : repeatListItems) {
                //REPEAT REGION METHODOLOGY CHANGED BY WFC 27 Feb 17
                //now looks for row and col headings potentially within the repeat region
                //contextList.add(Collections.singletonList(item)); // item added to the context
                int colOffset = repeatRegionWidth * repeatColumn;
                int rowOffset = repeatRegionHeight * repeatRow;
                List<List<String>> rowHeadingList = BookUtils.nameToStringLists(rowHeadingDescription, repeatRegion, rowOffset, colOffset);
                List<List<String>>contextList = BookUtils.nameToStringLists(contextDescription, repeatRegion, rowOffset, colOffset);
                List<List<String>>columnHeadingList = BookUtils.nameToStringLists(columnHeadingsDescription, repeatRegion, rowOffset, colOffset);

                // yes this is little inefficient as the headings are being resolved each time but that's just how it is for the moment
                cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), region, valueId, rowHeadingList, columnHeadingList,
                        contextList, userRegionOptions, quiet);
                displayDataRegion = new CellRegion(rootRow + (repeatRegionHeight * repeatRow) + repeatDataRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatDataColumnOffset
                        , rootRow + (repeatRegionHeight * repeatRow) + repeatDataLastRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatDataLastColumnOffset);
                loggedInUser.setSentCells(reportId, region + "-" + repeatRow + "-" + repeatColumn, cellsAndHeadingsForDisplay); // todo- perhaps address that this is a bit of a hack!
                RegionFillerService.fillData(sheet, cellsAndHeadingsForDisplay, displayDataRegion);
                contextList.remove(contextList.size() - 1); // item removed from the context
                repeatColumn++;
                if (repeatColumn == repeatColumns) { // zap if back to the first column
                    repeatColumn = 0;
                    repeatRow++;
                }
            }
        } else {
            fillData(sheet, cellsAndHeadingsForDisplay, displayDataRegion);
        }
    }

    // lower level, ignores repeat regions
    private static void fillData(Sheet sheet, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, CellRegion displayDataRegion) {
        int row = displayDataRegion.getRow();
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
                        if (cell.getType() != SCell.CellType.FORMULA) {
                            if (cell.getCellStyle().getFont().getName().equalsIgnoreCase("Code EAN13")) { // then a special case, need to use barcode encoding
                                cell.setValue(SpreadsheetService.prepareEAN13Barcode(cellValue.getStringValue()));// guess we'll see how that goes!
                            } else {
                                BookUtils.setValue(cell, cellValue.getStringValue());
                            }
                        }
                        // see if this works for highlighting
                        if (cellValue.isHighlighted()) {
                            CellOperationUtil.applyFontColor(Ranges.range(sheet, row, col), "#FF0000");
                        }
                        // commented for the moment, requires the overall unlock per sheet followed by the protect later
                        if (cellValue.isLocked()) {
                            Range selection = Ranges.range(sheet, row, col);
                            CellStyle oldStyle = selection.getCellStyle();
                            EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                            newStyle.setLocked(true);
                            selection.setCellStyle(newStyle);
                        }
                    }
                    col++;
                    localCol++;
                }
                row++;
            }
        }
    }
}