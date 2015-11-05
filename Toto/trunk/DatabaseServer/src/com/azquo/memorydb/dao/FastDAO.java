package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.AzquoMemoryDBEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Map;

/**
 * Created by edward on 30/10/15.
 *
 * Just a way to factor a few things off from the new fast value and name DAO classes.
 *
 */
public abstract class FastDAO {

    @Autowired
    protected JdbcTemplateUtils jdbcTemplateUtils;

    protected static final String ID = "id";

    public static final int UPDATELIMIT = 2500; // going to 2.5k as now some of the records can be quite large! Also simultaneous.

    public abstract String getTableName();

    // possibly very similar to what's in JsonRecordDAO. Not sure if it's worth factoring

    protected void bulkDelete(final AzquoMemoryDB azquoMemoryDB, final List<? extends AzquoMemoryDBEntity> entities) throws DataAccessException {
        if (!entities.isEmpty()) {
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            final StringBuilder updateSql = new StringBuilder("delete from `" + azquoMemoryDB.getMySQLName() + "`.`" + getTableName() + "` where " + ID + " in (");

            int count = 1;
            for (AzquoMemoryDBEntity azquoMemoryDBEntity : entities) {
                if (count == 1) {
                    updateSql.append(azquoMemoryDBEntity.getId());
                } else {
                    updateSql.append(",").append(azquoMemoryDBEntity.getId());
                }
                count++;
            }
            updateSql.append(")");
            //System.out.println(updateSql.toString());
            jdbcTemplateUtils.update(updateSql.toString(), namedParams);
        }
    }

    public final int findMaxId(final AzquoMemoryDB azquoMemoryDB) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select max(id) from `" + azquoMemoryDB.getMySQLName() + "`.`" + getTableName() + "`";
        Integer toReturn = jdbcTemplateUtils.queryForObject (SQL_SELECT_ALL, JsonRecordDAO.EMPTY_PARAMETERS_MAP, Integer.class);
        return toReturn != null ? toReturn : 0; // otherwise we'll get a null pinter boxing to int!
    }

    public void clearTable(final AzquoMemoryDB azquoMemoryDB){
        jdbcTemplateUtils.update("delete from `" + azquoMemoryDB.getMySQLName() + "`.`" + getTableName() + "`", JsonRecordDAO.EMPTY_PARAMETERS_MAP);

    }

    public boolean checkFastTableExists(final AzquoMemoryDB azquoMemoryDB){
        final List<Map<String, Object>> maps = jdbcTemplateUtils.queryForList("show tables from  `" + azquoMemoryDB.getMySQLName() + "` like 'fast_value' ", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
        return !maps.isEmpty();
    }

}