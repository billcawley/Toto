package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;

import java.util.Date;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 22/04/14.
 *
 * A choice typically is a drop down selection but it could be other things also, e.g. a text field that used in a search
 *
 */
public class UserChoice extends StandardEntity {

    private final int userId;
    private final String choiceName;
    private String choiceValue;
    private Date time;

    public UserChoice(int id, int userId, String choiceName, String choiceValue, Date time) {
        this.id = id;
        this.userId = userId;
        this.choiceName = choiceName;
        this.choiceValue = choiceValue;
        this.time = time;
    }

    @Override
    public String toString() {
        return "UserChoice{" +
                "userId=" + userId +
                ", choiceName=" + choiceName +
                ", choiceValue=" + choiceValue +
                ", time=" + time +
                '}';
    }

    public int getUserId() {
        return userId;
    }

    public String getChoiceName() {
        return choiceName;
    }

    public String getChoiceValue() {
        return choiceValue;
    }

    public void setChoiceValue(String choiceValue) {
        this.choiceValue = choiceValue;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }
}



