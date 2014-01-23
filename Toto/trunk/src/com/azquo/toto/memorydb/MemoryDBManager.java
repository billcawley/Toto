package com.azquo.toto.memorydb;

import com.azquo.toto.admindao.BusinessDAO;
import com.azquo.toto.admindao.DatabaseDAO;
import com.azquo.toto.adminentities.Database;
import com.azquo.toto.memorydbdao.StandardDAO;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;

/**
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the memory databases according to that
 */
public final class MemoryDBManager {

    private final HashMap<String, TotoMemoryDB> memoryDatabaseMap;
    private DatabaseDAO databaseDAO;
    private StandardDAO standardDAO;


    public MemoryDBManager(DatabaseDAO databaseDAO, StandardDAO standardDAO) throws Exception {
        this.databaseDAO = databaseDAO;
        this.standardDAO = standardDAO;
        memoryDatabaseMap = new HashMap<String, TotoMemoryDB>(); // by ID I think
        updateMemoryDBMap();
    }

    public synchronized TotoMemoryDB getTotoMemoryDB(Database database){
        return memoryDatabaseMap.get(database.getMySQLName());
    }

    public synchronized void updateMemoryDBMap() throws Exception {
        memoryDatabaseMap.clear();
        List<Database> databases = databaseDAO.findAll();
        for (Database database : databases){
            TotoMemoryDB totoMemoryDB = new TotoMemoryDB(database, standardDAO);
            memoryDatabaseMap.put(database.getMySQLName(), totoMemoryDB);
        }
    }

}
