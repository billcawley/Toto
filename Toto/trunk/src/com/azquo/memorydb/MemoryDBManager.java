package com.azquo.memorydb;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.adminentities.Database;
import com.azquo.memorydbdao.StandardDAO;

import java.util.HashMap;
import java.util.List;

/**
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the memory databases according to that
 */
public final class MemoryDBManager {

    private final HashMap<String, AzquoMemoryDB> memoryDatabaseMap;
    private DatabaseDAO databaseDAO;
    private StandardDAO standardDAO;


    public MemoryDBManager(DatabaseDAO databaseDAO, StandardDAO standardDAO) throws Exception {
        this.databaseDAO = databaseDAO;
        this.standardDAO = standardDAO;
        memoryDatabaseMap = new HashMap<String, AzquoMemoryDB>(); // by mysql name. Will be unique.
        loadMemoryDBMap();
    }

    public synchronized AzquoMemoryDB getAzquoMemoryDB(Database database) {
        return memoryDatabaseMap.get(database.getMySQLName());
    }

    public synchronized void loadMemoryDBMap() throws Exception {
        memoryDatabaseMap.clear();
        List<Database> databases = databaseDAO.findAll();
        for (Database database : databases) {
            AzquoMemoryDB azquoMemoryDB = new AzquoMemoryDB(database, standardDAO);
            memoryDatabaseMap.put(database.getMySQLName(), azquoMemoryDB);
        }
    }

    public synchronized void addNewToDBMap(Database database) throws Exception {
        AzquoMemoryDB azquoMemoryDB = new AzquoMemoryDB(database, standardDAO);
        memoryDatabaseMap.put(database.getMySQLName(), azquoMemoryDB);
    }

}
