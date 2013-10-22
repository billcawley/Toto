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
 * by implementing certain functions in the entity objects, make some functions generic. Update, insert, delete find by ID.
 * Unlike Feefo I don't know if there's much of a case for Cacheing.
 *
 * Note : for building SQL I'm veering away from stringbuilder as IntelliJ complains about it and string concantation etc is heavily optimised by the compiler
 */
public abstract class StandardDAO<EntityType extends StandardEntity> {

    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    public static final int SELECTLIMIT = 10000;

    public static final String ID = "id";

    public abstract String getTableName();

    public abstract Map<String, Object> getColumnNameValueMap(EntityType entity);

    public abstract RowMapper<EntityType> getRowMapper();

    public final class StandardEntityByIdRowMapper implements RowMapper<EntityType> {
        @Override
        public EntityType mapRow(final ResultSet rs, final int row) throws SQLException {
            return findById(rs.getInt(ID));
        }
    }

    public void updateById(final EntityType entity) throws DataAccessException {
        updateById(entity, getTableName());
    }

    public void updateById(final EntityType entity, String tableName) throws DataAccessException {
//        final NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateFactory.getJDBCTemplateForEntity(entity);
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "UPDATE `" + tableName + "` set ";
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

    public void insert(final EntityType entity) throws DataAccessException {
        insert(entity, false, getTableName());
    }

    // there may be no use for force ID in Toto
    /*
    public void insert(final EntityType entity, boolean forceId) throws DataAccessException {
        insert(entity, forceId, getTableName());
    }*/

    public void insert(final EntityType entity, boolean forceId, String tableName) throws DataAccessException {
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
        final String insertSql = "INSERT INTO `" + tableName + "` (" + columnsCommaList + ") VALUES (" + valuesCommaList + ")";
        final GeneratedKeyHolder keyHolder = new GeneratedKeyHolder(); // to get the id back
        jdbcTemplate.update(insertSql, namedParams, keyHolder, new String[]{ID});
        entity.setId(keyHolder.getKey().intValue());
    }

    public void store(final EntityType entity) throws DataAccessException {
        store(entity, getTableName());
    }

    public void store(final EntityType entity, String tableName) throws DataAccessException {
        if (entity.getId() > 0) {
            updateById(entity, tableName);
        } else {
            insert(entity, false, tableName);
        }
    }

    public void removeById(final EntityType entity) throws DataAccessException {
        removeById(entity, getTableName());
    }

    // removal means we should just have the object, pass it for the mo, Can cahnge to ID later

    public void removeById(final EntityType entity, String tableName) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("id", entity.getId());
        final String SQL_DELETE = "DELETE  from `" + tableName + "` where " + ID + " = :" + ID;
        jdbcTemplate.update(SQL_DELETE, namedParams);
    }

    public EntityType findById(int id) throws DataAccessException {
        return findById(id, getTableName());
    }

    public EntityType findById(int id, String tableName) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("id", id);
        final String FIND_BY_ID = "Select * from `" + tableName + "` where " + ID + " = :" + ID;
        final List<EntityType> results = jdbcTemplate.query(FIND_BY_ID, namedParams, getRowMapper());

        if (results.size() == 0) {
            //logger.warning("No customer found for id " + id + " in table " + table);
            return null;
        }
        return results.get(0);
    }

    // Assume not by id, adding wouldn't be difficult. A by table name one could be added later.

    public List<EntityType> findAll() throws DataAccessException {
        return findListWithWhereSQLAndParameters(null, null, false);
    }

    public List<EntityType> findListWithWhereSQLAndParameters(final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        return findListWithWhereSQLAndParameters(whereCondition, namedParams, lookupById, 0, SELECTLIMIT);
    }

/*    public List<EntityType> findListWithWhereSQLAndParameters(final EntityType entity, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final String tableName) throws DataAccessException {
        return findListWithWhereSQLAndParameters(entity, whereCondition, namedParams, lookupById, 0, SELECTLIMIT, tableName);
    }*/

    public List<EntityType> findListWithWhereSQLAndParameters(final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final int from, final int limit) throws DataAccessException {
        return findListWithWhereSQLAndParameters(whereCondition, namedParams, lookupById, from, limit, getTableName());
    }

    public List<EntityType> findListWithWhereSQLAndParameters(final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final int from, final int limit, String tableName) throws DataAccessException {
        if (limit > SELECTLIMIT) {
            throw new InvalidDataAccessApiUsageException("Error, limit in SQL select greater than : " + SELECTLIMIT);
        }
        if (lookupById) {
            final String SQL_SELECT = "Select `" + tableName + "`." + ID + " from `" + tableName + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT " + from + "," + limit;
            return jdbcTemplate.query(SQL_SELECT, namedParams, new StandardEntityByIdRowMapper());
        } else {
            final String SQL_SELECT_ALL = "Select `" + tableName + "`.* from `" + tableName + "`" + (whereCondition != null ? whereCondition : "");
            return jdbcTemplate.query(SQL_SELECT_ALL, namedParams, getRowMapper());
        }
    }


    public EntityType findOneWithWhereSQLAndParameters(final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        return findOneWithWhereSQLAndParameters(whereCondition, namedParams, lookupById, getTableName());
    }


    public EntityType findOneWithWhereSQLAndParameters(final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, String tableName) throws DataAccessException {
        if (lookupById) {
            final String SQL_SELECT = "Select `" + tableName + "`." + ID + " from `" + tableName + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
            final List<EntityType> results = jdbcTemplate.query(SQL_SELECT, namedParams, new StandardEntityByIdRowMapper());
            if (results.size() == 0) {
                return null;
            }
            return results.get(0);
        } else {
            final String SQL_SELECT_ALL = "Select `" + getTableName() + "`.* from `" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
            final List<EntityType> results = jdbcTemplate.query(SQL_SELECT_ALL, namedParams, getRowMapper());
            if (results.size() == 0) {
                return null;
            }
            return results.get(0);
        }
    }
}
