package com.azquo.spreadsheet.zk;

import com.azquo.TypedPair;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.memorydb.Constants;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import com.azquo.spreadsheet.transport.ProvenanceForDisplay;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zss.api.Exporter;
import org.zkoss.zss.api.Exporters;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;
import org.zkoss.zss.api.model.Sheet;
import org.zkoss.zss.model.SCell;
import org.zkoss.zss.model.SName;
import org.zkoss.zss.ui.Spreadsheet;
import org.zkoss.zssex.ui.widget.Ghost;
import org.zkoss.zul.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by edward on 26/01/17.
 *
 * Code to deal with the context menu, might be a fair bit going on here.
 *
 * Use of the ZK API to alter the user interface might be a little hacky, might be sensetive to changes to their API or implementation
 *
 * THis class is a little bigger than I'd like. Could break code off for provenance stuff?
 */
class ZKContextMenu {

    // can only be one of these each per menu, may as well have them as instance variables
    private final Spreadsheet myzss;
    private final LoggedInUser loggedInUser;
    private final int reportId;

    private final Menupopup editPopup;
    private final Label instructionsLabel;
    private final Popup provenancePopup;
    private final Popup debugPopup;
    private final Popup highlightPopup;
    private final Popup instructionsPopup;

    ZKContextMenu(Spreadsheet myzss){
        this.myzss = myzss;
        editPopup = new Menupopup();
        // todo - check ZK to see if there's a better way to do this
        editPopup.setId("editPopup");
        editPopup.setStyle("background-color:#ffffff");
        editPopup.setStyle("border: 5px solid #F58030");
        provenancePopup = new Popup();
        provenancePopup.setId("provenancePopup");
        provenancePopup.setDraggable("true");
        provenancePopup.setDroppable("true");
        provenancePopup.setStyle("background-color:#ffffff");
        provenancePopup.setStyle("border: 5px solid #F58030");
        debugPopup = new Popup();
        debugPopup.setId("debugPopup");
        debugPopup.setDraggable("true");
        debugPopup.setDroppable("true");
        debugPopup.setStyle("background-color:#ffffff");
        debugPopup.setStyle("border: 5px solid #F58030");
        instructionsLabel = new Label();
        instructionsPopup = new Popup();
        instructionsPopup.setId("instructionsPopup");
        instructionsPopup.setStyle("background-color:#ffffff");
        instructionsPopup.setStyle("border: 5px solid #F58030");
        instructionsLabel.setMultiline(true);
        instructionsPopup.appendChild(instructionsLabel);
        highlightPopup = new Popup();
        highlightPopup.setStyle("background-color:#ffffff");
        highlightPopup.setStyle("border: 5px solid #F58030");
        highlightPopup.setId("highlightPopup");

        // much hacking went into getting an appropriate object to hook into to make our extra contextual menu
        if (myzss.getFirstChild() != null) {
            myzss.getFirstChild().appendChild(editPopup);
            myzss.getFirstChild().appendChild(provenancePopup);
            myzss.getFirstChild().appendChild(debugPopup);
            myzss.getFirstChild().appendChild(instructionsPopup);
            myzss.getFirstChild().appendChild(highlightPopup);
        } else { // it took some considerable time to work out this hack
            Ghost g = new Ghost();
            g.setAttribute("zsschildren", "");
            myzss.appendChild(g);
            g.appendChild(editPopup);
            g.appendChild(provenancePopup);
            g.appendChild(debugPopup);
            g.appendChild(instructionsPopup);
            g.appendChild(highlightPopup);
        }
        Book book = myzss.getBook();
        loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
    }

    /*
    Simple formulae display
    Find the data region location
    Instructions Labels - more region definitions
    highlight
    */
    void showAzquoContextMenu(int cellRow, int cellCol, int mouseX, int mouseY, Component ref) {
        while (editPopup.getChildren().size() > 0) { // clear it out
            editPopup.removeChild(editPopup.getLastChild());
        }
        Component popupChild = provenancePopup.getFirstChild();
        while (popupChild != null) {
            provenancePopup.removeChild(popupChild);
            popupChild = provenancePopup.getFirstChild();
        }
        // clear debug too, factor at some point?
        popupChild = debugPopup.getFirstChild();
        while (popupChild != null) {
            debugPopup.removeChild(popupChild);
            popupChild = debugPopup.getFirstChild();
        }
        SCell sCell = myzss.getSelectedSheet().getInternalSheet().getCell(cellRow, cellCol);
        if (sCell.getType() == SCell.CellType.FORMULA) {
            String formula = sCell.getFormulaValue();
            Label provenanceLabel = new Label();
            provenanceLabel.setMultiline(true);
            provenanceLabel.setValue(ReportUIUtils.trimString("Spreadsheet formula: =" + formula));
            provenancePopup.appendChild(provenanceLabel);
            Menuitem auditItem = new Menuitem("Audit");
            editPopup.appendChild(auditItem);
            auditItem.setPopup(provenancePopup);
//            auditItem.addEventListener("onClick",
//                    event -> System.out.println("audit menu item clicked, formula bit . . ."));
            if (ref != null) {
                editPopup.open(ref, "at_pointer");
            } else {
                editPopup.open(mouseX - 140, mouseY);
            }
        } else {
            String region = null;
            int regionRow = 0;
            int regionColumn = 0;
            // adding support for repeat regions. There's an additional check for row headings in a normal data region but I think this is redundant in repeat regions
            List<SName> repeatRegionNames = BookUtils.getNamedRegionForRowAndColumnSelectedSheet(cellRow, cellCol, myzss.getSelectedSheet(), ReportRenderer.AZREPEATSCOPE);
            if (repeatRegionNames != null && !repeatRegionNames.isEmpty()) {
                ZKComposer.RegionRowCol regionRowColForRepeatRegion = ReportUIUtils.getRegionRowColForRepeatRegion(myzss.getBook(), cellRow, cellCol, repeatRegionNames.get(0));
                if (regionRowColForRepeatRegion != null) {
                    region = regionRowColForRepeatRegion.region;
                    regionRow = regionRowColForRepeatRegion.row;
                    regionColumn = regionRowColForRepeatRegion.col;
                }
            } else { // standard
                List<SName> names = BookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(cellRow, cellCol, myzss.getSelectedSheet());
                for (SName name : names) {
                    SName rowHeadingsName = myzss.getSelectedSheet().getBook().getInternalBook().getNameByName("az_rowheadings" + name.getName().substring(13));
                    if (rowHeadingsName != null) {
                        region = name.getName().substring("az_DataRegion".length());
                        regionRow = cellRow - name.getRefersToCellRegion().getRow();
                        regionColumn = cellCol - name.getRefersToCellRegion().getColumn();
                        break;
                    }
                }
            }
            try {
                final ProvenanceDetailsForDisplay provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, reportId, region, regionRow, regionColumn, 1000);
                if (provenanceDetailsForDisplay.getProcenanceForDisplayList() != null && !provenanceDetailsForDisplay.getProcenanceForDisplayList().isEmpty()) {
                    buildContextMenuProvenance(provenanceDetailsForDisplay);
                    buildContextMenuProvenanceDownload(provenanceDetailsForDisplay,reportId);
                    Menuitem auditItem = new Menuitem("Audit");
                    editPopup.appendChild(auditItem);
                    auditItem.setPopup(provenancePopup);
//                            auditItem.addEventListener("onClick",
//                                    event -> System.out.println("audit menu item clicked"));
                    buildContextMenuDrillDownIfApplicable(region,regionRow,regionColumn);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            buildContextMenuDebug(region,regionRow,regionColumn);
            buildContextMenuInstructions(region);

            popupChild = highlightPopup.getFirstChild();
            while (popupChild != null) {
                highlightPopup.removeChild(popupChild);
                popupChild = highlightPopup.getFirstChild();
            }
            UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
            int highlightDays = 0;
            if (userRegionOptions != null) {
                highlightDays = userRegionOptions.getHighlightDays();
            }
            String highlightString = "no highlight";
            if (highlightDays > 0) highlightString = highlightDays + " days";
            final String highlightList = "Current highlight is " + highlightString + "\n";
            Label highlightLabel = new Label();
            highlightLabel.setMultiline(true);
            highlightLabel.setValue(highlightList);
            highlightPopup.appendChild(highlightLabel);
            addHighlight(highlightPopup, 0);
            addHighlight(highlightPopup, 2);
            addHighlight(highlightPopup, 1);
            addHighlight(highlightPopup, 7);
            addHighlight(highlightPopup, 30);
            addHighlight(highlightPopup, 90);
            Menuitem highlightItem = new Menuitem("Highlight");
            editPopup.appendChild(highlightItem);
            highlightItem.setPopup(highlightPopup);
            if (ref != null) {
                editPopup.open(ref, "at_pointer");
            } else {
                editPopup.open(mouseX - 140, mouseY);
            }
        }
    }

    private void addHighlight(Popup highlightPopup, final int days) {
        String hDays = days + " days";
        if (days == 0) hDays = "none";
        if (days == 2) hDays = "1 hour";
        final Toolbarbutton highlightButton = new Toolbarbutton("highlight " + hDays);
        highlightButton.addEventListener("onClick",
                event -> showHighlight(days));
        highlightPopup.appendChild(highlightButton);
        Label highlightLabel = (new Label("\n\n"));
        highlightLabel.setMultiline(true);
        highlightPopup.appendChild(highlightLabel);
    }

    private void showHighlight(int days) {
        Book book = myzss.getBook();
        boolean reload = false;
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith("az_dataregion")) {
                String region = name.getName().substring(13);
                UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null) {
                    if (days == 0) break;
                    userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, "highlight=" + days + "\n");
                } else {
                    if (userRegionOptions.getHighlightDays() == days) break;
                    userRegionOptions.setHighlightDays(days);
                }
                UserRegionOptionsDAO.store(userRegionOptions);
                reload = true;
            }
        }
        if (reload) {
            ZKComposerUtils.reloadBook(myzss, book);
        }
    }

    private void buildContextMenuProvenance(ProvenanceDetailsForDisplay provenanceDetailsForDisplay){
        Label provenanceLabel;
        if (provenanceDetailsForDisplay.getFunction() != null){
            provenanceLabel = new Label();
            provenanceLabel.setMultiline(true);
            provenanceLabel.setValue(provenanceDetailsForDisplay.getFunction() + "\n");
            provenancePopup.appendChild(provenanceLabel);
        }
        int count = 0;
        for (ProvenanceForDisplay provenanceForDisplay : provenanceDetailsForDisplay.getProcenanceForDisplayList()){
            boolean breakLoop = false;
            provenanceLabel = new Label();
            provenanceLabel.setMultiline(true);
            provenanceLabel.setValue(provenanceForDisplay.toString() + "\n");
            provenancePopup.appendChild(provenanceLabel);
            if (provenanceForDisplay.getNames() != null && !provenanceForDisplay.getNames().isEmpty()){
                StringBuilder names  = new StringBuilder();
                for (String name : provenanceForDisplay.getNames()){
                    names.append("\t").append(name);
                }
                provenanceLabel = new Label();
                provenanceLabel.setMultiline(true);
                provenanceLabel.setValue(names.toString() + "\n");
                provenancePopup.appendChild(provenanceLabel);
            }
            if (provenanceForDisplay.getValuesWithIdsAndNames() != null && !provenanceForDisplay.getValuesWithIdsAndNames().isEmpty()){
                for (TypedPair<Integer, List<String>> value : provenanceForDisplay.getValuesWithIdsAndNames()){
                    if (value.getSecond() != null && !value.getSecond().isEmpty()){
                        Iterator<String> it = value.getSecond().iterator();
                        if (provenanceForDisplay.isInSpreadsheet() && value.getFirst() != null && value.getFirst() > 0){
                            final Toolbarbutton provButton = new Toolbarbutton("\t" + it.next());
                            provButton.addEventListener("onClick",
                                    event -> ZKComposerUtils.openDrillDown(loggedInUser, provenanceForDisplay.getName(), provenanceForDisplay.getContext(), value.getFirst()));
                            provenancePopup.appendChild(provButton);
                        } else {
                            provenanceLabel = new Label();
                            provenanceLabel.setMultiline(true);
                            provenanceLabel.setValue("\t" + it.next());
                            provenancePopup.appendChild(provenanceLabel);
                        }
                        StringBuilder names = new StringBuilder();
                        while (it.hasNext()){
                            if (names.length() > 0){
                                names.append(", ");
                            }
                            names.append(it.next());
                        }
                        provenanceLabel = new Label();
                        provenanceLabel.setMultiline(true);
                        provenanceLabel.setValue("\t\t" + names.toString() + "\n");
                        provenancePopup.appendChild(provenanceLabel);
                        count++;
                        if (count > 20){
                            provenanceLabel = new Label();
                            provenanceLabel.setMultiline(true);
                            provenanceLabel.setValue("\t\t.......\n");
                            provenancePopup.appendChild(provenanceLabel);
                            breakLoop = true;
                            break;
                        }
                    }
                }
            }
            if (breakLoop){
                break;
            }
        }
    }

    private void buildContextMenuProvenanceDownload(ProvenanceDetailsForDisplay provenanceDetailsForDisplay, int reportId){
        Toolbarbutton button = new Toolbarbutton("Download Full Audit");
        // todo - factor with very similar code to downloiad debug info
        button.addEventListener("onClick",
                event ->
                {
                    OnlineReport or = OnlineReportDAO.findById(reportId);
                    Book book1 = Importers.getImporter().imports(Sessions.getCurrent().getWebApp().getResourceAsStream("/WEB-INF/DebugAudit.xlsx"), "Report name");
                    for (int sheetNumber = 0; sheetNumber < book1.getNumberOfSheets(); sheetNumber++) {
                        Sheet sheet = book1.getSheetAt(sheetNumber);
                        List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
                        for (SName sName : namesForSheet) {
                            if (sName.getName().equalsIgnoreCase("Title")) {
                                final SCell cell = sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn());
                                cell.setStringValue("Audit " + or.getReportName());
                            }
                            if (sName.getName().equalsIgnoreCase("Data")) {
                                int yOffset = 0;
                                for (ProvenanceForDisplay provenanceForDisplay : provenanceDetailsForDisplay.getProcenanceForDisplayList()){
                                    int xOffset = 0;
                                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(provenanceForDisplay.toString());
                                    yOffset++;
                                    if (provenanceForDisplay.getNames() != null && !provenanceForDisplay.getNames().isEmpty()){
                                        for (String name : provenanceForDisplay.getNames()){
                                            xOffset++;
                                            sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(name);
                                        }
                                        yOffset++;
                                    }
                                    if (provenanceForDisplay.getValuesWithIdsAndNames() != null && !provenanceForDisplay.getValuesWithIdsAndNames().isEmpty()){
                                        for (TypedPair<Integer, List<String>> value : provenanceForDisplay.getValuesWithIdsAndNames()){
                                            if (value.getSecond() != null && !value.getSecond().isEmpty()){
                                                for (String valueOrName : value.getSecond()) {
                                                    xOffset++;
                                                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(valueOrName);
                                                }
                                                yOffset++;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Exporter exporter = Exporters.getExporter();
                    File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                    FileOutputStream fos = null;
                    try {
                        fos = new FileOutputStream(file);
                        exporter.export(book1, fos);
                    } finally {
                        if (fos != null) {
                            fos.close();
                        }
                    }
                    Filedownload.save(new AMedia("Audit " + or.getReportName() + ".xlsx", null, null, file, true));
                });
        provenancePopup.appendChild(button);
    }

    private void buildContextMenuDrillDownIfApplicable(String region, int regionRow, int regionColumn){
        for (SName sName : myzss.getBook().getInternalBook().getNames()) {
            if (sName.getName().toLowerCase().startsWith("az_drilldown" + region.toLowerCase())) {
                String qualifier = sName.getName().substring(("az_drilldown" + region).length()).replace("_", " ");
                String drillDownString = BookUtils.getSnameCell(sName).getStringValue();
                if (drillDownString.length() > 0) {
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, region);
                    final List<String> rowHeadings = cellsAndHeadingsForDisplay.getRowHeadings().get(regionRow);
                    List<String> colHeadings = new ArrayList<>();
                    for (int rowNo = 0; rowNo< cellsAndHeadingsForDisplay.getColumnHeadings().size();rowNo++){
                        colHeadings.add(cellsAndHeadingsForDisplay.getColumnHeadings().get(rowNo).get(regionColumn)); // last one is the bottom row of col headings
                    }
                    //String rowHeading = rowHeadings.get(rowHeadings.size() - 1); // the right of the row headings for that cell
                    for (int colNo = 0; colNo < rowHeadings.size(); colNo++){
                        drillDownString = ReportUIUtils.replaceAll(drillDownString,"[rowheading" + (colNo + 1) + "]", rowHeadings.get(colNo));
                    }
                    drillDownString = ReportUIUtils.replaceAll(drillDownString,"[rowheading]", rowHeadings.get(rowHeadings.size()-1));
                    for (int rowNo = 0; rowNo < colHeadings.size();rowNo++){
                        drillDownString = ReportUIUtils.replaceAll(drillDownString,"[columnheading" + (rowNo + 1) + "]", colHeadings.get(rowNo));
                    }
                    drillDownString = ReportUIUtils.replaceAll(drillDownString,"[columnheading]", colHeadings.get(colHeadings.size()-1));

                    Menuitem ddItem = new Menuitem("Drill Down" + qualifier);
                    editPopup.appendChild(ddItem);
                    // right, showProvenance has been simplified a little, I need to parse out the report name here
                    String reportName = "";
                    if (drillDownString.contains(Constants.IN_SPREADSHEET)){
                        drillDownString = drillDownString.substring(drillDownString.indexOf(Constants.IN_SPREADSHEET) + Constants.IN_SPREADSHEET.length()).trim();
                        if (drillDownString.contains(" with ")){
                            reportName = drillDownString.substring(0, drillDownString.indexOf(" with ")).replace("`","");
                            drillDownString = drillDownString.substring(drillDownString.indexOf(" with ") + 6);
                        }
                    }
                    final String freportName = reportName;
                    final String context = drillDownString; // by now it should be context
                    ddItem.addEventListener("onClick",
                            event -> ZKComposerUtils.openDrillDown(loggedInUser, freportName, context, 0));
                    // now need to find the headings - is this easy?
                }
            }
        }
    }

    private void buildContextMenuDebug(String region, int regionRow, int regionColumn){
        // ok, adding new debug info here, it doesn't require values in the cell unlike provenance
        try {
            String debugString = SpreadsheetService.getDebugForCell(loggedInUser, reportId, region, regionRow, regionColumn);
            Label debugLabel = new Label();
            debugLabel.setMultiline(true);
            debugLabel.setValue("Debug\n");
            debugPopup.appendChild(debugLabel);
            debugLabel = new Label();
            debugLabel.setMultiline(true);
            debugLabel.setValue(ReportUIUtils.trimString(debugString));
            debugPopup.appendChild(debugLabel);
            Toolbarbutton button = new Toolbarbutton("Download Full Debug");
            button.addEventListener("onClick",
                    event -> {
                        OnlineReport or = OnlineReportDAO.findById(reportId);
                        Book book1 = Importers.getImporter().imports(Sessions.getCurrent().getWebApp().getResourceAsStream("/WEB-INF/DebugAudit.xlsx"), "Report name");
                        for (int sheetNumber = 0; sheetNumber < book1.getNumberOfSheets(); sheetNumber++) {
                            Sheet sheet = book1.getSheetAt(sheetNumber);
                            List<SName> namesForSheet = BookUtils.getNamesForSheet(sheet);
                            for (SName sName : namesForSheet) {
                                if (sName.getName().equalsIgnoreCase("Title")) {
                                    final SCell cell = sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow(), sName.getRefersToCellRegion().getColumn());
                                    cell.setStringValue("Debug " + or.getReportName());
                                }
                                if (sName.getName().equalsIgnoreCase("Data")) {
                                    // here we parse out the string into cells. It could be passed as arrays but I'm not sure how much help that is
                                    final String[] split = debugString.split("\n");
                                    int yOffset = 0;
                                    for (String line : split) {
                                        int xOffset = 0;
                                        String[] cells = line.split("\t");
                                        for (String cell : cells) {
                                            sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(cell);
                                            xOffset++;
                                        }
                                        yOffset++;
                                    }
                                }
                            }
                        }

                        Exporter exporter = Exporters.getExporter();
                        File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                        FileOutputStream fos = null;
                        try {
                            fos = new FileOutputStream(file);
                            exporter.export(book1, fos);
                        } finally {
                            if (fos != null) {
                                fos.close();
                            }
                        }
                        Filedownload.save(new AMedia("Debug " + or.getReportName() + ".xlsx", null, null, file, true));
                    });
            debugPopup.appendChild(button);
            Menuitem debugItem = new Menuitem("Debug");
            editPopup.appendChild(debugItem);
            debugItem.setPopup(debugPopup);
            instructionsLabel.setValue("");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void buildContextMenuInstructions(String region){
        final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, region);
        if (sentCells != null && sentCells.getData().size() > 0) {
            StringBuilder instructionsText = new StringBuilder();
            instructionsText.append("COLUMN HEADINGS\n\n");
            List<List<String>> headings = sentCells.getColHeadingsSource();
            if (headings != null) {
                for (List<String> row : sentCells.getColHeadingsSource()) { // flatten for the mo
                    for (String cell : row) {
                        instructionsText.append(cell).append("\n");
                    }
                }
            }
            instructionsText.append("\nROW HEADINGS\n\n");
            headings = sentCells.getRowHeadingsSource();
            if (headings != null) {
                for (List<String> row : sentCells.getRowHeadingsSource()) { // flatten for the mo
                    for (String cell : row) {
                        instructionsText.append(cell).append("\n");
                    }
                }
            }
            headings = sentCells.getContextSource();
            instructionsText.append("\nCONTEXT\n\n");
            if (headings != null) {
                for (List<String> row : sentCells.getContextSource()) { // flatten for the mo
                    for (String cell : row) {
                        instructionsText.append(cell).append("\n");
                    }
                }
            }
            instructionsLabel.setValue(instructionsText.toString());
        }
        Menuitem instructionsItem = new Menuitem("Region definition");
        editPopup.appendChild(instructionsItem);
        instructionsItem.setPopup(instructionsPopup);
    }
}