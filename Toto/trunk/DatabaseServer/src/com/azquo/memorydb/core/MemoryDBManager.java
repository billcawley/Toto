package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.StandardDAO;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;

/**
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the memory databases according to that
 */
public final class MemoryDBManager {

//    List<AppEntityService> appServices = null;

    private final HashMap<String, AzquoMemoryDB> memoryDatabaseMap;

    private StandardDAO standardDAO;

    public MemoryDBManager(StandardDAO standardDAO) throws Exception {
        this.standardDAO = standardDAO;
        memoryDatabaseMap = new HashMap<String, AzquoMemoryDB>(); // by mysql name. Will be unique.
        loadMemoryDBMap();
    }

    // similar to above, todo : zap above if we can

    public synchronized AzquoMemoryDB getAzquoMemoryDB(String mySqlName) throws Exception {
        AzquoMemoryDB loaded;
        if (mySqlName.equals("temp")) {
            loaded = new AzquoMemoryDB(mySqlName, standardDAO);
            return loaded;
        }
        loaded = memoryDatabaseMap.get(mySqlName);
        if (loaded != null) {
            return loaded;
        }
        loaded = new AzquoMemoryDB(mySqlName, standardDAO);
        memoryDatabaseMap.put(mySqlName, loaded);
        // todo, add back in client side
/*        final OpenDatabase openDatabase = new OpenDatabase(0, database.getId(), new Date(), new GregorianCalendar(1900, 0, 0).getTime());// should start to get away from date
        openDatabaseDAO.store(openDatabase);*/
        return loaded;
    }

    public synchronized void loadMemoryDBMap() throws Exception {
        memoryDatabaseMap.clear();
        /* 01/04/2014  no preloading of databases
        List<Database> databases = databaseDAO.findAll();
        for (Database database : databases) {
            AzquoMemoryDB azquoMemoryDB = new AzquoMemoryDB(database, standardDAO);
            memoryDatabaseMap.put(database.getMySQLName(), azquoMemoryDB);
        }
        */
    }

    // todo : what if references to the memory db held in memory still??
    public synchronized void removeDatabase(String mysqlName) {
        memoryDatabaseMap.remove(mysqlName);

    }

    public synchronized void addNewToDBMap(String mysqlName) throws Exception {
        if (memoryDatabaseMap.get(mysqlName) != null) {
            throw new Exception("cannot create new memory database one attached to that mysql database already exists");
        }
        AzquoMemoryDB azquoMemoryDB = new AzquoMemoryDB(mysqlName, standardDAO);
        memoryDatabaseMap.put(mysqlName, azquoMemoryDB);
    }

}
