package com.azquo.spreadsheet.controller;

import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.LoginService;
import com.azquo.spreadsheet.SpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 31/10/13
 * Time: 19:45
 * Ok now we're not using Excel the purpose changes, it will serve a basic Login screen then jam a logged in conneciton against the session
 * I plan to get rid of connection id!
 */
@Controller
@RequestMapping("/Login")

public class LoginController {

    //   private static final Logger logger = Logger.getLogger(LoginController.class);
    @Autowired
    private LoginService loginService;
    @Autowired
    private SpreadsheetService spreadsheetService;

//    public static final String LOGGED_IN_CONNECTION_SESSION = "LOGGED_IN_CONNECTION_SESSION";
    // run in paralell with the old logged in conneciton for a bit
    public static final String LOGGED_IN_USER_SESSION = "LOGGED_IN_USER_SESSION";

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
            , @RequestParam(value = "user", required = false) String userEmail
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "connectionid", required = false) String connectionid

    ) throws Exception {

        String callerId = request.getRemoteAddr();
        if (callerId != null && userEmail != null && userEmail.equals("demo@user.com")) {
            userEmail += callerId;
        }

        if (connectionid != null && connectionid.length() > 0) { // nasty hack to support connection id from the plugin
            if (request.getServletContext().getAttribute(connectionid) != null) { // then pick up the temp logged in conneciton
                LoggedInUser loggedInUser = (LoggedInUser)request.getServletContext().getAttribute(connectionid);
                request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                request.getServletContext().removeAttribute(connectionid); // take it off the context
                if (!loggedInUser.getUser().isAdministrator()) {
                    // I relaise making a velocity and passing it to jsp is a bit crap, I just want it to work
                    spreadsheetService.showUserMenu(model, loggedInUser);// user menu being what magento users typically see when logging in, a velocity page
                    return "azquoReports";
                } else {
                    return "redirect:/api/ManageReports";
                }
            }
        } else {
            if (userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
                model.put("userEmail", userEmail);
                LoggedInUser loggedInUser = loginService.loginLoggedInUser(null, userEmail, password, null, false);
                if (loggedInUser != null) {
                    request.getSession().setAttribute(LOGGED_IN_USER_SESSION, loggedInUser);
                    if (!loggedInUser.getUser().isAdministrator()) {
                        // I relaise making a velocity and passing it to jsp is a bit crap, I just want it to work
                        spreadsheetService.showUserMenu(model, loggedInUser);// user menu being what magento users typically see when logging in, a velocity page
                        return "azquoReports";
                    } else {
                        return "redirect:/api/ManageReports";
                    }
                } else {// feedback to users about incorrect details
                    model.put("error", "incorrect login details");
                }
            }
        }
        return "login";
    }
}