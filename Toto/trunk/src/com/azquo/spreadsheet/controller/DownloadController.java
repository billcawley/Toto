package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.controller.PendingUploadController;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.dataimport.*;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zkoss.poi.ss.usermodel.Workbook;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/*
 * Copyright (C) 2016 Azquo Ltd.
 *
 * Edd note : this class will probably be redundant after zapping AzquoBook, the old non ZK spreadsheet renderer
 *
 */

@Controller
@RequestMapping("/Download")
public class DownloadController {

    @RequestMapping
    public void handleRequest(HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "image", required = false) String image
            , @RequestParam(value = "puid", required = false) String puid
            , @RequestParam(value = "lastFile", required = false) String lastFile
    ) {
        // deliver a pre prepared image. Are these names unique? Could images move between spreadsheets unintentionally?
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null) {
            return;
        }
        if (image != null && image.length() > 0) {
            response.setContentType("image/png"); // Set up mime type
            DatabaseServer databaseServer = loggedInUser.getDatabaseServer();
            String LOCALIP = "127.0.0.1";
            if (databaseServer.getIp().equals(LOCALIP) || image.startsWith("templates-")) {
                Path filePath = null;
                if (image.startsWith("templates")) {
                    int templateId = NumberUtils.toInt(image.substring(10));
                    ImportTemplate importTemplate = ImportTemplateDAO.findById(templateId);
                    filePath = Paths.get(SpreadsheetService.getHomeDir() + ImportService.dbPath + loggedInUser.getBusinessDirectory() + ImportService.importTemplatesDir + importTemplate.getFilenameForDisk());
                    try {
                        streamFileToBrowser(filePath, response);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
        if ("true".equalsIgnoreCase(lastFile) && loggedInUser.getLastFile() != null) {
            if (loggedInUser.getLastFileName() != null && loggedInUser.getLastFileName().toLowerCase().endsWith(".zip")){
                response.setContentType("application/zip");
            } else {
                response.setContentType("text/tab-separated-values"); // Set up mime type
            }
                try {
                    streamFileToBrowser(Paths.get(loggedInUser.getLastFile()), response,  loggedInUser.getLastFileName() != null ? loggedInUser.getLastFileName() : "download.tsv");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
        }
        if (NumberUtils.isDigits(puid)) {
            final PendingUpload pu = PendingUploadDAO.findById(Integer.parseInt(puid));
            HttpSession session = request.getSession();
            // todo - pending upload security for non admin users?
            if (pu.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
                if (session.getAttribute(PendingUploadController.WARNINGSWORKBOOK) != null) {
                    Workbook wb = (Workbook) session.getAttribute(PendingUploadController.WARNINGSWORKBOOK);
                    response.setContentType("application/vnd.ms-excel");
                    response.setHeader("Content-Disposition", "inline; filename=\"" + pu.getFileName() + "warnings.xlsx\"");
                    try {
                        ServletOutputStream outputStream = response.getOutputStream();
                        wb.write(outputStream);
                        outputStream.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    public static void streamFileToBrowser(Path filePath, HttpServletResponse response) throws IOException {
        streamFileToBrowser(filePath, response, filePath.getFileName().toString());
    }

    public static void streamFileToBrowser(Path filePath, HttpServletResponse response, String downloadFileName) throws IOException {
        response.setHeader("Content-Disposition", "inline; filename=\"" + downloadFileName + "\"");
        response.setHeader("Content-Length", String.valueOf(Files.size(filePath)));
        ServletOutputStream out = response.getOutputStream();
        try (InputStream input = Files.newInputStream(filePath)) {
            byte[] bucket = new byte[32 * 1024]; // 32k arbitrary
            int bytesRead = 0;
            while (bytesRead != -1) {
                //aInput.read() returns -1, 0, or more :
                bytesRead = input.read(bucket);
                if (bytesRead > 0) {
                    out.write(bucket, 0, bytesRead);
                }
            }
        }
        out.flush();
    }
}