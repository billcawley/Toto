package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;

import java.util.Date;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 18/02/14.
 * have made immutable which simplifies the code
 * <p>
 * Recording which databases are being accessed, not actually being used since the report/DB server split, need to get rid of it or start using it again. Todo
 */
final class OpenDatabase extends StandardEntity {
    // name user time db, email user when logged in

    private final int databaseId;
    private final Date open;
    private final Date close;

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

    public Date getClose() {
        return close;
    }
}