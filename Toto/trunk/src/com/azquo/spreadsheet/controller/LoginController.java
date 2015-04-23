package com.azquo.spreadsheet.controller;

import com.azquo.spreadsheet.LoggedInConnection;
import com.azquo.spreadsheet.LoginService;
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

    public static final String LOGGED_IN_CONNECTION_SESSION = "LOGGED_IN_CONNECTION_SESSION";

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
                request.getSession().setAttribute(LOGGED_IN_CONNECTION_SESSION, request.getServletContext().getAttribute(connectionid));
                request.getServletContext().removeAttribute(connectionid); // take it off the context
                return "redirect:/api/Online?reportid=1";
            }
        } else {
            if (userEmail != null && userEmail.length() > 0 && password != null && password.length() > 0) {
                model.put("userEmail", userEmail);
                LoggedInConnection loggedInConnection = loginService.login(null, userEmail, password, 60, null, false);
                if (loggedInConnection != null) {
                    request.getSession().setAttribute(LOGGED_IN_CONNECTION_SESSION, loggedInConnection);
                    return "redirect:/api/Online?reportid=1";
                } else {// feedback to users about incorrect details
                    model.put("error", "incorrect login details");
                }
            }
        }
        return "login";
    }
}