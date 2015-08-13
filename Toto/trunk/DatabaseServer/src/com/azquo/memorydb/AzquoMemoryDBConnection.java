package com.azquo.memorydb;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.service.NameService;
//import org.apache.log4j.Logger;

import java.util.*;

/**
 * Created by cawley on 21/10/14.
 * I need a simple superclass to chuck around outside this package
 * but which will stop code outside this package getting to the DB object itself
 * not necessary functionally, trying to enforce code structure
 * <p/>
 *
 * has been stripped right down to the db and permission,s I think that makes sense
 *
 */
public class AzquoMemoryDBConnection {

    //private static final Logger logger = Logger.getLogger(AzquoMemoryDBConnection.class);

    private final AzquoMemoryDB azquoMemoryDB;

    private final List<Set<Name>> readPermissions;

    private final List<Set<Name>> writePermissions;

    // A bit involved but it makes this object immutable, think that's worth it - note

    public AzquoMemoryDBConnection(AzquoMemoryDB azquoMemoryDB, DatabaseAccessToken databaseAccessToken, NameService nameService, List<String> languages)  throws Exception {
        this.azquoMemoryDB = azquoMemoryDB;
        if (databaseAccessToken.getWritePermissions() != null && !databaseAccessToken.getWritePermissions().isEmpty()) {
            writePermissions = nameService.decodeString(this, databaseAccessToken.getWritePermissions(), languages);
        } else {
            writePermissions = new ArrayList<Set<Name>>();
        }
        if (databaseAccessToken.getReadPermissions() != null && !databaseAccessToken.getReadPermissions().isEmpty()) {
            readPermissions = nameService.decodeString(this, databaseAccessToken.getReadPermissions(), languages);
        } else {
            readPermissions = new ArrayList<Set<Name>>();
        }
    }

    public AzquoMemoryDB getAzquoMemoryDB() {
        return azquoMemoryDB;
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

    public void setProvenance(final String user,final String method, final String name,final String context)throws Exception{
        if (this.provenance !=null   && this.provenance.getUser().equals(user)){
            long elapsed = new Date().getTime() - this.provenance.getTimeStamp().getTime();
            if(this.provenance.getMethod().equals(method) && this.provenance.getContext().equals(context) &&elapsed < 600000) {// ten minutes
                return;
            }

        }
        this.provenance = new Provenance(getAzquoMemoryDB(),user,new Date(),method, name, context);
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
        azquoMemoryDB.saveDataToMySQL();
    }

    public void persistInBackground() {
        (new Thread(new PersistenceRunner())).start();
    }

    public class PersistenceRunner implements Runnable {
        @Override
        public void run() {
            azquoMemoryDB.saveDataToMySQL();
        }
    }
}
