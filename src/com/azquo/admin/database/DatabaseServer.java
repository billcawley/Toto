package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;

import java.io.Serializable;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 05/08/15.
 * <p>
 * Only edited in the database, can make immutable
 * <p>
 * The Server being the server the database is physically hosted on (if that was not obvious)
 */
public final class DatabaseServer extends StandardEntity implements Serializable {
    // name user time db, email user when logged in

    private final String name;
    private final String ip;
    private final String sftpUrl;

    DatabaseServer(int id, String name, String ip, String sftpUrl) {
        this.id = id;
        this.name = name;
        this.ip = ip;
        this.sftpUrl = sftpUrl;
    }

    public String getName() {
        return name;
    }

    public String getIp() {
        return ip;
    }

    public String getSftpUrl() {
        return sftpUrl;
    }
}