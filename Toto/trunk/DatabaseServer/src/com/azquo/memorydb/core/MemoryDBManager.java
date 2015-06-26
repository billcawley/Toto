package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.StandardDAO;

import java.util.HashMap;

/**
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the memory databases according to that
 */
public final class MemoryDBManager {

    private final HashMap<String, AzquoMemoryDB> memoryDatabaseMap;

    private StandardDAO standardDAO;

    public MemoryDBManager(StandardDAO standardDAO) throws Exception {
        this.standardDAO = standardDAO;
        memoryDatabaseMap = new HashMap<String, AzquoMemoryDB>(); // by mysql name. Will be unique.
    }

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
    }

    public synchronized void addNewToDBMap(String mysqlName) throws Exception {
        if (memoryDatabaseMap.get(mysqlName) != null) {
            throw new Exception("cannot create new memory database one attached to that mysql database already exists");
        }
        AzquoMemoryDB azquoMemoryDB = new AzquoMemoryDB(mysqlName, standardDAO);
        memoryDatabaseMap.put(mysqlName, azquoMemoryDB);
    }

    public boolean isDBLoaded(String mysqlName) throws Exception {
        return memoryDatabaseMap.get(mysqlName) != null;
    }

}
