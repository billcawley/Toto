package com.azquo.spreadsheet.controller;

import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/*

Edd note : this class will probably be redundant after zapping AzquoBook.

 */

@Controller
@RequestMapping("/Download")
public class DownloadController {

    @Autowired
    LoginService loginService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    SpreadsheetService spreadsheetService;

    @RequestMapping
    public void handleRequest(HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "macros", required = false, defaultValue = "false") boolean withMacros
            , @RequestParam(value = "pdf", required = false, defaultValue = "false") boolean pdf
            , @RequestParam(value = "image", required = false) String image
    ) throws Exception {
        // deliver a preprepared image. Are these names unique? Could images move between spreadsheets unintentionally?
        // Edd note - use nio?
        if (image != null && image.length() > 0) {
            response.setContentType("image/png"); // Set up mime type
            OutputStream out = response.getOutputStream();
            byte[] bucket = new byte[32 * 1024];
            int length = 0;
            try {
                // new java 8 syntax, a little odd but I'll leave here for the moment
                try (InputStream input = new BufferedInputStream((new FileInputStream(spreadsheetService.getHomeDir() + "/temp/" + image)))) {
                    int bytesRead = 0;
                    while (bytesRead != -1) {
                        //aInput.read() returns -1, 0, or more :
                        bytesRead = input.read(bucket);
                        if (bytesRead > 0) {
                            out.write(bucket, 0, bytesRead);
                            length += bytesRead;
                        }
                    }
                }
                response.setHeader("Content-Disposition", "inline; filename=\"" + image + "\"");
                response.setHeader("Content-Length", String.valueOf(length));
                out.flush();
                return;
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            out.flush();
            return;
        }
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null) {
            return;
        }
        OnlineReport onlineReport = null;
        int reportId = loggedInUser.getReportId();
        if (reportId != 0) {
            try {
                onlineReport = onlineReportDAO.findById(reportId);
            } catch (Exception ignored) {

            }
        }
        // response passed into the functions to be dealt with there. Should mimetypes be dealt with out here?
        String fileName = "/azquobook.xls";
        if (onlineReport != null) {
            fileName = onlineReport.getFilename();
        }
        if (fileName.indexOf("/") > 0) {
            fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        }
        if (pdf) {
            spreadsheetService.saveBookasPDF(response, loggedInUser, fileName);
        } else {
            if (withMacros) {
                spreadsheetService.saveBookActive(response, loggedInUser, fileName);
            } else {
                spreadsheetService.saveBook(response, loggedInUser, fileName);
            }
        }
    }
}




