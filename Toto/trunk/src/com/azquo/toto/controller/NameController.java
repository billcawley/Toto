package com.azquo.toto.controller;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 11:41
 * We're going to try for spring annotation based controllers. Might look into some rest specific spring stuff later.
 * For the moment it parses instructions for manipulating the name set and calls the name service if the instructions seem correctly formed.
 */

import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
import com.azquo.toto.service.ProvenanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/Name")
public class NameController {

    /*public static final String LEVEL = "level";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String SORTED = "sorted";*/
    //public static final String LOWEST = "lowest";

    @Autowired
    private NameService nameService;
    @Autowired
    private ProvenanceService provenanceService;

    @Autowired
    private LoginService loginService;
//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "connectionid", required = false) String connectionId, @RequestParam(value = "instructions", required = false) String instructions,
                                @RequestParam(value = "jsonfunction", required = false) String jsonfunction, @RequestParam(value = "user", required = false) String user,
                                @RequestParam(value = "password", required = false) String password, @RequestParam(value = "database", required = false) String database) throws Exception {
        String result;
        try {

            if (connectionId == null) {
                LoggedInConnection loggedInConnection = loginService.login(database,user, password,0);
                 if (loggedInConnection == null){
                     return "error:no connection id";
                 }
                 connectionId = loggedInConnection.getConnectionId();
            }

            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }
            //system.out.println("json test : " + provenanceService.getTestProvenance(loggedInConnection).getAsJson());
            result = nameService.handleRequest(loggedInConnection, instructions);
        }catch(Exception e){
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
        if (jsonfunction != null && jsonfunction.length() > 0){
            return jsonfunction + "(" + result + ")";
        }
        else {
            return result;
        }
    }



}
