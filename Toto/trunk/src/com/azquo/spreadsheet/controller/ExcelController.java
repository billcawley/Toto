package com.azquo.spreadsheet.controller;

import com.azquo.TypedPair;
import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.transport.json.CellsAndHeadingsForExcel;
import com.azquo.spreadsheet.transport.json.ExcelJsonRequest;
import com.azquo.spreadsheet.transport.json.ExcelJsonSaveRequest;
import com.azquo.spreadsheet.transport.json.ProvenanceJsonRequest;
import com.azquo.spreadsheet.transport.*;
import com.azquo.spreadsheet.zk.ReportRenderer;
import com.azquo.spreadsheet.zk.ReportUIUtils;
import com.azquo.util.AzquoMailer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.zkoss.zss.api.Importers;
import org.zkoss.zss.api.model.Book;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static javax.xml.bind.DatatypeConverter.parseBase64Binary;

/**
 * Created by edward on 04/08/16.
 * <p>
 * We are reinstating some Excel functionality to assist in building reports. Initially the basics for logon and a few others.
 * <p>
 * Might need to be broken up later - maybe an Excel package as there's a zk package?
 */

@Controller
@RequestMapping("/Excel")
public class ExcelController {

    private static final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static final Map<String, LoggedInUser> excelConnections = new ConcurrentHashMap<>();// simple, for the moment should do it

    public static String SESSIONMARKER = "¬";
    @RequestMapping(produces = "text/json;charset=UTF-8")
    @ResponseBody
    public String handleRequest(HttpServletRequest request, @RequestParam(value = "logon", required = false, defaultValue = "") String logon
            , @RequestParam(value = "toggle", required = false) String toggle
            , @RequestParam(value = "logoff", required = false, defaultValue = "") String logoff
            , @RequestParam(value = "password", required = false, defaultValue = "") String password
            , @RequestParam(value = "sessionid", required = false, defaultValue = "") String sessionid
            , @RequestParam(value = "name", required = false, defaultValue = "") String name
            , @RequestParam(value = "reportNameCheck", required = false) String reportNameCheck
            , @RequestParam(value = "userChoices", required = false) String userChoices
            , @RequestParam(value = "choiceName", required = false) String choiceName
            , @RequestParam(value = "choiceValue", required = false) String choiceValue
            , @RequestParam(value = "resolveQuery", required = false) String resolveQuery
            , @RequestParam(value = "dropDownListForQuery", required = false) String dropDownListForQuery
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "jsonSave", required = false) String jsonSave
            , @RequestParam(value = "checkSession", required = false) String checkSession
            , @RequestParam(value = "provenanceJson", required = false) String provenanceJson
    ) {
        String errorMessage = null;
        try {
            if (reportNameCheck != null){
                reportNameCheck = reportNameCheck.trim();
            }
            if (toggle != null) {
                request.getSession().setAttribute("excelToggle", toggle);
                return "";
            }
            if (logoff != null && logoff.length() > 0) {
                return "removed from map with key : " + (excelConnections.remove(logoff) != null);
            }
            LoggedInUser loggedInUser = null;
            if (logon != null && logon.length() > 0) {
                loggedInUser = LoginService.loginLoggedInUser("", database, logon, password, reportNameCheck, false);
                if (loggedInUser == null) {
                    return "error: User " + logon + " with this password does not exist";
                }
                if (!"nic@azquo.com".equalsIgnoreCase(logon) && !SpreadsheetService.onADevMachine() && !request.getRemoteAddr().equals("82.68.244.254") && !request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().startsWith("0")) { // if it's from us don't email us :)
                    Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                    String title = "Excel login  " + logon + " - " + loggedInUser.getUser().getStatus() + " - " + (business != null ? business.getBusinessName() : "") + " from " + request.getRemoteAddr();
                    String userAgent = request.getHeader("User-Agent");
                    AzquoMailer.sendEMail("edd@azquo.com", "Edd", title, userAgent);
                    AzquoMailer.sendEMail("ed.lennox@azquo.com", "Ed", title, userAgent);
                    AzquoMailer.sendEMail("bill@azquo.com", "Bill", title, userAgent);
                    AzquoMailer.sendEMail("nic@azquo.com", "Nic", title, userAgent);
                    AzquoMailer.sendEMail("bruce.cooper@azquo.com", "Bruce", title, userAgent);
                }
                if (loggedInUser.getUser().getReportId() != 0) {
                    // populate the book as in the OnlineController but just do it server side then chuck it, the point is to sort the permissions
                    OnlineReport or = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                    if (or != null) { // then load it just here on the report server
                        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + or.getFilenameForDisk();
                        final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                        book.getInternalBook().setAttribute(OnlineController.REPORT_ID, or.getId());
                        book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                        book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                        ReportRenderer.populateBook(book, 0);
                    }
                }
                String session = Integer.toHexString(loggedInUser.hashCode()); // good as any I think
                excelConnections.put(session, loggedInUser);
                if (json == null && jsonSave == null && userChoices == null && dropDownListForQuery == null
                        && resolveQuery == null && choiceName == null && choiceValue == null && checkSession == null) { // a bit messy, sort later
                    return loggedInUser.getDbNames() + SESSIONMARKER + loggedInUser.getUser().getStatus()+ SESSIONMARKER + session;
                }
            }
            if (sessionid != null && sessionid.length() > 0) {
                loggedInUser = excelConnections.get(sessionid);
                if (loggedInUser!= null && database!=null){
                    LoginService.switchDatabase(loggedInUser,database);
                }
                if (checkSession != null && loggedInUser != null) {
                     return "true";
                }
            }
            if (checkSession != null) {
                return "false";
            }
            if (loggedInUser == null) {
                return "error: invalid sessionid " + sessionid;
            }
            if (database != null && !database.equals("listall") && !database.equals(loggedInUser.getDatabase().getName())) {
                LoginService.switchDatabase(loggedInUser, database);
            }
            if (reportNameCheck != null) { // try to identify the sheet, this just finds the id no need for security at the moment
                final OnlineReport forNameAndBusinessId = OnlineReportDAO.findForNameAndBusinessId(reportNameCheck, loggedInUser.getUser().getBusinessId());
                if (forNameAndBusinessId != null) {
                    return "" + forNameAndBusinessId.getId();
                }
                return "";
            }
            if (name != null && name.length() > 0) {
                List<String> names = SpreadsheetService.nameAutoComplete(loggedInUser, name);
                StringBuilder namesToReturn = new StringBuilder();
                for (String s : names) {
                    if (namesToReturn.length() == 0) {
                        namesToReturn.append(s);
                    } else {
                        namesToReturn.append("\n" + s);
                    }
                }
                return namesToReturn.toString();
            }
            if (database != null && database.equals("listall")) {
                List<Database> databases = null;
                if (loggedInUser.getUser().getStatus().equals("ADMINISTRATOR")) {
                    databases = DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
                } else {
                    databases = DatabaseDAO.findForUserId(loggedInUser.getUser().getId());
                }
                StringBuilder toReturn = new StringBuilder();
                for (Database db : databases) {
                    if (toReturn.length() == 0) toReturn.append(db.getName());
                    else toReturn.append("," + db.getName());

                }
                return toReturn.toString();
            }
            if (json != null && !json.isEmpty()) {
                // example from C# in excel!
                //{"rowHeadings":[["Opening balance"],["Inputs"],["Withdrawals"],["Interest"],["Closing balance"]],"columnHeadings":[["`All Months` children"]],"context":[[""]]}
                ExcelJsonRequest excelJsonRequest = jacksonMapper.readValue(json, ExcelJsonRequest.class);
                // set the DB if it's not set and the report is only allowed on one database
                if (loggedInUser.getDatabase() == null){
                    if (loggedInUser.getUser().isAdministrator()){
                        List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(excelJsonRequest.reportId);
                        if (databaseIdsForReportId.size() == 1){
                            LoginService.switchDatabase(loggedInUser, DatabaseDAO.findById(databaseIdsForReportId.iterator().next())); // fragile?
                        }
                    } else {
                        OnlineReport or = OnlineReportDAO.findById(excelJsonRequest.reportId);
                        TypedPair<OnlineReport, Database> onlineReportDatabaseTypedPair = loggedInUser.getPermissionsFromReport().get(or.getReportName().toLowerCase());
                        if (onlineReportDatabaseTypedPair != null){
                            LoginService.switchDatabase(loggedInUser, onlineReportDatabaseTypedPair.getSecond());
                        }
                    }
                }
                if (loggedInUser.getDatabase() == null){ // still null
                    return "error: unable to set database for report id " + excelJsonRequest.reportId;
                }
                // ok this will have to be moved
                String optionsSource = excelJsonRequest.optionsSource != null ? excelJsonRequest.optionsSource : "";

                UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), excelJsonRequest.reportId, excelJsonRequest.region, optionsSource);
                // UserRegionOptions from MySQL will have limited fields filled
                UserRegionOptions userRegionOptions2 = UserRegionOptionsDAO.findForUserIdReportIdAndRegion(loggedInUser.getUser().getId(), excelJsonRequest.reportId, excelJsonRequest.region);
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
                if (excelJsonRequest.rowHeadings == null || excelJsonRequest.rowHeadings.isEmpty()) { // no row headings is an import region - assign an empty sent cells. Todo - could this be factored?
                    List<List<String>> colHeadings = excelJsonRequest.columnHeadings;
                    // ok change from the logic used in ZK. In ZK we had to prepare a blank set of data cells to be modified
                    // as the user changed them but it's difficult to prepare them here as we don't know the data region size and luckily it's not necessary
                    // as the data is sent in a block from Excel. It might have changed size in the mean time (as in someone changed the data region size and now the headings don't match) but I'm nto that bothered by this for the mo
                    // put an empty data set here as the reference is final, fill it out below with the data sent size from the user
                    // note the col headings source is going in here as is without processing as in the case of ad-hoc it is not dynamic (i.e. an Azquo query), it's import file column headings, parsed into an array in Excel
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(excelJsonRequest.region, colHeadings, null, null, null, new ArrayList<>(), null, null, null, 0, userRegionOptions.getRegionOptionsForTransport(), null);
                    loggedInUser.setSentCells(excelJsonRequest.reportId, excelJsonRequest.sheetName, excelJsonRequest.region, cellsAndHeadingsForDisplay);
                    return "Empty space set to ad hoc data : " + excelJsonRequest.region;
                } else {
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), excelJsonRequest.region, 0, excelJsonRequest.rowHeadings, excelJsonRequest.columnHeadings,
                            excelJsonRequest.context, userRegionOptions, true);
                    loggedInUser.setSentCells(excelJsonRequest.reportId, excelJsonRequest.sheetName, excelJsonRequest.region, cellsAndHeadingsForDisplay);
                    return jacksonMapper.writeValueAsString(new CellsAndHeadingsForExcel(cellsAndHeadingsForDisplay));
                }
            }
            if (jsonSave != null && !jsonSave.isEmpty()) {
                // example?
                // todo : ad hoc . . .
                ExcelJsonSaveRequest excelJsonSaveRequest = jacksonMapper.readValue(jsonSave, ExcelJsonSaveRequest.class);
                String result = null;
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(excelJsonSaveRequest.reportId, excelJsonSaveRequest.sheetName, excelJsonSaveRequest.region);
                boolean adHoc = false;
                if (cellsAndHeadingsForDisplay != null) {
                    final List<List<CellForDisplay>> sentData = cellsAndHeadingsForDisplay.getData();
                    if (cellsAndHeadingsForDisplay.getRowHeadingsSource() == null && sentData.isEmpty()) { // as mentioned above, this means ad-hoc so populate the sent data with blank cells ready to be modified.
                        adHoc = true;
                        for (int rowNo = 0; rowNo < excelJsonSaveRequest.data.size(); rowNo++) {
                            List<CellForDisplay> oneRow = new ArrayList<>();
                            for (int colNo = 0; colNo < excelJsonSaveRequest.data.get(0).size(); colNo++) {
                                oneRow.add(new CellForDisplay(false, "", 0, false, rowNo, colNo, true, false, null, 0)); // make these ignored. Edd note : I'm not particularly happy about this, sent data should be sent data, this is just made up . . .
                            }
                            sentData.add(oneRow);
                        }
                    }
                    int itemsChanged = 0;
                    // ok now I need to look at the data sent by the excel and see if I need to change the cells and headings.
                    // CellFOrDisplay doens't detect changes so here I need to guess whether a change has happened or not to lessen checks on the server

                    if (!excelJsonSaveRequest.data.isEmpty()) {
                        // ignore changes outside the area - trim down to the sent data size
                        if (excelJsonSaveRequest.data.size() > sentData.size()){
                            excelJsonSaveRequest.data = excelJsonSaveRequest.data.subList(0, sentData.size());
                        }
                        if (excelJsonSaveRequest.data.get(0).size() > sentData.get(0).size()) {
                            for (List<String> row : excelJsonSaveRequest.data){
                                while (row.size() > sentData.get(0).size()){
                                    row.remove(row.size() - 1);
                                }
                            }
                        }

                        if (excelJsonSaveRequest.data.size() != sentData.size()) {
                            return "data region sizes between Excel and the server don't match for " + excelJsonSaveRequest.region;
                        }
                        if (excelJsonSaveRequest.data.get(0).size() != sentData.get(0).size()) {
                            System.out.println("first : " + excelJsonSaveRequest.data.get(0).size());
                            System.out.println("second : " + sentData.get(0).size());
                            return "data region sizes between Excel and the server don't match for " + excelJsonSaveRequest.region;
                        }
                    }
                    int rowIndex = 0;
                    for (List<String> row : excelJsonSaveRequest.data) {
                        int colIndex = 0;
                        for (String valueFromExcel : row) {
                            CellForDisplay cellForDisplay = sentData.get(rowIndex).get(colIndex);
                            String comment = excelJsonSaveRequest.comments.get(rowIndex).get(colIndex);
                            if (!cellForDisplay.isLocked()) { // no point saving if locked!
                                if (comment != null && !comment.isEmpty()) {
                                    cellForDisplay.setComment(comment);
                                }
                                try {
                                    double doubleValue = Double.parseDouble(valueFromExcel.replace(",", ""));
                                    // then it IS a double
                                    if (cellForDisplay.getDoubleValue() != doubleValue) {
                                        itemsChanged++;
                                        // fragments similar to ZK code
                                        cellForDisplay.setNewDoubleValue(doubleValue);
                                        String numericValue = doubleValue + "";
                                        if (numericValue.endsWith(".0")) {
                                            numericValue = numericValue.substring(0, numericValue.length() - 2);
                                        }
                                        cellForDisplay.setNewStringValue(numericValue);
                                    }
                                } catch (Exception e) {
                                    if (!cellForDisplay.getStringValue().equals(valueFromExcel)) {
                                        itemsChanged++;
                                        cellForDisplay.setNewDoubleValue(0);
                                        cellForDisplay.setNewStringValue(valueFromExcel);
                                    }
                                }
                            }
                            colIndex++;
                        }
                        rowIndex++;
                    }
                    // then reset the sent cells, they should be blanked after each save if it's an adhoc region. Need to think clearly about how things like this work.
                    if (adHoc) {
                        loggedInUser.setSentCells(excelJsonSaveRequest.reportId, excelJsonSaveRequest.sheetName, cellsAndHeadingsForDisplay.getRegion(), new CellsAndHeadingsForDisplay(cellsAndHeadingsForDisplay.getRegion(), cellsAndHeadingsForDisplay.getColumnHeadings(), null, null, null, new ArrayList<>(), null, null, null, 0, cellsAndHeadingsForDisplay.getOptions(), null));
                    }
                    if (itemsChanged > 0) {
                        OnlineReport report = OnlineReportDAO.findById(excelJsonSaveRequest.reportId);
                        loggedInUser.setContext(excelJsonSaveRequest.context); // in this case context for provenance
                        String toReturn = SpreadsheetService.saveData(loggedInUser, excelJsonSaveRequest.reportId, report.getReportName(), excelJsonSaveRequest.sheetName, excelJsonSaveRequest.region);
                        AdminService.updateNameAndValueCounts(loggedInUser, loggedInUser.getDatabase());
                        return toReturn;
                    } else {
                        return "true 0";
                    }
                }
            }

            if (userChoices != null) {
                return jacksonMapper.writeValueAsString(CommonReportUtils.getUserChoicesMap(loggedInUser));
            }
            if (dropDownListForQuery != null) {
                return jacksonMapper.writeValueAsString(CommonReportUtils.getDropdownListForQuery(loggedInUser, dropDownListForQuery));
            }
            if (resolveQuery != null) {
                return CommonReportUtils.resolveQuery(loggedInUser, resolveQuery);
            }
            if (choiceName != null && choiceValue != null) {
                choiceValue = choiceValue.trim();
                loggedInUser.userLog("Excel Choice select : " + choiceName + "," + choiceValue);
                SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceName, choiceValue);
            }
            if (provenanceJson != null) {
                ProvenanceJsonRequest provenanceJsonRequest = jacksonMapper.readValue(provenanceJson, ProvenanceJsonRequest.class);
                UserRegionOptions userRegionOptions = new UserRegionOptions(0, loggedInUser.getUser().getId(), provenanceJsonRequest.reportId, provenanceJsonRequest.region, ""); // todo - send options source from
                final ProvenanceDetailsForDisplay provenanceDetailsForDisplay = SpreadsheetService.getProvenanceDetailsForDisplay(loggedInUser, provenanceJsonRequest.reportId, provenanceJsonRequest.sheetName, provenanceJsonRequest.region, userRegionOptions, provenanceJsonRequest.row, provenanceJsonRequest.col, 1000);
                // todo - push the formatting to Excel! Just want it to work for the moment . . .
                StringBuilder toSend = new StringBuilder();
                int count = 0;
                int limit = 20;
                for (ProvenanceForDisplay provenanceForDisplay : provenanceDetailsForDisplay.getProcenanceForDisplayList()) {
                    toSend.append(provenanceForDisplay.toString() + "\n");
                    count++;
                    if (provenanceForDisplay.getNames() != null && !provenanceForDisplay.getNames().isEmpty()) {
                        for (String n : provenanceForDisplay.getNames()) {
                            toSend.append("\t" + n);
                        }
                        toSend.append("\n");
                        count++;
                    }
                    if (provenanceForDisplay.getValuesWithIdsAndNames() != null && !provenanceForDisplay.getValuesWithIdsAndNames().isEmpty()) {
                        for (TypedPair<Integer, List<String>> value : provenanceForDisplay.getValuesWithIdsAndNames()) {
                            if (value.getSecond() != null && !value.getSecond().isEmpty()) {
                                for (String valueOrName : value.getSecond()) {
                                    toSend.append("\n\t" + valueOrName);
                                }
                                count++;
                                if (++count > limit) {
                                    break;
                                }
                            }
                        }
                    }
                    if (count > limit) {
                        break;
                    }
                }
                return ReportUIUtils.trimString(toSend.toString());
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
            if (errorMessage == null){
                errorMessage = "Unknown server side error, check logs.";
            }
        } catch (Exception e) {
            errorMessage = e.getMessage();
            if (errorMessage == null){
                errorMessage = "Unknown server side error, check logs.";
            }
            e.printStackTrace();
        }
        if (errorMessage != null) {
            return "error: " + errorMessage;
        }
        return "no action taken";
    }

    @RequestMapping(headers = "content-type=multipart/*")
    @ResponseBody
    public String handleRequest(@RequestParam(value = "sessionid", required = false, defaultValue = "") String sessionid
            , @RequestParam(value = "database", required = false, defaultValue = "") String database
            , @RequestParam(value = "data", required = false) MultipartFile data
    ) throws Exception {
        LoggedInUser loggedInUser = null;
        if (sessionid != null) {
            loggedInUser = excelConnections.get(sessionid);
        }
        if (loggedInUser == null) {
            return "error: invalid sessionid " + sessionid;
        }
        if (database != null && !database.equals(loggedInUser.getDatabase().getName())) {
            LoginService.switchDatabase(loggedInUser, database);
        }
        // similar to code in manage databases
        String fileName = data.getOriginalFilename();
        // always move uplaoded files now, they'll need to be transferred to the DB server after code split
        File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // stop overwriting with successive uploads
        try (FileOutputStream fos = new FileOutputStream(moved)) {
            byte[] byteArray = data.getBytes();
            String s = new String(byteArray);
            fos.write(parseBase64Binary(s));
            fos.close();
        }
        // need to add in code similar to report loading to give feedback on imports
        try {
            List<String> languages = new ArrayList<>(loggedInUser.getLanguages());
            languages.remove(loggedInUser.getUser().getEmail());
            return ImportService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), languages, false);
        } catch (Exception e) {
            return CommonReportUtils.getErrorFromServerSideException(e);
        }
    }
}