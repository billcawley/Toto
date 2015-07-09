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
 * RIght, I'm going to chuck a few more things in her such as Provenance, Language and loose.
 * I'd rather not but right now the alternative is more messy.
 * <p/>
 * todo : address exactly what goes in here which is not a reference to the memory database
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
        return getProvenance("in spreadsheet");

    }

    public Provenance getProvenance(String where) {
        if (provenance == null) {
            try {
                provenance = new Provenance(getAzquoMemoryDB(), where, new Date(), "", "", "-");
            } catch (Exception ignored) {
            }
        }
        return provenance;
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
