package com.azquo.admin;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created 07/01/14 by edd
 * to factor off common bits on vanilla DAO stuff - the nature of the factoring may change a little if I go to static DAOs
 * <p>
 * Similar to JdbcTemplateUtils I'm doing a "dirty" assignment of the JdbcTemplate to a static field to avoid instances later of stateless objects
 * <p>
 * See comments in JdbcTemplateUtils, we're not using dependency injection or unit tests etc so I'm going static to simplify the code
 */
public class StandardDAO {

    private static final int SELECTLIMIT = 10000;
    public static final String ID = "id";
    public static final String MASTER_DB = "master_db";


    private static NamedParameterJdbcTemplate jdbcTemplate;

    // Copypasta from JdbcTemplateUtils (unlike there we won't be retrying on queries).
    public StandardDAO(NamedParameterJdbcTemplate jdbcTemplate) throws Exception {
        // moved from spreadsheet service as it won't be instantiated any more, just logging
        String thost = ""; // Currently just to put in the log
        try {
            thost = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            e.printStackTrace(); // Not exactly sure what it might be
        }
        System.out.println("host : " + thost);

        if (jdbcTemplate == null) {
            throw new Exception("Ack!, null data source passed to StandardDAO, Azquo report server won't function!");
        }
        /*
        Update hack - yoinked from https://dba.stackexchange.com/questions/169458/mysql-how-to-create-column-if-not-exists
         */

        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS\n" +
                "    WHERE\n" +
                "      (table_name = \"pending_upload\")\n" +
                "      AND (table_schema = \"master_db\")\n" +
                "      AND (column_name = \"team\")", new HashMap<>(), Integer.class) == 0){
            jdbcTemplate.update("ALTER TABLE `master_db`.`pending_upload` ADD `team` VARCHAR(255) NULL DEFAULT NULL ;", new HashMap<>());
        }
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS\n" +
                "    WHERE\n" +
                "      (table_name = \"user\")\n" +
                "      AND (table_schema = \"master_db\")\n" +
                "      AND (column_name = \"team\")", new HashMap<>(), Integer.class) == 0){
            jdbcTemplate.update("ALTER TABLE `master_db`.`user` ADD `team` VARCHAR(255) NULL DEFAULT NULL ;", new HashMap<>());
        }

        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `master_db`.`comment` (" +
                "`id` int(11) NOT NULL AUTO_INCREMENT" +
                ",`business_id` int(11) NOT NULL,`identifier` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL" +
                ",`team` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL" +
                ",`text` text COLLATE utf8_unicode_ci DEFAULT NULL,PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;", new HashMap<>());

        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS\n" +
                "    WHERE\n" +
                "      (table_name = \"database\")\n" +
                "      AND (table_schema = \"master_db\")\n" +
                "      AND (column_name = \"import_template_id\")", new HashMap<>(), Integer.class) == 0){
            jdbcTemplate.update("ALTER TABLE `master_db`.`database` ADD `import_template_id` int(11) NOT NULL DEFAULT '-1' ;", new HashMap<>());
        }
        // resuscitating the category
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS\n" +
                "    WHERE\n" +
                "      (table_name = \"online_report\")\n" +
                "      AND (table_schema = \"master_db\")\n" +
                "      AND (column_name = \"category\")", new HashMap<>(), Integer.class) == 0){
            jdbcTemplate.update("ALTER TABLE `master_db`.`online_report` ADD `category` VARCHAR(255) NULL DEFAULT NULL ;", new HashMap<>());
        }        /*
        Update hack - yoinked from https://dba.stackexchange.com/questions/169458/mysql-how-to-create-column-if-not-exists
         */

        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS\n" +
                "    WHERE\n" +
                "      (table_name = \"pending_upload\")\n" +
                "      AND (table_schema = \"master_db\")\n" +
                "      AND (column_name = \"import_result_path\")", new HashMap<>(), Integer.class) == 0){
            jdbcTemplate.update("drop TABLE master_db.`pending_upload`;", new HashMap<>());
            jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `master_db`.`pending_upload` (\n" +
                    "                                              `id` int(11) NOT NULL AUTO_INCREMENT,\n" +
                    "                                              `business_id` int(11) NOT NULL,\n" +
                    "                                              `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                    "                                              `processed_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
                    "                                              `file_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,\n" +
                    "                                              `file_path` varchar(255) COLLATE utf8_unicode_ci NOT NULL,\n" +
                    "                                              `created_by_user_id` int(11) NOT NULL,\n" +
                    "                                              `processed_by_user_id` int(11) DEFAULT NULL,\n" +
                    "                                              `database_id` int(11) NOT NULL,\n" +
                    "                                              `import_result_path` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,\n" +
                    "                                              PRIMARY KEY (`id`)\n" +
                    ") ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;\n", new HashMap<>());
        }
        StandardDAO.jdbcTemplate = jdbcTemplate; // I realise that this is "naughty", see comments at the top.
    }

    private static <T extends StandardEntity> void updateById(final T entity, final String tableName, final Map<String, Object> columnNameValueMap) throws DataAccessException {
//        final NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateFactory.getJDBCTemplateForEntity(entity);
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        StringBuilder updateSql = new StringBuilder("UPDATE `" + MASTER_DB + "`.`" + tableName + "` set ");
        for (Map.Entry<String, Object> columnNameValue : columnNameValueMap.entrySet()) {
            String columnName = columnNameValue.getKey();
            if (!columnName.equals(ID)) {
                updateSql.append("`").append(columnName).append("` = :").append(columnName).append(", ");
                namedParams.addValue(columnName, columnNameValue.getValue());
            }
        }
        updateSql.setLength(updateSql.length() - 2);//trim the last ", "
        updateSql.append(" where " + ID + " = :" + ID);
        namedParams.addValue("id", entity.getId());
        jdbcTemplate.update(updateSql.toString(), namedParams);
    }

    private static <T extends StandardEntity> void insert(final T entity, final String tableName, final Map<String, Object> columnNameValueMap) throws DataAccessException {
        StringBuilder columnsCommaList = new StringBuilder();
        StringBuilder valuesCommaList = new StringBuilder();
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        for (Map.Entry<String, Object> columnNameValue : columnNameValueMap.entrySet()) {
            String columnName = columnNameValue.getKey();
            if (!columnName.equals(ID)) { // skip id on insert
                columnsCommaList.append("`").append(columnName).append("`").append(", "); // build the comma separated list for use in the sql
                valuesCommaList.append(":").append(columnName).append(", "); // build the comma separated list for use in the sql
                namedParams.addValue(columnName, columnNameValue.getValue());
            }
        }
        columnsCommaList.setLength(columnsCommaList.length() - 2);//trim the last ", "
        valuesCommaList.setLength(valuesCommaList.length() - 2); //trim the last ", "
        final String insertSql = "INSERT INTO `" + MASTER_DB + "`.`" + tableName + "` (" + columnsCommaList + ") VALUES (" + valuesCommaList + ")";
        final GeneratedKeyHolder keyHolder = new GeneratedKeyHolder(); // to get the id back
        jdbcTemplate.update(insertSql, namedParams, keyHolder, new String[]{ID});
        entity.setId(keyHolder.getKey().intValue());
    }

    // slight repetition asking for both the column name value map and the entity? Should the entity maybe hold the column name value map?
    public static <T extends StandardEntity> void store(final T entity, final String tableName, final Map<String, Object> columnNameValueMap) throws DataAccessException {
        if (entity.getId() == 0) {
            insert(entity, tableName, columnNameValueMap);
        } else {
            updateById(entity, tableName, columnNameValueMap);
        }
    }

    public static <T extends StandardEntity> void removeById(final T entity, final String tableName) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(ID, entity.getId());
        final String SQL_DELETE = "DELETE  from `" + MASTER_DB + "`.`" + tableName + "` where " + ID + " = :" + ID;
        jdbcTemplate.update(SQL_DELETE, namedParams);
    }

    public static <T extends StandardEntity> T findById(int id, final String tableName, final RowMapper<T> rowMapper) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(ID, id);
        final String FIND_BY_ID = "Select * from `" + MASTER_DB + "`.`" + tableName + "` where " + ID + " = :" + ID;
        final List<T> results = jdbcTemplate.query(FIND_BY_ID, namedParams, rowMapper);
        if (results.size() == 0) {
            //logger.warning("No customer found for id " + id + " in table " + table);
            return null;
        }
        return results.get(0);
    }

    public static <T extends StandardEntity> List<T> findAll(final String tableName, final RowMapper<T> rowMapper) throws DataAccessException {
        return findListWithWhereSQLAndParameters(null, tableName, rowMapper, null);
    }

    public static <T extends StandardEntity> List<T> findListWithWhereSQLAndParameters(final String whereCondition, final String tableName, final RowMapper<T> rowMapper, final MapSqlParameterSource namedParams) throws DataAccessException {
        return findListWithWhereSQLAndParameters(whereCondition, tableName, rowMapper, namedParams, 0, SELECTLIMIT);
    }

    public static <T extends StandardEntity> List<T> findListWithWhereSQLAndParameters(final String whereCondition, final String tableName, final RowMapper<T> rowMapper, final MapSqlParameterSource namedParams, final int from, final int limit) throws DataAccessException {
        if (limit > SELECTLIMIT) {
            throw new InvalidDataAccessApiUsageException("Error, limit in SQL select greater than : " + SELECTLIMIT);
        }
        final String SQL_SELECT_ALL = "Select `" + MASTER_DB + "`.`" + tableName + "`.* from `" + MASTER_DB + "`.`" + tableName + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT " + from + "," + limit;
        return jdbcTemplate.query(SQL_SELECT_ALL, namedParams, rowMapper);
    }

    public static <T extends StandardEntity> T findOneWithWhereSQLAndParameters(final String whereCondition, final String tableName, final RowMapper<T> rowMapper, final MapSqlParameterSource namedParams) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + MASTER_DB + "`.`" + tableName + "`.* from `" + MASTER_DB + "`.`" + tableName + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
        final List<T> results = jdbcTemplate.query(SQL_SELECT_ALL, namedParams, rowMapper);
        if (results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    public static void update(final String setclause, final MapSqlParameterSource namedParams, final String tableName) throws DataAccessException {
        final String SQL_UPDATE = "update `" + MASTER_DB + "`.`" + tableName + "` " + setclause;
        jdbcTemplate.update(SQL_UPDATE, namedParams);
    }

    public static int findMaxId(final String tableName) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select max(id) from `" + MASTER_DB + "`.`" + tableName + "`";
        Integer toReturn = jdbcTemplate.queryForObject(SQL_SELECT_ALL, new HashMap<>(), Integer.class);
        return toReturn != null ? toReturn : 0; // otherwise we'll get a null pointer boxing to int!
    }

    public static NamedParameterJdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}