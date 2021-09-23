package com.azquo.admin.controller;

import com.azquo.admin.AdminService;
import com.azquo.dataimport.UploadRecord;
import com.azquo.dataimport.UploadRecordDAO;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.TreeNode;
import com.azquo.rmi.RMIClient;
import com.azquo.spreadsheet.CommonReportUtils;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import com.azquo.spreadsheet.transport.ProvenanceDetailsForDisplay;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import java.rmi.RemoteException;

@Controller
@RequestMapping("/AuditDatabase")
public class AuditDatabaseController {

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            ,@RequestParam(value = "datetime", required = false) String dateTime
     ) {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null) {
            if (dateTime==null) {
                CommonReportUtils.getDropdownListForQuery(loggedInUser, "edit:auditdatabase");
            }
            if (dateTime!=null && dateTime.equals("back")){
                dateTime = null;
            }
            ProvenanceDetailsForDisplay provenanceDetailsForDisplay = null;
            TreeNode treeNode = null;
            try{
                provenanceDetailsForDisplay = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getDatabaseAuditList(loggedInUser.getDataAccessToken(), dateTime, 1000);
                if (dateTime.length()>8){
                    treeNode = RMIClient.getServerInterface(loggedInUser.getDataAccessToken().getServerIp()).getIndividualProvenanceCounts(loggedInUser.getDataAccessToken(), 1000, dateTime);

                }
            }catch(Exception e){
                //unhandled at present
            }
            AdminService.setBanner(model,loggedInUser);
            if (provenanceDetailsForDisplay!=null){
                model.put("date", dateTime);
                model.put("provenanceForDisplays", provenanceDetailsForDisplay.getAuditForDisplayList());
                model.put("node", treeNode);
            }

            return "auditdatabase";
        }
        return "redirect:/api/Login";
    }
}
