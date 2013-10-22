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
 */
public abstract class StandardDAO {

    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    public static final int SELECTLIMIT = 10000;

    public final class StandardEntityByIdRowMapper<T extends StandardEntity> implements RowMapper<T> {

        private T standardEntity;

        public StandardEntityByIdRowMapper(final T standardEntity){
            this.standardEntity = standardEntity;
        }
        @Override
        public T mapRow(final ResultSet rs, final int row) throws SQLException {
            standardEntity.setId(rs.getInt(StandardEntity.ID));
            return (T)findById(standardEntity);
        }
    }

    public void updateById(final StandardEntity entity) throws DataAccessException {
//        final NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateFactory.getJDBCTemplateForEntity(entity);
        final StringBuilder updateSql = new StringBuilder();
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        updateSql.append("UPDATE `" + entity.getTableName() + "` set ");
        Map<String, Object> columnNameValueMap = entity.getColumnNameValueMap();
        for (String columnName : columnNameValueMap.keySet()){
            if (!columnName.equals(StandardEntity.ID)){
                updateSql.append("`" + columnName + "` = :" + columnName + ", ");
                namedParams.addValue(columnName, columnNameValueMap.get(columnName));
            }
        }
        updateSql.delete(updateSql.length() - 2, updateSql.length()); //trim the last ", "
        updateSql.append(" where " + StandardEntity.ID + " = :"+ StandardEntity.ID);
        namedParams.addValue("id", entity.getId());
        jdbcTemplate.update(updateSql.toString(), namedParams);
    }

    public void insert(final StandardEntity entity) throws DataAccessException {
        insert(entity, false);
    }

    public void insert(final StandardEntity entity, boolean forceId) throws DataAccessException {
        final StringBuilder columnsCommaList = new StringBuilder();
        final StringBuilder valuesCommaList = new StringBuilder();
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        Map<String, Object> columnNameValueMap = entity.getColumnNameValueMap();
        for (String columnName : columnNameValueMap.keySet()){
            if (forceId || !columnName.equals(StandardEntity.ID)){ // don't set id on insert unless we're forcing the id on an insert. Necessary for moving existing date.
                columnsCommaList.append("`" + columnName + "`").append(", "); // build the comma separated list for use in the sql
                valuesCommaList.append(":").append(columnName).append(", "); // build the comma separated list for use in the sql
                namedParams.addValue(columnName, columnNameValueMap.get(columnName));
            }
        }
        columnsCommaList.delete(columnsCommaList.length() - 2, columnsCommaList.length()); //trim the last ", "
        valuesCommaList.delete(valuesCommaList.length() - 2, valuesCommaList.length()); //trim the last ", "
        final String insertSql = "INSERT INTO `" + entity.getTableName() + "` (" + columnsCommaList + ") VALUES (" + valuesCommaList + ")";
        final GeneratedKeyHolder keyHolder = new GeneratedKeyHolder(); // to get the id back
        jdbcTemplate.update(insertSql, namedParams, keyHolder, new String[]{StandardEntity.ID});
        entity.setId(keyHolder.getKey().intValue());
    }

    public void store(final StandardEntity entity) throws DataAccessException {
        if (entity.getId() > 0){
            updateById(entity);
        } else {
            insert(entity, false);
        }
    }

    public void removeById(final StandardEntity entity) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("id", entity.getId());
        final String SQL_DELETE = "DELETE  from `" + entity.getTableName() + "` where " + StandardEntity.ID + " = :" + StandardEntity.ID;
        jdbcTemplate.update(SQL_DELETE, namedParams);
    }

    public <T extends StandardEntity> T findById(final T entity) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("id", entity.getId());
        final String FIND_BY_ID = "Select * from `" + entity.getTableName() + "` where " + StandardEntity.ID + " = :" + StandardEntity.ID;
        final List<T> results = jdbcTemplate.query(FIND_BY_ID, namedParams, (RowMapper<T>)entity.getRowMapper());

        if (results.size() == 0) {
            //logger.warning("No customer found for id " + id + " in table " + table);
            return null;
        }
        return results.get(0);
    }

    // Assume not by id, adding wouldn't be difficult

    public List<? extends StandardEntity> findAll(final StandardEntity entity) throws DataAccessException {
        return findListWithWhereSQLAndParameters(entity, null,null,false);
    }

    public List<? extends StandardEntity> findListWithWhereSQLAndParameters(final StandardEntity entity, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        return findListWithWhereSQLAndParameters(entity, whereCondition, namedParams, lookupById, 0, SELECTLIMIT);
    }

    /*
    OK this function represents a casting problem, that is to say that DAO classes will call it but want to return List<TheirClassName> and the compiler will say it can't check the casting
    which it can't. There was thought of making a few functions at the bottom of each DAO for this but in the end I decided that such list functions should just use these functions and @SuppressWarnings("uncheck
    I could change this latered")


    //public <T>  java.util.List<T> queryForList(java.lang.String sql, org.springframework.jdbc.core.namedparam.SqlParameterSource paramSource, java.lang.Class<T> elementType   */

    public <T extends StandardEntity>  java.util.List<T> findListWithWhereSQLAndParameters( T entity, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final int from, final int limit) throws DataAccessException {
        if (limit > SELECTLIMIT){
            throw new InvalidDataAccessApiUsageException("Error, limit in SQL select greater than : " + SELECTLIMIT);
        }
        if (lookupById){
            final String SQL_SELECT = "Select `" + entity.getTableName() + "`." + StandardEntity.ID + " from `" + entity.getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT " + from + "," + limit;
            StandardEntityByIdRowMapper mapById = new StandardEntityByIdRowMapper(entity);
            return jdbcTemplate.query(SQL_SELECT, namedParams, mapById);
        } else {
            final String SQL_SELECT_ALL = "Select `" + entity.getTableName() + "`.* from `" + entity.getTableName() + "`" + (whereCondition != null ? whereCondition : "");
            return jdbcTemplate.query(SQL_SELECT_ALL, namedParams, (RowMapper<T>)entity.getRowMapper());
        }
    }

    public StandardEntity findOneWithWhereSQLAndParameters(final StandardEntity entity, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        if (lookupById){
            final String SQL_SELECT = "Select `" + entity.getTableName() + "`." + StandardEntity.ID + " from `" + entity.getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
            StandardEntityByIdRowMapper mapById = new StandardEntityByIdRowMapper(entity);
            final List<? extends StandardEntity> results = jdbcTemplate.query(SQL_SELECT, namedParams, mapById);
            if (results.size() == 0) {
                return null;
            }
            return results.get(0);
        } else {
            final String SQL_SELECT_ALL = "Select `" + entity.getTableName() + "`.* from `" + entity.getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
            final List<? extends StandardEntity> results = jdbcTemplate.query(SQL_SELECT_ALL, namedParams, entity.getRowMapper());
            if (results.size() == 0) {
                return null;
            }
            return results.get(0);
        }
    }
}
