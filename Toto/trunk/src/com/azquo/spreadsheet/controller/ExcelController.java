package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.user.UserRegionOptions;
import com.azquo.admin.user.UserRegionOptionsDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.view.AzquoBookUtils;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import com.azquo.spreadsheet.view.ExcelJsonRequest;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
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

    static public Map<String, LoggedInUser> excelConnections = new ConcurrentHashMap<>();// simple, for the moment should do it

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "logon", required = false, defaultValue = "") String logon
            , @RequestParam(value = "logoff", required = false, defaultValue = "") String logoff
            , @RequestParam(value = "password", required = false, defaultValue = "") String password
            , @RequestParam(value = "sessionid", required = false, defaultValue = "") String sessionid
            , @RequestParam(value = "name", required = false, defaultValue = "") String name
            , @RequestParam(value = "userChoices", required = false) String userChoices
            , @RequestParam(value = "choiceName", required = false) String choiceName
            , @RequestParam(value = "choiceValue", required = false) String choiceValue
            , @RequestParam(value = "resolveQuery", required = false) String resolveQuery
            , @RequestParam(value = "dropDownListForQuery", required = false) String dropDownListForQuery
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "checkSession", required = false) String checkSession
    ) throws Exception {
        if (logoff != null && logoff.length() > 0) {
            return "removed from map with key : " + (excelConnections.remove(logoff) != null);
        }
        LoggedInUser loggedInUser = null;
        if (logon != null && logon.length() > 0) {
            loggedInUser = LoginService.loginLoggedInUser("", database, logon, password, false);
            if (loggedInUser == null) {
                return "error: user " + logon + " with this password does not exist";
            }
            if (loggedInUser.getDatabase() == null) {
                return "error: invalid database " + database;
            }
            String session = AdminService.shaHash("" + loggedInUser.hashCode()); // good as any I think
            excelConnections.put(session, loggedInUser);
            if (json == null && userChoices == null && dropDownListForQuery == null
                    && resolveQuery == null && choiceName == null && choiceValue == null && checkSession == null){ // a bit messy, sort later
                return session;
            }
        }
        if (sessionid != null && sessionid.length() > 0) {
            loggedInUser = excelConnections.get(sessionid);
            if (checkSession != null && loggedInUser != null){
                return "true";
            }
        }
        if (checkSession != null){
            return "false";
        }
        if (loggedInUser == null) {
            return "error: invalid sessionid " + sessionid;
        }
        if (database != null && !database.equals("listall") && !database.equals(loggedInUser.getDatabase().getName())){
            LoginService.switchDatabase(loggedInUser,database);
        }
        if (name != null && name.length() > 0){
            List<String> names = SpreadsheetService.nameAutoComplete(loggedInUser, name);
            StringBuilder namesToReturn = new StringBuilder();
            for (String s : names){
                if (namesToReturn.length() == 0){
                    namesToReturn.append(s);
                } else {
                    namesToReturn.append("\n" + s);
                }
            }
            return namesToReturn.toString();
        }
        if (database != null && database.equals("listall")){
            List<Database> databases = null;
            if (loggedInUser.getUser().getStatus().equals("ADMINISTRATOR")){
                databases = DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
            }else{
                databases = DatabaseDAO.findForUserId(loggedInUser.getUser().getId());
            }
            StringBuilder toReturn = new StringBuilder();
            for (Database db:databases){
                if (toReturn.length() == 0) toReturn.append(db.getName());
                else toReturn.append("," + db.getName());

            }
            return toReturn.toString();
        }
        if (json != null && !json.isEmpty()){
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

            CellsAndHeadingsForDisplay cellsAndHeadingsForDisplay = SpreadsheetService.getCellsAndHeadingsForDisplay(loggedInUser.getDataAccessToken(), excelJsonRequest.region, 0, excelJsonRequest.rowHeadings, excelJsonRequest.columnHeadings,
                    excelJsonRequest.context, userRegionOptions, true);
            loggedInUser.setSentCells(excelJsonRequest.reportId, excelJsonRequest.region, cellsAndHeadingsForDisplay);
            return jacksonMapper.writeValueAsString(cellsAndHeadingsForDisplay);
        }
        if (userChoices != null){
            return jacksonMapper.writeValueAsString(AzquoBookUtils.getUserChoicesMap(loggedInUser));
        }
        if (dropDownListForQuery != null){
            return jacksonMapper.writeValueAsString(AzquoBookUtils.getDropdownListForQuery(loggedInUser, dropDownListForQuery));
        }
        if (resolveQuery != null){
            return AzquoBookUtils.resolveQuery(loggedInUser, resolveQuery);
        }
        if (choiceName != null && choiceValue != null){
            loggedInUser.userLog("Choice select : " + choiceName + "," + choiceValue);
            SpreadsheetService.setUserChoice(loggedInUser.getUser().getId(), choiceName, choiceValue);
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
        if (database!=null && !database.equals(loggedInUser.getDatabase().getName())){
            LoginService.switchDatabase(loggedInUser,database);
        }
        // similar to code in manage databases
        String fileName = data.getOriginalFilename();
        // always move uplaoded files now, they'll need to be transferred to the DB server after code split
        File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + System.currentTimeMillis() + fileName); // stop overwriting with successive uploads
        FileOutputStream fos  = new FileOutputStream(moved);
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