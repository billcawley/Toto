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

    private final String databaseMySQLName;
    private final String readPermissions;
    private final String writePermissions;
    private final List<String> languages;
//    private final int sessionId;// optional, it might be useful to jam the client side session id in here, certainly useful for jstree initially

    public DatabaseAccessToken(String databaseMySQLName, String readPermissions, String writePermissions, List<String> languages) {
        this.databaseMySQLName = databaseMySQLName;
        this.readPermissions = readPermissions;
        this.writePermissions = writePermissions;
        this.languages = languages;
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
}