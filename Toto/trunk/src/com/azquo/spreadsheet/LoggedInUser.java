package com.azquo.spreadsheet;

import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.user.User;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.jsonentities.JsonChildren;
import com.azquo.spreadsheet.view.AzquoBook;
import com.azquo.spreadsheet.view.CellsAndHeadingsForDisplay;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
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
    private int reportId;

    private final Map<String, CellsAndHeadingsForDisplay> sentCellsMaps; // returned display data for each region
    // need to hold the current one unlike with ZK which holds onto the user after the spreadsheet is created
    private AzquoBook azquoBook;
    private List<String> languages;

    private Database database;

    // I'm a little unsure about this being separate but it will work for the moment
    private DatabaseServer databaseServer;

    private String readPermissions;
    private String writePermissions;
    private String context;

    private static final String defaultRegion = "default-region";

    // moved back in here now (was on the db server for a bit)

    private AtomicInteger lastJSTreeNodeId;

    private final Map<Integer, JsonChildren.Node> jsTreeLookupMap;

    protected LoggedInUser(String sessionId, final User user, DatabaseServer databaseServer, Database database, String readPermissions, String writePermissions) {
        this.sessionId = sessionId;
        this.user = user;
        reportId = 0;
        sentCellsMaps = new HashMap<>();
        azquoBook = null;
        languages = new ArrayList<>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        this.database = database;
        this.databaseServer = databaseServer;

        this.readPermissions = readPermissions;
        this.writePermissions = writePermissions;
        this.context = null;
        lastJSTreeNodeId = new AtomicInteger();
        jsTreeLookupMap = new ConcurrentHashMap<>();
    }

    public JsonChildren.Node getFromJsTreeLookupMap(int jsTreeNodeId){
        return jsTreeLookupMap.get(jsTreeNodeId);
    }

    public void clearJsTreeLookupMap(){ // necessary? Maybe a map could get a bit big, I'm not concerned right now
        jsTreeLookupMap.clear();
    }

    // ok we need to keep a session map of jstree ids which are created incrementally against the actual name ids, passing the nodes here seems fine

    public void assignIdForJsTreeNode(JsonChildren.Node node){
        node.id = lastJSTreeNodeId.incrementAndGet();
        jsTreeLookupMap.put(node.id, node);
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getReportId() {
        return reportId;
    }

    public void setReportId(int reportId) {
        this.reportId = reportId;
    }

    public CellsAndHeadingsForDisplay getSentCells(final String region) {
        if (region == null || region.isEmpty()) {
            return sentCellsMaps.get(defaultRegion);
        } else {
            return sentCellsMaps.get(region);
        }
    }

    public void setSentCells(final String region, final CellsAndHeadingsForDisplay sentCells) {
        if (region == null || region.isEmpty()) {
            this.sentCellsMaps.put(defaultRegion, sentCells);
        } else {
            this.sentCellsMaps.put(region, sentCells);
        }
    }

    public AzquoBook getAzquoBook() {
        return this.azquoBook;
    }

    public void setAzquoBook(AzquoBook azquoBook) {
        this.azquoBook = azquoBook;
    }


    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
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

    public String getReadPermissions() {
        return readPermissions;
    }

    public void setReadPermissions(String readPermissions) {
        this.readPermissions = readPermissions;
    }

    public String getWritePermissions() {
        return writePermissions;
    }

    public void setWritePermissions(String writePermissions) {
        this.writePermissions = writePermissions;
    }

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
        return new DatabaseAccessToken(sessionId, databaseServer.getIp(), database.getMySQLName(), readPermissions,writePermissions,languages);
    }
}
