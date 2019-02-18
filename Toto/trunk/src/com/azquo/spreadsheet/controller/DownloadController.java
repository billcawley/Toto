package com.azquo.spreadsheet.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.controller.PendingUploadController;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.dataimport.PendingUpload;
import com.azquo.dataimport.PendingUploadDAO;
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

/*
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
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
            if (databaseServer.getIp().equals(LOCALIP)) {
                String pathOffset = loggedInUser.getBusinessDirectory() + "/images/" + image;
                String dbPath = "/databases/";
                Path filePath = Paths.get(SpreadsheetService.getHomeDir() + dbPath + pathOffset);
                try {
                    streamFileToBrowser(filePath, response);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
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