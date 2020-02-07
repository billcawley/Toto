package com.azquo.spreadsheet.controller;

import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.dataimport.ImportService;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Edd note : this class will probably be redundant after zapping AzquoBook, the old non ZK spreadsheet renderer
 *
 */

@Controller
@RequestMapping("/DownloadTemplate")
public class DownloadTemplateController {

    @RequestMapping
    public void handleRequest(HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "reportId", required = false) String reportId
    ) throws Exception {
        // deliver a pre prepared image. Are these names unique? Could images move between spreadsheets unintentionally?
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null) {
            return;
        }
        if (reportId != null && reportId.length() > 0) {
            if ((loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
                //report id is assumed to be integer - sent from the website
                OnlineReport onlineReport;
                if (loggedInUser.getUser().isDeveloper()) { // for the user
                    onlineReport = OnlineReportDAO.findForIdAndUserId(Integer.parseInt(reportId), loggedInUser.getUser().getBusinessId());
                } else { //any for the business for admin
                    onlineReport = OnlineReportDAO.findForIdAndBusinessId(Integer.parseInt(reportId), loggedInUser.getUser().getBusinessId());
                }
                Path bookPath = Paths.get(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.onlineReportsDir + onlineReport.getFilenameForDisk());
                response.setContentType("application/vnd.ms-excel"); // Set up mime type
                try {
                    DownloadController.streamFileToBrowser(bookPath, response);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }
}