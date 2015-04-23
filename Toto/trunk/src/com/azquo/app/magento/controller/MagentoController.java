package com.azquo.app.magento.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.app.magento.service.DataLoadService;
import com.azquo.spreadsheet.*;
import com.azquo.util.AzquoMailer;
//dataimport org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.util.Date;

/**
 * Created by bill on 28/10/14
 * <p/>
 * Created to handle requests from the plugin.
 */

@Controller
@RequestMapping("/Magento")

public class MagentoController {

    @Autowired
    private DataLoadService dataLoadService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    SpreadsheetService spreadsheetService;

    @Autowired
    LoginService loginService;

    @Autowired
    DatabaseDAO databaseDAO;

    @Autowired
    AzquoMailer azquoMailer;

    @Autowired
    AdminService adminService;


    // we should start using the logger really
    //private static final Logger logger = Logger.getLogger(MagentoController.class);

    @RequestMapping(headers = "content-type=multipart/*")
    @ResponseBody
    public String handleRequest(HttpServletRequest request
            , @RequestParam(value = "db", required = false, defaultValue = "") String db
            , @RequestParam(value = "op", required = false, defaultValue = "") String op
            , @RequestParam(value = "logon", required = false, defaultValue = "") String logon
            , @RequestParam(value = "password", required = false, defaultValue = "") String password
            , @RequestParam(value = "data", required = false) MultipartFile data

    ) throws Exception {
        try {

            if (op == null) op = "";// can this happen with the annotation above?
            System.out.println("==================== db sent  : " + db + " op= " + op);
            //for testing only
            if (db == null) {
                db = "temp";
            }
            LoggedInConnection loggedInConnection = loginService.login(db, logon, password, 0, "", false);//will automatically switch the database to 'temp' if that's the only one
            if (loggedInConnection == null) {
                return "error: user " + logon + " with this password does not exist";
            }
            if (!db.equals("temp") && db.length() > 0) {
                spreadsheetService.switchDatabase(loggedInConnection, db);
                //todo  consider what happens if there's an error here (check the result from the line above?)
            }
            if (op.equals("connect")) {
                if (dataLoadService.findLastUpdate(loggedInConnection) != null) {
                    // was connection id here, hacking ths back in to get the logged in conneciton
                    String tempConnectionId = System.currentTimeMillis() + "";
                    request.getServletContext().setAttribute(tempConnectionId, loggedInConnection);
                    return tempConnectionId;
                } else {
                    dataLoadService.findRequiredTables(loggedInConnection);
                }
            }

            // need to look at this carefully. I don't think the Logged InCOnnection is how I'd implement this if I started again. On the other hand
            if (op.equals("restart")) {
                Database existingDb = loggedInConnection.getCurrentDatabase();
                adminService.emptyDatabase(loggedInConnection.getCurrentDBName());
                loginService.switchDatabase(loggedInConnection, null);
                loginService.switchDatabase(loggedInConnection, existingDb);
                return dataLoadService.findRequiredTables(loggedInConnection);
            }

            if (op.equals("lastupdate") || op.equals("requiredtables")) { // 'lastupdate' applies only to versions 1.1.0 and 1.1.1  (LazySusan and Lyco)

                return dataLoadService.findRequiredTables(loggedInConnection);
            }
            if (op.equals("updatedb")) {
                dataLoadService.findRequiredTables(loggedInConnection);//for curl commands only to load dates etc.

                if (loggedInConnection.getCurrentDatabase() != null) {
                    System.out.println("Running a magento update, memory db : " + loggedInConnection.getLocalCurrentDBName() + " max id on that db " + loggedInConnection.getMaxIdOnCurrentDB());
                }

                if (data != null) {
                    File moved = null;
                    long start = System.currentTimeMillis();
                    if (!spreadsheetService.onADevMachine() && !request.getRemoteAddr().equals("82.68.244.254") && !request.getRemoteAddr().equals("127.0.0.1")) { // if it's from us don't save it :)
                        moved = new File(spreadsheetService.getHomeDir() + "/temp/" + db + new Date());
                        data.transferTo(moved);
                    }
                    if (moved != null) {
                        FileInputStream fis = new FileInputStream(moved);
                        dataLoadService.loadData(loggedInConnection, fis);
                        fis.close();
                    } else {
                        dataLoadService.loadData(loggedInConnection, data.getInputStream());
                    }
                    long elapsed = System.currentTimeMillis() - start;
                    if (!spreadsheetService.onADevMachine() && !request.getRemoteAddr().equals("82.68.244.254") && !request.getRemoteAddr().equals("127.0.0.1")) { // if it's from us don't email us :)
                        String title = "Magento file upload " + logon + " from " + request.getRemoteAddr() + " elapsed time " + elapsed + " millisec";
                        azquoMailer.sendEMail("edd@azquo.com", "Edd", title, title);
                        azquoMailer.sendEMail("bill@azquo.com", "Bill", title, title);
                        azquoMailer.sendEMail("nic@azquo.com", "Nic", title, title);
                    }
                    // was connection id here, hacking ths back in to get the logged in conneciton
                    String tempConnectionId = System.currentTimeMillis() + "";
                    request.getServletContext().setAttribute(tempConnectionId, loggedInConnection);
                    return tempConnectionId;
                } else {
                    return "error: no data posted";
                }
                //return onlineService.readExcel(loggedInConnection, onlineReport, null, "");
            }
            if (op.equals("reports")) {
                OnlineReport onlineReport = onlineReportDAO.findById(1);//TODO  Sort out where the maintenance sheet should be referenced
                return spreadsheetService.readExcel(loggedInConnection, onlineReport, null, "");
            }
            return "unknown op";
        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
    }

    // when not multipart

    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request
            , @RequestParam(value = "db", required = false, defaultValue = "") String db
            , @RequestParam(value = "op", required = false, defaultValue = "") String op
            , @RequestParam(value = "logon", required = false, defaultValue = "") String logon
            , @RequestParam(value = "password", required = false, defaultValue = "") String password

    ) throws Exception {
        return handleRequest(request, db, op, logon, password, null);
    }
}