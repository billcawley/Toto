package com.azquo.spreadsheet.controller;

import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.spreadsheet.*;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Created by bill on 22/04/14.
 *
 * For inspecting databases
 *
 * modified by Edd to deal with new client/server model
 *
 */

@Controller
@RequestMapping("/Jstree")
public class JstreeController {

    @Autowired
    private LoginService loginService;

    @Autowired
    SpreadsheetService spreadsheetService;

    @Autowired
    OnlineReportDAO onlineReportDAO;

    private static final Logger logger = Logger.getLogger(JstreeController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request, HttpServletResponse response
            , @RequestParam(value = "op", required = false) String op
            , @RequestParam(value = "id", required = false) String jsTreeId
            , @RequestParam(value = "user", required = false) String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "database", required = false) String database
            , @RequestParam(value = "position", required = false) String position
            , @RequestParam(value = "parent", required = false) String parent
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "parents", required = false) String parents
            , @RequestParam(value = "topnode", required = false) String topNode //only for use at root.
            , @RequestParam(value = "itemschosen", required = false) String itemsChosen
    ) throws Exception {
        String jsonFunction = "azquojsonfeed";
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        try {
            if (loggedInUser == null) {
                if (user.equals("demo@user.com")) {
                    user += request.getRemoteAddr();
                }
                loggedInUser = loginService.loginLoggedInUser(database, user, password, "", false);
                if (loggedInUser == null) {
                    model.addAttribute("content", "error:not logged in");
                    return "utf8page";
                }
            }

            if ((database == null || database.length() == 0) && loggedInUser.getDatabase() != null) {
                database = loggedInUser.getDatabase().getName();
            }
            // from here I need to move code that references db objects (JsTreeNode Does) out of the controller into the service
            // the service may have some controller and view code but we just have to put up with that for the mo.
            String backupSearchTerm = loggedInUser.getAzquoBook().getRangeData("az_inputInspectChoice");// don't reallyunderstand, what's important is that this is now client side
            String result = spreadsheetService.processJSTreeRequest(loggedInUser.getDataAccessToken(),json,jsTreeId,topNode,op,parent,parents,database, itemsChosen,position,backupSearchTerm);
            // seems to be the logic from before, if children/new then don't do the funciton. Not sure why . . .
            if (!op.equals("children") && !op.equals("new")) {
                model.addAttribute("content", jsonFunction + "({\"response\":" + result + "})");
            } else {
                if (op.equals("new")){ // this is nasty and hacky, a bit of logic I needed to remove from the DB side, I'm returning the root id when new
                    if (parents == null){
                        parents = "false";
                    }
                    result = spreadsheetService.showNameDetails(loggedInUser,database,result,parents, itemsChosen);
                }

                model.addAttribute("content", result);
            }
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Content-type", "application/json");
        } catch (Exception e) {
            logger.error("jstree controller error", e);
            model.addAttribute("content", "error:" + e.getMessage());
        }
        return "utf8page";
    }
}
