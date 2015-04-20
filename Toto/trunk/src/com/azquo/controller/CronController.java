package com.azquo.controller;

import com.azquo.spreadsheet.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by bill on 01/04/14.
 *
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
            // todo - addres dropping from memory databases that have been inactive for a while
            //unloadInactiveDatabases();
        }
        return "OK";


    }
}