package com.azquo.memorydb.dao;

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
        jdbcTemplate.update("create database `" + databaseName + "`;", new HashMap<>()); // check for "database exists" in message?
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`name` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` longtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1 ;", StandardDAO.EMPTY_PARAMETERS_MAP);
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`value` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` longtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1 ", StandardDAO.EMPTY_PARAMETERS_MAP);
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`provenance` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `json` longtext NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE = utf8mb4_unicode_ci AUTO_INCREMENT=1  ;", StandardDAO.EMPTY_PARAMETERS_MAP);
    }

    public void emptyDatabase(String databaseName) throws IOException {
        databaseName = databaseName.replace("`", "oh no you don't");
        jdbcTemplate.update("truncate `" + databaseName + "`.name", StandardDAO.EMPTY_PARAMETERS_MAP);
        jdbcTemplate.update("truncate `" + databaseName + "`.value", StandardDAO.EMPTY_PARAMETERS_MAP);
        jdbcTemplate.update("truncate `" + databaseName + "`.provenance", StandardDAO.EMPTY_PARAMETERS_MAP);
    }

    public void dropDatabase(String databaseName) throws IOException {
        // we assume the database name is safe, should we???
        databaseName = databaseName.replace("`", "oh no you don't");
        jdbcTemplate.update("drop database `" + databaseName + "`", StandardDAO.EMPTY_PARAMETERS_MAP);
    }

}
