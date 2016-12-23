package com.azquo.spreadsheet.controller;

import com.azquo.TypedPair;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.view.*;
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
 */

@Controller
@RequestMapping("/Excel")
public class ExcelController {

    static final ObjectMapper jacksonMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static Map<String, LoggedInUser> excelConnections = new ConcurrentHashMap<>();// simple, for the moment should do it

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
            if (toggle != null) {
                request.getSession().setAttribute("excelToggle", toggle);
                return "";
            }
            if (logoff != null && logoff.length() > 0) {
                return "removed from map with key : " + (excelConnections.remove(logoff) != null);
            }
            LoggedInUser loggedInUser = null;
            if (logon != null && logon.length() > 0) {
                loggedInUser = LoginService.loginLoggedInUser("", database, logon, password, false);
                if (loggedInUser == null) {
                    return "error: user " + logon + " with this password does not exist";
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
                if (loggedInUser.getUser().getReportId() != 0){
                    // populate the book as in the OnlineController but just do it server side then chuck it, the point is to sort the permissions
                    OnlineReport or = OnlineReportDAO.findById(loggedInUser.getUser().getReportId());
                    if (or != null){ // then load it just here on the report server
                        String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + or.getFilenameForDisk();
                        final Book book = Importers.getImporter().imports(new File(bookPath), "Report name");
                        book.getInternalBook().setAttribute(OnlineController.REPORT_ID, or.getId());
                        book.getInternalBook().setAttribute(OnlineController.BOOK_PATH, bookPath);
                        book.getInternalBook().setAttribute(OnlineController.LOGGED_IN_USER, loggedInUser);
                        ZKAzquoBookUtils.populateBook(book, 0);
                    }
                }
                String session = Integer.toHexString(loggedInUser.hashCode()); // good as any I think
                excelConnections.put(session, loggedInUser);
                if (json == null && jsonSave == null && userChoices == null && dropDownListForQuery == null
                        && resolveQuery == null && choiceName == null && choiceValue == null && checkSession == null) { // a bit messy, sort later
                    return session;
                }
            }
            if (sessionid != null && sessionid.length() > 0) {
                loggedInUser = excelConnections.get(sessionid);
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
                if (forNameAndBusinessId != null){
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
                // value id??
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
                if (excelJsonRequest.rowHeadings == null || excelJsonRequest.rowHeadings.isEmpty()){ // no row headings is an import region - assign an empty sent cells. Todo - could this be factored?
                    List<List<String>> colHeadings = excelJsonRequest.columnHeadings;
                    // ok change from the logic used in ZK. In ZK we had to prepare a blank set of data cells to be modified
                    // as the user changed them but it's difficult to prepare them here as we don't know the data region size and luckily it's not necessary
                    // as the data is sent in a block from Excel. It might have changed size in the mean time (as in someone changed the data region size and now the headings don't match) but I'm nto that bothered by this for the mo
                    // put an empty data set here as the reference is final, fill it out below with the data sent size from the user
                    // note the col headings source is going in here as is without processing as in the case of ad-hoc it is not dynamic (i.e. an Azquo query), it's import file column headings, parsed into an array in Excel
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = new CellsAndHeadingsForDisplay(excelJsonRequest.region, colHeadings, null, new ArrayList<>(), null, null, null, 0, userRegionOptions.getRegionOptionsForTransport(), null);
                    loggedInUser.setSentCells(excelJsonRequest.reportId, excelJsonRequest.region, cellsAndHeadingsForDisplay);
                    return "Empty space set to ad hoc data : " + excelJsonRequest.region;
                } else {
                    CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), excelJsonRequest.region, 0, excelJsonRequest.rowHeadings, excelJsonRequest.columnHeadings,
                            excelJsonRequest.context, userRegionOptions, true);
                    loggedInUser.setSentCells(excelJsonRequest.reportId, excelJsonRequest.region, cellsAndHeadingsForDisplay);
                    return jacksonMapper.writeValueAsString(new CellsAndHeadingsForExcel(cellsAndHeadingsForDisplay));
                }
            }
            if (jsonSave != null && !jsonSave.isEmpty()) {
                // example?
                // todo : ad hoc . . .
                ExcelJsonSaveRequest excelJsonSaveRequest = jacksonMapper.readValue(jsonSave, ExcelJsonSaveRequest.class);
                String result = null;
                CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = loggedInUser.getSentCells(excelJsonSaveRequest.reportId, excelJsonSaveRequest.region);
                boolean adHoc = false;
                if (cellsAndHeadingsForDisplay != null) {
                    final List<List<CellForDisplay>> sentData = cellsAndHeadingsForDisplay.getData();
                    if (cellsAndHeadingsForDisplay.getRowHeadingsSource() == null && sentData.isEmpty()){ // as mentioned above, this means ad-hoc so populate the sent data with blank cells ready to be modified.
                        adHoc = true;
                        for (int rowNo = 0; rowNo < excelJsonSaveRequest.data.size(); rowNo++) {
                            List<CellForDisplay> oneRow = new ArrayList<>();
                            for (int colNo = 0; colNo < excelJsonSaveRequest.data.get(0).size(); colNo++) {
                                oneRow.add(new CellForDisplay(false, "", 0, false, rowNo, colNo, true, false, null)); // make these ignored. Edd note : I'm not particularly happy about this, sent data should be sent data, this is just made up . . .
                            }
                            sentData.add(oneRow);
                        }
                    }
                    int itemsChanged = 0;
                    // ok now I need to look at the data sent by the excel and see if I need to change the cells and headings.
                    // CellFOrDisplay doens't detect changes so here I need to guess whether a change has happened or not to lessen checks on the server

                    // todo : problem. If the data region is bigger than the sent cells then in ZK this is a moot point, changes outside the data region are ignored.
                    // here they are not, the contents of teh data region are sent back including for example empty space in ad hoc stuff and this trips it up.
                    // ignore changes outside the area? Not sure . . .

                    if (!excelJsonSaveRequest.data.isEmpty()) {
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
                                if (comment != null && !comment.isEmpty()){
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
                    if (adHoc){
                        loggedInUser.setSentCells(excelJsonSaveRequest.reportId, cellsAndHeadingsForDisplay.getRegion(), new CellsAndHeadingsForDisplay(cellsAndHeadingsForDisplay.getRegion(), cellsAndHeadingsForDisplay.getColumnHeadings(), null, new ArrayList<>(), null, null, null, 0, cellsAndHeadingsForDisplay.getOptions(), null));
                    }
                    if (itemsChanged > 0) {
                        OnlineReport report = OnlineReportDAO.findById(excelJsonSaveRequest.reportId);
                        loggedInUser.setContext(excelJsonSaveRequest.context); // in this case context for provenance
                        result = SpreadsheetService.saveData(loggedInUser, excelJsonSaveRequest.region, excelJsonSaveRequest.reportId, report.getReportName());
                        if ("true".equals(result)) {
                            return itemsChanged + " cells saved.";
                        } else {
                            return result;
                        }
                    } else {
                        return "No changed detected.";
                    }
                }
            }

            if (userChoices != null) {
                return jacksonMapper.writeValueAsString(AzquoBookUtils.getUserChoicesMap(loggedInUser));
            }
            if (dropDownListForQuery != null) {
                return jacksonMapper.writeValueAsString(AzquoBookUtils.getDropdownListForQuery(loggedInUser, dropDownListForQuery));
            }
            if (resolveQuery != null) {
                return AzquoBookUtils.resolveQuery(loggedInUser, resolveQuery);
            }
            if (choiceName != null && choiceValue != null) {
                choiceValue = choiceValue.trim();
                loggedInUser.userLog("Choice select : " + choiceName + "," + choiceValue);
                SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceName, choiceValue);
            }
            if (provenanceJson != null) {
                ProvenanceJsonRequest provenanceJsonRequest = jacksonMapper.readValue(provenanceJson, ProvenanceJsonRequest.class);
                TypedPair<Integer, String> fullProvenance = AzquoBookUtils.getFullProvenanceStringForCell(loggedInUser, provenanceJsonRequest.reportId
                        , provenanceJsonRequest.region, provenanceJsonRequest.row, provenanceJsonRequest.col);
                return ZKComposer.trimString(fullProvenance.getSecond());
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
        FileOutputStream fos = new FileOutputStream(moved);
        byte[] byteArray = data.getBytes();
        String s = new String(byteArray);
        fos.write(parseBase64Binary(s));
        fos.close();
        // need to add in code similar to report loading to give feedback on imports
        try {
            List<String> languages = new ArrayList<>(loggedInUser.getLanguages());
            languages.remove(loggedInUser.getUser().getEmail());
            return ImportService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), languages, false);
        } catch (Exception e) {
            return AzquoBookUtils.getErrorFromServerSideException(e);
        }
    }
}