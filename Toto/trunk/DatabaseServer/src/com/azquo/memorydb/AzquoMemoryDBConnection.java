package com.azquo.memorydb;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.AzquoMemoryDBIndex;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
//import org.apache.log4j.Logger;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 21/10/14.
 * <p>
 * Changed over time especially since the server/client split. Holds permissions, the user log and provenance currently.
 */
public class AzquoMemoryDBConnection {

    //private static final Logger logger = Logger.getLogger(AzquoMemoryDBConnection.class);

    private final AzquoMemoryDB azquoMemoryDB;

    private final AzquoMemoryDBIndex azquoMemoryDBIndex; // just shorthand - always set to the memoryDB's index

    private final List<String> languages;

    //private final List<Set<Name>> readPermissions;

   // private final List<Set<Name>> writePermissions;

    private final StringBuffer userLog; // threadsafe, it probably needs to be

    protected Provenance provenance = null;


    // A bit involved but it makes this object immutable, think that's worth it - note
    // new logic here : we'll say that top test that have no permissions are added as allowed - if someone has added a department for example they should still have access to all dates

    private AzquoMemoryDBConnection(AzquoMemoryDB azquoMemoryDB, DatabaseAccessToken databaseAccessToken, List<String> languages, StringBuffer userLog) throws Exception {
        this.azquoMemoryDB = azquoMemoryDB;
        this.azquoMemoryDBIndex = azquoMemoryDB.getIndex();
        /*
        if (databaseAccessToken.getWritePermissions() != null && !databaseAccessToken.getWritePermissions().isEmpty()) {
            writePermissions = NameQueryParser.decodeString(this, databaseAccessToken.getWritePermissions(), languages);
            addExtraPermissionIfRequired(writePermissions);
        } else {
            writePermissions = new ArrayList<>();
        }
        if (databaseAccessToken.getReadPermissions() != null && !databaseAccessToken.getReadPermissions().isEmpty()) {
            readPermissions = NameQueryParser.decodeString(this, databaseAccessToken.getReadPermissions(), languages);
            addExtraPermissionIfRequired(readPermissions);
        } else {
            readPermissions = new ArrayList<>();
        }
        */
        this.userLog = userLog;

        this.languages = databaseAccessToken.getLanguages();
    }

    // todo, clean this up when sessions are expired, maybe a last accessed time?
    private static final Map<String, StringBuffer> sessionLogs = new ConcurrentHashMap<>();

    // after some thinking trim this down to the basics. Would have just been a DB name for that server but need permissions too.
    // may cache in future to save DB/Permission lookups. Depends on how consolidated client/server calls can be made . . .
    public static AzquoMemoryDBConnection getConnectionFromAccessToken(DatabaseAccessToken databaseAccessToken) throws Exception {
        // todo - address opendb count (do we care?) and exceptions
        // todo - also - keep a map of connections? Expiry could be an issue . . .
        StringBuffer sessionLog = sessionLogs.computeIfAbsent(databaseAccessToken.getUserSessionId(), t -> new StringBuffer()); // computeIfAbsent is such a wonderful thread safe call
        AzquoMemoryDB memoryDB = AzquoMemoryDB.getAzquoMemoryDB(databaseAccessToken.getPersistenceName(), sessionLog);
        // we can't do the lookup for permissions out here as it requires the connection, hence pass things through
        return new AzquoMemoryDBConnection(memoryDB, databaseAccessToken, databaseAccessToken.getLanguages(), sessionLog);
    }

    public static String getSessionLog(DatabaseAccessToken databaseAccessToken) throws Exception {
        StringBuffer log = sessionLogs.get(databaseAccessToken.getUserSessionId());
        if (log != null) {
            return log.toString();
        }
        return "";
    }

    private void addExtraPermissionIfRequired(List<Set<Name>> permissions) {
        /* assume that top names which don't contain any of the selection criteria are ok - easiest way to do this is probably to collect the top names that do
        and remove them from top names then add that result as the last set if there's anything left */

        Set<Name> topNamesWithPermissions = new HashSet<>();
        for (Set<Name> listNames : permissions) {
            for (Name check : listNames) {
                topNamesWithPermissions.addAll(check.findTopParents());
            }
        }
        final Set<Name> topNamesToAdd = new HashSet<>(NameService.findTopNames(this, Constants.DEFAULT_DISPLAY_NAME));
        topNamesToAdd.removeAll(topNamesWithPermissions);
        if (!topNamesToAdd.isEmpty()) { // there are top names which have nothing to do with the permissions, add them as an ok set
            permissions.add(topNamesToAdd); // wrap it in a set
        }
    }

    public AzquoMemoryDB getAzquoMemoryDB() {
        return azquoMemoryDB;
    }

    public AzquoMemoryDBIndex getAzquoMemoryDBIndex() {
        return azquoMemoryDBIndex;
    }

    public Provenance getProvenance() {
        if (provenance == null) {
            try {
                provenance = new Provenance(getAzquoMemoryDB(), "unknown provenance", "", "", "-");
            } catch (Exception ignored) {
            }
        }
        return provenance;
    }

    public void setProvenance(final String user, final String method, String name, final String context) throws Exception {
        Provenance latest = azquoMemoryDB.getMostRecentProvenance();
        // not sure how latest and method cen get set as null but best to be careful with it
        if (latest != null && latest.getUser().equals(user)) {
            //check to remove a timestamp
            int xlsPos = name.indexOf(".xls");
            if (xlsPos > 0){
                int underscorePos = name.substring(0,xlsPos).lastIndexOf("_");
                if (xlsPos - underscorePos > 14){
                    boolean istimestamp = true;
                    for (int i=underscorePos + 1;i<xlsPos;i++){
                        if (name.charAt(i) < '0' || name.charAt(i) > '9'){
                            istimestamp = false;
                            break;
                        }
                    }
                    if (istimestamp){
                        name = name.substring(0,underscorePos) + name.substring(xlsPos);
                    }
                }
            }
            if (latest.getMethod() != null && latest.getMethod().equals(method) && latest.getName() != null && latest.getName().equals(name) &&
                    latest.getContext() != null && latest.getContext().equals(context) && latest.getTimeStamp().plusSeconds(30).isAfter(LocalDateTime.now())) {
                this.provenance = latest;
                return;
            }
        }
        this.provenance = new Provenance(getAzquoMemoryDB(), user, method, name, context);
    }

    public void setProvenance(final Provenance p) throws Exception {
        this.provenance = p;
    }

    public long getDBLastModifiedTimeStamp() {
        return azquoMemoryDB.getLastModifiedTimeStamp();
    }

    // tellingly never used. Might be if the connections were put in a map

    /*    public void setNewProvenance(String provenanceMethod, String provenanceName) {
            setNewProvenance(provenanceMethod, provenanceName, "","");
        }

        public void setNewProvenance(String provenanceMethod, String provenanceName, String context, String user) {
            try {
                provenance = new Provenance(getAzquoMemoryDB(), user, new Date(), provenanceMethod, provenanceName, context);
            } catch (Exception e) {
                logger.error("can't set a new provenance", e);
            }
        }
    */
    /*
    // todo : the sets could still be modified
    public List<Set<Name>> getReadPermissions() {
        return Collections.unmodifiableList(this.readPermissions);
    }

    public List<Set<Name>> getWritePermissions() {
        return Collections.unmodifiableList(this.writePermissions);
    }

    */
    public void persist() {
        azquoMemoryDB.persistToDataStore();
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

    public List<String>getLanguages(){ return languages;}

    public void addToUserLogNoException(String toAdd, boolean newline) {
        if (newline) {
            System.out.println(toAdd);
            userLog.append(toAdd).append("\n");
        } else {
            System.out.print(toAdd);
            userLog.append(toAdd);
        }
    }
}