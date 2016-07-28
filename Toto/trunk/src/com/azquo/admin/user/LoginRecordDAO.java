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
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 07/01/14.
 * Records each user login.
 */
public final class LoginRecordDAO {

    // the default table name for this data.
    private static final String TABLENAME = "login_record";

    // column names except ID which is in the superclass

    private static final String USERID = "user_id";
    private static final String DATABASEID = "database_id";
    private static final String TIME = "time";

    public static Map<String, Object> getColumnNameValueMap(LoginRecord lir) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, lir.getId());
        toReturn.put(USERID, lir.getUserId());
        toReturn.put(DATABASEID, lir.getDatabaseId());
        toReturn.put(TIME, lir.getTime());
        return toReturn;
    }

    private static final class LoginRecordRowMapper implements RowMapper<LoginRecord> {
        @Override
        public LoginRecord mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new LoginRecord(rs.getInt(StandardDAO.ID)
                        , rs.getInt(USERID)
                        , rs.getInt(DATABASEID)
                        , rs.getDate(TIME));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final LoginRecordRowMapper loginRecordRowMapper = new LoginRecordRowMapper();

    public static List<LoginRecord> findForUserId(final int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE `" + USERID + "` = :" + USERID, TABLENAME, loginRecordRowMapper, namedParams);
    }

    public static void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        StandardDAO.getJdbcTemplate().update("DELETE FROM " + StandardDAO.MASTER_DB + ".`" + TABLENAME + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }

    public static LoginRecord findById(int id){
        return StandardDAO.findById(id, TABLENAME, loginRecordRowMapper);
    }

    public static void removeById(LoginRecord loginRecord){
        StandardDAO.removeById(loginRecord, TABLENAME);
    }

    public static void store(LoginRecord loginRecord){
        StandardDAO.store(loginRecord, TABLENAME, getColumnNameValueMap(loginRecord));
    }


}