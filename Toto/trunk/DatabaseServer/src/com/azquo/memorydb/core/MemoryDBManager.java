package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.NameDAO;
import com.azquo.memorydb.dao.StandardDAO;
import com.azquo.memorydb.dao.ValueDAO;
import com.azquo.spreadsheet.JSTreeService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;

/**
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the memory databases according to that - it will need support for different servers.
 *
 * I would like to use concurrent hashmap but the putifabsent pattern does not lend itself as well as it might - the catch being the long instantiation time of the memory db as it loads
 * park for the mo unless it's a performance issue, since this is the only place that instantiates and hence loads the db could move the managing of that to here.
 */
public final class MemoryDBManager {

    private final HashMap<String, AzquoMemoryDB> memoryDatabaseMap;

    private final StandardDAO standardDAO;

    private final NameDAO nameDAO;

    private final ValueDAO valueDAO;


    @Autowired
    private JSTreeService jsTreeService;

    public MemoryDBManager(StandardDAO standardDAO, NameDAO nameDAO, ValueDAO valueDAO) throws Exception {
        this.standardDAO = standardDAO;
        this.nameDAO = nameDAO;
        this.valueDAO = valueDAO;
        memoryDatabaseMap = new HashMap<>(); // by mysql name. Will be unique.
    }

    public synchronized AzquoMemoryDB getAzquoMemoryDB(String mySqlName, StringBuffer sessionLog) throws Exception {
        AzquoMemoryDB loaded;
        if (mySqlName.equals("temp")) {
            loaded = new AzquoMemoryDB(mySqlName, standardDAO, nameDAO,valueDAO, sessionLog);
            return loaded;
        }
        loaded = memoryDatabaseMap.get(mySqlName);
        if (loaded != null) {
            return loaded;
        }
        loaded = new AzquoMemoryDB(mySqlName, standardDAO, nameDAO,valueDAO, sessionLog);
        memoryDatabaseMap.put(mySqlName, loaded);
        // todo, add back in client side?
/*        final OpenDatabase openDatabase = new OpenDatabase(0, database.getId(), new Date(), new GregorianCalendar(1900, 0, 0).getTime());// should start to get away from date
        openDatabaseDAO.store(openDatabase);*/
        return loaded;
    }

    // worth being aware that if the db is still referenced somewhere then the garbage collector won't chuck it (which is what we want)

    public synchronized  void removeDBfromMap(String mysqlName) throws Exception {
        if (memoryDatabaseMap.get(mysqlName) != null){
            memoryDatabaseMap.remove(mysqlName);
        }
        // a pain, nevessary as references may be in here
        jsTreeService.unloadingDatabase(mysqlName);
    }

    public synchronized void addNewToDBMap(String mysqlName) throws Exception {
        if (memoryDatabaseMap.get(mysqlName) != null) {
            throw new Exception("cannot create new memory database one attached to that mysql database already exists");
        }
        AzquoMemoryDB azquoMemoryDB = new AzquoMemoryDB(mysqlName, standardDAO, nameDAO,valueDAO, null); // blank session log here unless we really care about telling this to the user?
        memoryDatabaseMap.put(mysqlName, azquoMemoryDB);
    }

    public boolean isDBLoaded(String mysqlName) throws Exception {
        return memoryDatabaseMap.get(mysqlName) != null;
    }

    public boolean wasDBFastLoaded(String mysqlName) throws Exception {
        AzquoMemoryDB check = memoryDatabaseMap.get(mysqlName);
        return check != null && check.getFastLoaded();
    }

    public void convertDatabase(String mysqlName) throws Exception {
        AzquoMemoryDB check = memoryDatabaseMap.get(mysqlName);
        if (check != null){
            check.saveToNewTables();
        }
    }

}
