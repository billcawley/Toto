package com.azquo.app.reviews.controller;

/**
 * Created by edd on 09/10/14.
 */
import com.azquo.app.reviews.service.ReviewsCustomerService;
import com.azquo.app.reviews.service.UserService;
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
    private ReviewsCustomerService reviewsCustomerService;
    @Autowired
    private UserService userService;

    @RequestMapping
    public String handleRequest(ModelMap model) throws Exception {
        //System.out.print("Creating a merchant " + reviewsCustomerService.createMerchant("Test Merchant", "Test merchant's address", "test merhcants email", "2134567890"));
        //System.out.print("Creating a user " + userService.createUser(merchant, "edd@azquo.com", "password24"));
        return "merchantadmin";
    }


}

