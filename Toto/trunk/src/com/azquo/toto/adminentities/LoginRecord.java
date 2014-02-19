package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Created by cawley on 18/02/14.
 * have made immutable which simplifies the code
 */
public final class LoginRecord extends StandardEntity {
    // name user time db, email user when logged in

    final int userId;
    final int databaseId;
    final Date time;

    public LoginRecord(int id, int userId, int databaseId, Date time) {
        this.id = id;
        this.userId = userId;
        this.databaseId = databaseId;
        this.time = time;
    }

    @Override
    public String toString() {
        return "LoginRecord{" +
                "userId=" + userId +
                ", databaseId=" + databaseId +
                ", time=" + time +
                '}';
    }

    public int getUserId() {
        return userId;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public Date getTime() {
        return time;
    }
}
