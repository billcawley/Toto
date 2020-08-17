package com.azquo.memorydb.service;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.dao.MySQLDatabaseManager;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * Basic DB side admin functions.
 *
 * <p>
 */
public class DSAdminService {

    public static void emptyDatabase(String persistenceName) {
        if (AzquoMemoryDB.isDBLoaded(persistenceName)) { // then persist via the loaded db, synchronizes thus solving the "delete or empty while persisting" problem
            final AzquoMemoryDB azquoMemoryDB = AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null);
            azquoMemoryDB.synchronizedClear();
            AzquoMemoryDB.removeDBFromMap(persistenceName);
        } else {
            emptyDatabaseInPersistence(persistenceName);
        }
    }

    // todo - add code to check relationships so that all parents have matching children and vice versa
    public static void checkDatabase(String persistenceName)  {
        final AzquoMemoryDB azquoMemoryDB = AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null);
        System.out.println("Database check for : " + persistenceName);
        int count = 0;
        for (Integer nameId : azquoMemoryDB.getAllNameIds()){
            Name check = azquoMemoryDB.getNameById(nameId);
            if (check == null){
                System.out.println("Null name with id : " + nameId);
            } else {
                for (Name parent : check.getParents()){
                    if (parent == null){
                        System.out.println("Name  " + check + " null parent");
                        break;
                    }
                }
                for (Name child : check.getChildren()){
                    if (child == null){
                        System.out.println("Name  " + check + " null child");
                        break;
                    }
                }
                for (Value value : check.getValues()){
                    if (value == null){
                        System.out.println("Name  " + check + " null value");
                        break;
                    }
                }
            }
            count++;
            if (count%1000 == 0){
                System.out.println("Name count " + count);
            }
        }
        count = 0;
        for (Integer valueId : azquoMemoryDB.getAllValueIds()){
            Value check = azquoMemoryDB.getValueById(valueId);
            if (check == null){
                System.out.println("Null value with id : " + valueId);
            } else {
                for (Name name : check.getNames()){
                    if (name == null){
                        System.out.println("Value " + check + " null name");
                        break;
                    }
                }
            }
            count++;
            if (count%1000 == 0){
                System.out.println("Value count " + count);
            }
        }
    }

    public static void emptyDatabaseInPersistence(String persistenceName) {
        MySQLDatabaseManager.emptyDatabase(persistenceName);
    }

    public static void dropDatabase(String persistenceName) {
        if (AzquoMemoryDB.isDBLoaded(persistenceName)) { // then persist via the loaded db, synchronizes thus solving the "delete or empty while persisting" problem
            final AzquoMemoryDB azquoMemoryDB = AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null);
            azquoMemoryDB.synchronizedDrop();
            AzquoMemoryDB.removeDBFromMap(persistenceName);
        } else {
            dropDatabaseInPersistence(persistenceName);
        }
    }

    public static void dropDatabaseInPersistence(String persistenceName) {
        MySQLDatabaseManager.dropDatabase(persistenceName);
    }

    public static void createDatabase(final String persistenceName) throws Exception {
        if (AzquoMemoryDB.isDBLoaded(persistenceName)) {
            throw new Exception("cannot create new memory database one attached to that mysql database " + persistenceName + " already exists");
        }
        MySQLDatabaseManager.createNewDatabase(persistenceName);
    }

    public static boolean databaseWithNameExists(final String checkName) {
        return MySQLDatabaseManager.databaseWithNameExists(checkName);
    }
}