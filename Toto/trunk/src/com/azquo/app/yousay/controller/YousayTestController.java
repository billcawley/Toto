package com.azquo.app.yousay.controller;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.adminentities.Database;
import com.azquo.app.yousay.entity.Feedback;
import com.azquo.app.yousay.service.FeedbackService;
import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.MemoryDBManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 *
 */

@Controller
@RequestMapping("/YousayTest")


public class YousayTestController {

    @Autowired
    private DatabaseDAO databaseDAO;

    @Autowired
    private MemoryDBManager memoryDBManager;
    @Autowired
    private FeedbackService feedbackService;


    @RequestMapping
    @ResponseBody

    public String handleRequest() throws Exception {

        // I'm just going to grab the first DB and make some feedbacks in it

        List<Database> allDatabases = databaseDAO.findAll();

        Database first = allDatabases.get(0);

        AzquoMemoryDB memoryDB = memoryDBManager.getAzquoMemoryDB(first);

        for (int i = 0; i < 10; i++){
            Random generator = new Random();
            int random = generator.nextInt(4) + 1;
            switch (random) {
                case 1:
                    new Feedback(memoryDB,feedbackService, new Date(), "--", "here is a comment that the product or service was very bad");
                    break;
                case 2:
                    new Feedback(memoryDB,feedbackService, new Date(), "-", "here is a comment that the product or service was bad");
                    break;
                case 3:
                    new Feedback(memoryDB,feedbackService, new Date(), "+", "here is a comment that the product or service was good");
                    break;
                case 4:
                    new Feedback(memoryDB,feedbackService, new Date(), "++", "here is a comment that the product or service was very good");
                    break;
            }
        }

        memoryDB.saveDataToMySQL();

        StringBuilder toReturn = new StringBuilder();

        feedbackService.updateFeedbackByRatingsMap(memoryDB);

        toReturn.append("<html><body>Yousay test controller");

        toReturn.append("testing indexing, here are feedbacks by rating : <br/>");

        for (Feedback feedback : feedbackService.findForRating(memoryDB,"--")){
            toReturn.append(feedback.toString() + "<br/>");
        }
        for (Feedback feedback : feedbackService.findForRating(memoryDB,"-")){
            toReturn.append(feedback.toString() + "<br/>");
        }
        for (Feedback feedback : feedbackService.findForRating(memoryDB,"+")){
            toReturn.append(feedback.toString() + "<br/>");
        }
        for (Feedback feedback : feedbackService.findForRating(memoryDB,"++")){
            toReturn.append(feedback.toString() + "<br/>");
        }

        toReturn.append("</body></html>");

        return  toReturn.toString();

    }
}
