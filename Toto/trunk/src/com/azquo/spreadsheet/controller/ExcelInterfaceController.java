package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.List;

/**
 * Created by edward on 18/11/16.
 *
 * The way to download an Excel file that will talk to Azquo!
 *
 * Stripped down top of online controller - factor?
 */
@Controller
@RequestMapping("/ExcelInterface")
public class ExcelInterfaceController {
    private final Runtime runtime = Runtime.getRuntime();
    private final int mb = 1024 * 1024;

    @RequestMapping
    public void handleRequest(HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "reportid", required = false) String reportId
            , @RequestParam(value = "databaseid", required = false) String databaseId
            , @RequestParam(value = "permissionid", required = false) String permissionId
            , @RequestParam(value = "database", required = false, defaultValue = "") String database

    ) {
        try {
            LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
            if (loggedInUser != null) {
                OnlineReport onlineReport = null;
                if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper()) && reportId != null && reportId.length() > 0 && !reportId.equals("1")) { // admin, we allow a report and possibly db to be set
                    //report id is assumed to be integer - sent from the website
                    if (loggedInUser.getUser().isDeveloper()) { // for the user
                        onlineReport = OnlineReportDAO.findForIdAndUserId(Integer.parseInt(reportId), loggedInUser.getUser().getBusinessId());
                    } else { //any for the business for admin
                        onlineReport = OnlineReportDAO.findForIdAndBusinessId(Integer.parseInt(reportId), loggedInUser.getUser().getBusinessId());
                    }
                    // todo - decide which method to switch databases
                    if (databaseId != null && databaseId.length() > 0) {
                        final List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(onlineReport.getId());
                        for (int dbId : databaseIdsForReportId) {
                            if (dbId == Integer.parseInt(databaseId)) {
                                LoginService.switchDatabase(loggedInUser, DatabaseDAO.findById(dbId));
                            }
                        }
                    }
                    if (database != null) {
                        LoginService.switchDatabase(loggedInUser, database);
                    }
                } else if (permissionId != null && permissionId.length() > 0) {
                    //new logic for permissions ad hoc on a report
                    if (loggedInUser.getPermissionsFromReport().get(permissionId.toLowerCase()) != null) { // then we have a permission as set by a report
                        onlineReport = OnlineReportDAO.findForNameAndBusinessId(permissionId, loggedInUser.getUser().getBusinessId());
                        if (onlineReport != null) {
                            reportId = onlineReport.getId() + ""; // hack for permissions
                            LoginService.switchDatabase(loggedInUser, loggedInUser.getPermissionsFromReport().get(permissionId.toLowerCase()).getSecond());
                        }
                    }
                }
                // db and report should be sorted by now
                if (onlineReport != null) {
                    // sort the session id, prefix it on the file name and then download it to the user
                    String sessionHash =  AdminService.shaHash("" + loggedInUser.hashCode());
                    ExcelController.excelConnections.put(sessionHash, loggedInUser); // could be repeatedly putting, do we care?
                    String bookPath = SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + "/onlinereports/" + onlineReport.getFilenameForDisk();
                    response.setContentType("application/vnd.ms-excel"); // Set up mime type
                    String extension = onlineReport.getFilenameForDisk().substring(onlineReport.getFilenameForDisk().lastIndexOf("."));
                    response.addHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(sessionHash + "-" + onlineReport.getId() + "-" + onlineReport.getReportName() + extension));
                    OutputStream out = response.getOutputStream();
                    FileInputStream in = new FileInputStream(bookPath);
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = in.read(buffer)) > 0){
                        out.write(buffer, 0, length);
                    }
                    in.close();
                    out.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}











