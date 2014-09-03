package com.azquo.controller;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 11:41
 *
 * for reading and manipulating names. Uses only Json, the controller will sort the login but will leave the rest to the name service
 * Maybe later deal with JSON parse errors here?
 *
 */

import com.azquo.jsonrequestentities.NameJsonRequest;
import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import com.azquo.service.NameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;

@Controller
@RequestMapping("/Name")
public class NameController {

    private static final Logger logger = Logger.getLogger(NameController.class);

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @Autowired
    private NameService nameService;
    @Autowired
    private LoginService loginService;
//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request)throws Exception {

        Enumeration<String> parameterNames = request.getParameterNames();

        String json = "";
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameterValues(paramName)[0];
            if (paramName.equals("json")) {
                json = paramValue;
            }
        }
        String callerIP = request.getRemoteAddr();

        try {
            if (json.length() > 0) {
                NameJsonRequest nameJsonRequest;
                try {
                    nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class);
                } catch (Exception e) {
                    logger.error("name json parse problem", e);
                    return "error:badly formed json " + e.getMessage();
                }
                LoggedInConnection loggedInConnection = loginService.getConnectionFromJsonRequest(nameJsonRequest, callerIP);
                if (loggedInConnection == null) {
                    return "error:invalid connection id or login credentials";
                }
                String result = nameService.processJsonRequest(loggedInConnection, nameJsonRequest);
                return nameJsonRequest.jsonFunction != null && nameJsonRequest.jsonFunction.length() > 0 ? nameJsonRequest.jsonFunction + "(" + result + ")" : result;
            } else {
                return "error: empty json string passed";
            }
        } catch (Exception e) {
            logger.error("name controller error", e);
            return "error:" + e.getMessage();
        }
    }
}
