package com.azquo.toto.dao;

import com.azquo.toto.memorydb.TotoMemoryDBEntity;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:18
 * Most data tables in the database have common features such as an id and a simple place that they're stored which means we can,
 * factor things off here
 *
 * Note : for building SQL I'm veering away from stringbuilder as IntelliJ complains about it and string concantation etc is heavily optimised by the compiler
 *
 * Due to new memory DB we don't get Ids back here any more, it deals with them.
 *
 * In Feefo the abstraction here was very important on account of how much stuff could happen in the DAOs
 *
 * With Toto's memory DB this is not so important although it's still nice.
 *
 */
public abstract class StandardDAO<EntityType extends TotoMemoryDBEntity> {

    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    // call it 100000, might as well go for big selects to populate the memory DB

    private static final int SELECTLIMIT = 100000;

    protected static final String ID = "id";

    protected abstract String getTableName();

    protected abstract Map<String, Object> getColumnNameValueMap(EntityType entity);

    protected abstract RowMapper<EntityType> getRowMapper(TotoMemoryDB totoMemoryDB);

    private final class StandardEntityByIdRowMapper implements RowMapper<EntityType> {

        TotoMemoryDB totoMemoryDB;

        public StandardEntityByIdRowMapper(final TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }

        @Override
        public EntityType mapRow(final ResultSet rs, final int row) throws SQLException {
            return findById(totoMemoryDB, rs.getInt(ID));
        }
    }

    public void updateById(final TotoMemoryDB totoMemoryDB, final EntityType entity) throws DataAccessException {
//        final NamedParameterJdbcTemplate jdbcTemplate = jdbcTemplateFactory.getJDBCTemplateForEntity(entity);
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "UPDATE `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "` set ";
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

    // now always assumes it's been passed an ID

    public void insert(final TotoMemoryDB totoMemoryDB, final EntityType entity) throws DataAccessException {
        String columnsCommaList = "";
        String valuesCommaList = "";
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        Map<String, Object> columnNameValueMap = getColumnNameValueMap(entity);
        for (String columnName : columnNameValueMap.keySet()) {
            columnsCommaList += "`" + columnName + "`" + ", "; // build the comma separated list for use in the sql
            valuesCommaList += ":" + columnName + ", "; // build the comma separated list for use in the sql
            namedParams.addValue(columnName, columnNameValueMap.get(columnName));
        }
        columnsCommaList = columnsCommaList.substring(0, columnsCommaList.length() - 2); //trim the last ", "
        valuesCommaList = valuesCommaList.substring(0, valuesCommaList.length() - 2); //trim the last ", "
        final String insertSql = "INSERT INTO `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "` (" + columnsCommaList + ") VALUES (" + valuesCommaList + ")";
        jdbcTemplate.update(insertSql, namedParams);
    }

    // inspired by value but could be used for a lot

    public void bulkInsert(final TotoMemoryDB totoMemoryDB, final Set<EntityType> entities) throws DataAccessException {
        if (!entities.isEmpty()){
            long track = System.currentTimeMillis();

            EntityType first = entities.iterator().next();

            String columnsCommaList = "";
            Map<String, Object> columnNameValueMap = getColumnNameValueMap(first);
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();

            for (String columnName : columnNameValueMap.keySet()) {
                columnsCommaList += "`" + columnName + "`" + ", "; // build the comma separated list for use in the sql
            }

            columnsCommaList = columnsCommaList.substring(0, columnsCommaList.length() - 2); //trim the last ", "
            StringBuilder insertSql = new StringBuilder();

            insertSql.append("INSERT INTO `").append(totoMemoryDB.getDatabaseName()).append("`.`").append(getTableName()).append("` (").append(columnsCommaList).append(") VALUES ");

            int count = 1;

            for(EntityType entity : entities){


                columnNameValueMap = getColumnNameValueMap(entity);
                insertSql.append("(");
                for (String columnName : columnNameValueMap.keySet()) {
                    insertSql.append(":").append(columnName).append(count).append(", "); // build the comma separated list for use in the sql
                    namedParams.addValue(columnName + count, columnNameValueMap.get(columnName));
                }
                insertSql.delete(insertSql.length() - 2, insertSql.length());
                insertSql.append("), ");
                count++;
            }

            insertSql.delete(insertSql.length() - 2, insertSql.length());
            System.out.println(insertSql.toString());
            jdbcTemplate.update(insertSql.toString(), namedParams);
            System.out.println("bulk inserted " + entities.size() + " " + first.getClass().getName() + " objects in " + (System.currentTimeMillis() - track));
        }
    }

    public void store(final TotoMemoryDB totoMemoryDB, final EntityType entity) throws DataAccessException {
        if (entity.getNeedsInserting()) {
            insert(totoMemoryDB, entity);
        } else {
            updateById(totoMemoryDB, entity);
        }
    }
    // removal means we should just have the object, pass it for the mo, Can cahnge to ID later

    public void removeById(final TotoMemoryDB totoMemoryDB, final EntityType entity) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("id", entity.getId());
        final String SQL_DELETE = "DELETE  from `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "` where " + ID + " = :" + ID;
        jdbcTemplate.update(SQL_DELETE, namedParams);
    }

    public EntityType findById(final TotoMemoryDB totoMemoryDB, int id) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("id", id);
        final String FIND_BY_ID = "Select * from `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "` where " + ID + " = :" + ID;
        final List<EntityType> results = jdbcTemplate.query(FIND_BY_ID, namedParams, getRowMapper(totoMemoryDB));

        if (results.size() == 0) {
            //logger.warning("No customer found for id " + id + " in table " + table);
            return null;
        }
        return results.get(0);
    }

    // Assume not by id, adding wouldn't be difficult. A by table name one could be added later.

    public List<EntityType> findAll(final TotoMemoryDB totoMemoryDB) throws DataAccessException {
        return findListWithWhereSQLAndParameters(totoMemoryDB, null, null, false);
    }

    public List<EntityType> findListWithWhereSQLAndParameters(final TotoMemoryDB totoMemoryDB, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        return findListWithWhereSQLAndParameters(totoMemoryDB, whereCondition, namedParams, lookupById, 0, SELECTLIMIT);
    }

/*    public List<EntityType> findListWithWhereSQLAndParameters(final EntityType entity, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final String tableName) throws DataAccessException {
        return findListWithWhereSQLAndParameters(entity, whereCondition, namedParams, lookupById, 0, SELECTLIMIT, tableName);
    }*/

    public List<EntityType> findListWithWhereSQLAndParameters(final TotoMemoryDB totoMemoryDB, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById, final int from, final int limit) throws DataAccessException {
        if (limit > SELECTLIMIT) {
            throw new InvalidDataAccessApiUsageException("Error, limit in SQL select greater than : " + SELECTLIMIT);
        }
        if (lookupById) {
            final String SQL_SELECT = "Select `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "`." + ID + " from `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT " + from + "," + limit;
            return jdbcTemplate.query(SQL_SELECT, namedParams, new StandardEntityByIdRowMapper(totoMemoryDB));
        } else {
            final String SQL_SELECT_ALL = "Select `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "`.* from `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "");
            return jdbcTemplate.query(SQL_SELECT_ALL, namedParams, getRowMapper(totoMemoryDB));
        }
    }


    public EntityType findOneWithWhereSQLAndParameters(final TotoMemoryDB totoMemoryDB, final String whereCondition, final MapSqlParameterSource namedParams, final boolean lookupById) throws DataAccessException {
        if (lookupById) {
            final String SQL_SELECT = "Select `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "`." + ID + " from `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
            final List<EntityType> results = jdbcTemplate.query(SQL_SELECT, namedParams, new StandardEntityByIdRowMapper(totoMemoryDB));
            if (results.size() == 0) {
                return null;
            }
            return results.get(0);
        } else {
            final String SQL_SELECT_ALL = "Select `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "`.* from `" + totoMemoryDB.getDatabaseName() + "`.`" + getTableName() + "`" + (whereCondition != null ? whereCondition : "") + " LIMIT 0,1";
            final List<EntityType> results = jdbcTemplate.query(SQL_SELECT_ALL, namedParams, getRowMapper(totoMemoryDB));
            if (results.size() == 0) {
                return null;
            }
            return results.get(0);
        }
    }


}
