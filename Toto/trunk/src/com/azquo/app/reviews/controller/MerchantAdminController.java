package com.azquo.app.reviews.controller;

/**
 * Created by edd on 09/10/14.
 */
import com.azquo.admindao.DatabaseDAO;
import com.azquo.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 *
 */

@Controller
@RequestMapping("/MerchantAdmin")


public class MerchantAdminController {

    @Autowired
    private DatabaseDAO databaseDAO;
    @Autowired
    private LoginService loginService;

    @RequestMapping
    public String handleRequest(ModelMap model) throws Exception {
        model.addAttribute("content", "test");
        return "utf8page";
    }


}

