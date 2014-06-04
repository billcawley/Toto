package com.azquo.controller;


import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.service.AzquoBook;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.OnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

    public String handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Enumeration<String> parameterNames = request.getParameterNames();

        String connectionId = null;
        String reportId = null;

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("connectionid")) {
                connectionId = paramValue;
            } else if (paramName.equals("reportid")) {
                reportId = paramValue;

            }
        }
        LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);
        if (loggedInConnection == null) {
            return "error: invalid or expired connection";
        }
        OnlineReport onlineReport = null;

        if (reportId != null) {
            try {
                onlineReport = onlineReportDAO.findById(Integer.parseInt(reportId));
            } catch (Exception e) {

            }
        }


        String fileName = "/azquobook.xls";
        if (onlineReport != null) {
            fileName = onlineReport.getFilename();
        }
        fileName = fileName.substring(fileName.lastIndexOf("/") + 1);
        onlineService.saveBook(response, loggedInConnection, fileName);
        return "";
    }
}




