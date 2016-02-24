package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.HBaseDAO;
import com.azquo.memorydb.dao.NameDAO;
import com.azquo.memorydb.dao.JsonRecordDAO;
import com.azquo.memorydb.dao.ValueDAO;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the memory databases according to that - it will need support for different servers.
 *
 * I would like to use concurrent hashmap but the putifabsent pattern does not lend itself as well as it might - the catch being the long instantiation time of the memory db as it loads
 * park for the mo unless it's a performance issue, since this is the only place that instantiates and hence loads the db could move the managing of that to here.
 *
 */
public final class MemoryDBManager {

    private final ConcurrentHashMap<String, AzquoMemoryDB> memoryDatabaseMap;

    private final JsonRecordDAO jsonRecordDAO;

    private final NameDAO nameDAO;

    private final ValueDAO valueDAO;

    private final HBaseDAO hBaseDAO;

    public MemoryDBManager(JsonRecordDAO jsonRecordDAO, NameDAO nameDAO, ValueDAO valueDAO, HBaseDAO hBaseDAO) throws Exception {
        this.jsonRecordDAO = jsonRecordDAO;
        this.nameDAO = nameDAO;
        this.valueDAO = valueDAO;
        this.hBaseDAO = hBaseDAO;
        memoryDatabaseMap = new ConcurrentHashMap<>(); // by data store name. Will be unique.
    }

    public AzquoMemoryDB getAzquoMemoryDB(String persistenceName, StringBuffer sessionLog) throws Exception {
        AzquoMemoryDB loaded;
        if (persistenceName.equals("temp")) {
            loaded = new AzquoMemoryDB(persistenceName, jsonRecordDAO, nameDAO,valueDAO, hBaseDAO, sessionLog);
            return loaded;
        }
        // should be fine. Notably allows
        return memoryDatabaseMap.computeIfAbsent(persistenceName, t-> new AzquoMemoryDB(persistenceName, jsonRecordDAO, nameDAO,valueDAO, hBaseDAO, sessionLog));

        // todo, add back in client side?
/*        final OpenDatabase openDatabase = new OpenDatabase(0, database.getId(), new Date(), new GregorianCalendar(1900, 0, 0).getTime());// should start to get away from date
        openDatabaseDAO.store(openDatabase);*/

    }

    // worth being aware that if the db is still referenced somewhere then the garbage collector won't chuck it (which is what we want)

    public void removeDBfromMap(String persistenceName) throws Exception {
        memoryDatabaseMap.remove(persistenceName);
    }

    public boolean isDBLoaded(String persistenceName) throws Exception {
        return memoryDatabaseMap.containsKey(persistenceName);
    }

}