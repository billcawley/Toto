package com.azquo.toto.web.controller;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 11:41
 * We're going to try for spring annotation based controllers
 */

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/test")
public class TestController {

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public String handleRequest(){

        return "blah de blah";
    }

}
