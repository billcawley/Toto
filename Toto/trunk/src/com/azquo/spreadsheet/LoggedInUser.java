package com.azquo.spreadsheet;

import com.azquo.StringLiterals;
import com.azquo.admin.business.Business;
import com.azquo.admin.database.Database;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.DatabaseServer;
import com.azquo.admin.onlinereport.OnlineReport;
import com.azquo.admin.onlinereport.OnlineReportDAO;
import com.azquo.admin.onlinereport.UserActivity;
import com.azquo.admin.onlinereport.UserActivityDAO;
import com.azquo.admin.user.User;
import com.azquo.dataimport.ImportService;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.spreadsheet.transport.json.JsonChildren;
import com.azquo.spreadsheet.transport.CellsAndHeadingsForDisplay;
import io.keikai.api.model.Book;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 12/05/15.
 * <p>
 * On the new client/server model the old LoggedInConnection will not do. We want an object representing a logged in user against the session
 * which holds no database classes. It will have a fair bit of stuff that was in logged in connection but no DB classes
 * <p>
 */
public class LoggedInUser implements Serializable {

    // I don't care about equals and hashcode on these two currently
    public static class ReportIdDatabaseId {
        final int reportId;
        final int databaseId;

        public ReportIdDatabaseId(int reportId, int databaseId) {
            this.reportId = reportId;
            this.databaseId = databaseId;
        }

        public int getReportId() {
            return reportId;
        }

        public int getDatabaseId() {
            return databaseId;
        }
    }

    public static class ReportDatabase {
        final OnlineReport report;
        final Database database;

        public ReportDatabase(OnlineReport report, Database database) {
            this.report = report;
            this.database = database;
        }

        public OnlineReport getReport() {
            return report;
        }

        public Database getDatabase() {
            return database;
        }
    }

    private static final String userLogsPath = "User Logs/"; // with a space

    private final String sessionId; // it's used to match to a log server side
    private final User user;

    // in theory the concantation of strings for keys could trip up, maybe make more robust? TODO
    private final Map<String, CellsAndHeadingsForDisplay> sentCellsMaps; // returned display data for each region

    private Database database;
    private OnlineReport onlineReport;
    // todo - this should not really be in here but transient will do for the mo to stop people being booted on tomcat restart
    private transient Book book;

    // I'm a little unsure about this being separate but it will work for the moment
    private DatabaseServer databaseServer;

    //private String readPermissions;
    //private String writePermissions;
    private String imageStoreName;
    // context is not the same as context per region and in fact this needs to be like sent cells in that it needs to be per report and perhaps stored as pairs? Or a map. Choices for report TODO
    private String context;

    private final Business business;

    // now uses ids given the problems of leaving full objects in the session (going out of sync with the database)
    // report, database, generally set from a home user menu
    private final Map<String, ReportIdDatabaseId> reportIdDatabaseIdPermissions; // hold them here after they're set by a "home page" report for linking

    private Set<String> formPermissions; // form permissions, more simple than above

    private static final String defaultRegion = "default-region";
    private static final String defaultSheet = "default-sheet";

    // moved back in here now (was on the db server for a bit)

    private final AtomicInteger lastJSTreeNodeId;

    private final Map<Integer, JsonChildren.Node> jsTreeLookupMap;

    // a bit hacky, I just want a place to put the last converted file. For Modus, won't support more than one file etc. Just make it work for the mo
    private String lastFile = null;
    private String lastFileName = null;

    // public allowing hack for xml scanning - need to sort - todo
    public LoggedInUser(String sessionId, final User user, DatabaseServer databaseServer, Database database, String imageStoreName, Business business) {
        this.sessionId = sessionId;
        this.user = user;
        this.business = business;
        sentCellsMaps = new HashMap<>();
        this.database = database;
        this.onlineReport = null;
        this.book = null;
        this.databaseServer = databaseServer;
        //this.readPermissions = readPermissions;
        //this.writePermissions = writePermissions;
        this.imageStoreName = imageStoreName;
        this.context = null;
        lastJSTreeNodeId = new AtomicInteger();
        jsTreeLookupMap = new ConcurrentHashMap<>();
        // make log files dir if required

        Path test = Paths.get(SpreadsheetService.getHomeDir() + ImportService.dbPath + userLogsPath);
        if (!Files.exists(test)){
            try {
                Files.createDirectories(test); // in case it doesn't exist
            } catch (IOException e) {
                e.printStackTrace(); // fine to just dump the stack
            }
        }
        reportIdDatabaseIdPermissions = new ConcurrentHashMap<>();
        formPermissions = new HashSet<>();
    }

    public JsonChildren.Node getFromJsTreeLookupMap(int jsTreeNodeId) {
        return jsTreeLookupMap.get(jsTreeNodeId);
    }

    // ok we need to keep a session map of jstree ids which are created incrementally against the actual name ids, passing the nodes here seems fine
    public void assignIdForJsTreeNode(JsonChildren.Node node) {
        node.id = lastJSTreeNodeId.incrementAndGet();
        jsTreeLookupMap.put(node.id, node);
    }

    // adding in report id (a little hacky, could maybe change later?) otherwise two reports on different tabs could clash on identically named regions - bug identified by drilldowns

    public CellsAndHeadingsForDisplay getSentCells(final int reportId, String sheetName, String region) {
        if (sheetName == null || sheetName.isEmpty()){
            sheetName = defaultSheet;
        }
        if (region == null || region.isEmpty()) {
            region = defaultRegion;
        }
        return sentCellsMaps.get(reportId + "-"  + sheetName + "-" + region.toLowerCase());
    }

    public void setSentCells(final int reportId, String sheetName, String region, final CellsAndHeadingsForDisplay sentCells) {
        if (sheetName == null || sheetName.isEmpty()){
            sheetName = defaultSheet;
        }
        if (region == null || region.isEmpty()) {
            region = defaultRegion;
        }
        this.sentCellsMaps.put(reportId + "-"  + sheetName + "-" + region.toLowerCase(), sentCells);
    }

    public List<CellsAndHeadingsForDisplay> getSentForReport(final int reportId) {
        List<CellsAndHeadingsForDisplay> toReturn = new ArrayList<>();
        for (Map.Entry<String, CellsAndHeadingsForDisplay> keyCells : sentCellsMaps.entrySet()) {
            if (keyCells.getKey().startsWith(reportId + "-")) {
                toReturn.add(keyCells.getValue());
            }
        }
        return toReturn;
    }

    public List<CellsAndHeadingsForDisplay> getSentForReportAndSheet(final int reportId, String sheetName) {
        if (sheetName == null || sheetName.isEmpty()){
            sheetName = defaultSheet;
        }
        List<CellsAndHeadingsForDisplay> toReturn = new ArrayList<>();
        for (Map.Entry<String, CellsAndHeadingsForDisplay> keyCells : sentCellsMaps.entrySet()) {
            if (keyCells.getKey().startsWith(reportId + "-" + sheetName + "-")) {
                toReturn.add(keyCells.getValue());
            }
        }
        return toReturn;
    }

    public User getUser() {
        return user;
    }

    public Database getDatabase() {
        return database;
    }

    public OnlineReport getOnlineReport() {
        return onlineReport;
    }

    public void setBook(Book book){
        this.book = book;
    }

    public Book getBook() {
        return book;
    }

    public void setOnlineReport(OnlineReport onlineReport){
        this.onlineReport = onlineReport;
    }
    public void setDatabaseWithServer(DatabaseServer databaseServer, Database database) {
        this.databaseServer = databaseServer;
        this.database = database;
    }

    public String getImageStoreName() {
        return imageStoreName;
    }

    public void setImageStoreName(String imageStoreName) {
        this.imageStoreName = imageStoreName;
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

    // todo - fix this hack later! Get code out of dsimport service - make use of copy set here
    public boolean copyMode = false;

    public DatabaseAccessToken getDataAccessToken() {
        return new DatabaseAccessToken(sessionId, user.getEmail(), databaseServer.getIp(), (copyMode ? StringLiterals.copyPrefix + user.getEmail(): "") + database.getPersistenceName());
    }

    public Business getBusiness(){
        return business;
    }

    public String getBusinessDirectory() {
        return business.getBusinessDirectory();
    }

    // deliberately look up the permissions each time - we want to be up to date with master_db
    public ReportDatabase getPermission(String reportName){
        ReportIdDatabaseId idPair = reportIdDatabaseIdPermissions.get(reportName.toLowerCase());
        if (idPair != null){
            Database byId = DatabaseDAO.findById(idPair.getDatabaseId());
            OnlineReport onlineReport = OnlineReportDAO.findById(idPair.getReportId());
            if (byId != null && onlineReport != null){
                return new ReportDatabase(onlineReport,byId);
            } else { // zap reference to records which don't exist!
                reportIdDatabaseIdPermissions.remove(reportName.toLowerCase());
            }
        }
        return null;
    }

    public void setReportDatabasePermission(String key, OnlineReport onlineReport, Database database){
        reportIdDatabaseIdPermissions.put(key != null ? key.toLowerCase() : onlineReport.getReportName().toLowerCase(), new ReportIdDatabaseId(onlineReport.getId(), database.getId()));
    }

    public Map<String, ReportIdDatabaseId> getReportIdDatabaseIdPermissions() {
        return reportIdDatabaseIdPermissions;
    }

    public Set<String> getFormPermissions() {
        return formPermissions;
    }

    public void setFormPermissions(Set<String> formPermissions) {
        this.formPermissions = formPermissions;
    }

    // just pop it open and closed, should be a little cleaner
/*    public void userLog(String message) {
        try {
            Files.write(Paths.get(SpreadsheetService.getHomeDir() + ImportService.dbPath + userLogsPath + user.getEmail() + "-" + df.format(LocalDateTime.now()) + ".log"),
                    (df2.format(LocalDateTime.now()) + "\t" + message + "\n").getBytes(Charset.forName("UTF-8")), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    public void userLog(String activity, Map<String, String> parameters) {
        UserActivity ua = new UserActivity(0, user.getBusinessId(), user.getEmail(), activity, parameters);
        UserActivityDAO.store(ua);
    }

    public String getLastFile() {
        return lastFile;
    }

    public void setLastFile(String lastFile) {
        this.lastFile = lastFile;
    }

    @Override
    public String toString() {
        return "LoggedInUser{" +
                "sessionId='" + sessionId + '\'' +
                ", user=" + user +
                ", sentCellsMaps=" + sentCellsMaps +
                ", database=" + database +
                ", onlineReport=" + onlineReport +
                ", databaseServer=" + databaseServer +
                ", imageStoreName='" + imageStoreName + '\'' +
                ", context='" + context + '\'' +
                ", businessDirectory='" + business.getBusinessDirectory() + '\'' +
                ", reportIdDatabaseIdPermissions=" + reportIdDatabaseIdPermissions +
                ", lastJSTreeNodeId=" + lastJSTreeNodeId +
                ", jsTreeLookupMap=" + jsTreeLookupMap +
                ", copyMode=" + copyMode +
                '}';
    }

    public String getLastFileName() {
        return lastFileName;
    }

    public void setLastFileName(String lastFileName) {
        this.lastFileName = lastFileName;
    }
}