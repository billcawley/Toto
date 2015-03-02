package com.azquo.view;

/**
 * Created by cawley on 24/02/15
 *
 *
 */
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.azquo.service.*;
import org.zkoss.zss.api.*;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.jsp.BookProvider;
import org.zkoss.zss.model.*;


public class ZKAzquoBookProvider implements BookProvider{

    public static final String BOOK_PATH = "BOOK_PATH";
    public static final String LOGGED_IN_CONNECTION = "LOGGED_IN_CONNECTION";
    public static final String VALUE_SERVICE = "VALUE_SERVICE";
    public static final String NAME_SERVICE = "NAME_SERVICE";

    public Book loadBook(ServletContext servletContext, HttpServletRequest request, HttpServletResponse res) {
        // ok I'm going to paste the online controller stuff and see if I can make this play ball
        // No. I'm going to use the session for the mo. From here I currently can't get to spring objects easily (DAOs etc)

        String bookPath = (String)request.getSession().getAttribute(BOOK_PATH);
        LoggedInConnection loggedInConnection = (LoggedInConnection)request.getSession().getAttribute(LOGGED_IN_CONNECTION);
        ValueService valueService = (ValueService)request.getAttribute(VALUE_SERVICE);
//        NameService nameService = (NameService)request.getAttribute(NAME_SERVICE);

        if (bookPath != null && loggedInConnection != null) {

            // ok so now what we want is the equivalent of the read excel function in onlineservice
            Book book;
            // ok lets have a bit of hack here - jam a report against the
            try {
                book = Importers.getImporter().imports(new File(bookPath) , "Report name");
                Sheet sheet = book.getSheetAt(0);


                // I really want to be able to run through ranges but for the moment do this

                Range columnHeadingsDescription = Ranges.rangeByName(sheet,"az_ColumnHeadings");
                Range rowHeadingsDescription = Ranges.rangeByName(sheet,"az_RowHeadings");

                // ok the old style got the region as an Excel copy/paste string then converted that into headers
                // I think I'll try to skip the middle man
                // adapted from createnames list form excel region
                if (columnHeadingsDescription != null && rowHeadingsDescription != null){
                    // to build here but this doens't deal with the limiting and sorting which is important
/*                   List<List<List<DataRegionHeading>>> columnHeadings = getHeadingsLists(columnHeadingsDescription,sheet,valueService,nameService, loggedInConnection);
                    // transpose, expand, transpose again
                    List<List<DataRegionHeading>> expandedColumnHeadings = valueService.transpose2DList(valueService.expandHeadings(valueService.transpose2DList(columnHeadings)));
                    List<List<List<DataRegionHeading>>> rowHeadings = getHeadingsLists(rowHeadingsDescription,sheet,valueService,nameService, loggedInConnection);
                    List<List<DataRegionHeading>> expandedRowHeadings = valueService.expandHeadings(rowHeadings);*/
                    // so use the ones prepared by the sheet before
                    // this is hacky at the mo, typically read headers, load up data (which will modify headers) then load headers. In this case headers etc are set up already.
                    List<List<Object>> displayObjectsForNewSheet = new ArrayList<List<Object>>();
                    valueService.getExcelDataForColumnsRowsAndContext(loggedInConnection, loggedInConnection.getContext(""), "", -1, 100, 0, displayObjectsForNewSheet);
                    List<List<DataRegionHeading>> expandedColumnHeadings = valueService.getColumnHeadingsAsArray(loggedInConnection,"");
                    List<List<DataRegionHeading>> expandedRowHeadings = valueService.getRowHeadingsAsArray(loggedInConnection,"", -1);

                    // now, put the headings into the sheet!
                    // might be factored into fill range in a bit
                    Range displayColumnHeadings = Ranges.rangeByName(sheet,"az_DisplayColumnHeadings");
                    Range displayRowHeadings = Ranges.rangeByName(sheet,"az_DisplayRowHeadings");
                    Range displayDataRegion = Ranges.rangeByName(sheet,"az_DataRegion");

                    // ok the plan here is remove all the merges then put them back in after the region expanding.
                    List<CellRegion> merges =  new ArrayList<CellRegion>(sheet.getInternalSheet().getMergedRegions());

                    for (CellRegion merge : merges){
                        CellOperationUtil.unmerge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()));
                    }

                    int rowsToAdd;
                    int colsToAdd;

                    if (displayColumnHeadings != null && displayRowHeadings != null && displayDataRegion != null){
                        // add rows
                        int maxCol = 0;
                        for (int i = 0; i <= sheet.getLastRow(); i++){
                            if (sheet.getLastColumn(i) > maxCol){
                                maxCol = sheet.getLastColumn(i);
                            }
                        }
                        if ((displayRowHeadings.getRowCount() < expandedRowHeadings.size()) && displayRowHeadings.getRowCount() > 2){ // then we need to expand, and there is space to do so (3 or more allocated already)
                            rowsToAdd = expandedRowHeadings.size() - (displayRowHeadings.getRowCount());
                            for (int i = 0; i < rowsToAdd; i++){
                                int rowToCopy = displayRowHeadings.getRow() + 1; // I think this is correct, middle row of 3?
                                Range copySource = Ranges.range(sheet, rowToCopy, 0, rowToCopy, maxCol);
                                Range insertRange = Ranges.range(sheet, rowToCopy + 1, 0, rowToCopy + 1, maxCol); // insert at the 3rd row
                                CellOperationUtil.insertRow(insertRange); // get formatting from above
                                if (sheet.getInternalSheet().getRow(rowToCopy).getHeight() != sheet.getInternalSheet().getRow(rowToCopy + 1).getHeight()){ // height may not match on insert/paste, if not set it
                                    sheet.getInternalSheet().getRow(rowToCopy + 1).setHeight(sheet.getInternalSheet().getRow(rowToCopy).getHeight());
                                }
                                // ok now copy contents, this should (ha!) copy the row that was just shifted down to the one just created
                                CellOperationUtil.paste(copySource,insertRange);
                            }
                        }
                        // add columns
                        int maxRow = sheet.getLastRow();
                        if (displayColumnHeadings.getColumnCount() < expandedColumnHeadings.get(0).size() && displayColumnHeadings.getColumnCount() > 2){ // then we need to expand
                            colsToAdd = expandedColumnHeadings.get(0).size() - (displayColumnHeadings.getColumnCount());
                            for (int i = 0; i < colsToAdd; i++){
                                int colToCopy = displayColumnHeadings.getColumn() + 1; // I think this is correct, middle row of 3?
                                Range copySource = Ranges.range(sheet, 0, colToCopy, maxRow, colToCopy);
                                Range insertRange = Ranges.range(sheet, 0, colToCopy + 1, maxRow, colToCopy + 1); // insert at the 3rd col
                                CellOperationUtil.insertColumn(insertRange); // get formatting from above
                                // ok now copy contents, this should (ha!) copy the row that was just shifted down to the one just created
                                CellOperationUtil.paste(copySource,insertRange);
                                if (sheet.getInternalSheet().getColumn(colToCopy).getWidth() != sheet.getInternalSheet().getColumn(colToCopy + 1).getWidth()){ // width may not match on insert/paste, if not set it
                                    sheet.getInternalSheet().getColumn(colToCopy + 1).setWidth(sheet.getInternalSheet().getColumn(colToCopy).getWidth());
                                }
                            }
                        }
                        // ok there should be the right space for the headings
                        int row = displayRowHeadings.getRow();
                        for (List<DataRegionHeading> rowHeading : expandedRowHeadings){
                            int col = displayRowHeadings.getColumn();
                            for (DataRegionHeading heading : rowHeading){
                                sheet.getInternalSheet().getCell(row,col).setValue(heading.getAttribute() != null ? heading.getAttribute() : heading.getName().getDisplayNameForLanguages(loggedInConnection.getLanguages()));
                                col++;
                            }
                            row++;
                        }
                        row = displayColumnHeadings.getRow();
                        for (List<DataRegionHeading> colHeading : expandedColumnHeadings){
                            int col = displayColumnHeadings.getColumn();
                            for (DataRegionHeading heading : colHeading){
                                sheet.getInternalSheet().getCell(row,col).setValue(heading.getAttribute() != null ? heading.getAttribute() : heading.getName().getDisplayNameForLanguages(loggedInConnection.getLanguages()));
                                col++;
                            }
                            row++;
                        }
                        row = displayDataRegion.getRow();
                        for (List<Object> rowCellValues : displayObjectsForNewSheet){
                            int col = displayDataRegion.getColumn();
                            for (Object cellValue : rowCellValues){
                                sheet.getInternalSheet().getCell(row,col).setValue(cellValue);
                                col++;
                            }
                            row++;
                        }
                        // this is a pain, it seems I need to call 2 functions on each formula cell or the formaul may not be calculated. ANNOYING!
                        Iterator<SRow> rowIterator =  sheet.getInternalSheet().getRowIterator(); // only rows with values in them
                        while(rowIterator.hasNext()){
                            Iterator<SCell> cellIterator = sheet.getInternalSheet().getCellIterator(rowIterator.next().getIndex());
                            while (cellIterator.hasNext()){
                                SCell cell = cellIterator.next();
                                if (cell.getType() == SCell.CellType.FORMULA){
                                    //System.out.println("doing the cell thing on " + cell);
                                    cell.getFormulaResultType();
                                    cell.clearFormulaResultCache();
                                }
                            }
                        }
                    }

                    // snap the charts
                    for (SChart chart : sheet.getInternalSheet().getCharts()){
                        ViewAnchor oldAnchor = chart.getAnchor();
                        int row = oldAnchor.getRowIndex();
                        int col = oldAnchor.getColumnIndex();
                        int width = oldAnchor.getWidth();
                        int height = oldAnchor.getHeight();
                        chart.setAnchor(new ViewAnchor(row, col, 0, 0,width, height));
                    }
                    // now remerge? Should work
                    for (CellRegion merge : merges){
                        // I think we do want to merge horizontally (the boolean flag)
                        CellOperationUtil.merge(Ranges.range(sheet, merge.getRow(), merge.getColumn(), merge.getLastRow(), merge.getLastColumn()), true);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
            return book;
        } else {
            try {
                // todo, replace with a blank book?
                return Importers.getImporter().imports(new File("/home/cawley/databases/Magen_swimshop/onlinereports/MagentoBestSellers.xlsx") , "Best Sellers By Period");
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
