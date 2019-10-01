package com.azquo.memorydb;

import com.azquo.memorydb.core.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 21/10/14.
 * <p>
 * Changed over time especially since the server/client split. Holds permissions, the user log and provenance currently.
 */
public class AzquoMemoryDBConnection {

    //private static final Logger logger = Logger.getLogger(AzquoMemoryDBConnection.class);

    private final AzquoMemoryDB azquoMemoryDB;

    private final AzquoMemoryDBIndex azquoMemoryDBIndex; // just shorthand - always set to the memoryDB's index

    //private final List<Set<Name>> readPermissions;

   // private final List<Set<Name>> writePermissions;

    private final StringBuffer userLog; // thread safe, it probably needs to be

    protected Provenance provenance = null;

    private volatile boolean unusedProvenance = false;

    // bears an explanation. When dealing with filter (multi select) sets then provenance *might* be required. So allow suggestions here if provenance is null when required
    // if I just plain set provenance it might make a fair few redundant provenances and trying to check in findOrCreateNameInParent isn't practical either
    // as it's too low level - that function is used a lot when importing. Hence suggestions if required.
    private String provenanceUserSuggestion = "unknown";
    private String provenanceMethodSuggestion = "";
    private String provenanceNameSuggestion = "";
    private String provenanceContextSuggestion = "";

    private AzquoMemoryDBConnection(AzquoMemoryDB azquoMemoryDB, StringBuffer userLog) {
        this.azquoMemoryDB = azquoMemoryDB;
        this.azquoMemoryDBIndex = azquoMemoryDB.getIndex();
        this.userLog = userLog;
    }

    // todo, clean this up when sessions are expired, maybe a last accessed time?
    private static final Map<String, StringBuffer> sessionLogs = new ConcurrentHashMap<>();

    // after some thinking trim this down to the basics. Would have just been a DB name for that server but need permissions too.
    // may cache in future to save DB/Permission lookups. Depends on how consolidated client/server calls can be made . . .
    public static AzquoMemoryDBConnection getConnectionFromAccessToken(DatabaseAccessToken databaseAccessToken)  {
        // todo - address opendb count (do we care?) and exceptions
        // todo - also - keep a map of connections? Expiry could be an issue . . .
        StringBuffer sessionLog = sessionLogs.computeIfAbsent(databaseAccessToken.getUserSessionId(), t -> new StringBuffer()); // computeIfAbsent is such a wonderful thread safe call
        AzquoMemoryDB memoryDB = AzquoMemoryDB.getAzquoMemoryDB(databaseAccessToken.getPersistenceName(), sessionLog);
        // we can't do the lookup for permissions out here as it requires the connection, hence pass things through
        return new AzquoMemoryDBConnection(memoryDB, sessionLog);
    }

    public static AzquoMemoryDBConnection getTemporaryCopyConnectionFromAccessToken(DatabaseAccessToken databaseAccessToken) throws Exception {
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        long time = System.currentTimeMillis();
        // I'm just doing this to be a bit helpful in the logs . . .
        if (AzquoMemoryDB.copyExists(azquoMemoryDBConnection.getAzquoMemoryDB().getPersistenceName())){
            azquoMemoryDBConnection.addToUserLog("******* USING A COPY OF THE " + azquoMemoryDBConnection.getAzquoMemoryDB().getPersistenceName() + " DATABASE TO RUN VALIDATION AGAINST");
            return new AzquoMemoryDBConnection(AzquoMemoryDB.getCopyOfAzquoMemoryDB(azquoMemoryDBConnection.getAzquoMemoryDB().getPersistenceName()), azquoMemoryDBConnection.userLog);
        } else {
            azquoMemoryDBConnection.addToUserLog("******* MAKING A COPY OF THE " + azquoMemoryDBConnection.getAzquoMemoryDB().getPersistenceName() + " DATABASE TO RUN VALIDATION AGAINST");
            AzquoMemoryDB copyOfAzquoMemoryDB = AzquoMemoryDB.getCopyOfAzquoMemoryDB(azquoMemoryDBConnection.getAzquoMemoryDB().getPersistenceName());
            azquoMemoryDBConnection.addToUserLog("******* COPY COMPLETE IN " + ((System.currentTimeMillis() - time) / 1000) + " SECOND(S)");
            return new AzquoMemoryDBConnection(copyOfAzquoMemoryDB, azquoMemoryDBConnection.userLog);
        }
    }

    public static String getSessionLog(DatabaseAccessToken databaseAccessToken)  {
        StringBuffer log = sessionLogs.get(databaseAccessToken.getUserSessionId());
        if (log != null) {
            return log.toString();
        }
        return "";
    }

    public void zapTemporaryCopy() {
        AzquoMemoryDB.zapTemporarayCopyOfAzquoMemoryDB(getAzquoMemoryDB().getPersistenceName());
    }

    public AzquoMemoryDB getAzquoMemoryDB() {
        return azquoMemoryDB;
    }

    public AzquoMemoryDBIndex getAzquoMemoryDBIndex() {
        return azquoMemoryDBIndex;
    }

    public Provenance getProvenance() {
        if (provenance == null) {
            // best put this here to stop NPE but this really shouldn't happen.
            try {
                provenance = new Provenance(getAzquoMemoryDB(), provenanceUserSuggestion, provenanceMethodSuggestion, provenanceNameSuggestion, provenanceContextSuggestion);
            } catch (Exception ignored) {
            }
        }
        unusedProvenance = false;
        return provenance;
    }

    public boolean isUnusedProvenance() {
        return unusedProvenance;
    }

    // not the comment by the field definitions above
    public void suggestProvenance(final String user, final String method, String name, final String context) {
        provenanceUserSuggestion = user;
        provenanceMethodSuggestion = method;
        provenanceNameSuggestion = name;
        provenanceContextSuggestion = context;
    }

    public void setProvenance(final String user, final String method, String name, final String context) throws Exception {
        // the question is whether the provenance was used not whether it is new - maybe needs a rename
        unusedProvenance = true;
        Provenance latest = azquoMemoryDB.getMostRecentProvenance();
        // not sure how latest and method cen get set as null but best to be careful with it
        if (latest != null && latest.getUser().equals(user)) {
            if (latest.getMethod() != null && latest.getMethod().equals(method) && latest.getName() != null && latest.getName().equals(name) &&
                    latest.getContext() != null && latest.getContext().equals(context) && latest.getTimeStamp().plusSeconds(30).isAfter(LocalDateTime.now())) {
                this.provenance = latest;
                return;
            }
        }
        this.provenance = new Provenance(getAzquoMemoryDB(), user, method, name, context);
    }

    public void setProvenance(final Provenance p)  {
        this.provenance = p;
    }

    public long getDBLastModifiedTimeStamp() {
        return azquoMemoryDB.getLastModifiedTimeStamp();
    }

    public void persist() {
        azquoMemoryDB.persistToDataStore();
    }

    public List<Value> getValuesChanged(){
        return azquoMemoryDB.getValuesChanged();
    }

    public void lockTest() {
        azquoMemoryDB.lockTest();
    }

    public void persistInBackground() {
        (new Thread(new PersistenceRunner())).start();
    }

    private static String STOP = "STOP";

    public void setStopInUserLog() {
        userLog.setLength(0);
        userLog.append(STOP);
    }

    // change to callable for consistency?
    private class PersistenceRunner implements Runnable {
        @Override
        public void run() {
            azquoMemoryDB.persistToDataStore();
        }
    }

    public void clearUserLog() {
        userLog.setLength(0);
    }

    public void addToUserLog(String toAdd) throws Exception {
        addToUserLog(toAdd, true);
    }

    public void addToUserLog(String toAdd, boolean newline) throws Exception {
        boolean exception = userLog.toString().equals(STOP);
        addToUserLogNoException(toAdd, newline);
        if (exception) {
            userLog.append("INTERRUPTED BY USER, THROWING EXCEPTION\n");
            throw new Exception("Execution interrupted by user");
        }
    }

    public void addToUserLogNoException(String toAdd, boolean newline) {
        if (newline) {
            System.out.println(toAdd);
            userLog.append(toAdd).append("\n");
        } else {
            System.out.print(toAdd);
            userLog.append(toAdd);
        }
    }

    @Override
    public String toString() {
        return "AzquoMemoryDBConnection{" +
                "azquoMemoryDB=" + azquoMemoryDB +
                ", azquoMemoryDBIndex=" + azquoMemoryDBIndex +
                ", provenance=" + provenance +
                ", unusedProvenance=" + unusedProvenance +
                '}';
    }
}