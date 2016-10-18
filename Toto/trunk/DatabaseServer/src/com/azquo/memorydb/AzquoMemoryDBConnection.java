package com.azquo.memorydb;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.AzquoMemoryDBIndex;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
//import org.apache.log4j.Logger;

import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 21/10/14.
 * I need a simple superclass to chuck around outside this package
 * but which will stop code outside this package getting to the DB object itself
 * not necessary functionally, trying to enforce code structure
 * <p/>
 *
 * has been stripped right down to the db and permissions I think that makes sense
 *
 * Adding in a user session as this should make it easier to log stuff back to the user in the browser
 *
 */
public class AzquoMemoryDBConnection {

    //private static final Logger logger = Logger.getLogger(AzquoMemoryDBConnection.class);

    private final AzquoMemoryDB azquoMemoryDB;

    private final AzquoMemoryDBIndex azquoMemoryDBIndex; // just shorthand - always set to the memoryDB's index

    private final List<Set<Name>> readPermissions;

    private final List<Set<Name>> writePermissions;

    private final StringBuffer userLog; // threadsafe, it probably needs to be

    // A bit involved but it makes this object immutable, think that's worth it - note
    // new logic here : we'll say that top test that have no permissions are added as allowed - if someone has added a department for example they should still have access to all dates

    public AzquoMemoryDBConnection(AzquoMemoryDB azquoMemoryDB, DatabaseAccessToken databaseAccessToken, List<String> languages, StringBuffer userLog)  throws Exception {
        this.azquoMemoryDB = azquoMemoryDB;
        this.azquoMemoryDBIndex = azquoMemoryDB.getIndex();
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
        this.userLog = userLog;
    }

    private void addExtraPermissionIfRequired(List<Set<Name>> permissions){
        /* assume that top names which don't contain any of the selection criteria are ok - easiest way to do this is probably to collect the top names that do
        and remove them from top names then add that result as the last set if there's anything left */

        Set<Name> topNamesWithPermissions = new HashSet<>();
        for (Set<Name> listNames : permissions) {
            for (Name check : listNames){
                topNamesWithPermissions.addAll(check.findTopParents());
            }
        }
        final Set<Name> topNamesToAdd = new HashSet<>(NameService.findTopNames(this, Constants.DEFAULT_DISPLAY_NAME));
        topNamesToAdd.removeAll(topNamesWithPermissions);
        if (!topNamesToAdd.isEmpty()){ // there are top names which have nothing to do with the permissions, add them as an ok set
            permissions.add(topNamesToAdd); // wrap it in a set
        }
    }

    public AzquoMemoryDB getAzquoMemoryDB() {
        return azquoMemoryDB;
    }

    public AzquoMemoryDBIndex getAzquoMemoryDBIndex() {
        return azquoMemoryDBIndex;
    }

    protected Provenance provenance = null;

    public Provenance getProvenance() {
        if (provenance == null) {
            try {
                provenance = new Provenance(getAzquoMemoryDB(), "unknown provenance", new Date(), "", "", "-");
            } catch (Exception ignored) {
            }
        }
        return provenance;
    }

    public void setProvenance(final String user,final String method, final String name, final String context)throws Exception{
        if (provenance != null && provenance.getUser().equals(user)){
            long elapsed = new Date().getTime() - provenance.getTimeStamp().getTime();
            if (provenance.getMethod().equals(method) && provenance.getContext().equals(context) && elapsed < 600000) {// ten minutes
                return;
            }
        }
        this.provenance = new Provenance(getAzquoMemoryDB(),user,new Date(),method, name, context);
    }

    public long getDBLastModifiedTimeStamp(){
        return azquoMemoryDB.getLastModifiedTimeStamp();
    }

    // tellingly never used.

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
    // todo : the sets could still be modified
    public List<Set<Name>> getReadPermissions() {
        return Collections.unmodifiableList(this.readPermissions);
    }

    public List<Set<Name>> getWritePermissions() {
        return Collections.unmodifiableList(this.writePermissions);
    }

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

    public void addToUserLog(String toAdd) throws Exception{
        addToUserLog(toAdd, true);
    }

    public void addToUserLog(String toAdd, boolean newline) throws Exception{
        boolean exception = userLog.toString().equals(STOP);
        addToUserLogNoException(toAdd, newline);
        if (exception){
            userLog.append("INTERRUPTED BY USER, THROWING EXCEPTION\n");
            throw new Exception("Execution interrupted by user");
        }
    }

    public void addToUserLogNoException(String toAdd, boolean newline) {
        if (newline){
            System.out.println(toAdd);
            userLog.append(toAdd).append("\n");
        } else {
            System.out.print(toAdd);
            userLog.append(toAdd);
        }
    }
}