package com.azquo.memorydb.dao;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.HashMap;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 08/01/14.
 * for creating and otherwise manipulating the MySLQ databases that are used to persist the memory databases
 */
public class MySQLDatabaseManager {

    public static void createNewDatabase(String databaseName) {

        // we assume the database name is safe, should we???
        databaseName = databaseName.replace("`", "oh no you don't");

        // ok balls to trying to load the file, let's just have the strings here
        JdbcTemplateUtils.update("create database `" + databaseName + "`;", new HashMap<>()); // check for "database exists" in message?
        // provenance using the old model
        JdbcTemplateUtils.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`provenance` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` longtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1  ;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        NameDAO.createFastTableIfItDoesntExist(databaseName);
        ValueDAO.createFastTableIfItDoesntExist(databaseName);
        ValueDAO.createValueHistoryTableIfItDoesntExist(databaseName);
    }

    public static void emptyDatabase(String databaseName) {
        databaseName = databaseName.replace("`", "oh no you don't");
        JdbcTemplateUtils.update("truncate `" + databaseName + "`.provenance", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        JdbcTemplateUtils.update("truncate `" + databaseName + "`." + NameDAO.FASTNAME, JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        JdbcTemplateUtils.update("truncate `" + databaseName + "`." + ValueDAO.FASTVALUE, JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        JdbcTemplateUtils.update("truncate `" + databaseName + "`." + ValueDAO.VALUEHISTORY, JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

    public static void copyDatabase(String from, String to) {
        JdbcTemplateUtils.update("insert into `" + to + "`.provenance select * from `" + from + "`.provenance", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        JdbcTemplateUtils.update("insert into `" + to + "`.fast_name select * from `" + from + "`.fast_name", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        JdbcTemplateUtils.update("insert into `" + to + "`.fast_value select * from `" + from + "`.fast_value", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        JdbcTemplateUtils.update("insert into `" + to + "`.value_history select * from `" + from + "`.value_history", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

    public static void dropDatabase(String databaseName) {
        // we assume the database name is safe, should we???
        databaseName = databaseName.replace("`", "oh no you don't");
        JdbcTemplateUtils.update("drop database `" + databaseName + "`", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

    public static boolean databaseWithNameExists(String checkName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("NAME", checkName);
        return JdbcTemplateUtils.queryCount("SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = :NAME", namedParams) > 0;
    }
}