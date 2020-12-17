package com.azquo.spreadsheet.zk;

import com.azquo.StringLiterals;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import io.keikai.api.CellOperationUtil;
import io.keikai.api.Range;
import io.keikai.api.Ranges;
import io.keikai.api.model.*;
import io.keikai.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by edward on 12/01/17.
 * <p>
 * Break off fill code from the old Azquo Book Utils to put in here
 */

class RegionFillerService {
    // as it says. Need to consider the factoring here given the number of parameters passed
    // this had clonecols which was added to "selection" to make it wider, this made no sense and has been removed
    static void fillRowHeadings(LoggedInUser loggedInUser, Sheet sheet, String region, CellRegion displayRowHeadings
            , CellRegion displayDataRegion, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, UserRegionOptions userRegionOptions) throws Exception {
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
            int rowHeadingCount = cellsAndHeadingsForDisplay.getRowHeadings().get(0).size();
            int permuteTotalCount = userRegionOptions.getPermuteTotalCount();
            if (permuteTotalCount>= rowHeadingCount){
                permuteTotalCount = rowHeadingCount - 1;
            }
            // this is, I believe, simply code to stop duplicates on multi level headings. E.g. 2019 and months you don't want to say 2019 12 times just once
            for (String heading : rowHeading) {
                if (heading != null && !heading.equals(".") && (sheet.getInternalSheet().getCell(row, col).getType() != SCell.CellType.STRING || sheet.getInternalSheet().getCell(row, col).getStringValue().isEmpty())) { // as with AzquoBook don't overwrite existing cells when it comes to headings
                    SCell cell = sheet.getInternalSheet().getCell(row, col);
                    cell.setValue(heading);
                    if (permuteTotalCount> 1) {
                        if (lineNo > 0 && lastRowHeadings.size() > col - startCol && lastRowHeadings.get(col - startCol) != null && lastRowHeadings.get(col - startCol).equals(heading)) {
                            //disguise the heading by making foreground colour = background colour
                            Range selection = Ranges.range(sheet, row, col, row, col);
                            CellOperationUtil.applyFontColor(selection, sheet.getInternalSheet().getCell(row, col).getCellStyle().getBackColor().getHtmlColor());
                        }
                    }
                }
                col++;
            }
            lineNo++;
            if (isHierarchy && lineNo > 1 && permuteTotalCount > 0) {
                int sameValues;
                for (sameValues = 0; sameValues < lastRowHeadings.size(); sameValues++) {
                    if (!rowHeading.get(sameValues).equals(lastRowHeadings.get(sameValues))) {
                        break;
                    }
                }
                //format the row headings for hierarchy.  Each total level has sa different format.   clear visible names in all but on heading
                // as in is there more than one difference between these headings and the one above? If so it's a total - highlight bold or a more complex option if the AZTOTALFORMAT named region is there
                if (sameValues < rowHeadingCount - 1) {
                    int totalCount = rowHeading.size() - sameValues - 1;
                    //this is a total line
                    int selStart = displayRowHeadings.getColumn();
                    int selEnd = displayDataRegion.getColumn() + displayDataRegion.getColumnCount() - 1;
                    SCell lineFormat = BookUtils.getSnameCell(sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZTOTALFORMAT + totalCount + region));
                    Range selection = Ranges.range(sheet, row - 1, selStart, row - 1, selEnd);
                    Range headingRange = Ranges.range(sheet, row - 1, selStart, row - 1, selStart + displayRowHeadings.getColumnCount() - 1);
                    if (lineFormat == null) {
                        CellOperationUtil.applyFontBoldweight(selection, Font.Boldweight.BOLD);
                        // to be consistent with the plugin the default also will make the background blue
                        CellOperationUtil.applyBackColor(selection, "75bee9");
                    } else {
                        if (totalCount == 1 && totalRow == -1) {
                            totalRow = lineFormat.getRowIndex();
                        }
                        // EFC commenting the block below - it is breaking LSB and I can't see what it is for. Since it relates to formatting commenting it won't break any figures
                        // the break was that a line below the data region was being pulled into the data region so do NOT just uncomment this code, it will break things
                        // Svn 1892 the comment from WFC was Will accept formulae based on local totals in pivot tables
                        /*if (totalCount == 1 && totalRow > displayRowHeadings.getRow()) {
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
                        }*/
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
                    if (sameValues >= permuteTotalCount){
                        sheet.getInternalSheet().getRow(row-1).setHidden(true);
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
                       if (rowHeading.contains(" sorted")) { // maybe factor the string literal? Need to make it work for the mo
                        rowHeading = rowHeading.substring(0, rowHeading.indexOf(" sorted")).trim();
                    }
                    // create the filter set, it may not exist which would error below
                    //first check if permute has used a temporary set...
                    if (rowHeading.toLowerCase().contains(" as ")){
                        String tempFilterName = rowHeading.substring(rowHeading.toLowerCase().indexOf(" as ")+ 4).trim();
                        if (tempFilterName.startsWith(StringLiterals.QUOTE+"")&& tempFilterName.endsWith(StringLiterals.QUOTE + "")){
                            rowHeading = tempFilterName;
                        }
                    }
                    rowHeading = rowHeading.replace(StringLiterals.QUOTE + "", "").trim();
                    RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).createFilterSet(loggedInUser.getDataAccessToken(), "az_" + rowHeading, loggedInUser.getUser().getEmail(), null);
                    String colHeading = ChoicesService.multiList(loggedInUser, "az_" + rowHeading, "`" + rowHeading + "` children");
                    if (colHeading.equals("[all]")) colHeading = rowHeading;
                    BookUtils.setValue(sheet.getInternalSheet().getCell(hrow, hcol++), colHeading);
                }
            }
        }
        // for some reason the last row wasn't notifying correctly! I don't know why though it is rather concerning. Anyway, add 1 to the last row  here . . .
        Ranges.range(sheet, displayRowHeadings.getRow(), displayRowHeadings.getColumn(), displayRowHeadings.lastRow + 1, displayRowHeadings.lastColumn).notifyChange();
    }

    //← → ↑ ↓ ↔ ↕ ah I can just paste it here, thanks IntelliJ :)
    static void fillColumnHeadings(Sheet sheet, UserRegionOptions userRegionOptions, CellRegion displayColumnHeadings
            , CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay) {
        int row = displayColumnHeadings.getRow();
        // ignore row is for when the number of rows making up the column headings form the server is greater than the number of rows it's going to fit in
        // in which case use the bottom rows of the column headings
        int ignoreRow = cellsAndHeadingsForDisplay.getColumnHeadings().size() - displayColumnHeadings.getRowCount();
        // todo stop showing sorting where it's not relevant
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
                    if (sCell.getType() != SCell.CellType.STRING && sCell.getType() != SCell.CellType.NUMBER && sCell.getType()!=SCell.CellType.FORMULA) {
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
                    if (columnSort && !heading.equals(".") && !heading.isEmpty()) { // don't sort "." headings they are probably derived in the spreadsheet
                        String sortArrow = "↕ ";
                        if ((((col - displayColumnHeadings.getColumn()) + 1) + "").equals(userRegionOptions.getSortColumn())) {
                            sortArrow = userRegionOptions.getSortColumnAsc() ? "↑ " : "↓ ";
                        }
                        if (!sCell.getStringValue().contains(sortArrow)) {
                            if (sCell.getType() == SCell.CellType.STRING && sCell.getStringValue().isEmpty()) {
                                sCell.setValue(sortArrow + heading);
                            } else {
                                sCell.setValue(sortArrow + sCell.getValue());
                            }
                        }
                        String value = sCell.getStringValue();
                        value = value.substring(2);
                        Range chosenRange = Ranges.range(sheet, row, col, row, col);
                        // todo, investigate how commas would fit in in a heading name
                        // think I'll just zap em for the mo.
                        value = value.replace(",", "");
                        chosenRange.setValidation(Validation.ValidationType.LIST, false, Validation.OperatorType.EQUAL, true,
                                "↕ " + value + ",↑ " + value + ",↓ " + value, null,
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
// should it be lastrow + 1 as with display row headings? Or + 1 on the cols?? Watch for this
        Ranges.range(sheet, displayColumnHeadings.getRow(), displayColumnHeadings.getColumn(), displayColumnHeadings.lastRow, displayColumnHeadings.lastColumn).notifyChange();
    }

    // now dedicated to just repeat regions - if the regions aren't there it will NPE, I think this is correct
    // crucial to the repeat regions is that the top left region, before being copied around, is a useful size for all variations
    static void fillDataForRepeatRegions(LoggedInUser loggedInUser, int reportId, Sheet sheet, String region, UserRegionOptions userRegionOptions
            , CellRegion displayRowHeadings, CellRegion displayColumnHeadings, CellRegion displayDataRegion, SName rowHeadingDescription
            , SName columnHeadingsDescription, SName contextDescription, int maxRow, int valueId, boolean quiet, Set<String> repeatRegionTracker) throws Exception {
        // note - this means the repeatRegion may have been expanded but I think this makes sense - before if it wasn't big enough it would break
        // won't be properly tested until we need it again
        // the region to be repeated, will contain headings and an item which changes for each repetition
        final SName repeatRegion = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATREGION + region);
        // the target space we can repeat into. Can expand down but not across
        final SName repeatScope = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATSCOPE + region);
        // the list of items we can repeat over
        final SName repeatList = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATLIST + region);
        // the cell we'll put the items in the list in
        final SName repeatItem = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATITEM + region);


        // the list of items we can repeat over
        final SName repeatList2 = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATLIST + "2" + region);
        // the cell we'll put the items in the list in
        final SName repeatItem2 = sheet.getBook().getInternalBook().getNameByName(ReportRenderer.AZREPEATITEM + "2" + region);

        /*
        new criteria - if we have repeatList2 and repeatItem2 then repeatList and repeatItem define the columns in terms of repeat regions
        and repeatList2 and repeatItem2 are the rows. Normally the repeatScope will only extend downward but under these circumstances it
        will be to the right and down to accomodate the criteria
        */

        final int repeatRegionWidth = repeatRegion.getRefersToCellRegion().getColumnCount();
        final int repeatScopeWidth = repeatScope.getRefersToCellRegion().getColumnCount();
        final int repeatRegionHeight = repeatRegion.getRefersToCellRegion().getRowCount();
        final int repeatScopeHeight = repeatScope.getRefersToCellRegion().getRowCount();
        // we'll need the relative location of the item to populate it in each instance
        final int rootRow = repeatRegion.getRefersToCellRegion().getRow();
        final int rootCol = repeatRegion.getRefersToCellRegion().getColumn();
        final int repeatItemRowOffset = repeatItem.getRefersToCellRegion().getRow() - repeatRegion.getRefersToCellRegion().getRow();
        final int repeatItemColumnOffset = repeatItem.getRefersToCellRegion().getColumn() - repeatRegion.getRefersToCellRegion().getColumn();

        int repeatItem2RowOffset = 0;
        int repeatItem2ColumnOffset = 0;

        String repeatListText = BookUtils.getSnameCell(repeatList).getStringValue();
        List<String> repeatListItems = CommonReportUtils.getDropdownListForQuery(loggedInUser, repeatListText);
        List<String> repeatListItems2 = null;
        int repeatColumns;
        int rowsRequired = 0;
        if (repeatList2 != null && repeatItem2 != null) { // new cols x rows according to two repeat lists logic
            repeatItem2RowOffset = repeatItem2.getRefersToCellRegion().getRow() - repeatRegion.getRefersToCellRegion().getRow();
            repeatItem2ColumnOffset = repeatItem2.getRefersToCellRegion().getColumn() - repeatRegion.getRefersToCellRegion().getColumn();
            String repeatListText2 = BookUtils.getSnameCell(repeatList2).getStringValue();
            repeatListItems2 = CommonReportUtils.getDropdownListForQuery(loggedInUser, repeatListText2);
            // ok first make sure the repeatscope is wide enough
            repeatColumns = repeatListItems.size();
            int columnsRequired =  repeatColumns * repeatRegionWidth;
            // only expand repeat scope width in here - not required under a vanilla repeat region
            if (columnsRequired > repeatScopeWidth) {
                int columnsToAdd = columnsRequired - repeatScopeWidth;
                int insertCol = repeatScope.getRefersToCellRegion().getColumn() + repeatScope.getRefersToCellRegion().getColumnCount() - 1; // I think this is correct, just after the second column?
                Range insertRange = Ranges.range(sheet, 0, insertCol, maxRow, insertCol + columnsToAdd - 1); // insert just before the last col
                CellOperationUtil.insertColumn(insertRange);
            }
            rowsRequired = repeatListItems2.size() * repeatRegionHeight;
        } else { // old logic -
            // so the expansion within each region will be dealt with by fill region internally but out here I need to ensure that there's enough space for the regions
            repeatColumns = repeatScopeWidth / repeatRegionWidth; // as many as can fit in the required size
            int repeatRowsRequired = repeatListItems.size() / repeatColumns;
            if (repeatListItems.size() % repeatColumns > 0) {
                repeatRowsRequired++;
            }
            rowsRequired = repeatRowsRequired * repeatRegionHeight;
        }

        if (rowsRequired > repeatScopeHeight) {
            int rowsToAdd = rowsRequired - repeatScopeHeight;
            int insertRow = repeatScope.getRefersToCellRegion().getRow() + repeatScope.getRefersToCellRegion().getRowCount() - 1; // last row, inserting here shifts the row down - should it be called last row? -1 on the row count as rows are inclusive. Rows 3-4 is not 1 row it's 2!
            Range insertRange = Ranges.range(sheet, insertRow, 0, insertRow + rowsToAdd - 1, 0).toRowRange(); // rows to add - 1 as rows are inclusive
            CellOperationUtil.insertRow(insertRange);
        }
        // a nasty bug WFC discovered - if the repeat scope isn't bigger than the repeat region then the repeat region may have been stretched!

        // so there should be enough space now the thing is to format the space
        int repeatColumn = 0;
        int repeatRow = 0;
        // work out where the data region is relative to the repeat region, we wait until now as it may have been expanded
        final int repeatDataRowOffset = displayDataRegion.getRow() - repeatRegion.getRefersToCellRegion().getRow();
        final int repeatDataColumnOffset = displayDataRegion.getColumn() - repeatRegion.getRefersToCellRegion().getColumn();
        final int repeatDataLastRowOffset = displayDataRegion.getLastRow() - repeatRegion.getRefersToCellRegion().getRow();
        final int repeatDataLastColumnOffset = displayDataRegion.getLastColumn() - repeatRegion.getRefersToCellRegion().getColumn();

        // a nasty bug WFC discovered - if the repeat scope isn't bigger than the repeat region then the repeat region may have been stretched!
//        Range copySource = Ranges.range(sheet, rootRow, rootCol, repeatRegion.getRefersToCellRegion().getLastRow(), repeatRegion.getRefersToCellRegion().getLastColumn());
        Range copySource = Ranges.range(sheet, rootRow, rootCol, rootRow + repeatRegionHeight - 1, rootCol + repeatRegionWidth - 1);
        boolean copyFormatting = repeatRegionTracker.add(copySource.asString()); // crude but the point is : only copy formatting if we've not copied from this range before
/*        System.out.println("1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n");
        System.out.println("copyformatting : " + copyFormatting + " region " + region);
        System.out.println(copySource.asString());
        System.out.println("1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n1\n");*/
        // prepare the sapce for the data, it may have things like formulae
        if (repeatList2 != null && repeatItem2 != null) { // new cols x rows according to two repeat lists logic
            for (String item2 : repeatListItems2) { // rows are the second one, columns first
                for (String item : repeatListItems) {
                    if (copyFormatting){
                        Range insertRange = Ranges.range(sheet, rootRow + (repeatRegionHeight * repeatRow), rootCol + (repeatRegionWidth * repeatColumn), rootRow + (repeatRegionHeight * repeatRow) + repeatRegionHeight - 1, rootCol + (repeatRegionWidth * repeatColumn) + repeatRegionWidth - 1);
                        if (repeatRow > 0 || repeatColumn > 0) { // no need to paste over the first
                            CellOperationUtil.paste(copySource, insertRange);
                            if (repeatRow == 0) { // then it's the top line after the first - need to adjust column widths and hidden
                                for (int colOffest = 0; colOffest < copySource.getColumnCount(); colOffest++) {
                                    // copy width and hidden
                                    final SColumn sourceCol = sheet.getInternalSheet().getColumn(rootCol + colOffest);
                                    final SColumn newCol = sheet.getInternalSheet().getColumn(rootCol + (repeatRegionWidth * repeatColumn) + colOffest);
                                    newCol.setWidth(sourceCol.getWidth());
                                    newCol.setHidden(sourceCol.isHidden());
                                }
                            }
                            if (repeatColumn == 0) { // then it's the first column after the first row - need to adjust row heights and hidden
                                for (int rowOffest = 0; rowOffest < copySource.getRowCount(); rowOffest++) {
                                    // copy width and hidden
                                    final SRow sourceRow = sheet.getInternalSheet().getRow(rootRow + rowOffest);
                                    final SRow newRow = sheet.getInternalSheet().getRow(rootRow + (repeatRegionHeight * repeatRow) + rowOffest);
                                    newRow.setHeight(sourceRow.getHeight());
                                    newRow.setHidden(sourceRow.isHidden());
                                }
                            }
                        }
                    }
                    // and set the item
                    BookUtils.setValue(sheet.getInternalSheet().getCell(rootRow + (repeatRegionHeight * repeatRow) + repeatItemRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatItemColumnOffset), item);
                    Ranges.range(sheet, rootRow + (repeatRegionHeight * repeatRow) + repeatItemRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatItemColumnOffset).notifyChange();
                    // and set item2!
                    BookUtils.setValue(sheet.getInternalSheet().getCell(rootRow + (repeatRegionHeight * repeatRow) + repeatItem2RowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatItem2ColumnOffset), item2);
                    Ranges.range(sheet, rootRow + (repeatRegionHeight * repeatRow) + repeatItem2RowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatItem2ColumnOffset).notifyChange();
                    repeatColumn++;
                }
                repeatColumn = 0;
                repeatRow++;
            }
        } else {
            for (String item : repeatListItems) {
                if (copyFormatting) {
                    Range insertRange = Ranges.range(sheet, rootRow + (repeatRegionHeight * repeatRow), rootCol + (repeatRegionWidth * repeatColumn), rootRow + (repeatRegionHeight * repeatRow) + repeatRegionHeight - 1, rootCol + (repeatRegionWidth * repeatColumn) + repeatRegionWidth - 1);
                    if (repeatRow > 0 || repeatColumn > 0) { // no need to paste over the first
                        // I agree with intelliJ, could factor! later. todo
                        CellOperationUtil.paste(copySource, insertRange);
                        if (repeatRow == 0) { // then it's the top line after the first - need to adjust column widths and hidden
                            for (int colOffest = 0; colOffest < copySource.getColumnCount(); colOffest++) {
                                // copy width and hidden
                                final SColumn sourceCol = sheet.getInternalSheet().getColumn(rootCol + colOffest);
                                final SColumn newCol = sheet.getInternalSheet().getColumn(rootCol + (repeatRegionWidth * repeatColumn) + colOffest);
                                newCol.setWidth(sourceCol.getWidth());
                                newCol.setHidden(sourceCol.isHidden());
                            }
                        }
                        if (repeatColumn == 0) { // then it's the first column after the first row - need to adjust row heights and hidden
                            for (int rowOffest = 0; rowOffest < copySource.getRowCount(); rowOffest++) {
                                // copy width and hidden
                                final SRow sourceRow = sheet.getInternalSheet().getRow(rootRow + rowOffest);
                                final SRow newRow = sheet.getInternalSheet().getRow(rootRow + (repeatRegionHeight * repeatRow) + rowOffest);
                                newRow.setHeight(sourceRow.getHeight());
                                newRow.setHidden(sourceRow.isHidden());
                            }
                        }
                    }
                }
                // and set the item
                BookUtils.setValue(sheet.getInternalSheet().getCell(rootRow + (repeatRegionHeight * repeatRow) + repeatItemRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatItemColumnOffset), item);
                Ranges.range(sheet, rootRow + (repeatRegionHeight * repeatRow) + repeatItemRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatItemColumnOffset).notifyChange();
                repeatColumn++;
                if (repeatColumn == repeatColumns) { // zap if back to the first column
                    repeatColumn = 0;
                    repeatRow++;
                }
            }
        }
        // reset tracking among the repeat regions
        repeatColumn = 0;
        repeatRow = 0;
        // and now do the data, separate loop otherwise I'll be copy/pasting data from the first area
        // requirement from WFC - only show first display row/col headings if they're outside the repeat region
        boolean showColumnHeadings = displayColumnHeadings != null;
        boolean showRowHeadings = displayRowHeadings != null;
        if (repeatList2 != null && repeatItem2 != null) { // new cols x rows according to two repeat lists logic
            for (String item2 : repeatListItems2) {
                for (String item : repeatListItems) {
                    repeatRegionFill(loggedInUser, reportId, sheet, region, userRegionOptions,  showRowHeadings ? displayRowHeadings : null, showColumnHeadings ? displayColumnHeadings : null,rowHeadingDescription, columnHeadingsDescription, contextDescription, valueId, quiet, repeatRegion, repeatRegionWidth, repeatRegionHeight, rootRow, rootCol, repeatColumn, repeatRow, repeatDataRowOffset, repeatDataColumnOffset, repeatDataLastRowOffset, repeatDataLastColumnOffset);
                    // after the first time check to see if we should keep showing the row headings or column headings
                    if (repeatColumn == 0 && repeatRow == 0){
                        if (showColumnHeadings && displayColumnHeadings.row < repeatRegion.getRefersToCellRegion().row){
                            showColumnHeadings = false;
                        }
                        if (showRowHeadings && displayRowHeadings.column < repeatRegion.getRefersToCellRegion().column){
                            showRowHeadings = false;
                        }
                    }
                    repeatColumn++;
                }
                repeatColumn = 0;
                repeatRow++;
            }
        } else {
            for (String item : repeatListItems) {
                repeatRegionFill(loggedInUser, reportId, sheet, region, userRegionOptions,  showRowHeadings ? displayRowHeadings : null, showColumnHeadings ? displayColumnHeadings : null, rowHeadingDescription, columnHeadingsDescription, contextDescription, valueId, quiet, repeatRegion, repeatRegionWidth, repeatRegionHeight, rootRow, rootCol, repeatColumn, repeatRow, repeatDataRowOffset, repeatDataColumnOffset, repeatDataLastRowOffset, repeatDataLastColumnOffset);
                // after the first time check to see if we should keep showing the row headings or column headings
                if (repeatColumn == 0 && repeatRow == 0){
                    if (showColumnHeadings && displayColumnHeadings.row < repeatRegion.getRefersToCellRegion().row){
                        showColumnHeadings = false;
                    }
                    if (showRowHeadings && displayRowHeadings.column < repeatRegion.getRefersToCellRegion().column){
                        showRowHeadings = false;
                    }
                }
                repeatColumn++;
                if (repeatColumn == repeatColumns) { // zap if back to the first column
                    repeatColumn = 0;
                    repeatRow++;
                }
            }
        }
    }

    /* ok passing this number of fields isn't great, it's due to an extracted function now the repeat region code has two modes based on whether there are two lists
     one for x and one for y or just one list. Could reduce params further, might mean repeatedly deducing e.g repeat region height but the performance hit would be
     minimal and code would be cleaner. Todo
     */


    private static void repeatRegionFill(LoggedInUser loggedInUser, int reportId, Sheet sheet, String region, UserRegionOptions userRegionOptions
            , CellRegion displayRowHeadings, CellRegion displayColumnHeadings, SName rowHeadingDescription, SName columnHeadingsDescription, SName contextDescription
            , int valueId, boolean quiet, SName repeatRegion, int repeatRegionWidth, int repeatRegionHeight, int rootRow
            , int rootCol, int repeatColumn, int repeatRow, int repeatDataRowOffset, int repeatDataColumnOffset
            , int repeatDataLastRowOffset, int repeatDataLastColumnOffset) throws Exception {
        //REPEAT REGION METHODOLOGY CHANGED BY WFC 27 Feb 17
        //now looks for row and col headings potentially within the repeat region - before it assumed they'd be the same each time now this is not true, headings could be dependant on the repeat item
        // hence the offset being passed to the name to string lists
        int colOffset = repeatRegionWidth * repeatColumn;
        int rowOffset = repeatRegionHeight * repeatRow;
        List<List<String>> rowHeadingList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser,rowHeadingDescription, repeatRegion, rowOffset, colOffset);
        List<List<String>> contextList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser, contextDescription, repeatRegion, rowOffset, colOffset);
        List<List<String>> columnHeadingList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser,columnHeadingsDescription, repeatRegion, rowOffset, colOffset);
        CellRegion displayDataRegion = new CellRegion(rootRow + (repeatRegionHeight * repeatRow) + repeatDataRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatDataColumnOffset
                , rootRow + (repeatRegionHeight * repeatRow) + repeatDataLastRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatDataLastColumnOffset);

        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser, region, valueId, rowHeadingList, columnHeadingList,
                contextList, userRegionOptions, quiet, null);
        // so I now need to do the row and column headings also
        if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) {
            // yes, these offsets are being calculated every time but the performance save of finding them first would be minimal and would make the code more complex now this chunk is factored off
            int repeatRowHeadingsRowOffset = displayRowHeadings.getRow() - repeatRegion.getRefersToCellRegion().getRow();
            int repeatRowHeadingsColumnOffset = displayRowHeadings.getColumn() - repeatRegion.getRefersToCellRegion().getColumn();
            int repeatRowHeadingsLastRowOffset = displayRowHeadings.getLastRow() - repeatRegion.getRefersToCellRegion().getRow();
            int repeatRowHeadingsLastColumnOffset = displayRowHeadings.getLastColumn() - repeatRegion.getRefersToCellRegion().getColumn();
            RegionFillerService.fillRowHeadings(loggedInUser, sheet, region,
                    // display row headings region practically speaking
                    new CellRegion(rootRow + (repeatRegionHeight * repeatRow) + repeatRowHeadingsRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatRowHeadingsColumnOffset
                            , rootRow + (repeatRegionHeight * repeatRow) + repeatRowHeadingsLastRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatRowHeadingsLastColumnOffset)
                    , displayDataRegion, cellsAndHeadingsForDisplay, userRegionOptions);
        }
        if (displayColumnHeadings != null) {
            int repeatColumnHeadingsRowOffset = displayColumnHeadings.getRow() - repeatRegion.getRefersToCellRegion().getRow();
            int repeatColumnHeadingsColumnOffset = displayColumnHeadings.getColumn() - repeatRegion.getRefersToCellRegion().getColumn();
            int repeatColumnHeadingsLastRowOffset = displayColumnHeadings.getLastRow() - repeatRegion.getRefersToCellRegion().getRow();
            int repeatColumnHeadingsLastColumnOffset = displayColumnHeadings.getLastColumn() - repeatRegion.getRefersToCellRegion().getColumn();
            RegionFillerService.fillColumnHeadings(sheet, userRegionOptions,
                    // display column headings region practically speaking
                    new CellRegion(rootRow + (repeatRegionHeight * repeatRow) + repeatColumnHeadingsRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatColumnHeadingsColumnOffset
                            , rootRow + (repeatRegionHeight * repeatRow) + repeatColumnHeadingsLastRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatColumnHeadingsLastColumnOffset)
                    , cellsAndHeadingsForDisplay);
        }
        loggedInUser.setSentCells(reportId, sheet.getSheetName(), region + "-" + repeatRow + "-" + repeatColumn, cellsAndHeadingsForDisplay); // todo- perhaps address that this is a bit of a hack!
        RegionFillerService.fillData(sheet, cellsAndHeadingsForDisplay, displayDataRegion);
    }

    private static void fillRepeatRegion(LoggedInUser loggedInUser,
                                         Sheet sheet,
                                         int reportId,
                                         int valueId,
                                         String region,
                                         UserRegionOptions userRegionOptions,
                                         boolean quiet,
                                         SName rowHeadingDescription,
                                         SName contextDescription,
                                         SName repeatRegion,
                                         SName columnHeadingsDescription,
                                         CellRegion displayRowHeadings,
                                         CellRegion displayColumnHeadings,
                                         int repeatRegionWidth,
                                        int repeatRegionHeight,
                                        int rootRow,
                                         int rootCol,
                                         int repeatDataRowOffset,
                                         int repeatDataColumnOffset,
                                         int repeatDataLastRowOffset,
                                         int repeatDataLastColumnOffset,
                                         int repeatRowHeadingsRowOffset,
                                         int repeatRowHeadingsColumnOffset,
                                         int repeatRowHeadingsLastRowOffset,
                                         int repeatRowHeadingsLastColumnOffset,
                                         int repeatColumnHeadingsRowOffset,
                                         int repeatColumnHeadingsColumnOffset,
                                         int repeatColumnHeadingsLastRowOffset,
                                         int repeatColumnHeadingsLastColumnOffset,
                                         int repeatColumn,
                                         int repeatRow
                                         ) throws Exception{
        //REPEAT REGION METHODOLOGY CHANGED BY WFC 27 Feb 17
        //now looks for row and col headings potentially within the repeat region - before it assumed they'd be the same each time now this is not true, headings could be dependant on the repeat item
        // hence the offset being passed to the name to string lists
        int colOffset = repeatRegionWidth * repeatColumn;
        int rowOffset = repeatRegionHeight * repeatRow;
        List<List<String>> rowHeadingList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser,rowHeadingDescription, repeatRegion, rowOffset, colOffset);
        List<List<String>> contextList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser,contextDescription, repeatRegion, rowOffset, colOffset);
        List<List<String>> columnHeadingList = BookUtils.replaceUserChoicesInRegionDefinition(loggedInUser,columnHeadingsDescription, repeatRegion, rowOffset, colOffset);
        CellRegion displayDataRegion = new CellRegion(rootRow + (repeatRegionHeight * repeatRow) + repeatDataRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatDataColumnOffset
                , rootRow + (repeatRegionHeight * repeatRow) + repeatDataLastRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatDataLastColumnOffset);

        CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser, region, valueId, rowHeadingList, columnHeadingList,
                contextList, userRegionOptions, quiet, null);
        // so I now need to do the row and column headings also
        if (displayRowHeadings != null && cellsAndHeadingsForDisplay.getRowHeadings() != null) {
            displayRowHeadings = new CellRegion(rootRow + (repeatRegionHeight * repeatRow) + repeatRowHeadingsRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatRowHeadingsColumnOffset
                    , rootRow + (repeatRegionHeight * repeatRow) + repeatRowHeadingsLastRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatRowHeadingsLastColumnOffset);
            RegionFillerService.fillRowHeadings(loggedInUser, sheet, region, displayRowHeadings, displayDataRegion, cellsAndHeadingsForDisplay, userRegionOptions);
        }
        if (displayColumnHeadings != null) {
            displayColumnHeadings = new CellRegion(rootRow + (repeatRegionHeight * repeatRow) + repeatColumnHeadingsRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatColumnHeadingsColumnOffset
                    , rootRow + (repeatRegionHeight * repeatRow) + repeatColumnHeadingsLastRowOffset, rootCol + (repeatRegionWidth * repeatColumn) + repeatColumnHeadingsLastColumnOffset);
            RegionFillerService.fillColumnHeadings(sheet, userRegionOptions, displayColumnHeadings, cellsAndHeadingsForDisplay);
        }
        loggedInUser.setSentCells(reportId, sheet.getSheetName(), region + "-" + repeatRow + "-" + repeatColumn, cellsAndHeadingsForDisplay); // todo- perhaps address that this is a bit of a hack!
        RegionFillerService.fillData(sheet, cellsAndHeadingsForDisplay, displayDataRegion);

    }

    static void fillData(Sheet sheet, CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay, CellRegion displayDataRegion) {
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
                        /*if (cellValue.isLocked()) {
                            Range selection = Ranges.range(sheet, row, col);
                            CellStyle oldStyle = selection.getCellStyle();
                            EditableCellStyle newStyle = selection.getCellStyleHelper().createCellStyle(oldStyle);
                            newStyle.setLocked(true);
                            selection.setCellStyle(newStyle);
                        }*/
                    }
                    col++;
                    localCol++;
                }
                row++;
            }
        }
        // based off a worrying effect on displayRowHeadings where the last row didn't notify and I put in a lastRow + 1 I'm going to put in a + 1 here
        Ranges.range(sheet, displayDataRegion.getRow(), displayDataRegion.getColumn(), displayDataRegion.lastRow + 1, displayDataRegion.lastColumn).notifyChange();

    }
}