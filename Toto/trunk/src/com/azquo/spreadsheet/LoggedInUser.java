package com.azquo.spreadsheet;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.user.User;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 12/05/15.
 *
 * On the new client/server model the old LoggedInConnection will not do. We want an object representing a logged in user against the session
 * which holds no database classes. It will have a fair bit of stuff that was in logged in connection but no DB classes
 *
 */
public class LoggedInUser {

    private static final Logger logger = Logger.getLogger(LoggedInUser.class);

    private final String sessionId; // it's used to match to a log server side
    private final User user;

    // this report id should only be there for legacy reasons for AzquoBook, it makes no sense in the context of mulitple tabs hence ZK should not use it
    private int reportId;

    private final Map<String, CellsAndHeadingsForDisplay> sentCellsMaps; // returned display data for each region

    private List<String> languages;

    private Database database;

    // I'm a little unsure about this being separate but it will work for the moment
    private DatabaseServer databaseServer;

    private String readPermissions;
    private String writePermissions;
    private String imageStoreName;
    private String context;

    private final String businessDirectory;

    private Map<String, String> permissionsFromReport; // hold them here after they're set by a "home page" report for linking

    private static final String defaultRegion = "default-region";

    // moved back in here now (was on the db server for a bit)

    private AtomicInteger lastJSTreeNodeId;

    private final Map<Integer, JsonChildren.Node> jsTreeLookupMap;

    protected LoggedInUser(String sessionId, final User user, DatabaseServer databaseServer, Database database, String readPermissions, String writePermissions, String imageStoreName, String businessDirectory) {
        this.sessionId = sessionId;
        this.user = user;
        this.businessDirectory = businessDirectory;
        reportId = 0;
        sentCellsMaps = new HashMap<>();
        languages = new ArrayList<>(2);
        languages.add(user.getEmail()); // ok this is part of a new idea to deal with names created by "as" and otehr names that might be assigned for a user. Needs testing.
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        this.database = database;
        this.databaseServer = databaseServer;

        this.readPermissions = readPermissions;
        this.writePermissions = writePermissions;
        this.imageStoreName = imageStoreName;
        this.context = null;
        lastJSTreeNodeId = new AtomicInteger();
        jsTreeLookupMap = new ConcurrentHashMap<>();
    }

    public JsonChildren.Node getFromJsTreeLookupMap(int jsTreeNodeId){
        return jsTreeLookupMap.get(jsTreeNodeId);
    }

    // ok we need to keep a session map of jstree ids which are created incrementally against the actual name ids, passing the nodes here seems fine
    public LoggedInUser(LoggedInUser originalUser){

        this.sessionId = originalUser.sessionId;
        this.user = originalUser.user;
        reportId = 0;
        sentCellsMaps = new HashMap<>();
        languages = new ArrayList<>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        languages.add(originalUser.user.getEmail()); // ok this is part of a new idea to deal with names created by "as" and otehr names that might be assigned for a user. Needs testing.
        this.database = originalUser.database;
        this.databaseServer = originalUser.databaseServer;
        this.readPermissions = originalUser.readPermissions;
        this.writePermissions = originalUser.writePermissions;
        this.context = null;
        lastJSTreeNodeId = new AtomicInteger();
        jsTreeLookupMap = new ConcurrentHashMap<>();
        this.businessDirectory = originalUser.businessDirectory;
    }

    public void assignIdForJsTreeNode(JsonChildren.Node node){
        node.id = lastJSTreeNodeId.incrementAndGet();
        jsTreeLookupMap.put(node.id, node);
    }

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
    }

    // adding in report id (a little hacky, could maybe change later?) otherwise two reports on different tabs could clash on identically named regions - bug identified by drilldowns

    public CellsAndHeadingsForDisplay getSentCells(final int reportId, final String region) {
        if (region == null || region.isEmpty()) {
            return sentCellsMaps.get(reportId + "-" + defaultRegion);
        } else {
            return sentCellsMaps.get(reportId + "-" + region.toLowerCase());
        }
    }

    public void setSentCells(final int reportId, final String region, final CellsAndHeadingsForDisplay sentCells) {
        if (region == null || region.isEmpty()) {
            this.sentCellsMaps.put(reportId + "-" + defaultRegion, sentCells);
        } else {
            this.sentCellsMaps.put(reportId + "-" + region.toLowerCase(), sentCells);
        }
    }


    // todo a version that includes the email and one that doesn't
    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        if (languages != null){ // should not be!
            languages.add(0, user.getEmail()); // make it first
        }
        this.languages = languages;
    }

    public User getUser() {
        return user;
    }

    public String getDatabaseType(){
        return database.getDatabaseType();
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabaseWithServer(DatabaseServer databaseServer, Database database) {
        this.databaseServer = databaseServer;
        this.database = database;
    }

    public String getImageStoreName() { return imageStoreName; };

    public void setImageStoreName(String imageStoreName) {this.imageStoreName = imageStoreName; }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public DatabaseServer getDatabaseServer() {
        return databaseServer;
    }

    public DatabaseAccessToken getDataAccessToken(){
        return new DatabaseAccessToken(sessionId, databaseServer.getIp(), database.getPersistenceName(), readPermissions,writePermissions,languages);
    }

    public String getBusinessDirectory() {
        return businessDirectory;
    }

    public Map<String, String> getPermissionsFromReport() {
        return permissionsFromReport;
    }

    public void setPermissionsFromReport(Map<String, String> permissionsFromReport) {
        this.permissionsFromReport = permissionsFromReport;
    }
}