package com.azquo.toto.controller;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 11:41
 * We're going to try for spring annotation based controllers. Might look into some rest specific spring stuff later.
 * For the moment it parses instructions for manipulating the name set and calls the name service if the instructions seem correctly formed.
 */

import com.azquo.toto.jsonrequestentities.NameJsonRequest;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
import com.azquo.toto.service.ProvenanceService;
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
                                @RequestParam(value = "password", required = false) String password, @RequestParam(value = "database", required = false) String database,
                                @RequestParam(value = "json", required = false) String json) throws Exception {
/*        NameJsonRequest testjson = new NameJsonRequest();
        testjson.user = "username";
        testjson.password = "password34234";
        testjson.database = "database4343";
        testjson.operation = "operation13544235";
        testjson.connectionId = 2;
        testjson.jsonFunction = "here is the json function";
        testjson.name = "here is a name";
        testjson.oldParent = 1;
        testjson.newParent = 2;
        testjson.newPosition = 4;
        testjson.attributes = "here are some attributes, how to represent, maybe JSON key/pair?";
        testjson.withData = true;
        System.out.println("json test : " + jacksonMapper.writeValueAsString(testjson));
        System.out.println("json back : " + jacksonMapper.writeValueAsString(jacksonMapper.readValue(jacksonMapper.writeValueAsString(testjson), NameJsonRequest.class)));*/
        try {
            if (json != null && json.length() > 0){ // new style paramters sent as json
                NameJsonRequest nameJsonRequest;
                try{
                    nameJsonRequest = jacksonMapper.readValue(json, NameJsonRequest.class);
                } catch (Exception e){
                    return "error:badly formed json " + e.getMessage();
                }
                LoggedInConnection loggedInConnection = loginService.getConnectionFromJsonRequest(nameJsonRequest);
                if (loggedInConnection == null){
                    return "error:invalid connection id or login credentials";
                }
                String result = nameService.processJsonRequest(loggedInConnection, nameJsonRequest);
                return nameJsonRequest.jsonFunction != null && nameJsonRequest.jsonFunction.length() > 0 ? nameJsonRequest.jsonFunction + "(" + result + ")" : result;
            } else { // old style
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
                String result = nameService.handleRequest(loggedInConnection, instructions);
                return jsonfunction != null && jsonfunction.length() > 0 ? jsonfunction + "(" + result + ")" : result;
            }
        }catch(Exception e){
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
    }
}
