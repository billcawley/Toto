package com.azquo.service;

import com.azquo.admindao.DatabaseDAO;
import com.azquo.admindao.MySQLDatabaseManager;
import com.azquo.adminentities.Business;
import com.azquo.adminentities.Database;
import com.azquo.adminentities.User;
import com.azquo.memorydb.MemoryDBManager;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 22/10/14.
 *
 * TODO - concurrent maps???
 *
 */
public class AppDBConnectionMap {

    private final Map<String, AzquoMemoryDBConnection> connectionMap;
    private final Map<String, Database> dbByNameMap;
    private final MySQLDatabaseManager mySQLDatabaseManager;
    private final MemoryDBManager memoryDBManager;
    private final AdminService adminService;
    private final Business business;
    private final DatabaseDAO databaseDAO;

    public AppDBConnectionMap(AdminService adminService, MemoryDBManager memoryDBManager, MySQLDatabaseManager mySQLDatabaseManager, DatabaseDAO databaseDAO, int businessId) {
        this.databaseDAO = databaseDAO;
        this.mySQLDatabaseManager = mySQLDatabaseManager;
        this.memoryDBManager = memoryDBManager;
        this.adminService = adminService;
        connectionMap = new HashMap<String, AzquoMemoryDBConnection>();
        dbByNameMap = new HashMap<String, Database>();
        business = adminService.getBusinessById(businessId);
        if (business != null){
            List<Database> databasesForBusiness = adminService.getDatabaseListForBusiness(business);
            for (Database database : databasesForBusiness){
                    dbByNameMap.put(database.getMySQLName(), database);
            }
        }
    }
    // was a simple get but we're going to lazy load
    public AzquoMemoryDBConnection getConnection(String mysqlName){
        mysqlName = mysqlName.toLowerCase();
        AzquoMemoryDBConnection azquoMemoryDBConnection = connectionMap.get(mysqlName);
        if (azquoMemoryDBConnection != null){
            return azquoMemoryDBConnection;
        } else {
            if (dbByNameMap.get(mysqlName) != null){
                try {
                    connectionMap.put(dbByNameMap.get(mysqlName).getMySQLName(),
                            new AzquoMemoryDBConnection(memoryDBManager.getAzquoMemoryDB(dbByNameMap.get(mysqlName)), new User(0, null, null, business.getId(), "", "connection pool", "", "", "" )));
                    return connectionMap.get(mysqlName);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public void deleteDatabase(String mysqlName){
        Database db = dbByNameMap.get(mysqlName);
        if (db != null){
            try {
                adminService.dropDatabase(db.getMySQLName());
                databaseDAO.removeById(db);
                dbByNameMap.remove(mysqlName);
                connectionMap.remove(mysqlName);
                memoryDBManager.removeDatabase(dbByNameMap.get(mysqlName));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void newDatabase(String databaseName) throws Exception{
        Database existing = databaseDAO.findForName(business.getId(),databaseName);
        if (existing != null){
            throw new Exception("that database " + databaseName + "already exists");
        }
        // todo - factor this it's in two places at the moment
        final String mysqlName = (business.getBusinessName() + "     ").substring(0, 5).trim().replaceAll("[^A-Za-z0-9_]", "") + "_" + databaseName.replaceAll("[^A-Za-z0-9_]", "").toLowerCase();
        final Database database = new Database(0, new Date(), new Date(130, 1, 1), business.getId(), databaseName, mysqlName, 0, 0);
        // todo here and elsewhere, stop mysql dbs overwriting each other
            mySQLDatabaseManager.createNewDatabase(mysqlName);
            databaseDAO.store(database);
            memoryDBManager.addNewToDBMap(database);
            dbByNameMap.put(database.getMySQLName(), database);
            connectionMap.put(database.getMySQLName(),
            new AzquoMemoryDBConnection(memoryDBManager.getAzquoMemoryDB(database), new User(0, null, null, business.getId(), "", "connection pool", "", "", "" )));
    }

}
