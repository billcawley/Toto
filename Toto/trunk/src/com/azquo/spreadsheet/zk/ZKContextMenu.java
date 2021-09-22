package com.azquo.spreadsheet.zk;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.StringLiterals;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.OnlineController;
import com.azquo.spreadsheet.transport.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.math.NumberUtils;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Components;
import org.zkoss.zk.ui.Sessions;
import io.keikai.api.*;
import io.keikai.api.model.Book;
import io.keikai.api.model.Sheet;
import io.keikai.model.SCell;
import io.keikai.model.SName;
import io.keikai.model.SSheet;
import io.keikai.ui.Spreadsheet;
import io.keikaiex.ui.widget.Ghost;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.MouseEvent;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.*;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by edward on 26/01/17.
 * <p>
 * Code to deal with the context menu, might be a fair bit going on here.
 * <p>
 * Use of the ZK API to alter the user interface might be a little hacky, might be sensetive to changes to their API or implementation
 * <p>
 * THis class is a little bigger than I'd like. Could break code off for provenance stuff?
 * <p>
 * todo - string literals and check name lookups work for sheet names not just book names
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

    ZKContextMenu(Spreadsheet myzss) {
        this.myzss = myzss;
        editPopup = new Menupopup();
        // ZK SUPPORT - MATT NOTES: This is a reasonable way to create a popup and a menupopup. I'm not seeing a reason for the setId in your sample, but I assume you are using these IDs in other code locations
        // EFC note - removed the set id
        // ZK SUPPORT - MATT NOTES: draggable and dropable are related to the ZK drag and drop workflow. It doesn't appear that you are using this workflow in this sample
        // Draggable indicate that an object can be grabbed and dragged (a ghost duplicate will be dragged around by the mouse)
        // Droppable indicates that the object can be the target of a drop. If the mouse is hovering a droppable object, and the mouse button is released, the droppable object will trigger the onDrop event
        // https://www.zkoss.org/wiki/ZK_Developer's_Reference/UI_Patterns/Drag_and_Drop
        // again assuming that you are using this in a different code location
        // EFC note - removed those two also
        editPopup.setId("editPopup");
        setPopupStyle(editPopup);
        provenancePopup = new Popup();
        setPopupStyle(provenancePopup);
        debugPopup = new Popup();
        setPopupStyle(debugPopup);
        instructionsLabel = new Label();
        instructionsPopup = new Popup();
        setPopupStyle(instructionsPopup);
        instructionsLabel.setMultiline(true);
        instructionsPopup.appendChild(instructionsLabel);
        highlightPopup = new Popup();
        setPopupStyle(highlightPopup);
        // ZK SUPPORT - MATT NOTES: adding the popup as a child of a complex component is risky. A safer options is to assign them to the page directly, since they are displayed per x and y coordinates anyway.
        editPopup.setPage(myzss.getPage());
        provenancePopup.setPage(myzss.getPage());
        debugPopup.setPage(myzss.getPage());
        instructionsPopup.setPage(myzss.getPage());
        highlightPopup.setPage(myzss.getPage());
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
    void showAzquoContextMenu(int cellRow, int cellCol, int mouseX, int mouseY, Component ref, Spreadsheet myzss) throws JsonProcessingException {
        // ZK SUPPORT - MATT NOTES: recommended shortcut ;)
        Components.removeAllChildren(editPopup);
        Components.removeAllChildren(provenancePopup);
        Components.removeAllChildren(debugPopup);
        String region = null;
        int regionRow = 0;
        int regionColumn = 0;
        // adding support for repeat regions. There's an additional check for row headings in a normal data region but I think this is redundant in repeat regions
        List<SName> repeatRegionNames = BookUtils.getNamedRegionForRowAndColumnSelectedSheet(cellRow, cellCol, myzss.getSelectedSheet(), StringLiterals.AZREPEATSCOPE);
        if (!repeatRegionNames.isEmpty()) {
            // repeat can overlap now, this should help
            for (SName name : repeatRegionNames) {
                ZKComposer.RegionRowCol regionRowColForRepeatRegion = ReportUIUtils.getRegionRowColForRepeatRegion(myzss.getBook(), cellRow, cellCol, name);
                if (regionRowColForRepeatRegion != null) {
                    region = regionRowColForRepeatRegion.region;
                    regionRow = regionRowColForRepeatRegion.row;
                    regionColumn = regionRowColForRepeatRegion.col;
                    break;
                }
            }
        } else { // standard
            List<SName> names = BookUtils.getNamedDataRegionForRowAndColumnSelectedSheet(cellRow, cellCol, myzss.getSelectedSheet());
            for (SName name : names) {
                SName rowHeadingsName = BookUtils.getNameByName("az_rowheadings" + name.getName().substring(13), myzss.getSelectedSheet());
                if (rowHeadingsName != null) {
                    region = name.getName().substring(StringLiterals.AZDATAREGION.length());
                    regionRow = cellRow - name.getRefersToCellRegion().getRow();
                    regionColumn = cellCol - name.getRefersToCellRegion().getColumn();
                    break;
                }
            }
        }
        // going to put a check on region not being null - should provenance etc work on headings? It will stop it for the mo
        if (region != null) {
            // Edd adding in the user region options that are now required due to column and row languages
            SName optionsRegion = BookUtils.getNameByName(StringLiterals.AZOPTIONS + region, myzss.getSelectedSheet());
            String source = null;
            if (optionsRegion != null) {
                source = BookUtils.getSnameCell(optionsRegion).getStringValue();
            }
            UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, source);
            Database origDatabase = null;
            DatabaseServer origServer = null;
            try {
                // copy pasted from ReportRenderer about line 300, maybe factor?
                String databaseName = userRegionOptions.getDatabaseName();
                // fairly simple addition to allow multiple databases on the same report
                // also - can we deal with repeat regions?
                ProvenanceDetailsForDisplay provenanceDetailsForDisplay = null;
                if (databaseName != null) { // then switch database, fill and switch back!
                    origDatabase = loggedInUser.getDatabase();
                    origServer = loggedInUser.getDatabaseServer();
                    try {
                        LoginService.switchDatabase(loggedInUser, databaseName);
                        provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, reportId, myzss.getSelectedSheetName(), region, userRegionOptions, regionRow, regionColumn, 1000);
                    } catch (Exception e) {
                        e.printStackTrace(); // might want to do user feedback?
                    }
                } else {
                    provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, reportId, myzss.getSelectedSheetName(), region, userRegionOptions, regionRow, regionColumn, 1000);
                }
                if (provenanceDetailsForDisplay.getAuditForDisplayList() != null && !provenanceDetailsForDisplay.getAuditForDisplayList().isEmpty()) {
                    buildContextMenuProvenance(provenanceDetailsForDisplay, myzss);
                    buildContextMenuProvenanceDownload(provenanceDetailsForDisplay, reportId);
                    Menuitem auditItem = new Menuitem("Audit");
                    editPopup.appendChild(auditItem);
                    // todo zap other setpopups
                    // ZK SUPPORT - MATT NOTES: you may add a onClick event the the menuitem itself programatically in Java. This is more reliable that using the setPopup method, since you may also perform java logic at this stage, and control the new popup without relying on the relation to the previous menuitem
                    auditItem.addEventListener(Events.ON_CLICK, (EventListener<MouseEvent>) event -> {
                        // ZK SUPPORT - MATT NOTES: you may open a popup at arbitrary coordinates, or based on another component's position.
                        // In this case, I do not use the menuitem position, as it may already be closed at client-side
                        provenancePopup.open(myzss.isShowFormulabar() ? (event.getX() - 140) : event.getX(), event.getY());
                    });
                    buildContextMenuDrillDownIfApplicable(myzss.getSelectedSheetName(), region, regionRow, regionColumn);
                }
                buildContextMenuDebug(myzss.getSelectedSheetName(), region, userRegionOptions, regionRow, regionColumn);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (origServer != null && origDatabase != null) {
                loggedInUser.setDatabaseWithServer(origServer, origDatabase);
            }
            buildContextMenuInstructions(myzss.getSelectedSheetName(), region);
            Components.removeAllChildren(highlightPopup);

            // just concerned withe the user ones as opposed to the report ones
            userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
            int highlightDays = 0;
            if (userRegionOptions != null) {
                highlightDays = userRegionOptions.getHighlightDays();
            }
            String highlightString = highlightString(highlightDays);
            final String highlightList = "Current highlight is " + highlightString + "\n";
            Label highlightLabel = new Label();
            highlightLabel.setMultiline(true);
            highlightLabel.setValue(highlightList);
            highlightPopup.appendChild(highlightLabel);
            addHighlight(highlightPopup, 0);
            addHighlight(highlightPopup, RegionOptions.LATEST);
            addHighlight(highlightPopup, RegionOptions.ONEHOUR);
            addHighlight(highlightPopup, 1);
            addHighlight(highlightPopup, 7);
            addHighlight(highlightPopup, 30);
            addHighlight(highlightPopup, 90);
            Menuitem highlightItem = new Menuitem("Highlight");
            editPopup.appendChild(highlightItem);
            highlightItem.addEventListener(Events.ON_CLICK, (EventListener<MouseEvent>) event -> {
                // ZK SUPPORT - MATT NOTES: you may open a popup at arbitrary coordinates, or based on another component's position.
                // In this case, I do not use the menuitem position, as it may already be closed at client-side
                highlightPopup.open(event.getX(), event.getY());
            });
            if (ref != null) {
                editPopup.open(ref, "at_pointer");
            } else {
                editPopup.open(mouseX - 140, mouseY);
            }
        }

    }


    private void addHighlight(Popup highlightPopup, final int days) {
        String hDays = highlightString(days);
        final Toolbarbutton highlightButton = new Toolbarbutton("highlight " + hDays);
        highlightButton.addEventListener("onClick",
                event -> showHighlight(days));
        highlightPopup.appendChild(highlightButton);
        Label highlightLabel = (new Label("\n\n"));
        highlightLabel.setMultiline(true);
        highlightPopup.appendChild(highlightLabel);
    }

    private String highlightString(int days){
        if (days == 0) return "none";
        if (days == RegionOptions.LATEST) return"latest";
        if (days == RegionOptions.ONEHOUR){
            return "1 hour";
        }
        return days + " days";

    }

    private void showHighlight(int days) {
        Book book = myzss.getBook();
        boolean reload = false;
        LoggedInUser loggedInUser = (LoggedInUser) book.getInternalBook().getAttribute(OnlineController.LOGGED_IN_USER);
        int reportId = (Integer) book.getInternalBook().getAttribute(OnlineController.REPORT_ID);
        loggedInUser.setHighlightDays(days);
        for (SName name : book.getInternalBook().getNames()) {
            if (name.getName().toLowerCase().startsWith(StringLiterals.AZDATAREGION)) {
                String region = name.getName().substring(13);
                UserRegionOptions userRegionOptions = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), reportId, region);
                if (userRegionOptions == null) {
                    if (days != 0){
                        userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), reportId, region, "highlight=" + days + "\n");
                        UserRegionOptionsDAO.store(userRegionOptions);
                        reload = true;
                    }
                } else {
                    if (userRegionOptions.getHighlightDays() != days) {
                        userRegionOptions.setHighlightDays(days);
                        UserRegionOptionsDAO.store(userRegionOptions);
                        reload = true;
                    }
                }
            }
        }
        if (reload) {
            ZKComposerUtils.reloadBook(myzss, book);
        }
    }

    private Label geLabelForProvenanceMenu(String colour, String text) {
        Label provenanceLabel = new Label();
        provenanceLabel.setPre(true);
        provenanceLabel.setStyle("color:#" + colour + "; font-size:12pt");
        provenanceLabel.setMultiline(true);
        provenanceLabel.setValue(text);
        return provenanceLabel;
    }

    private void buildContextMenuProvenance(ProvenanceDetailsForDisplay provenanceDetailsForDisplay, Spreadsheet myzss) {

        provenancePopup.appendChild(geLabelForProvenanceMenu("000000", provenanceDetailsForDisplay.getHeadline() + "\n"));

        if (provenanceDetailsForDisplay.getFunction() != null) {
            provenancePopup.appendChild(geLabelForProvenanceMenu("000000", "Function : " + provenanceDetailsForDisplay.getFunction() + "\n"));
        }
        int count = 0;
        for (ProvenanceForDisplay provenanceForDisplay : provenanceDetailsForDisplay.getAuditForDisplayList()) {
            boolean breakLoop = false;
            provenancePopup.appendChild(geLabelForProvenanceMenu("0000ff", provenanceForDisplay.toString() + "\n"));
            if (provenanceForDisplay.getNames() != null && !provenanceForDisplay.getNames().isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (String name : provenanceForDisplay.getNames()) {
                    names.append("  ").append(name);
                }
                provenancePopup.appendChild(geLabelForProvenanceMenu("333333", names.toString() + "\n"));
            }
            if (provenanceForDisplay.getValueDetailsForProvenances() != null && !provenanceForDisplay.getValueDetailsForProvenances().isEmpty()) {
                for (ValueDetailsForProvenance valueDetailsForProvenance : provenanceForDisplay.getValueDetailsForProvenances()) {
                    if (provenanceForDisplay.isInSpreadsheet() && valueDetailsForProvenance.getId() > 0) {
                        final Toolbarbutton provButton = new Toolbarbutton("    " + valueDetailsForProvenance.getValueTextForDisplay());
                        provButton.setStyle("color:#000000; font-size:12pt");
                        // are we going to switch to another sheet? need to pas the book through
                        provButton.addEventListener("onClick",
                                event -> {
    /* Now we have the option to make a sheet per choice finding the sheet shouldn't be that difficult, is there a
    report name that matches along with a repeat item that matches one of the choices the issue then is how to focus
    the cell - we would need to scan the sent regions for the value id maybe or create the drilldown report but don't
    show it and instead just get the cell coordinates we're interested off it
     */
                                    Book book = myzss.getBook();
                                    boolean trySheetSwitch = false;
                                    for (SName name : book.getInternalBook().getNames()) {
                                        if (name.getName().toLowerCase().contains(StringLiterals.AZREPEATSHEET)) {
                                            trySheetSwitch = true;
                                            break;
                                        }
                                    }
                                    if (trySheetSwitch) {
                                        for (SSheet sheet : book.getInternalBook().getSheets()) {
                                            SName reportNameName = BookUtils.getNameByName(StringLiterals.AZREPORTNAME, book.getSheet(sheet.getSheetName()));
                                            if (reportNameName != null && reportNameName.getRefersToSheetName().equals(sheet.getSheetName()) &&
                                                    BookUtils.getSnameCell(reportNameName).getStringValue().equalsIgnoreCase(provenanceForDisplay.getName())) { // then it's a candidate
                                                SName repeatItemName = book.getInternalBook().getNameByName(StringLiterals.AZREPEATITEM, sheet.getSheetName()); // should be fine we want it specific to this
                                                if (repeatItemName != null) {
                                                    String repeatItem = BookUtils.getSnameCell(repeatItemName).getStringValue();
                                                    // does the repeatItem match a choice? I suppose there could be false matches, cross that bridge when we come to it.
                                                    Map<String, String> stringStringMap = ChoicesService.parseChoicesFromDrillDownContextString(provenanceForDisplay.getContext());
                                                    if (stringStringMap.values().contains(repeatItem)) { // it should case match as from the same source
                                                        // then it's this sheet we want
                                                        myzss.setSelectedSheet(sheet.getSheetName());
                                                        // ok, now, how to set the cell according to the value???
                                                        List<CellsAndHeadingsForDisplay> sentForReportAndSheet = loggedInUser.getSentForReportAndSheet(reportId, repeatItem);
                                                        String region = null;
                                                        int rowIndex = 0;
                                                        int colIndex = 0;
                                                        for (CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay : sentForReportAndSheet) {
                                                            rowIndex = 0;
                                                            for (List<CellForDisplay> row : cellsAndHeadingsForDisplay.getData()) {
                                                                colIndex = 0;
                                                                for (CellForDisplay cellForDisplay : row) {
                                                                    // now check the value id
                                                                    if (cellForDisplay.getValueId() == valueDetailsForProvenance.getId()) {
                                                                        region = cellsAndHeadingsForDisplay.getRegion(); // how we'll detect to break the outer loops
                                                                        break;
                                                                    }
                                                                    colIndex++;
                                                                }
                                                                if (region != null) {
                                                                    break;
                                                                }
                                                                rowIndex++;
                                                            }
                                                            if (region != null) {
                                                                break;
                                                            }
                                                        }
                                                        if (region != null) { // so now fund the data region's name, add the row and col indexes and we should have our cell to select!
                                                            SName dataRegionName = BookUtils.getNameByName(StringLiterals.AZDATAREGION + region, book.getSheet(sheet.getSheetName()));
                                                            if (dataRegionName.getRefersToSheetName().equalsIgnoreCase(sheet.getSheetName())) { // gotta double check as global names may be left hanging around . . .
                                                                myzss.focusTo(dataRegionName.getRefersToCellRegion().getRow() + rowIndex, dataRegionName.getRefersToCellRegion().getColumn() + colIndex);
                                                                myzss.setFocus(true);
                                                                return; // don't do the normal stuff
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    ZKComposerUtils.openDrillDown(loggedInUser, provenanceForDisplay.getName(), provenanceForDisplay.getContext(), valueDetailsForProvenance.getId());
                                });
                        provenancePopup.appendChild(provButton);
                    } else { // value without id? Not sure how that happens . . .
                        provenancePopup.appendChild(geLabelForProvenanceMenu("000000", "    " + valueDetailsForProvenance.getValueTextForDisplay()));

                    }
                    StringBuilder names = new StringBuilder();
                    for (String nameString : valueDetailsForProvenance.getNames()) {
                        names.append(", ");
                        names.append(nameString);
                    }
                    // I can't format within a label, need different labels for
                    provenancePopup.appendChild(geLabelForProvenanceMenu("333333", names.toString() + "\n"));
                    if (valueDetailsForProvenance.getHistoricValuesAndProvenance() != null) {
                        for (ValueDetailsForProvenance.HistoricValueAndProvenance historicValue : valueDetailsForProvenance.getHistoricValuesAndProvenance()) {
                            provenancePopup.appendChild(geLabelForProvenanceMenu("ff0000", "        " + historicValue.getValue()));
                            provenancePopup.appendChild(geLabelForProvenanceMenu("0000ff", "  " + historicValue.getProvenance() + "\n"));
                        }
                    }
                    count++;
                    if (count > 20) {
                        provenancePopup.appendChild(geLabelForProvenanceMenu("000000", "    .......\n"));
                        breakLoop = true;
                        break;
                    }
                }
            }
            if (breakLoop) {
                break;
            }
        }
    }

    private void buildContextMenuProvenanceDownload(ProvenanceDetailsForDisplay provenanceDetailsForDisplay, int reportId) {
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
                                int xOffset = 0;
                                String[] splitHeadline = provenanceDetailsForDisplay.getHeadline().split(",");
                                for (String item : splitHeadline) {
                                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(item);
                                    xOffset++;
                                }
                                yOffset++;
                                for (ProvenanceForDisplay provenanceForDisplay : provenanceDetailsForDisplay.getAuditForDisplayList()) {
                                    xOffset = 0;
                                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(provenanceForDisplay.toString());
                                    CellOperationUtil.applyFontColor(Ranges.range(sheet, sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset), "#0000FF");
                                    yOffset++;
                                    if (provenanceForDisplay.getNames() != null && !provenanceForDisplay.getNames().isEmpty()) {
                                        for (String name : provenanceForDisplay.getNames()) {
                                            xOffset++;
                                            sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(name);
                                        }
                                        yOffset++;
                                    }
                                    if (provenanceForDisplay.getValueDetailsForProvenances() != null && !provenanceForDisplay.getValueDetailsForProvenances().isEmpty()) {
                                        for (ValueDetailsForProvenance valueDetailsForProvenance : provenanceForDisplay.getValueDetailsForProvenances()) {
                                            xOffset = 0;
                                            xOffset++;
                                            if (NumberUtils.isNumber(valueDetailsForProvenance.getValueTextForDisplay())) {
                                                sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setNumberValue(Double.parseDouble(valueDetailsForProvenance.getValueTextForDisplay()));
                                            } else {
                                                sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(valueDetailsForProvenance.getValueTextForDisplay());
                                            }
                                            for (String name : valueDetailsForProvenance.getNames()) {
                                                xOffset++;
                                                sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(name);
                                            }
                                            yOffset++;
                                            if (valueDetailsForProvenance.getHistoricValuesAndProvenance() != null) {
                                                xOffset = 2;
                                                for (ValueDetailsForProvenance.HistoricValueAndProvenance historicValue : valueDetailsForProvenance.getHistoricValuesAndProvenance()) {
                                                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset).setStringValue(historicValue.getValue());
                                                    CellOperationUtil.applyFontColor(Ranges.range(sheet, sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset), "#FF0000");
                                                    sheet.getInternalSheet().getCell(sName.getRefersToCellRegion().getRow() + yOffset, sName.getRefersToCellRegion().getColumn() + xOffset + 1).setStringValue(historicValue.getProvenance());
                                                    yOffset++;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Exporter exporter = Exporters.getExporter();
                    File file = File.createTempFile(Long.toString(System.currentTimeMillis()), "temp");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        exporter.export(book1, fos);
                    }
                    Filedownload.save(new AMedia("Audit " + or.getReportName() + ".xlsx", null, null, file, true));
                });
        provenancePopup.appendChild(button);
    }

    private void buildContextMenuDrillDownIfApplicable(String sheetName, String region, int regionRow, int regionColumn) {
        for (SName sName : myzss.getBook().getInternalBook().getNames()) {
            // going through all the names there might be one without a name
            if (sName.getName() != null && sName.getName().toLowerCase().startsWith("az_drilldown" + region.toLowerCase())) {
                String qualifier = sName.getName().substring((StringLiterals.AZDRILLDOWN + region).length()).replace("_", " ");
                String drillDownString = BookUtils.getSnameCell(sName).getStringValue();
                if (drillDownString.length() > 0) {
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(reportId, sheetName, region);
                    final List<String> rowHeadings = cellsAndHeadingsForDisplay.getRowHeadings().get(regionRow);
                    List<String> colHeadings = new ArrayList<>();
                    for (int rowNo = 0; rowNo < cellsAndHeadingsForDisplay.getColumnHeadings().size(); rowNo++) {
                        colHeadings.add(cellsAndHeadingsForDisplay.getColumnHeadings().get(rowNo).get(regionColumn)); // last one is the bottom row of col headings
                    }
                    //String rowHeading = rowHeadings.get(rowHeadings.size() - 1); // the right of the row headings for that cell
                    for (int colNo = 0; colNo < rowHeadings.size(); colNo++) {
                        drillDownString = ReportUIUtils.replaceAll(drillDownString, "[rowheading" + (colNo + 1) + "]", rowHeadings.get(colNo));
                    }
                    drillDownString = ReportUIUtils.replaceAll(drillDownString, "[rowheading]", rowHeadings.get(rowHeadings.size() - 1));
                    for (int rowNo = 0; rowNo < colHeadings.size(); rowNo++) {
                        drillDownString = ReportUIUtils.replaceAll(drillDownString, "[columnheading" + (rowNo + 1) + "]", colHeadings.get(rowNo));
                    }
                    drillDownString = ReportUIUtils.replaceAll(drillDownString, "[columnheading]", colHeadings.get(colHeadings.size() - 1));

                    Menuitem ddItem = new Menuitem("Drill Down" + qualifier);
                    editPopup.appendChild(ddItem);
                    // right, showProvenance has been simplified a little, I need to parse out the report name here
                    String reportName = "";
                    if (drillDownString.contains(StringLiterals.IN_SPREADSHEET)) {
                        drillDownString = drillDownString.substring(drillDownString.indexOf(StringLiterals.IN_SPREADSHEET) + StringLiterals.IN_SPREADSHEET.length()).trim();
                        if (drillDownString.contains(" with ")) {
                            reportName = drillDownString.substring(0, drillDownString.indexOf(" with ")).replace("`", "");
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

    private void buildContextMenuDebug(String sheetName, String region, UserRegionOptions userRegionOptions, int regionRow, int regionColumn) {
        // ok, adding new debug info here, it doesn't require values in the cell unlike provenance
        try {
            String debugString = "\nRegion : " + region + "\n\n" + SpreadsheetService.getDebugForCell(loggedInUser, reportId, sheetName, region, userRegionOptions, regionRow, regionColumn);
            Label debugLabel = new Label();
            debugLabel.setMultiline(true);
            debugLabel.setValue("Derivation\n");
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
                                    cell.setStringValue("Derivation " + or.getReportName());
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
                        try (FileOutputStream fos = new FileOutputStream(file)) {
                            exporter.export(book1, fos);
                        }
                        Filedownload.save(new AMedia("Derivation " + or.getReportName() + ".xlsx", null, null, file, true));
                    });
            debugPopup.appendChild(button);
            Menuitem debugItem = new Menuitem("Derivation");
            editPopup.appendChild(debugItem);

            debugItem.addEventListener(Events.ON_CLICK, (EventListener<MouseEvent>) event -> {
                // ZK SUPPORT - MATT NOTES: you may open a popup at arbitrary coordinates, or based on another component's position.
                // In this case, I do not use the menuitem position, as it may already be closed at client-side
                debugPopup.open(event.getX(), event.getY());
            });
            instructionsLabel.setValue("");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void buildContextMenuInstructions(String sheetName, String region) throws JsonProcessingException {
        final CellsAndHeadingsForDisplay sentCells = loggedInUser.getSentCells(reportId, sheetName, region);
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
        instructionsItem.addEventListener(Events.ON_CLICK, (EventListener<MouseEvent>) event -> {
            // ZK SUPPORT - MATT NOTES: you may open a popup at arbitrary coordinates, or based on another component's position.
            // In this case, I do not use the menuitem position, as it may already be closed at client-side
            instructionsPopup.open(event.getX(), event.getY());
        });

        String host = myzss.getPage().getDesktop().getSession().getLocalName();
        //System.out.println("web host " + host);
        /*if (host.contains("localhost") || host.contains("192.168.1.16")){
            ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            // may as well prepare the json for the excel controller here, in future it will be JS but this is for a demo
            // zap new lines they just cause a pain for the JS
            String json = jacksonMapper.writeValueAsString("{\"reportId\": \"\"," +
                    "\"sheetName\": \"\"," +
                    "\"region\": \"\"," +
                    "\"optionsSource\": \"\"," +
                    "\"rowHeadings\": " + jacksonMapper.writeValueAsString(sentCells.getRowHeadingsSource()) + " ," +
                    "\"columnHeadings\": " + jacksonMapper.writeValueAsString(sentCells.getColHeadingsSource()) + "," +
                    "\"context\": " + jacksonMapper.writeValueAsString(sentCells.getContextSource()) +  "," +
                    "\"query\": [[]]," +
                    "\"userContext\": \"\"," +
                    "\"data\": [[]]," +
                    "\"comments\": [[]]}");

            Menuitem demoItem = new Menuitem("Graph Demo");
            editPopup.appendChild(demoItem);
            OnlineReport or = OnlineReportDAO.findById(reportId);
            demoItem.addEventListener(Events.ON_CLICK, (EventListener<MouseEvent>) event -> {
                Clients.evalJavaScript("window.open(\"/graphtest.jsp?report=" + URLEncoder.encode(or.getReportName(), "UTF-8")  + "&json=" + URLEncoder.encode(json) + "\")");
            });

            Menuitem demoItem2 = new Menuitem("Graph Demo YX");
            editPopup.appendChild(demoItem2);
            demoItem2.addEventListener(Events.ON_CLICK, (EventListener<MouseEvent>) event -> {
                Clients.evalJavaScript("window.open(\"/graphtest.jsp?report=" + URLEncoder.encode(or.getReportName(), "UTF-8")  + "&json=" + URLEncoder.encode(json) + "&YX=true\")");
            });

        }*/
    }

    public static void setPopupStyle(Popup popup) {
        popup.setStyle("background-color:#ffffff; border:5px solid #f58030; text-align:left");
    }
}