package com.azquo.admindao;

import com.azquo.adminentities.OpenDatabase;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by bill on 01/04/14.
 */
public final class OpenDatabaseDAO extends StandardDAO<OpenDatabase> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "open_database";
    }

    // column names except ID which is in the superclass

    public static final String DATABASEID = "database_id";
    public static final String OPEN = "open";
    public static final String CLOSE = "close";

    @Override
    public Map<String, Object> getColumnNameValueMap(OpenDatabase openDatabase) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, openDatabase.getId());
        toReturn.put(DATABASEID, openDatabase.getDatabaseId());
        toReturn.put(OPEN, openDatabase.getOpen());
        toReturn.put(CLOSE, openDatabase.getClose());
        return toReturn;
    }

    public static final class OpenDatabaseRowMapper implements RowMapper<OpenDatabase> {

        @Override
        public OpenDatabase mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new OpenDatabase(rs.getInt(ID)
                        , rs.getInt(DATABASEID)
                        , rs.getDate(OPEN)
                        , rs.getDate(CLOSE));
               } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<OpenDatabase> getRowMapper() {
        return new OpenDatabaseRowMapper();
    }

    public void closeForDatabaseId(final int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(CLOSE, new Date());
        namedParams.addValue(DATABASEID, databaseId);
        update(" SET `" + CLOSE + "`=:" + CLOSE + " WHERE `" + CLOSE + "` = '0000-00-00' and `" + DATABASEID + "` = :" + DATABASEID, namedParams);
    }

    public void removeForDatabaseId(int databaseId){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        jdbcTemplate.update("DELETE FROM " + MASTER_DB + ".`" + getTableName() + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);

    }



}