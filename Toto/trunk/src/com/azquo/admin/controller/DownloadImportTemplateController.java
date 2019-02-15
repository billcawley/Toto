package com.azquo.admin.controller;

import com.azquo.dataimport.ImportService;
import com.azquo.dataimport.ImportTemplate;
import com.azquo.dataimport.ImportTemplateDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.DownloadController;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/*
Created by EFC 11/09/2018

Download a database and its reports
 */

@Controller
@RequestMapping("/DownloadImportTemplate")

public class DownloadImportTemplateController {

    @RequestMapping
    public void handleRequest(HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "importTemplateId", required = false) String importTemplateId
    ) throws Exception {
        // deliver a pre prepared image. Are these names unique? Could images move between spreadsheets unintentionally?
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null) {
            return;
        }
        if (importTemplateId != null && importTemplateId.length() > 0) {
            if (loggedInUser.getUser().isAdministrator()) {
                //report id is assumed to be integer - sent from the website
                ImportTemplate importTemplate = ImportTemplateDAO.findForIdAndBusinessId(Integer.parseInt(importTemplateId), loggedInUser.getUser().getBusinessId());
                Path bookPath = Paths.get(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk());
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
