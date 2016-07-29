package com.azquo.memorydb.core;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Oh-kay. While one can spin up a memory db from spring this is probably not the way to go, this will be the object that
 * reads the entries in the database table and spins up the memory databases according to that - it will need support for different servers.
 *
 *
 */
public final class MemoryDBManager {

    private static final ConcurrentHashMap<String, AzquoMemoryDB> memoryDatabaseMap = new ConcurrentHashMap<>(); // by data store name. Will be unique

    public static AzquoMemoryDB getAzquoMemoryDB(String persistenceName, StringBuffer sessionLog) throws Exception {
        AzquoMemoryDB loaded;
        if (persistenceName.equals("temp")) {
            loaded = new AzquoMemoryDB(persistenceName, sessionLog);
            return loaded;
        }
        // should be fine. Notably allows concurrent loading of databases. Two big ones might be a prob but we don't want to jam up loading of small ones while a big one loads.
        return memoryDatabaseMap.computeIfAbsent(persistenceName, t-> new AzquoMemoryDB(persistenceName, sessionLog));

        // todo, add back in client side?
/*        final OpenDatabase openDatabase = new OpenDatabase(0, database.getId(), new Date(), new GregorianCalendar(1900, 0, 0).getTime());// should start to get away from date
        openDatabaseDAO.store(openDatabase);*/

    }

    // worth being aware that if the db is still referenced somewhere then the garbage collector won't chuck it (which is what we want)

    public static void removeDBFromMap(String persistenceName) throws Exception {
        memoryDatabaseMap.remove(persistenceName);
    }

    public static boolean isDBLoaded(String persistenceName) throws Exception {
        return memoryDatabaseMap.containsKey(persistenceName);
    }
}