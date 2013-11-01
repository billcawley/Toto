package com.azquo.toto.controller;

import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
import com.azquo.toto.service.ValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 13:46
 *
 * instructions to manipulate the data itself, will become more complex and deal with a connection to instructions can be in state
 *
 */
@Controller
@RequestMapping("/Value")
public class ValueController {

    @Autowired
    private NameService nameService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private ValueService valueServiceService;

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "instructions", required = false) String instructions) throws Exception {

        if (instructions == null){
            return "no command passed";
        }
        //instructions = instructions.trim();

        return "value controller here!";
    }
}