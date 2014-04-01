package com.azquo.memorydb;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.OpenDatabaseDAO;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.OpenDatabase;
import com.azquo.memorydbdao.StandardDAO;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the memory databases according to that
 */
public final class MemoryDBManager {

    @Autowired
    OpenDatabaseDAO openDatabaseDAO;

    private final HashMap<String, AzquoMemoryDB> memoryDatabaseMap;
    private DatabaseDAO databaseDAO;
    private StandardDAO standardDAO;


    public MemoryDBManager(DatabaseDAO databaseDAO, StandardDAO standardDAO) throws Exception {
        this.databaseDAO = databaseDAO;
        this.standardDAO = standardDAO;
        memoryDatabaseMap = new HashMap<String, AzquoMemoryDB>(); // by mysql name. Will be unique.
        loadMemoryDBMap();
    }

    public synchronized AzquoMemoryDB getAzquoMemoryDB(Database database) throws Exception {
        AzquoMemoryDB loaded = memoryDatabaseMap.get(database.getMySQLName());
        if (loaded != null){
            return loaded;
        }
        loaded = new AzquoMemoryDB(database, standardDAO);
        memoryDatabaseMap.put(database.getMySQLName(), loaded);
        final OpenDatabase openDatabase = new OpenDatabase(0, database.getId(), new Date(), new Date(0,0,0));
        openDatabaseDAO.store(openDatabase);
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


    public synchronized void removeDatabase(String dbName){
        memoryDatabaseMap.remove(dbName);

    }


    public synchronized void addNewToDBMap(Database database) throws Exception {
        AzquoMemoryDB azquoMemoryDB = new AzquoMemoryDB(database, standardDAO);
        memoryDatabaseMap.put(database.getMySQLName(), azquoMemoryDB);
    }

}
