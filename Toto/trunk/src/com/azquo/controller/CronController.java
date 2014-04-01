package com.azquo.controller;

import com.azquo.service.LoggedInConnection;
import com.azquo.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by bill on 01/04/14.
 */

@Controller
@RequestMapping("/Cron")


public class CronController {

    @Autowired
    LoginService loginService;



    @RequestMapping
    @ResponseBody

    public String handleRequest(@RequestParam(value = "crontype") final String cronType) throws Exception {

        if (cronType.equals("minute")){
            //remove defunct connections and open databases
           loginService.zapConnectionsTimedOut();
        }
        return "OK";


    }
}