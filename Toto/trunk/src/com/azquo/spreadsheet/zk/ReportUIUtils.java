package com.azquo.spreadsheet.zk;

import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.OnlineController;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.model.CellRegion;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zss.ui.event.CellMouseEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by edward on 17/01/17.
 * <p>
 * Could maybe be called composer utils, t
 */
public class ReportUIUtils {
    static List<SName> getNamedRegionForRowAndColumnSelectedSheet(Spreadsheet myzss, int row, int col) {
        // now how to get the name?? Guess run through them. Feel there should be a better way.
        final Book book = myzss.getBook();
        List<SName> found = new ArrayList<>();
        for (SName name : book.getInternalBook().getNames()) { // seems best to loop through names checking which matches I think
            if (name.getRefersToSheetName() != null && name.getRefersToSheetName().equals(myzss.getSelectedSheet().getSheetName())
                    && name.getRefersToCellRegion() != null
                    && row >= name.getRefersToCellRegion().getRow() && row <= name.getRefersToCellRegion().getLastRow()
                    && col >= name.getRefersToCellRegion().getColumn() && col <= name.getRefersToCellRegion().getLastColumn()) {
                found.add(name);
            }
        }
        return found;
    }

    public static String trimString(String stringToShow) {
        if (stringToShow.length() > 300) {
            stringToShow = stringToShow.substring(0, 300) + " . . .\n";
        }
        return stringToShow;
    }

    static String replaceAll(String original, String toFind, String replacement) {
        int foundPos = original.toLowerCase().indexOf(toFind);
        while (foundPos >= 0) {
            original = original.substring(0, foundPos) + replacement + original.substring(foundPos + toFind.length());
            foundPos = original.toLowerCase().indexOf(toFind);
        }
        return original;
    }

    static String pivotItem(CellMouseEvent event, String filterName, String choicesName, int choiceWidth) {
        SName contextFilters = event.getSheet().getBook().getInternalBook().getNameByName(filterName);//obsolete
        if (contextFilters != null) {
            String[] filters = BookUtils.getSnameCell(contextFilters).getStringValue().split(",");
            CellRegion contextChoices = BookUtils.getCellRegionForSheetAndName(event.getSheet(), choicesName);
            if (contextChoices != null) {
                int headingRow = contextChoices.getRow();
                int headingCol = contextChoices.getColumn();
                int headingRows = contextChoices.getRowCount();
                int filterCount = 0;
                //on the top of pivot tables, the options are shown as pair groups separated by a space, sometimes on two rows, also separated by a space
                // this logic is repeated from add validation, dedupe?
                for (String filter : filters) {
                    List<String> optionsList = CommonReportUtils.getDropdownListForQuery((LoggedInUser) event.getSheet().getBook().getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER), "`" + filter + "` children");
                    if (optionsList != null && optionsList.size() > 1) {
                        int rowOffset = filterCount % headingRows;
                        int colOffset = filterCount / headingRows;
                        int chosenRow = headingRow + rowOffset;
                        int chosenCol = headingCol + choiceWidth * colOffset + 1;
                        if (chosenRow == event.getRow() && chosenCol == event.getColumn()) {
                            return filter.trim();
                        }
                        filterCount++;
                    }
                }
            }
        }
        //look in the row headings.
        for (SName name : event.getSheet().getBook().getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith(ReportRenderer.AZROWHEADINGS)) {
                //surely there must be a better way of getting the first cell off a region!
                String firstItem = "";
                try {
                    firstItem = name.getBook().getSheetByName(name.getRefersToSheetName()).getCell(name.getRefersToCellRegion().getRow(), name.getRefersToCellRegion().getColumn()).getStringValue();
                } catch (Exception e) {
                    //there's an error in the formula - certainly not a permute!
                }
                if (firstItem.toLowerCase().startsWith("permute(")) {
                    String[] rowHeadings = firstItem.substring("permute(".length(), firstItem.length() - 1).split(",");
                    String displayRowHeadingsString = "az_Display" + name.getName().substring(3);
                    CellRegion displayRowHeadings = BookUtils.getCellRegionForSheetAndName(event.getSheet(), displayRowHeadingsString);
                    if (displayRowHeadings != null) {
                        int hrow = displayRowHeadings.getRow() - 1;
                        int hcol = displayRowHeadings.getColumn();
                        for (String rowHeading : rowHeadings) {
                            if (hrow == event.getRow() && hcol++ == event.getColumn()) {
                                if (rowHeading.contains(" sorted")) { // maybe factor the string literal? Need to make it work for the mo
                                    rowHeading = rowHeading.substring(0, rowHeading.indexOf(" sorted")).trim();
                                }
                                return rowHeading.replace("`", "");
                            }
                        }
                    }
                }
            }
        }
        //...and the column headings
        for (SName name : event.getSheet().getBook().getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith(ReportRenderer.AZCOLUMNHEADINGS)) {
                //surely there must be a better way of getting the first cell off a region!
                SCell sCell = name.getBook().getSheetByName(name.getRefersToSheetName()).getCell(name.getRefersToCellRegion().getRow(), name.getRefersToCellRegion().getColumn());
                String firstItem = "";
                try {
                    firstItem = sCell.getStringValue();
                } catch (Exception e) {
                    //no need - firstItem = ""
                }
                if (firstItem.toLowerCase().startsWith("permute(")) {
                    String[] colHeadings = firstItem.substring("permute(".length(), firstItem.length() - 1).split(",");
                    String displayColHeadingsString = "az_Display" + name.getName().substring(3);
                    CellRegion displayColHeadings = BookUtils.getCellRegionForSheetAndName(event.getSheet(), displayColHeadingsString);
                    if (displayColHeadings != null) {
                        int hrow = displayColHeadings.getRow();
                        int hcol = displayColHeadings.getColumn() - 1;
                        for (String colHeading : colHeadings) {
                            if (hrow++ == event.getRow() && hcol == event.getColumn()) {
                                if (colHeading.contains(" sorted")) {
                                    colHeading = colHeading.substring(0, colHeading.indexOf(" sorted")).trim();
                                }
                                return colHeading.replace("`", "");
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    // work out what the local region and row and column is for a given cell in a repeat score
    static ZKComposer.RegionRowCol getRegionRowColForRepeatRegion(Book book, int row, int col, SName repeatScopeName) {
        String repeatRegionName = repeatScopeName.getName().substring(ReportRenderer.AZREPEATSCOPE.length());
        SName repeatRegion = book.getInternalBook().getNameByName(ReportRenderer.AZREPEATREGION + repeatRegionName);
        SName repeatDataRegion = book.getInternalBook().getNameByName("az_DataRegion" + repeatRegionName); // todo string literals ergh!
        // deal with repeat regions, it means getting sent cells that have been set as following : loggedInUser.setSentCells(reportId, region + "-" + repeatRow + "-" + repeatColumn, cellsAndHeadingsForDisplay)
        if (repeatRegion != null && repeatDataRegion != null) { // ergh, got to try and find the right sent cell!
            // local row ancd col starts off local to the repeat scope then the region and finally the data region in the repeated region
            int localRow = row - repeatScopeName.getRefersToCellRegion().getRow();
            int localCol = col - repeatScopeName.getRefersToCellRegion().getColumn();
            // NOT row and col in a cell cense, row and coll in a repeated region sense
            int repeatRow = 0;
            int repeatCol = 0;
            // ok so keep chopping the row and col in repeat scope until we have the row and col in the repeat region but we know WHICH repeat region we're in
            while (localRow - repeatRegion.getRefersToCellRegion().getRowCount() > 0) {
                repeatRow++;
                localRow -= repeatRegion.getRefersToCellRegion().getRowCount();
            }
            while (localCol - repeatRegion.getRefersToCellRegion().getColumnCount() > 0) {
                repeatCol++;
                localCol -= repeatRegion.getRefersToCellRegion().getColumnCount();
            }
            // so now we should know the row and col local to the particular repeat region we're in and which repeat region we're in. Try to get the sent cells!
            int dataRowStartInRepeatRegion = repeatDataRegion.getRefersToCellRegion().getRow() - repeatScopeName.getRefersToCellRegion().getRow();
            int dataColStartInRepeatRegion = repeatDataRegion.getRefersToCellRegion().getColumn() - repeatScopeName.getRefersToCellRegion().getColumn();
            // take into account the offset from the top left of the repeated region and the data region in it
            localRow -= dataRowStartInRepeatRegion;
            localCol -= dataColStartInRepeatRegion;
            return new ZKComposer.RegionRowCol(repeatRegion.getRefersToSheetName(), repeatRegionName + "-" + repeatRow + "-" + repeatCol, localRow, localCol);
        }
        return null;
    }
}