package com.azquo.spreadsheet.zk;

import com.azquo.StringLiterals;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.CellForDisplay;
import com.csvreader.CsvWriter;
import io.keikai.api.model.Sheet;
import io.keikai.model.SName;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.poi.ss.util.AreaReference;
import org.zeroturnaround.zip.FileSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.util.Clients;
import io.keikai.api.Importers;
import io.keikai.api.Ranges;
import io.keikai.api.model.Book;
import io.keikai.model.SSheet;
import io.keikai.ui.Spreadsheet;
import org.zkoss.zul.Filedownload;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by edward on 26/01/17.
 * <p>
 * Just some small static functions factored off the big old ZKcomposer
 */
class ZKComposerUtils {
    static void reloadBook(Spreadsheet myzss, Book book) {
        try {
            // new book from same source
            final Book newBook = Importers.getImporter().imports(new File((String) book.getInternalBook().getAttribute(OnlineController.BOOK_PATH)), "Report name");
            for (String key : book.getInternalBook().getAttributes().keySet()) {// copy the attributes over
                // don't move zss internal stuff, it might interfere
                if (!key.toLowerCase().contains("keikai")){
                    newBook.getInternalBook().setAttribute(key, book.getInternalBook().getAttribute(key));
                }
            }
            newBook.getInternalBook().setAttribute(OnlineController.LOCKED_RESULT, null); // zap the locked result, it will be checked below and we only want it there if  populate book put it there
            if (ReportRenderer.populateBook(newBook, 0)) { // check if formulae made saveable data
                Clients.evalJavaScript("document.getElementById(\"saveDataButton\").style.display=\"flex\";document.getElementById(\"restoreDataButton\").style.display=\"flex\";");
            }
            if (newBook.getInternalBook().getAttribute(OnlineController.LOCKED_RESULT) != null) {
                String message = (String) newBook.getInternalBook().getAttribute(OnlineController.LOCKED_RESULT);
                Clients.evalJavaScript("document.getElementById(\"lockedResult\").innerHTML='<textarea class=\"public\" style=\"height:60px;width:400px;font:10px monospace;overflow:auto;font-family:arial;background:#f58030;color:#fff;font-size:14px;border:0\">" + StringEscapeUtils.escapeJavaScript(message) + "</textarea>';");
            } else {
                Clients.evalJavaScript("document.getElementById(\"lockedResult\").innerHTML='';");
            }

            myzss.setBook(newBook); // and set to the ui. I think if I set to the ui first it becomes overwhelmed trying to track modifications (lots of unhelpful null pointers)
            if (myzss.getSelectedSheet().isHidden()) {
                for (SSheet s : myzss.getSBook().getSheets()) {
                    if (s.getSheetVisible() == SSheet.SheetVisible.VISIBLE) {
                        myzss.setSelectedSheet(s.getSheetName());
                        break;
                    }
                }
            }
            Ranges.range(myzss.getSelectedSheet()).notifyChange(); // try to update the lot - sometimes it seems it does not!

            checkCSVDownload(newBook);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void openDrillDown(LoggedInUser loggedInUser, String reportName, String context, int valueId) throws Exception{
        ChoicesService.setChoices(loggedInUser, context);
        OnlineReport or = null;
        String permissionId = null;
        if (reportName != null) {
            if (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) {
                int databaseId = loggedInUser.getDatabase().getId();
                or = OnlineReportDAO.findForDatabaseIdAndName(databaseId, reportName);
                if (or == null) {
                    or = OnlineReportDAO.findForDatabaseIdAndName(0, reportName);
                }
            } else if (loggedInUser.getPermission(reportName) != null) {
                permissionId = reportName;
            }
        } else {
            reportName = "unspecified";
        }
        if (permissionId != null) { // database removed from permission, redundant
            Clients.evalJavaScript("window.open(\"/api/Online?permissionid=" + permissionId + (valueId != 0 ? "&valueid=" + valueId : "") + "\")");
        } else if (or != null) {
            Clients.evalJavaScript("window.open(\"/api/Online?reportid=" + or.getId() + "&database=" + loggedInUser.getDatabase().getName() + (valueId != 0 ? "&valueid=" + valueId : "") + "\")");
        } else {
            Clients.evalJavaScript("alert(\"the report '" + reportName + "` is no longer available\")");
        }
    }

    // check through the book to see if there are regions that we need to have as CSV downloads. Awkwardly we can't just check  the cells and headings for display - display column headings must be used if availabe
    // hence we need to run through names, adding the csv download flag to the region options in CellsAndHeadingsForDisplay won't get us out of it

    static void checkCSVDownload(Book book) throws Exception {
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        // could be more than one and zipping might be a good idea anyway for fast downloads
        List<ZipEntrySource> toZip = new ArrayList<>();
        for (int sheetNumber = 0; sheetNumber < book.getNumberOfSheets(); sheetNumber++) {
            Sheet sheet = book.getSheetAt(sheetNumber);
            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
            for (SName name : namesForSheet){
                if (name.getName().toLowerCase().startsWith(StringLiterals.AZDATAREGION)){
                    String region = name.getName().substring(StringLiterals.AZDATAREGION.length());
                    SName optionsRegion = BookUtils.getNameByName(StringLiterals.AZOPTIONS + region, sheet);
                    if (optionsRegion != null) {
                        String optionsSource = BookUtils.getSnameCell(optionsRegion).getStringValue();
                        int reportId = (int) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
                        UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, optionsSource);
                        if (userRegionOptions.getCsvDownload() || userRegionOptions.getCsvRenderedDownload()){ // then we're off
                            SName displayColumnHeadings = BookUtils.getNameByName(StringLiterals.AZDISPLAYCOLUMNHEADINGS + region, sheet);
                            File newTempFile = File.createTempFile("csv export", ".csv");
                            newTempFile.deleteOnExit();
                            CsvWriter csvWriter = new CsvWriter(newTempFile.toString(), ',', StandardCharsets.UTF_8);
                            if (displayColumnHeadings != null){
                                AreaReference areaReference= new AreaReference(displayColumnHeadings.getRefersToFormula(), null);
                                ImportService.rangeToCSV(book.getSheet(displayColumnHeadings.getRefersToSheetName()),areaReference,csvWriter);
                            } else {
                                for (List<String> headingRow : loggedInUser.getSentCells(reportId, sheet.getSheetName(), region).getColumnHeadings()){
                                    for (String heading : headingRow){
                                        csvWriter.write(heading.replace("\n", "\\\\n").replace(",", "").replace("\t", "\\\\t"));//nullify the tabs and carriage returns.  Note that the double slash is deliberate so as not to confuse inserted \\n with existing \n
                                    }
                                    csvWriter.endRecord();
                                }
                            }
                            if (userRegionOptions.getCsvRenderedDownload()){
                                AreaReference areaReference= new AreaReference(name.getRefersToFormula(), null);
                                ImportService.rangeToCSV(book.getSheet(name.getRefersToSheetName()),areaReference,csvWriter);
                            } else {
                                for (List<CellForDisplay> dataRow : loggedInUser.getSentCells(reportId, sheet.getSheetName(), region).getData()){
                                    for (CellForDisplay dataCell : dataRow){
                                        csvWriter.write(dataCell.getStringValue().replace("\n", "\\\\n").replace(",", "").replace("\t", "\\\\t"));
                                    }
                                    csvWriter.endRecord();
                                }
                            }
                            csvWriter.flush();
                            csvWriter.close();
                            SName downloadName = BookUtils.getNameByName(StringLiterals.AZCSVDOWNLOADNAME + region, sheet);
                            if (downloadName != null){
                                toZip.add(new FileSource(BookUtils.getRegionValue(sheet, downloadName.getRefersToCellRegion()) + ".csv", newTempFile));
                            } else {
                                toZip.add(new FileSource(region + "export.csv", newTempFile));

                            }
                        }
                    }
                }
            }
        }
        if (!toZip.isEmpty()){
            File tempzip = File.createTempFile("csvexport", ".zip");
            ZipEntrySource[] zes = new ZipEntrySource[toZip.size()];
            toZip.toArray(zes);
            ZipUtil.pack(zes, tempzip);
            tempzip.deleteOnExit();
            book.getInternalBook().setAttribute("csvdownload", tempzip);
        }
    }

}