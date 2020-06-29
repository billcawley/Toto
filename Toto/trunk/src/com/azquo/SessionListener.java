package com.azquo;

import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.controller.LoginController;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by edward on 02/11/16.
 *
 * Basic session tracking
 */
public class SessionListener implements HttpSessionListener {

    public static final Set<HttpSession> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // not rocket surgery . . .
    public void sessionCreated(HttpSessionEvent sessionEvent) {
        sessions.add(sessionEvent.getSession());
    }

    public void sessionDestroyed(HttpSessionEvent sessionEvent) {
        sessions.remove(sessionEvent.getSession());
        LoggedInUser loggedInUser = (LoggedInUser) sessionEvent.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);
        if (loggedInUser != null){
            loggedInUser.userLog("Logout by timetout", new HashMap<>());
        }

    }
}