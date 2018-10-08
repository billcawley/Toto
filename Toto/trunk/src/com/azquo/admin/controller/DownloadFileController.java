package com.azquo.admin.controller;

import com.azquo.admin.database.UploadRecord;
import com.azquo.admin.database.UploadRecordDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.DownloadController;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Created by edward on 25/05/16.
 *
 * To download uploaded files,
 */
@Controller
@RequestMapping("/DownloadFile")
public class DownloadFileController {

    @RequestMapping
    public void handleRequest(HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "uploadRecordId", required = false) String uploadRecordId
    ) {
        // deliver a pre prepared image. Are these names unique? Could images move between spreadsheets unintentionally?
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())){
            if (uploadRecordId != null && uploadRecordId.length() > 0) {
                final UploadRecord byId = UploadRecordDAO.findById(Integer.parseInt(uploadRecordId));
                if (byId != null && byId.getTempPath() != null && byId.getTempPath().length() > 0 && byId.getBusinessId() == loggedInUser.getUser().getBusinessId()
                        && (loggedInUser.getUser().isAdministrator() || byId.getUserId() == loggedInUser.getUser().getId())){ // admin is all for the business, developer just for that user
                    if (byId.getTempPath().endsWith(".xls")){
                        response.setContentType("application/vnd.ms-excel");
                    }
                    if (byId.getTempPath().endsWith(".xlsx")){
                        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                    }
                    if (byId.getTempPath().endsWith(".csv") || byId.getTempPath().endsWith(".txt")){
                        response.setContentType("application/octet-stream");
                    }
                    String fileName = byId.getFileName();
                    int brackedPos = fileName.indexOf(" - (");
                    if (brackedPos > 0){
                        fileName = fileName.substring(0,brackedPos).trim();
                    }
                    Path filePath = Paths.get(byId.getTempPath());
                    try {
                        DownloadController.streamFileToBrowser(filePath, response, fileName);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }
}