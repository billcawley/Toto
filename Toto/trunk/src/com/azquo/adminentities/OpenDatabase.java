package com.azquo.adminentities;

import java.util.Date;

/**
 * Created by cawley on 18/02/14.
 * have made immutable which simplifies the code
 */
public final class OpenDatabase extends StandardEntity {
    // name user time db, email user when logged in

    final int id;
    final int databaseId;
    final Date open;
    final Date close;

    public OpenDatabase(int id, int databaseId, Date open, Date close) {
        this.id = id;
        this.databaseId = databaseId;
        this.open = open;
        this.close = close;
    }

    @Override
    public String toString() {
        return "OpenDatabase{" +
                "databaseId=" + databaseId +
                ", open=" + open +
                ", close=" + close +
                '}';
    }


    public int getDatabaseId() {
        return databaseId;
    }

    public Date getOpen() {
        return open;
    }

    public Date getClose(){
        return close;
    }
}
