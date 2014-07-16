package com.azquo.controller;


import com.azquo.admindao.OnlineReportDAO;
import com.azquo.adminentities.OnlineReport;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.OnlineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

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

    @RequestMapping
    public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Enumeration<String> parameterNames = request.getParameterNames();

        String connectionId = null;
        boolean withMacros =false;

        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("connectionid")) {
                connectionId = paramValue;
            }else if (paramName.equals("macros")){
                withMacros = true;
            }
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
            } catch (Exception e) {

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
        return;
    }
}




