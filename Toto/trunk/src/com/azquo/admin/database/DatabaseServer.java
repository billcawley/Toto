package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 05/08/15.
 *
 * Only edited in the database, can make immutable
 *
 * The Server being the server the database is physically hosted on (if that was not obvious)
 */
public final class DatabaseServer extends StandardEntity {
    // name user time db, email user when logged in

    private final String name;
    private final String ip;
    private final String sftpUrl;

    public DatabaseServer(int id, String name, String ip, String sftpUrl) {
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
