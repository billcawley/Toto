package com.azquo.controller;


import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.OnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.Enumeration;


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
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Enumeration<String> parameterNames = request.getParameterNames();

        String connectionId = null;
        boolean withMacros =false;
        String image = "";

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("connectionid")) {
                connectionId = paramValue;
            }else if (paramName.equals("macros")){
                withMacros = true;
            }else if (paramName.equals("image")){
                image = paramValue;
            }
        }
        if (image.length() > 0){
            InputStream input = new BufferedInputStream((new FileInputStream("/home/azquo/temp/" + image)));
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
        LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);
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


        String fileName = "/azquobook.xls";
        if (onlineReport != null) {
            fileName = onlineReport.getFilename();
        }
        fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        if (withMacros){
            onlineService.saveBookActive(response, loggedInConnection,fileName);
        }else{
            onlineService.saveBook(response, loggedInConnection, fileName);
        }
    }



}




