package com.azquo.admin.controller;

import com.azquo.admin.database.UploadRecord;
import com.azquo.admin.database.UploadRecordDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * Created by edward on 25/05/16.
 *
 * to download uploaded files
 */
@Controller
@RequestMapping("/DownloadFile")
public class DownloadFileController {
    @Autowired
    UploadRecordDAO uploadRecordDAO;


    @RequestMapping
    public void handleRequest(HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "uploadRecordId", required = false) String uploadRecordId
    ) throws Exception {
        // deliver a pre prepared image. Are these names unique? Could images move between spreadsheets unintentionally?
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return;
        }
        if (uploadRecordId != null && uploadRecordId.length() > 0) {
            final UploadRecord byId = uploadRecordDAO.findById(Integer.parseInt(uploadRecordId));
            if (byId != null && byId.getTempPath() != null && byId.getTempPath().length() > 0 && byId.getBusinessId() == loggedInUser.getUser().getBusinessId()){
                if (byId.getTempPath().endsWith(".xls")){
                    response.setContentType("application/vnd.ms-excel");
                }
                if (byId.getTempPath().endsWith(".xlsx")){
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                }
                // maybe add a few more later
                OutputStream out = response.getOutputStream();
                byte[] bucket = new byte[32 * 1024];
                int length = 0;
                try {
                    // new java 8 syntax, a little odd but I'll leave here for the moment
                    try (InputStream input = new BufferedInputStream(new FileInputStream(byId.getTempPath()))) {
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
                    response.setHeader("Content-Disposition", "inline; filename=\"" + byId.getFileName() + "\"");
                    response.setHeader("Content-Length", String.valueOf(length));
                    out.flush();
                    return;
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                out.flush();
            }
        }
    }
}
