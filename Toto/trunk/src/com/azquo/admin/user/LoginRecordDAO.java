package com.azquo.admin.user;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 07/01/14.
 * Details on each database
 */
public final class LoginRecordDAO extends StandardDAO<LoginRecord> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "login_record";
    }

    // column names except ID which is in the superclass

    public static final String USERID = "user_id";
    public static final String DATABASEID = "database_id";
    public static final String TIME = "time";

    @Override
    public Map<String, Object> getColumnNameValueMap(LoginRecord lir) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, lir.getId());
        toReturn.put(USERID, lir.getUserId());
        toReturn.put(DATABASEID, lir.getDatabaseId());
        toReturn.put(TIME, lir.getTime());
        return toReturn;
    }

    public static final class LoginRecordRowMapper implements RowMapper<LoginRecord> {
        @Override
        public LoginRecord mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new LoginRecord(rs.getInt(ID)
                        , rs.getInt(USERID)
                        , rs.getInt(DATABASEID)
                        , rs.getDate(TIME));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<LoginRecord> getRowMapper() {
        return new LoginRecordRowMapper();
    }

    public List<LoginRecord> findForUserId(final int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return findListWithWhereSQLAndParameters(" WHERE `" + USERID + "` = :" + USERID, namedParams, false);
    }

    public void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        jdbcTemplate.update("DELETE FROM " + MASTER_DB + ".`" + getTableName() + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);

    }
}