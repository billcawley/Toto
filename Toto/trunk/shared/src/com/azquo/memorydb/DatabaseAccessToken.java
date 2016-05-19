package com.azquo.memorydb;

import java.io.Serializable;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 18/05/15.
 *
 * Passed from client to database server after establishing credentials etc, what the DB server uses to create a connection internally.
 *
 */
public class DatabaseAccessToken implements Serializable {

    private final String userSessionId; // ok, used for status updates/user interruptions, could be a user id from ZK or maybe just the tomcat session
    private final String serverIp; // not strictly part of the access token but I think it should probably be in here
    private final String persistenceName;
    private final String readPermissions;
    private final String writePermissions;
    private final List<String> languages;

    public DatabaseAccessToken(String userSessionId, String serverIp, String persistenceName, String readPermissions, String writePermissions, List<String> languages) {
        this.userSessionId = userSessionId;
        this.serverIp = serverIp;
        this.persistenceName = persistenceName;
        this.readPermissions = readPermissions;
        this.writePermissions = writePermissions;
        this.languages = languages;
    }

    public String getServerIp() {
        return serverIp;
    }

    public String getPersistenceName() {
        return persistenceName;
    }

    String getReadPermissions() {
        return readPermissions;
    }

    String getWritePermissions() {
        return writePermissions;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public String getUserSessionId() {
        return userSessionId;
    }

    @Override
    public String toString() {
        return "DatabaseAccessToken{" +
                "userSessionId='" + userSessionId + '\'' +
                ", serverIp='" + serverIp + '\'' +
                ", persistenceName='" + persistenceName + '\'' +
                ", readPermissions='" + readPermissions + '\'' +
                ", writePermissions='" + writePermissions + '\'' +
                ", languages=" + languages +
                '}';
    }
}