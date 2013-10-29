package com.azquo.toto.dao;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Value;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 10:28
 * DAO for Values, under new model just used for persistence,will hopefully be pretty simple
 * */
public class ValueDAO extends StandardDAO<Value> {
    // the default table name for this data.
    @Override
    public String getTableName() {
        return "value";
    }

    // column names (except ID)

    public static final String PROVENANCEID = "provenance_id";
    public static final String DOUBLE = "double";
    public static final String TEXT = "text";
    public static final String DELETEDINFO = "deleted_info";

    // related table and column names

    public static String VALUENAME = "value_name";
    public static String VALUEID = "value_id";
    public static String NAMEID = "name_id";

    @Override
    public Map<String, Object> getColumnNameValueMap(Value value) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, value.getId());
        toReturn.put(PROVENANCEID, value.getProvenanceId());
        toReturn.put(DOUBLE, value.getDoubleValue());
        toReturn.put(TEXT, value.getText());
        toReturn.put(DELETEDINFO, value.getDeletedInfo());
        return toReturn;
    }

    public static final class ValueRowMapper implements RowMapper<Value> {
        TotoMemoryDB totoMemoryDB;
        public ValueRowMapper(TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }
        @Override
        public Value mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Value(totoMemoryDB,rs.getInt(ID), rs.getInt(PROVENANCEID),rs.getInt(DOUBLE),rs.getString(TEXT),rs.getString(DELETEDINFO));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Value> getRowMapper(TotoMemoryDB totoMemoryDB) {
        return new ValueRowMapper(totoMemoryDB);
    }

    // for speed
    public boolean linkValueToNames(final TotoMemoryDB totoMemoryDB, final Value value, final Set<Name> names) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "INSERT INTO `" + totoMemoryDB.getDatabaseName() + "`.`" + VALUENAME + "` (`" + VALUEID + "`,`" + NAMEID + "`) VALUES ";
        int count = 1;
        for (Name name : names) {
            // I'm taking off the check - I think it's so rare we'll just let the DB complain
            updateSql += "(:" + VALUEID + count + ",:" + NAMEID + count + "),";
            namedParams.addValue(VALUEID + count, value.getId());
            namedParams.addValue(NAMEID + count, name.getId());
            count++;
        }
        updateSql = updateSql.substring(0, updateSql.length() - 1);
        long track = System.currentTimeMillis();
        jdbcTemplate.update(updateSql, namedParams);
        System.out.println("value name link time : " + (System.currentTimeMillis() - track) + " for " + (count - 1) + " labels");
        return true;
    }

    public boolean unlinkValueFromNames(final TotoMemoryDB totoMemoryDB, final Value value) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        String updateSql = "Delete from `" + totoMemoryDB.getDatabaseName() + "`.`" + VALUENAME + "` where `" + VALUEID + "` = :" + VALUEID;
        jdbcTemplate.update(updateSql, namedParams);
        return true;
    }

/*    public void setDeleted(String totoMemoryDB.getDatabaseName(), Value value) {
        value.setDeleted(true);
        store(totoMemoryDB.getDatabaseName(), value);
    }*/

   // for loading into the memory db

    public List<Integer> findNameIdsForValue(final TotoMemoryDB totoMemoryDB, final Value value) {

        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        final String FIND_EXISTING_LINK = "Select `" + NAMEID + "` from `" + totoMemoryDB.getDatabaseName() + "`.`" + VALUENAME + "` where `" + VALUEID + "` = :" + VALUEID;
        return jdbcTemplate.queryForList(FIND_EXISTING_LINK, namedParams, Integer.class);

    }

}
