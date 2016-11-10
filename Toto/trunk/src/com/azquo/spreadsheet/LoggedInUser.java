package com.azquo.spreadsheet;

import com.azquo.TypedPair;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.user.User;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 12/05/15.
 *
 * On the new client/server model the old LoggedInConnection will not do. We want an object representing a logged in user against the session
 * which holds no database classes. It will have a fair bit of stuff that was in logged in connection but no DB classes
 *
 * TODO - serializable? Bit of a pain to go all the way down.
 */
public class LoggedInUser {

    private static final String userLogsPath = "User Logs/"; // with a space

    private final String sessionId; // it's used to match to a log server side
    private final User user;


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

    private Map<String, TypedPair<OnlineReport, Database>> permissionsFromReport; // hold them here after they're set by a "home page" report for linking

    private static final String defaultRegion = "default-region";

    // moved back in here now (was on the db server for a bit)

    private AtomicInteger lastJSTreeNodeId;

    private final Map<Integer, JsonChildren.Node> jsTreeLookupMap;

    private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat df2 = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    protected LoggedInUser(String sessionId, final User user, DatabaseServer databaseServer, Database database, String readPermissions, String writePermissions, String imageStoreName, String businessDirectory) {
        this.sessionId = sessionId;
        this.user = user;
        this.businessDirectory = businessDirectory;
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
        // make log files dir if required
            File test = new File(SpreadsheetService.getHomeDir() + ImportService.dbPath + userLogsPath);
            if (!test.exists()){
                test.mkdirs();
            }
    }

    public JsonChildren.Node getFromJsTreeLookupMap(int jsTreeNodeId){
        return jsTreeLookupMap.get(jsTreeNodeId);
    }

    // ok we need to keep a session map of jstree ids which are created incrementally against the actual name ids, passing the nodes here seems fine
    public LoggedInUser(LoggedInUser originalUser){

        this.sessionId = originalUser.sessionId;
        this.user = originalUser.user;
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

    // adding in report id (a little hacky, could maybe change later?) otherwise two reports on different tabs could clash on identically named regions - bug identified by drilldowns

    public CellsAndHeadingsForDisplay getSentCells(final int reportId, final String region) {
        if (region == null || region.isEmpty()) {
            return sentCellsMaps.get(reportId + "-" + defaultRegion);
        } else {
            return sentCellsMaps.get(reportId + "-" + region.toLowerCase());
        }
    }

    public List<CellsAndHeadingsForDisplay> getSentForReport(final int reportId) {
        List<CellsAndHeadingsForDisplay> toReturn = new ArrayList<>();
        for (String key : sentCellsMaps.keySet()){
            if (key.startsWith(reportId + "-")){
                toReturn.add(sentCellsMaps.get(key));
            }
        }
        return toReturn;
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
        return new DatabaseAccessToken(sessionId, user.getEmail(), databaseServer.getIp(), database.getPersistenceName(), readPermissions,writePermissions,languages);
    }

    public String getBusinessDirectory() {
        return businessDirectory;
    }

    public Map<String, TypedPair<OnlineReport, Database>> getPermissionsFromReport() {
        return permissionsFromReport;
    }

    public void setPermissionsFromReport(Map<String, TypedPair<OnlineReport, Database>> permissionsFromReport) {
        this.permissionsFromReport = permissionsFromReport;
    }
    // just pop it open and closed, should be a little cleaner
    public void userLog(String message){
        try {
            Files.write(Paths.get(SpreadsheetService.getHomeDir() + ImportService.dbPath + userLogsPath + user.getEmail() + "-" + df.format(new Date()) + ".log"),
                    (df2.format(new Date()) + "\t" + message + "\n").getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}