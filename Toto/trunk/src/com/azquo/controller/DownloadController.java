package com.azquo.controller;

import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.OnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

@Controller
@RequestMapping("/Download")
public class DownloadController {

    @Autowired
    LoginService loginService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    @Autowired
    OnlineService onlineService;

    @RequestMapping
    public void handleRequest(HttpServletRequest request
            , HttpServletResponse response
            , @RequestParam(value = "macros", required = false, defaultValue = "false")  boolean withMacros
            , @RequestParam(value = "pdf", required = false, defaultValue = "false")  boolean pdf
            , @RequestParam(value = "image", required = false)  String image
                              ) throws Exception {
        // deliver a preprepared image. Are these names unique? Could images move between spreadsheets unintentionally?
        if (image != null && image.length() > 0){
            InputStream input = new BufferedInputStream((new FileInputStream(onlineService.getHomeDir() +  "/temp/" + image)));
            response.setContentType("image/png"); // Set up mime type
            OutputStream out = response.getOutputStream();
            byte[] bucket = new byte[32*1024];
            int length = 0;
            try  {
                try {
                    //Use buffering? No. Buffering avoids costly access to disk or network;
                    //buffering to an in-memory stream makes no sense.
                    int bytesRead = 0;
                    while(bytesRead != -1){
                        //aInput.read() returns -1, 0, or more :
                        bytesRead = input.read(bucket);
                        if(bytesRead > 0){
                            out.write(bucket, 0, bytesRead);
                            length += bytesRead;
                        }
                    }
                }
                finally {
                    input.close();
                    //result.close(); this is a no-operation for ByteArrayOutputStream
                }
                response.setHeader("Content-Disposition", "inline; filename=\"" + image + "\"");
                response.setHeader("Content-Length", String.valueOf(length));
                out.flush();
                return;
            }
            catch (IOException ex){
                ex.printStackTrace();
            }
            out.flush();
            return;
        }
        LoggedInConnection loggedInConnection = (LoggedInConnection)request.getSession().getAttribute(LoginController.LOGGED_IN_CONNECTION_SESSION);
        if (loggedInConnection == null) {
            return;
        }
        OnlineReport onlineReport = null;
        int reportId = loggedInConnection.getReportId();
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
        if (pdf){
            onlineService.saveBookasPDF(response, loggedInConnection, fileName);
        }else {
            if (withMacros) {
                onlineService.saveBookActive(response, loggedInConnection, fileName);
            } else {
                onlineService.saveBook(response, loggedInConnection, fileName);
            }
        }
    }



}




