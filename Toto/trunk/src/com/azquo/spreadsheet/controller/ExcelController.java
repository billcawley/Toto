package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.dataimport.ImportService;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by edward on 04/08/16.
 * <p>
 * We are reinstating some Excel functionality to assist in building reports. Initially the basics for logon and a few others.
 */

@Controller
@RequestMapping("/Excel")
public class ExcelController {

    public Map<String, LoggedInUser> excelConnections = new ConcurrentHashMap<>();// simple, for the moment should do it

    @ResponseBody
    public String handleRequest(@RequestParam(value = "logon", required = false, defaultValue = "") String logon
            , @RequestParam(value = "logoff", required = false, defaultValue = "") String logoff
            , @RequestParam(value = "password", required = false, defaultValue = "") String password
            , @RequestParam(value = "sessionid", required = false, defaultValue = "") String sessionid
            , @RequestParam(value = "name", required = false, defaultValue = "") String name
            , @RequestParam(value = "database", required = false) String database
    ) throws Exception {
        if (logoff != null && logoff.length() > 0) {
            return "removed from map with key : " + (excelConnections.remove(logoff) != null);
        }
        if (logon != null && logon.length() > 0) {
            LoggedInUser loggedInUser = LoginService.loginLoggedInUser("", database, logon, password, false);//will automatically switch the database to 'temp' if that's the only one
            if (loggedInUser == null) {
                return "error: user " + logon + " with this password does not exist";
            }
            if (loggedInUser.getDatabase() == null) {
                return "error: invalid database " + database;
            }
            String session = AdminService.encrypt("" + System.currentTimeMillis(), "" + hashCode()); // good as any I think
            excelConnections.put(session, loggedInUser);
            return session;
        }
        LoggedInUser loggedInUser = null;
        if (sessionid != null) {
            loggedInUser = excelConnections.get(sessionid);
        }
        if (loggedInUser == null) {
            return "error: invalid sessionid " + sessionid;
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
        }
        return "no action taken";
    }

    @RequestMapping(headers = "content-type=multipart/*")
    @ResponseBody
    public String handleRequest(@RequestParam(value = "sessionid", required = false, defaultValue = "") String sessionid
            , @RequestParam(value = "data", required = false) MultipartFile data
    ) throws Exception {
        LoggedInUser loggedInUser = null;
        if (sessionid != null) {
            loggedInUser = excelConnections.get(sessionid);
        }
        if (loggedInUser == null) {
            return "error: invalid sessionid " + sessionid;
        }
        // similar to code in manage databases
        String fileName = data.getOriginalFilename();
        // always move uplaoded files now, they'll need to be transferred to the DB server after code split
        File moved = new File(SpreadsheetService.getHomeDir() + "/temp/" + fileName);
        data.transferTo(moved);
        // need to add in code similar to report loading to give feedback on imports
        try {
            List<String> languages = new ArrayList<>(loggedInUser.getLanguages());
            languages.remove(loggedInUser.getUser().getEmail());
            return ImportService.importTheFile(loggedInUser, fileName, moved.getAbsolutePath(), languages, false);
        } catch (Exception e) {
            //e.printStackTrace();
            Throwable t = e;
            int check = 0;
            while (t.getCause() != null && check < 20) {
                t = t.getCause();
                check++;
            }
            String exceptionError = t.getMessage();
            if (exceptionError != null && exceptionError.contains("error:"))
                exceptionError = exceptionError.substring(exceptionError.indexOf("error:"));
            return exceptionError;
        }
    }
}
