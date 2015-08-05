package com.azquo.memorydb;

import java.io.Serializable;
import java.util.List;

/**
 * Created by cawley on 18/05/15.
 *
 * Passed from client to database server after establishing credentials etc
 *
 */
public class DatabaseAccessToken implements Serializable {

    private final String serverIp; // not strictly part of the access token but I think it should probably be in here
    private final String databaseMySQLName;
    private final String readPermissions;
    private final String writePermissions;
    private final List<String> languages;
//    private final int sessionId;// optional, it might be useful to jam the client side session id in here, certainly useful for jstree initially

    public DatabaseAccessToken(String serverIp, String databaseMySQLName, String readPermissions, String writePermissions, List<String> languages) {
        this.serverIp = serverIp;
        this.databaseMySQLName = databaseMySQLName;
        this.readPermissions = readPermissions;
        this.writePermissions = writePermissions;
        this.languages = languages;
    }

    public String getServerIp() {
        return serverIp;
    }

    public String getDatabaseMySQLName() {
        return databaseMySQLName;
    }

    public String getReadPermissions() {
        return readPermissions;
    }

    public String getWritePermissions() {
        return writePermissions;
    }

    public List<String> getLanguages() {
        return languages;
    }

    @Override
    public String toString() {
        return "DatabaseAccessToken{" +
                "serverIp='" + serverIp + '\'' +
                ", databaseMySQLName='" + databaseMySQLName + '\'' +
                ", readPermissions='" + readPermissions + '\'' +
                ", writePermissions='" + writePermissions + '\'' +
                ", languages=" + languages +
                '}';
    }
}