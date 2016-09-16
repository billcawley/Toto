package com.azquo.app.magento.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServerDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.database.Database;
import com.azquo.app.magento.service.DataLoadService;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.Constants;
import com.azquo.spreadsheet.*;
import com.azquo.util.AzquoMailer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 28/10/14
 * <p/>
 * Created to handle requests from the plugin. Want to zap the connection ID, depends on what the plugin allows
 *
 */

@Controller
@RequestMapping("/Magento")

public class MagentoController {

    // we should start using the logger really
    //private static final Logger logger = Logger.getLogger(MagentoController.class);

    @RequestMapping(headers = "content-type=multipart/*")
    @ResponseBody
    public String handleRequest(HttpServletRequest request,HttpServletResponse response
            , @RequestParam(value = "db", required = false, defaultValue = "") String db
            , @RequestParam(value = "op", required = false, defaultValue = "") String op
            , @RequestParam(value = "logon", required = false, defaultValue = "") String logon
            , @RequestParam(value = "password", required = false, defaultValue = "") String password
            , @RequestParam(value = "data", required = false) MultipartFile data
    ) throws Exception {
        SimpleDateFormat df = new SimpleDateFormat("yyMMdd-hhmmss");
        try {

            if (op == null) op = "";// can this happen with the annotation above?
            System.out.println("==================== db sent  : " + db + " op= " + op);
            //for testing only
            if (db == null || db.equals("")) { // default to temp if not passed
                db = "temp";
            }
            LoggedInUser loggedInUser = LoginService.loginLoggedInUser("",db, logon, password, false);
            if (loggedInUser == null) {
                return "error: user " + logon + " with this password does not exist";
            }
            // theree was a db switch here - pointless since we've just logged in with the DB
            if (op.equals("connect")) {
                if (DataLoadService.findLastUpdate(loggedInUser.getDataAccessToken(), request.getRemoteAddr()) != null) {
                    // was connection id here, hacking ths back in to get the logged in connection. We're dealing with the legacy of the conneciton id still in the plugin.
                    String tempConnectionId = System.currentTimeMillis() + "" + hashCode(); // adding the hashcode to make it much harder for someone to hack the connection id (which we need to zap)
                    request.getServletContext().setAttribute(tempConnectionId, loggedInUser);
                    return tempConnectionId;
                } else {
                    findRequiredTables(loggedInUser, request.getRemoteAddr());
                }
            }
            if (op.equals("restart")) {
                Database existingDb = loggedInUser.getDatabase();
                if (existingDb != null){
                    // an insecure call . . .
                    AdminService.emptyDatabase(DatabaseServerDAO.findById(existingDb.getDatabaseServerId()), loggedInUser.getDatabase());
                    Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                    String title = "Magento db clear " + logon + " - " + loggedInUser.getUser().getStatus() + " - " + (business != null ? business.getBusinessName() : "") + " from " + request.getRemoteAddr();
                    AzquoMailer.sendEMail("edd@azquo.com", "Edd", title, title);
                    AzquoMailer.sendEMail("ed.lennox@azquo.com", "Ed", title, title);
                    AzquoMailer.sendEMail("bill@azquo.com", "Bill", title, title);
                    AzquoMailer.sendEMail("nic@azquo.com", "Nic", title, title);
                }
                return findRequiredTables(loggedInUser, request.getRemoteAddr());
            }
            if (op.equals("lastupdate") || op.equals("requiredtables")) { // 'lastupdate' applies only to versions 1.1.0 and 1.1.1  (LazySusan and Lyco)
                return findRequiredTables(loggedInUser, request.getRemoteAddr());
            }
            if (op.equals("updatedb")) {
                findRequiredTables(loggedInUser, request.getRemoteAddr());//for curl commands only to load dates etc.
                if (loggedInUser.getDatabase() != null) {
                    System.out.println("Running a magento update, memory db : " + loggedInUser.getDatabase().getName() + " don't currently have access to max id, need to add that back in");
                }
                if (data != null) {
                    long start = System.currentTimeMillis();
                    // now copying all files, will make it easier for the client/server split. No passing of input streams just the file name
                    String tempDir = "/temp/" +loggedInUser.getDatabase().getPersistenceName()+ "-" + df.format(new Date());
                    File moved = new File(SpreadsheetService.getHomeDir() + tempDir);
                    data.transferTo(moved);
                    DataLoadService.loadData(loggedInUser.getDataAccessToken(), moved.getAbsolutePath(), request.getRemoteAddr(), loggedInUser.getUser().getName());
                    long elapsed = System.currentTimeMillis() - start;
                    if (!SpreadsheetService.onADevMachine() && !request.getRemoteAddr().equals("82.68.244.254") && !request.getRemoteAddr().equals("127.0.0.1") && !request.getRemoteAddr().startsWith("0")) { // if it's from us don't email us :)
                        Business business = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                        String title = "Magento file upload " + logon + " - " + loggedInUser.getUser().getStatus() + " - " + (business != null ? business.getBusinessName() : "") + " from " + request.getRemoteAddr() + " elapsed time " + elapsed + " millisec";
                        AzquoMailer.sendEMail("edd@azquo.com", "Edd", title, title);
                        AzquoMailer.sendEMail("ed.lennox@azquo.com", "Ed", title, title);
                        AzquoMailer.sendEMail("bill@azquo.com", "Bill", title, title);
                        AzquoMailer.sendEMail("nic@azquo.com", "Nic", title, title);
                    }
                    // was connection id here, hacking ths back in to get the logged in conneciton
                    String tempConnectionId = System.currentTimeMillis() + "";
                    request.getServletContext().setAttribute(tempConnectionId, loggedInUser);
                    return tempConnectionId;
                } else { 
                    return "error: no data posted";
                }
            }
            if (op.equals("reports")) {
                response.sendRedirect("/api/Online?reportid=1"); // I think that will do it
            }
            return "unknown op";
        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
    }

    // when not multipart, just pass it through

    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request,HttpServletResponse response
            , @RequestParam(value = "db", required = false, defaultValue = "") String db
            , @RequestParam(value = "op", required = false, defaultValue = "") String op
            , @RequestParam(value = "logon", required = false, defaultValue = "") String logon
            , @RequestParam(value = "password", required = false, defaultValue = "") String password

    ) throws Exception {
        return handleRequest(request, response, db, op, logon, password, null);
    }

    private String findRequiredTables(LoggedInUser loggedInUser, String remoteAddress) throws Exception{
        if (loggedInUser.getDatabase() == null){
            return "error: no database selected";
        }
        if (DataLoadService.magentoDBNeedsSettingUp(loggedInUser.getDataAccessToken())){
            String magentoSetupFile = SpreadsheetService.getHomeDir() + "/databases/ecommerce/setup/ecommerce setup.xlsx";
            String fileName = "magentosetup.xlsx";
            ImportService.importTheFile(loggedInUser, fileName, magentoSetupFile, Collections.singletonList(Constants.DEFAULT_DISPLAY_NAME), false);
        }
        return DataLoadService.findRequiredTables(loggedInUser.getDataAccessToken(), remoteAddress);
    }
}