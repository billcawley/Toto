package com.azquo.admin;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created 07/01/14 by edd
 * to factor off common bits on vanilla DAO stuff - the nature of the factoring may change a little if I go to static DAOs
 *
 * Similar to JdbcTemplateUtils I'm doing a "dirty" assignment of the JdbcTemplate to a static field to avoid instances later of stateless objects
 *
 * See comments in JdbcTemplateUtils, we're not using dependency injection or unit tests etc so I'm going static to simplify the code
 *
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

        if (jdbcTemplate == null){
            throw new Exception("Ack!, null data source passed to StandardDAO, Azquo report server won't function!");
        }
        StandardDAO.jdbcTemplate = jdbcTemplate; // I realise that this is "naughty", see comments at the top.
    }

    private static <T extends StandardEntity> void updateById(final T entity, final String tableName, final Map<String, Object> columnNameValueMap) throws DataAccessException {
//        final NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateFactory.getJDBCTemplateForEntity(entity);
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "UPDATE `" + MASTER_DB + "`.`" + tableName + "` set ";
        for (String columnName : columnNameValueMap.keySet()) {
            if (!columnName.equals(ID)) {
                updateSql += "`" + columnName + "` = :" + columnName + ", ";
                namedParams.addValue(columnName, columnNameValueMap.get(columnName));
            }
        }
        updateSql = updateSql.substring(0, updateSql.length() - 2); //trim the last ", "
        updateSql += " where " + ID + " = :" + ID;
        namedParams.addValue("id", entity.getId());
        jdbcTemplate.update(updateSql, namedParams);
    }

     private static <T extends StandardEntity> void insert(final T entity, final String tableName, final Map<String, Object> columnNameValueMap) throws DataAccessException {
        String columnsCommaList = "";
        String valuesCommaList = "";
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        for (String columnName : columnNameValueMap.keySet()) {
            if (!columnName.equals(ID)) { // skip id on insert
                columnsCommaList += "`" + columnName + "`" + ", "; // build the comma separated list for use in the sql
                valuesCommaList += ":" + columnName + ", "; // build the comma separated list for use in the sql
                namedParams.addValue(columnName, columnNameValueMap.get(columnName));
            }
        }
        columnsCommaList = columnsCommaList.substring(0, columnsCommaList.length() - 2); //trim the last ", "
        valuesCommaList = valuesCommaList.substring(0, valuesCommaList.length() - 2); //trim the last ", "
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

    // bottom two lines off the net, needed as result sets don't use the new date classes
    public static LocalDateTime getLocalDateTimeFromDate(Date date) {
        if (date == null) {
            return null;
        }
        Instant instant = Instant.ofEpochMilli(date.getTime());
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public static int findMaxId(final String tableName) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select max(id) from `" + MASTER_DB + "`.`" + tableName + "`";
        Integer toReturn = jdbcTemplate.queryForObject(SQL_SELECT_ALL, new HashMap<>(), Integer.class);
        return toReturn != null ? toReturn : 0; // otherwise we'll get a null pointer boxing to int!
    }

    public static NamedParameterJdbcTemplate getJdbcTemplate(){
        return jdbcTemplate;
    }
}