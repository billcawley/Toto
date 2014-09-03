package com.azquo.adminentities;

import java.util.Date;

/**
 * Created by bill on 22/04/14.
 *
 */
public class UserChoice extends StandardEntity {

    final int userId;
    final int reportId;
    final String choiceName;
    String choiceValue;
    Date time;

    public UserChoice(int id, int userId, int reportId, String choiceName, String choiceValue, Date time) {
        this.id = id;
        this.userId = userId;
        this.reportId = reportId;
        this.choiceName = choiceName;
        this.choiceValue = choiceValue;
        this.time = time;
    }

    @Override
    public String toString() {
        return "UserChoice{" +
                "userId=" + userId +
                ", reportId=" + reportId +
                ", choiceName=" + choiceName +
                ", choiceValue=" + choiceValue +
                ", time=" + time +
                '}';
    }

    public int getUserId() {
        return userId;
    }

    public int getReportId() {
        return reportId;
    }

    public String getChoiceName() {
        return choiceName;
    }

    public String getChoiceValue() {return choiceValue; }

    public void setChoiceValue(String choiceValue) { this.choiceValue = choiceValue; }

    public Date getTime() {return time; }

    public void setTime(Date time){ this.time = time; }
}



