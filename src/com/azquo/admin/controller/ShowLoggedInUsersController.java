package com.azquo.admin.controller;

import com.azquo.SessionListener;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.*;

/**
 * Created by edward on 02/11/16.
 * <p>
 * Just an internal run down of live sessions on the server so we know if it's ok to deploy.
 */
@Controller
@RequestMapping("/ShowLoggedInUsers")
public class ShowLoggedInUsersController {
    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request
    )
    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && loggedInUser.getUser().isAdministrator()) {
            List<HttpSession> listSessionByDate = new ArrayList<>(SessionListener.sessions.values());
            listSessionByDate.sort(Comparator.comparing(HttpSession::getLastAccessedTime)); // I think that will sort it!
            Collections.reverse(listSessionByDate); // most recent first
            for (HttpSession session : listSessionByDate) {
                System.out.println(session.getId());
                LoggedInUser user = (LoggedInUser) session.getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                if (user != null && !user.getUser().getEmail().equalsIgnoreCase("nic@azquo.com")) { // don't show the server monitoring logins
                    Date lastAccessed = new Date(session.getLastAccessedTime());
                    System.out.println("Last accessed :  " + lastAccessed + " " + user.getUser().getEmail());
                }
            }
            return "ok";
        } else {
            return "redirect:/api/Login";
        }
    }
}