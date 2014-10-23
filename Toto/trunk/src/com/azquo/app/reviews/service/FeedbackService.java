package com.azquo.app.reviews.service;

import com.azquo.app.reviews.entity.Feedback;
import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.service.AppEntityService;

import java.util.*;

/**
 */
public class FeedbackService extends AppEntityService<Feedback> {

    // ok the app entity services will need to manage their own indexes

    final Map<AzquoMemoryDB, Map<String, Set<Feedback>>> feedbackByRatingsMap = new HashMap<AzquoMemoryDB, Map<String, Set<Feedback>>>();

    @Override
    public String getTableName() {
        return "feedback";
    }

    @Override
    public void loadEntityFromJson(AzquoMemoryDB azquoMemoryDB, int id, String json) throws Exception
    {
        new Feedback(azquoMemoryDB, id, this,json);
    }

    public void updateFeedbackByRatingsMap(AzquoMemoryDB azquoMemoryDB){
        Map<String, Set<Feedback>> feedbacksByRatingsForDB = feedbackByRatingsMap.get(azquoMemoryDB);
        if (feedbacksByRatingsForDB == null){
            feedbacksByRatingsForDB = new HashMap<String, Set<Feedback>>();
            feedbackByRatingsMap.put(azquoMemoryDB, feedbacksByRatingsForDB);
        }
        feedbacksByRatingsForDB.clear();
        for (Feedback feedback : findAll(azquoMemoryDB)){
            if (feedbacksByRatingsForDB.get(feedback.rating) == null){
                feedbacksByRatingsForDB.put(feedback.rating, new HashSet<Feedback>());
            }
            feedbacksByRatingsForDB.get(feedback.rating).add(feedback);
        }
    }

    public Collection<Feedback> findForRating(AzquoMemoryDB azquoMemoryDB, String rating){
        Set<Feedback> toReturn = feedbackByRatingsMap.get(azquoMemoryDB).get(rating);
        if (toReturn != null){
            return toReturn;
        } else {
            return new ArrayList<Feedback>();
        }
    }

}
