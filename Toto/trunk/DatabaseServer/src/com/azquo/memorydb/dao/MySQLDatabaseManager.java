package com.azquo.memorydb.dao;

import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.HashMap;

/**
 * Created by cawley on 08/01/14.
 * for creating and otherwise manipulating the databases that are used to persist the memory databases
 */
public class MySQLDatabaseManager {
    @Autowired
    protected JdbcTemplateUtils jdbcTemplateUtils;
    @Autowired
    protected NameDAO nameDAO;
    @Autowired
    protected ValueDAO valueDAO;

    public void createNewDatabase(String databaseName) throws IOException {

        // we assume the database name is safe, should we???
        databaseName = databaseName.replace("`", "oh no you don't");

        // ok balls to trying to load the file, let's just have the strings here
        jdbcTemplateUtils.update("create database `" + databaseName + "`;", new HashMap<>()); // check for "database exists" in message?
        jdbcTemplateUtils.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`name` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` longtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1 ;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        jdbcTemplateUtils.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`value` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` longtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1 ", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        jdbcTemplateUtils.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`provenance` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` longtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1  ;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        nameDAO.createFastTableIfItDoesntExist(databaseName);
        valueDAO.createFastTableIfItDoesntExist(databaseName);
    }

    public void emptyDatabase(String databaseName) throws IOException {
        databaseName = databaseName.replace("`", "oh no you don't");
        jdbcTemplateUtils.update("truncate `" + databaseName + "`.name", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        jdbcTemplateUtils.update("truncate `" + databaseName + "`.value", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        jdbcTemplateUtils.update("truncate `" + databaseName + "`.provenance", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        nameDAO.clearTable(databaseName);
        valueDAO.clearTable(databaseName);
    }

    public void dropDatabase(String databaseName) throws IOException {
        // we assume the database name is safe, should we???
        databaseName = databaseName.replace("`", "oh no you don't");
        jdbcTemplateUtils.update("drop database `" + databaseName + "`", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

}
