package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.admin.business.Business;
import com.azquo.admin.business.BusinessDAO;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.user.User;
import com.azquo.admin.user.UserDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.commons.lang.math.NumberUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.xml.crypto.Data;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by edward on 05/04/17.
 *
 * Copy business could be very helpful for developing more complex models
 */
@Controller
@RequestMapping("/CopyBusiness")
public class CopyBusinessController {

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "businessName", required = false) String businessName
            , @RequestParam(value = "userEmail", required = false) String userEmail
            , @RequestParam(value = "password", required = false) String password
    )
    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && loggedInUser.getUser().isAdministrator()) {
            StringBuilder error = new StringBuilder();
            try {
                if (businessName == null || businessName.isEmpty()
                        || userEmail == null || userEmail.isEmpty()
                        || password == null || password.isEmpty()){
                    error.append("Please enter all fields\n");
                } else {
                    if (BusinessDAO.findByName(businessName) != null){
                        error.append("THat business already exists\n");
                    } else if (UserDAO.findByEmail(userEmail) != null){
                        error.append("THat user already exists\n");
                    } else { // ok we copy, the business, databases, database report links, reports. No other users yet - should I?
                        Business b = BusinessDAO.findById(loggedInUser.getUser().getBusinessId());
                        Business.BusinessDetails businessDetails = b.getBusinessDetails();
                        AdminService.registerBusiness(userEmail, userEmail, password, businessName
                                , businessDetails.address1, businessDetails.address2, businessDetails.address3
                                , businessDetails.address4, businessDetails.postcode, businessDetails.telephone
                                , businessDetails.website);
                        // get the user to get the new business. If either of these are null something is very wrong.
                        User newUser = UserDAO.findByEmail(userEmail);
                        Map<Integer, Integer> oldDBIdNewDBId = new HashMap<>();
                        for (Database source : DatabaseDAO.findForBusinessId(b.getId())){ // all of them for this business
                            Database newDb = AdminService.copyDatabase(source,newUser);
                            oldDBIdNewDBId.put(source.getId(), newDb.getId()); // will be useful for mapping if we copy users
                        }
                        for (OnlineReport or : OnlineReportDAO.findForBusinessId(b.getId())){
                            OnlineReport newReport = AdminService.copyReport(loggedInUser, or, newUser); // this should copy files
                            // now do the links
                            List<Integer> databaseIdsForReportId = DatabaseReportLinkDAO.getDatabaseIdsForReportId(or.getId());
                            for (Integer oldDbId  : databaseIdsForReportId){
                                DatabaseReportLinkDAO.link(oldDBIdNewDBId.get(oldDbId), newReport.getId()); // so make a new link with the new ids
                            }
                        }
                        return "redirect:/api/Login?logoff=true";
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                error.append(e.getMessage());
            }
            if (error.length() > 0) {
                String exceptionError = error.toString();
                model.put("error", exceptionError);
            }
            return "copybusiness";
        } else {
            return "redirect:/api/Login";
        }
    }

}
