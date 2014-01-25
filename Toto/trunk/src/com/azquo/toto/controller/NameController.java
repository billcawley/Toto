package com.azquo.toto.controller;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 11:41
 * We're going to try for spring annotation based controllers. Might look into some rest specific spring stuff later.
 *
 * for reading and manipulating names. Uses only Json, the controller will sort the login but will leave the rest to the name service
 * Maybe later deal with JSON parse errors here?
 *
 */

import com.azquo.toto.jsonrequestentities.NameJsonRequest;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/Name")
public class NameController {

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @Autowired
    private NameService nameService;
    @Autowired
    private LoginService loginService;
//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "json", required = true) String json) throws Exception {
        try {
            if (json.length() > 0) {
                NameJsonRequest nameJsonRequest;
                try {
                    nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class);
                } catch (Exception e) {
                    return "error:badly formed json " + e.getMessage();
                }
                LoggedInConnection loggedInConnection = loginService.getConnectionFromJsonRequest(nameJsonRequest);
                if (loggedInConnection == null) {
                    return "error:invalid connection id or login credentials";
                }
                String result = nameService.processJsonRequest(loggedInConnection, nameJsonRequest);
                return nameJsonRequest.jsonFunction != null && nameJsonRequest.jsonFunction.length() > 0 ? nameJsonRequest.jsonFunction + "(" + result + ")" : result;
            } else {
                return "error: empty json string passed";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
    }
}
