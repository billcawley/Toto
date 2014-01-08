package com.azquo.toto.admindao;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
                "  `provenance_id` int(11) NOT NULL,\n" +
                "  `additive` tinyint(1) NOT NULL DEFAULT '1',\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;", new HashMap<String, Object>());
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`name_attribute` (\n" +
                "  `name_id` int(11) NOT NULL,\n" +
                "  `attribute_name` varchar(40) COLLATE utf8_unicode_ci NOT NULL,\n" +
                "  `attribute_value` varchar(255) COLLATE utf8_unicode_ci NOT NULL\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;", new HashMap<String, Object>());
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`name_set_definition` (\n" +
                "  `parent_id` int(11) NOT NULL,\n" +
                "  `child_id` int(11) NOT NULL,\n" +
                "  `position` smallint(5) NOT NULL DEFAULT '0',\n" +
                "  PRIMARY KEY (`parent_id`,`child_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;", new HashMap<String, Object>());
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`peer_set_definition` (\n" +
                "  `name_id` int(11) NOT NULL,\n" +
                "  `peer_id` int(11) NOT NULL,\n" +
                "  `position` smallint(5) NOT NULL DEFAULT '0',\n" +
                "  `additive` tinyint(1) NOT NULL DEFAULT '1',\n" +
                "  PRIMARY KEY (`name_id`,`peer_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;", new HashMap<String, Object>());
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`provenance` (\n" +
                "  `id` int(11) NOT NULL,\n" +
                "  `user` varchar(255) COLLATE utf8_unicode_ci NOT NULL,\n" +
                "  `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                "  `method` varchar(40) COLLATE utf8_unicode_ci NOT NULL,\n" +
                "  `name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,\n" +
                "  `row_headings` text COLLATE utf8_unicode_ci,\n" +
                "  `column_headings` text COLLATE utf8_unicode_ci,\n" +
                "  `context` text COLLATE utf8_unicode_ci,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;", new HashMap<String, Object>());
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`value` (\n" +
                "  `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                "  `provenance_id` int(11) NOT NULL,\n" +
                "  `double` double DEFAULT NULL,\n" +
                "  `text` text COLLATE utf8_unicode_ci,\n" +
                "  `deleted_info` text COLLATE utf8_unicode_ci,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci AUTO_INCREMENT=1 ;", new HashMap<String, Object>());
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`value_name` (\n" +
                "  `value_id` int(11) NOT NULL,\n" +
                "  `name_id` int(11) NOT NULL,\n" +
                "  PRIMARY KEY (`name_id`,`value_id`),\n" +
                "  KEY `value_id` (`value_id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;", new HashMap<String, Object>());
    }

}
