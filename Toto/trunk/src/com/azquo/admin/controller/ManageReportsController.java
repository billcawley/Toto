package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.User;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 24/04/15.
 * <p>
 * No delete or create but updating of reports. Simple as all on one page, a list. May need pagination at some point.
 */

@Controller
@RequestMapping("/ManageReports")
public class ManageReportsController {

    private static final Logger logger = Logger.getLogger(ManageReportsController.class);

    public class DatabaseSelected{
        private final boolean selected;
        private final Database database;

        public DatabaseSelected(boolean selected, Database database) {
            this.selected = selected;
            this.database = database;
        }

        public boolean isSelected() {
            return selected;
        }

        public Database getDatabase() {
            return database;
        }
    }

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request,

                                @RequestParam(value = "editId", required = false) String editId
            , @RequestParam(value = "deleteId", required = false) String deleteId
            , @RequestParam(value = "databaseIdList", required = false) String[] databaseIdList // I think this is correct?
            , @RequestParam(value = "name", required = false) String name
            , @RequestParam(value = "category", required = false) String category
            , @RequestParam(value = "explanation", required = false) String explanation
            , @RequestParam(value = "submit", required = false) String submit
    ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        // I assume secure until we move to proper spring security
        if (loggedInUser == null || !loggedInUser.getUser().isAdministrator()) {
            return "redirect:/api/Login";
        } else {
            if (deleteId != null){
                AdminService.removeReportById(loggedInUser,Integer.parseInt(deleteId));
            }
            if (editId != null) {
                final OnlineReport theReport = OnlineReportDAO.findById(Integer.parseInt(editId)); // yes could exception, don't care at the mo
                if (theReport.getBusinessId() == loggedInUser.getUser().getBusinessId()) {
                    // ok check to see if data was submitted
                    if (submit != null) {
                        // to keep intelliJ happy, I'm not sure if this is a good idea or not? I suppose protects against logic above being changed unpredictably
                        DatabaseReportLinkDAO.unLinkReport(theReport.getId());
                        for (String databaseId : databaseIdList){
                            DatabaseReportLinkDAO.link(Integer.parseInt(databaseId), theReport.getId());
                        }
                        theReport.setReportName(name);
                        theReport.setReportCategory(category);
                        theReport.setExplanation(explanation);
                        OnlineReportDAO.store(theReport);
                        model.put("reports", AdminService.getReportList(loggedInUser));
                        return "managereports";
                    }
                    final List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(theReport.getId());
                    model.put("id", editId);
                    List<DatabaseSelected> databasesSelected = new ArrayList<>();
                    final List<Database> forBusinessId = DatabaseDAO.findForBusinessId(loggedInUser.getUser().getBusinessId());
                    for (Database database : forBusinessId){
                        databasesSelected.add(new DatabaseSelected(databaseIdsForReportId.contains(database.getId()), database));
                    }
                    model.put("databasesSelected", databasesSelected);
                    model.put("name", theReport.getReportName());
                    model.put("file", theReport.getFilename());
                    model.put("category", theReport.getReportCategory() != null ? theReport.getReportCategory() : "");
                    model.put("explanation", theReport.getExplanation() != null ? theReport.getExplanation() : "");
                    return "editreport";
                }
            }
            // if not editing then very simple
            model.put("reports", AdminService.getReportList(loggedInUser));
            return "managereports";
        }
    }
}