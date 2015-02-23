package com.azquo.app.magento.controller;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.app.magento.service.DataLoadService;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.OnlineService;
import com.azquo.util.AzquoMailer;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
//import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Created by bill on 28/10/14
 *
 * Created to handle requests from the plugin.
 *
 */

@Controller
@RequestMapping("/Magento")

public class MagentoController {

    @Autowired
    private DataLoadService dataLoadService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    OnlineService onlineService;

    @Autowired
    LoginService loginService;

    @Autowired
    DatabaseDAO databaseDAO;

    @Autowired
    AzquoMailer azquoMailer;

    // we should start using the logger really
    //private static final Logger logger = Logger.getLogger(MagentoController.class);

    @RequestMapping(headers = "content-type=multipart/*")
    @ResponseBody
    public String handleRequest(HttpServletRequest request
            , @RequestParam(value = "db", required = false, defaultValue = "") String db
            , @RequestParam(value = "op", required = false, defaultValue = "") String op
            , @RequestParam(value = "logon", required = false, defaultValue = "") String logon
            , @RequestParam(value = "password", required = false, defaultValue = "") String password
            , @RequestParam(value = "connectionid", required = false, defaultValue = "") String connectionId
            , @RequestParam(value = "data", required = false) MultipartFile data

    ) throws Exception {
        LoggedInConnection loggedInConnection = null;
        try {

            if (op == null) op = "";
            if (connectionId != null) {
                loggedInConnection = loginService.getConnection(connectionId);
            }
            System.out.println("==================== db sent  : " + db);
            if (loggedInConnection == null) {
                //for testing only
                if (db == null) db = "temp";
                loggedInConnection = loginService.login(db, logon, password, 0, "", false);//will automatically switch the database to 'temp' if that's the only one
                if (loggedInConnection == null) {
                    return "error: user " + logon + " with this password does not exist";
                }
                if (!db.equals("temp")) {
                    onlineService.switchDatabase(loggedInConnection, db);
                    //todo  consider what happens if there's an error here (check the result from the line above?)
                }
                //loggedInConnection = loginService.login("test","magentobill","password",0,"",false);
            }
            if (op.equals("lastupdate")) {
                return dataLoadService.findLastUpdate(loggedInConnection);
            }
            if (op.equals("updatedb")) {
                if (loggedInConnection.getCurrentDatabase() != null) {
                    System.out.println("Running a magento update, memory db : " + loggedInConnection.getLocalCurrentDBName() + " max id on that db " + loggedInConnection.getMaxIdOnCurrentDB());
                }
                if (data != null){
                    File moved = null;
                    if (!onlineService.onADevMachine() && !request.getRemoteAddr().equals("82.68.244.254")){ // if it's from us don't email us :)
                        azquoMailer.sendEMail("edd@azquo.com", "Edd", "Magento file upload " + db, "Magento file upload " + db);
                        azquoMailer.sendEMail("bill@azquo.com", "Bill", "Magento file upload " + db, "Magento file upload " + db);
                        azquoMailer.sendEMail("nic@azquo.com", "Nic", "Magento file upload " + db, "Magento file upload " + db);
                        moved = new File(onlineService.getHomeDir() + "/temp/" + db + new Date());
                        data.transferTo(moved);
                    }
                    if (moved != null){
                        FileInputStream fis = new FileInputStream(moved);
                        dataLoadService.loadData(loggedInConnection, fis);
                        fis.close();
                    } else {
                        dataLoadService.loadData(loggedInConnection, data.getInputStream());
                    }
                    return loggedInConnection.getConnectionId() + "";
                } else {
                    return "error: no data posted";
                }
                //return onlineService.readExcel(loggedInConnection, onlineReport, null, "");
            }
            if (op.equals("reports")) {
                OnlineReport onlineReport = onlineReportDAO.findById(1);//TODO  Sort out where the maintenance sheet should be referenced
                return onlineService.readExcel(loggedInConnection, onlineReport, null, "");

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
            , @RequestParam(value = "connectionid", required = false, defaultValue = "") String connectionId

    ) throws Exception {
        return handleRequest(request, db, op, logon, password, connectionId, null);
    }

}