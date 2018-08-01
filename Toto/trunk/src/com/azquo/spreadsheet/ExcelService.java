package com.azquo.spreadsheet;

import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.zk.BookUtils;
import com.azquo.spreadsheet.zk.ReportRenderer;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;



public class ExcelService {

    public static final String BOOK_PATH = "BOOK_PATH";
    public static final String LOGGED_IN_USER = "LOGGED_IN_USER";
    public static final String REPORT_ID = "REPORT_ID";


    public static UserRegionOptions getUserRegionOptions(LoggedInUser loggedInUser, String optionsSource, int reportId, String region) {
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
        return userRegionOptions;
    }


    public static File listReports(HttpServletRequest request, List<OnlineReport> reports) throws Exception{

       Book book = Importers.getImporter().imports(request.getServletContext().getResourceAsStream("/WEB-INF/ReportMenu.xlsx"), "Report name");
        Sheet sheet = book.getSheetAt(0);
        List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
        for (SName sName : namesForSheet) {
            if (sName.getName().equalsIgnoreCase("Title")) {
                final SCell cell = sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn());
                cell.setStringValue("Reports available");
            }
            if (sName.getName().equalsIgnoreCase("Data")) {
                int yOffset = -1;
                char firstChar = 0;
                String dbName = "";

                for (OnlineReport or : reports) {
                    if (!or.getDatabase().equals(dbName)){
                        firstChar = 0;
                        dbName = or.getDatabase();
                        yOffset++;
                    }
                    if (or.getExplanation()!= null && or.getExplanation().length() > 0 && or.getExplanation().charAt(0) > firstChar){
                        firstChar = or.getExplanation().charAt(0);
                        yOffset++;
                    }
                    if (firstChar > 0 && (or.getExplanation()== null||or.getExplanation().length()==0)){
                        firstChar = 0;
                        yOffset++;
                    }
                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn()).setStringValue("Template");
                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn()+1).setStringValue(or.getAuthor());
                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn()+2).setStringValue(or.getDatabase());
                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn()+3).setStringValue(or.getReportName());
                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn()+4).setStringValue(or.getExplanation().trim());
                    //sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + 1).setStringValue(or.getReportName());
                    yOffset++;
                }
            }
        }
        Exporter exporter = Exporters.getExporter();
        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            exporter.export(book, fos);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
        return file;
    }

    public static File createReport(LoggedInUser loggedInUser, OnlineReport onlineReport, boolean template )throws Exception{
        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk();
        if (template){
            return new File(bookPath);
        }

        final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
        book.getInternalBook().setAttribute(BOOK_PATH, bookPath);
        book.getInternalBook().setAttribute(LOGGED_IN_USER, loggedInUser);
        // todo, address allowing multiple books open for one user. I think this could be possible. Might mean passing a DB connection not a logged in one
        book.getInternalBook().setAttribute(REPORT_ID, onlineReport.getId());
        ReportRenderer.populateBook(book, 0);
         Exporter exporter = Exporters.getExporter();
        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            exporter.export(book, fos);
        } finally {
            if (fos != null) {
                fos.close();
            }
        }

        return file;
    }
}








