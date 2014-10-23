package com.azquo.app.reviews.entity;

import com.azquo.app.reviews.service.FeedbackService;
import com.azquo.memorydb.AppEntity;
import com.azquo.memorydb.AzquoMemoryDB;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;

import java.util.Date;

/**
 * Created by cawley on 18/08/14.
 *
 * From dad's email needs rating feedback date response date sale date product
 */
public class Feedback extends AppEntity<FeedbackService>{

    private static final Logger logger = Logger.getLogger(Feedback.class);

    // to final or not to final? I guess try to make final and change if I need to

    public final Date timeStamp;
    public final String rating;
    public final String comment;

    public Feedback(AzquoMemoryDB azquoMemoryDB, int id, FeedbackService service, String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id, service);
        JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);
        this.timeStamp = transport.timeStamp;
        this.rating = transport.rating;
        this.comment = transport.comment;
    }

    public Feedback(AzquoMemoryDB azquoMemoryDB, FeedbackService service,
                    Date timeStamp, String rating, String comment) throws Exception {
        super(azquoMemoryDB, 0, service);
        this.timeStamp = timeStamp;
        this.rating = rating;
        this.comment = comment;
    }

    // follow the main entity pattern

    private static class JsonTransport {
        public final Date timeStamp;
        public final String rating;
        public final String comment;

        @JsonCreator
        private JsonTransport(@JsonProperty("timeStamp") Date timeStamp
                , @JsonProperty("rating") String rating
                , @JsonProperty("comment") String comment) {
            this.timeStamp = timeStamp;
            this.rating = rating;
            this.comment = comment;
        }
    }

    @Override
    public String getAsJson() {
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(timeStamp, rating,comment));
        } catch (Exception e) {
            logger.error("can't get a feedback as json", e);
        }
        return "";
    }

    @Override
    public String toString() {
        return "Feedback{" +
                "timeStamp=" + timeStamp +
                ", rating='" + rating + '\'' +
                ", comment='" + comment + '\'' +
                '}';
    }
}