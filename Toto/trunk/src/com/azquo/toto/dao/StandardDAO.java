package com.azquo.toto.dao;

import com.azquo.toto.entity.StandardEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:18
 * Most data tables in the database have common features such as an id and a simple place that they're stored which means we can,
 * factor things off here
 * Unlike Feefo I don't know if there's much of a case for Cacheing.
 *
 * Note : for building SQL I'm veering away from stringbuilder as IntelliJ complains about it and string concantation etc is heavily optimised by the compiler
 */
public abstract class StandardDAO<EntityType extends StandardEntity> {

    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    // call it 100000, might as well go for big selects to populate the memory DB

    public static final int SELECTLIMIT = 100000;

    public static final String ID = "id";

    public abstract String getTableName();

    public abstract Map<String, Object> getColumnNameValueMap(EntityType entity);

    public abstract RowMapper<EntityType> getRowMapper();

    public final class StandardEntityByIdRowMapper implements RowMapper<EntityType> {

        String databaseName;

        public StandardEntityByIdRowMapper(final String databaseName){
            this.databaseName = databaseName;
        }

        @Override
        public EntityType mapRow(final ResultSet rs, final int row) throws SQLException {
            return findById(databaseName, rs.getInt(ID));
        }
    }

    public void updateById(final String databaseName, final EntityType entity) throws DataAccessException {
//        final NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateFactory.getJDBCTemplateForEntity(entity);
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "UPDATE `" + databaseName + "`.`" + getTableName() + "` set ";
        Map<String, Object> columnNameValueMap = getColumnNameValueMap(entity);
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

    public void insert(final String databaseName, final EntityType entity) throws DataAccessException {
        insert(databaseName, entity, false);
    }

    // there may be no use for force ID in Toto
    /*
    public void insert(final EntityType entity, boolean forceId) throws DataAccessException {
        insert(entity, forceId, getTableName());
    }*/

    public void insert(final String databaseName, final EntityType entity, boolean forceId) throws DataAccessException {
        String columnsCommaList = "";
        String valuesCommaList = "";
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        Map<String, Object> columnNameValueMap = getColumnNameValueMap(entity);
        for (String columnName : columnNameValueMap.keySet()) {
            if (forceId || !columnName.equals(ID)) { // don't set id on insert unless we're forcing the id on an insert. Necessary for moving existing data.
                columnsCommaList += "`" + columnName + "`" + ", "; // build the comma separated list for use in the sql
                valuesCommaList += ":" + columnName + ", "; // build the comma separated list for use in the sql
                namedParams.addValue(columnName, columnNameValueMap.get(columnName));
            }
        }
        columnsCommaList = columnsCommaList.substring(0, columnsCommaList.length() - 2); //trim the last ", "
        valuesCommaList = valuesCommaList.substring(0, valuesCommaList.length() - 2); //trim the last ", "
        final String insertSql = "INSERT INTO `" + databaseName + "`.`" + getTableName() + "` (" + columnsCommaList + ") VALUES (" + valuesCommaList + ")";
        final GeneratedKeyHolder keyHolder = new GeneratedKeyHolder(); // to get the id back
        jdbcTemplate.update(insertSql, namedParams, keyHolder, new String[]{ID});
        entity.setId(keyHolder.getKey().intValue());
    }

    public void store(final String databaseName, final EntityType entity) throws DataAccessException {
        if (entity.getId() > 0) {
            updateById(databaseName, entity);
        } else {
            insert(databaseName, entity, false);
        }
    }

    // removal means we should just have the object, pass it for the mo, Can cahnge to ID later

    public void removeById(final String databaseName, final EntityType entity) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("id", entity.getId());
        final String SQL_DELETE = "DELETE  from `" + databaseName + "`.`" + getTableName() + "` where " + ID + " = :" + ID;
        jdbcTemplate.update(SQL_DELETE, namedParams);
    }

    public EntityType findById(final String databaseName, int id) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("id", id);
        final String FIND_BY_ID = "Select * from `" + databaseName + "`.`" + getTableName() + "` where " + ID + " = :" + ID;
        final List<EntityType> results = jdbcTemplate.query(FIND_BY_ID, namedParams, getRowMapper());

        if (results.size() == 0) {
            //logger.warning("No customer found for id " + id + " in table " + table);
            return null;
        }
        return results.get(0);
    }

    // Assume not by id, adding wouldn't be difficult. A by table name one could be added later.

    public List<EntityType> findAll(final String databaseName) throws DataAccessException {
        return findListWithWhereSQLAndParameters(databaseName, null, null, false);
    }

    public List<EntityType> findListWithWhereSQLAndParameters(final String databaseName, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        return findListWithWhereSQLAndParameters(databaseName, whereCondition, namedParams, lookupById, 0, SELECTLIMIT);
    }

/*    public List<EntityType> findListWithWhereSQLAndParameters(final EntityType entity, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final String tableName) throws DataAccessException {
        return findListWithWhereSQLAndParameters(entity, whereCondition, namedParams, lookupById, 0, SELECTLIMIT, tableName);
    }*/

    public List<EntityType> findListWithWhereSQLAndParameters(final String databaseName, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final int from, final int limit) throws DataAccessException {
        if (limit > SELECTLIMIT) {
            throw new InvalidDataAccessApiUsageException("Error, limit in SQL select greater than : " + SELECTLIMIT);
        }
        if (lookupById) {
            final String SQL_SELECT = "Select `" + databaseName + "`.`" + getTableName() + "`." + ID + " from `" + databaseName + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT " + from + "," + limit;
            return jdbcTemplate.query(SQL_SELECT, namedParams, new StandardEntityByIdRowMapper(databaseName));
        } else {
            final String SQL_SELECT_ALL = "Select `" + databaseName + "`.`" + getTableName() + "`.* from `" + databaseName + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "");
            return jdbcTemplate.query(SQL_SELECT_ALL, namedParams, getRowMapper());
        }
    }


    public EntityType findOneWithWhereSQLAndParameters(final String databaseName, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        if (lookupById) {
            final String SQL_SELECT = "Select `" + databaseName + "`.`" + getTableName() + "`." + ID + " from `" + databaseName + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
            final List<EntityType> results = jdbcTemplate.query(SQL_SELECT, namedParams, new StandardEntityByIdRowMapper(databaseName));
            if (results.size() == 0) {
                return null;
            }
            return results.get(0);
        } else {
            final String SQL_SELECT_ALL = "Select `" + databaseName + "`.`" + getTableName() + "`.* from `" + databaseName + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
            final List<EntityType> results = jdbcTemplate.query(SQL_SELECT_ALL, namedParams, getRowMapper());
            if (results.size() == 0) {
                return null;
            }
            return results.get(0);
        }
    }

    public int findTotalCount(final String databaseName) throws DataAccessException {
            final String SQL_SELECT = "Select count(*) from `" + databaseName + "`.`" + getTableName() + "`";
            return jdbcTemplate.queryForObject(SQL_SELECT, new MapSqlParameterSource() , Integer.class);
    }


}
