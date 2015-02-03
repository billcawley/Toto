package com.azquo.admindao;

import com.azquo.adminentities.StandardEntity;
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
 * Created 07/01/14 by edd
 * to factor off common bits on vanilla db access
 */
public abstract class StandardDAO<EntityType extends StandardEntity> {

    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    private static final int SELECTLIMIT = 10000;
    protected static final String ID = "id";
    protected static final String MASTER_DB = "master_db";

    protected abstract String getTableName();

    protected abstract Map<String, Object> getColumnNameValueMap(EntityType entity);

    protected abstract RowMapper<EntityType> getRowMapper();

    private final class StandardEntityByIdRowMapper implements RowMapper<EntityType> {
        @Override
        public EntityType mapRow(final ResultSet rs, final int row) throws SQLException {
            return findById(rs.getInt(ID));
        }
    }

    public final void updateById(final EntityType entity) throws DataAccessException {
//        final NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateFactory.getJDBCTemplateForEntity(entity);
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "UPDATE `" + MASTER_DB + "`.`" + getTableName() + "` set ";
        final Map<String, Object> columnNameValueMap = getColumnNameValueMap(entity);
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

    public final void insert(final EntityType entity) throws DataAccessException {
        String columnsCommaList = "";
        String valuesCommaList = "";
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        final Map<String, Object> columnNameValueMap = getColumnNameValueMap(entity);
        for (String columnName : columnNameValueMap.keySet()) {
            if (!columnName.equals(ID)) { // skip id on insert
                columnsCommaList += "`" + columnName + "`" + ", "; // build the comma separated list for use in the sql
                valuesCommaList += ":" + columnName + ", "; // build the comma separated list for use in the sql
                namedParams.addValue(columnName, columnNameValueMap.get(columnName));
            }
        }
        columnsCommaList = columnsCommaList.substring(0, columnsCommaList.length() - 2); //trim the last ", "
        valuesCommaList = valuesCommaList.substring(0, valuesCommaList.length() - 2); //trim the last ", "
        final String insertSql = "INSERT INTO `" + MASTER_DB + "`.`" + getTableName() + "` (" + columnsCommaList + ") VALUES (" + valuesCommaList + ")";
        final GeneratedKeyHolder keyHolder = new GeneratedKeyHolder(); // to get the id back
        jdbcTemplate.update(insertSql, namedParams, keyHolder, new String[]{ID});
        entity.setId(keyHolder.getKey().intValue());
    }

    public final void store(final EntityType entity) throws DataAccessException {
        if (entity.getId() == 0) {
            insert(entity);
        } else {
            updateById(entity);
        }
    }

    public final void removeById(final EntityType entity) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(ID, entity.getId());
        final String SQL_DELETE = "DELETE  from `" + MASTER_DB + "`.`" + getTableName() + "` where " + ID + " = :" + ID;
        jdbcTemplate.update(SQL_DELETE, namedParams);
    }

    public final EntityType findById(int id) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(ID, id);
        final String FIND_BY_ID = "Select * from `" + MASTER_DB + "`.`" + getTableName() + "` where " + ID + " = :" + ID;
        final List<EntityType> results = jdbcTemplate.query(FIND_BY_ID, namedParams, getRowMapper());
        if (results.size() == 0) {
            //logger.warning("No customer found for id " + id + " in table " + table);
            return null;
        }
        return results.get(0);
    }


    public final List<EntityType> findAll() throws DataAccessException {
        return findListWithWhereSQLAndParameters(null, null, false);
    }

    public final List<EntityType> findListWithWhereSQLAndParameters(final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        return findListWithWhereSQLAndParameters(whereCondition, namedParams, lookupById, 0, SELECTLIMIT);
    }

    public final List<EntityType> findListWithWhereSQLAndParameters(final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final int from, final int limit) throws DataAccessException {
        if (limit > SELECTLIMIT) {
            throw new InvalidDataAccessApiUsageException("Error, limit in SQL select greater than : " + SELECTLIMIT);
        }
        if (lookupById) {
            final String SQL_SELECT = "Select `" + MASTER_DB + "`.`" + getTableName() + "`." + ID + " from `" + MASTER_DB + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT " + from + "," + limit;
            return jdbcTemplate.query(SQL_SELECT, namedParams, new StandardEntityByIdRowMapper());
        } else {
            final String SQL_SELECT_ALL = "Select `" + MASTER_DB + "`.`" + getTableName() + "`.* from `" + MASTER_DB + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT " + from + "," + limit;
            return jdbcTemplate.query(SQL_SELECT_ALL, namedParams, getRowMapper());
        }
    }

    public final EntityType findOneWithWhereSQLAndParameters(final String whereCondition, final MapSqlParameterSource namedParams) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + MASTER_DB + "`.`" + getTableName() + "`.* from `" + MASTER_DB + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
        final List<EntityType> results = jdbcTemplate.query(SQL_SELECT_ALL, namedParams, getRowMapper());
        if (results.size() == 0) {
            return null;
        }
        return results.get(0);
    }

    public final void update(final String setclause, final MapSqlParameterSource namedParams) throws DataAccessException {
        final String SQL_UPDATE = "update `" + MASTER_DB + "`.`" + getTableName() + "` " + setclause;
        jdbcTemplate.update(SQL_UPDATE, namedParams);

    }
}
