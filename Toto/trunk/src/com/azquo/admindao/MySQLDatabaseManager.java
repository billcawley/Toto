package com.azquo.admindao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.util.HashMap;

/**
 * Created by cawley on 08/01/14.
 * for creating and otherwise manipulating the databases that are used to persist the memory databases
 */
public class MySQLDatabaseManager {
    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    public void createNewDatabase(String databaseName) throws IOException {

        // we assume the database name is safe, should we???
        databaseName = databaseName.replace("`", "oh no you don't");

        // ok balls to trying to load the file, let's just have the strings here
        jdbcTemplate.update("create database `" + databaseName + "`;", new HashMap<String, Object>()); // check for "database exists" in message?
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`name` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` mediumtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1 ;", new HashMap<String, Object>());
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`value` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` mediumtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1 ", new HashMap<String, Object>());
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`provenance` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` mediumtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1  ;", new HashMap<String, Object>());
    }

    public void dropDatabase(String databaseName) throws IOException {
        // we assume the database name is safe, should we???
        databaseName = databaseName.replace("`", "oh no you don't");
        jdbcTemplate.update("drop database `" + databaseName + "`", new HashMap<String, Object>());
    }

}
