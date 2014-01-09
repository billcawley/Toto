package com.azquo.toto.memorydb;

import com.azquo.toto.admindao.DatabaseDAO;
import com.azquo.toto.adminentities.Database;
import com.azquo.toto.memorydbdao.NameDAO;
import com.azquo.toto.memorydbdao.ProvenanceDAO;
import com.azquo.toto.memorydbdao.ValueDAO;

import java.util.HashMap;
import java.util.List;

/**
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the databases according to that
 */
public final class MemoryDBManager {

    private final HashMap<String, TotoMemoryDB> memoryDatabaseMap;

    private final DatabaseDAO databaseDAO;


    private final ValueDAO valueDAO;
    private final NameDAO nameDAO;
    private final ProvenanceDAO provenanceDAO;


    public MemoryDBManager(DatabaseDAO databaseDAO, ValueDAO valueDAO, NameDAO nameDAO, ProvenanceDAO provenanceDAO) throws Exception {
        this.databaseDAO = databaseDAO;
        this.valueDAO = valueDAO;
        this.nameDAO = nameDAO;
        this.provenanceDAO = provenanceDAO;
        memoryDatabaseMap = new HashMap<String, TotoMemoryDB>(); // by ID I think
        List<Database> databases = databaseDAO.findAll();
        for (Database database : databases){
            TotoMemoryDB totoMemoryDB = new TotoMemoryDB(database.getMySQLName(), nameDAO, valueDAO, provenanceDAO);
            memoryDatabaseMap.put(database.getMySQLName(), totoMemoryDB);
        }
    }

    public TotoMemoryDB getTotoMemoryDB(Database database){
        return memoryDatabaseMap.get(database.getMySQLName());
    }

}
