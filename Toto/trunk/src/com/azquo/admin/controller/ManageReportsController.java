package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Created by cawley on 24/04/15.
 *
 * No delete or create but updating of reports. Simple as all on one page, a list. May need pagination at some point.
 */

@Controller
@RequestMapping("/ManageReports")
public class ManageReportsController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private OnlineReportDAO onlineReportDAO;

    private static final Logger logger = Logger.getLogger(ManageReportsController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
    )

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);

        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            final List<OnlineReport> reports = adminService.getReportList(loggedInUser);
            for (OnlineReport report : reports) {
                boolean store = false;
                String explanation = request.getParameter("explanation" + report.getId());
                String userStatus = request.getParameter("userStatus" + report.getId());
                String businessType = request.getParameter("businessType" + report.getId());
                String category = request.getParameter("reportCategory" + report.getId());
                if (explanation !=null && explanation.equalsIgnoreCase("delete")){
                    report.setActive(false);
                }
                if (explanation != null && !explanation.equals(report.getExplanation())) {
                    report.setExplanation(explanation);
                    store = true;
                }
                if (userStatus != null && !userStatus.equals("userStatus")) {
                    report.setUserStatus(request.getParameter("userStatus" + report.getId()));
                    store = true;
                }
                if (businessType != null && !businessType.equals(report.getDatabaseType())) {
                    report.setDatabaseType(businessType);
                    store = true;
                }
                if (category != null && !category.equals(report.getReportCategory())) {
                    report.setReportCategory(category);
                    store = true;
                }
                if (store) {
                    onlineReportDAO.store(report);
                }
            }
            model.put("reports", reports);
            return "managereports";
        }
    }
}