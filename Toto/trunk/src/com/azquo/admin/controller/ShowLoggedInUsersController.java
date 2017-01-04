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
 *
 * Just an internal run down of live sessions on the server so we know if it's ok to deploy.
 */
@Controller
@RequestMapping("/ShowLoggedInUsers")
public class ShowLoggedInUsersController {
    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request
    ) throws Exception

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null && (loggedInUser.getUser().isAdministrator() || loggedInUser.getUser().isDeveloper())) {
            StringBuilder list = new StringBuilder();
            List<HttpSession> listSessionByDate = new ArrayList<>(SessionListener.sessions);
            listSessionByDate.sort(Comparator.comparing(HttpSession::getLastAccessedTime)); // I think that will sort it!
            Collections.reverse(listSessionByDate); // most recent first
            for (HttpSession session : listSessionByDate){
                LoggedInUser user = (LoggedInUser) session.getAttribute(LoginController.LOGGED_IN_USER_SESSION);
                if (user != null && !user.getUser().getEmail().equalsIgnoreCase("nic@azquo.com")){ // don't show the server monitoring logins
                    Date lastAccessed = new Date(session.getLastAccessedTime());
                    list.append("Last accessed :  " + lastAccessed + " " +  user.getUser().getEmail() + "<br/>");
                }
            }
            return list.toString();
        } else {
            return "redirect:/api/Login";
        }
    }

}
