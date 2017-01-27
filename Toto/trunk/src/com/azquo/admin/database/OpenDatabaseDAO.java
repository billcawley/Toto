package com.azquo.admin.database;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 01/04/14.
 * <p>
 * Recording which databases are being accessed, not actually being used since the report/DB server split, need to get rid of it or start using it again. Todo
 */
public final class OpenDatabaseDAO {

    private static final String TABLENAME = "open_database";

    // column names except ID which is in the superclass

    private static final String DATABASEID = "database_id";
    private static final String OPEN = "open";
    private static final String CLOSE = "close";

    private static Map<String, Object> getColumnNameValueMap(OpenDatabase openDatabase) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, openDatabase.getId());
        toReturn.put(DATABASEID, openDatabase.getDatabaseId());
        toReturn.put(OPEN, openDatabase.getOpen());
        toReturn.put(CLOSE, openDatabase.getClose());
        return toReturn;
    }

    private static final class OpenDatabaseRowMapper implements RowMapper<OpenDatabase> {
        @Override
        public OpenDatabase mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new OpenDatabase(rs.getInt(StandardDAO.ID)
                        , rs.getInt(DATABASEID)
                        , rs.getDate(OPEN)
                        , rs.getDate(CLOSE));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final OpenDatabaseRowMapper openDatabaseRowMapper = new OpenDatabaseRowMapper();

    public static void closeForDatabaseId(final int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(CLOSE, LocalDateTime.now());
        namedParams.addValue(DATABASEID, databaseId);
        StandardDAO.getJdbcTemplate().update(" SET `" + CLOSE + "`=:" + CLOSE + " WHERE `" + CLOSE + "` = '0000-00-00' and `" + DATABASEID + "` = :" + DATABASEID, namedParams);
    }

    public static void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        StandardDAO.getJdbcTemplate().update("DELETE FROM " + StandardDAO.MASTER_DB + ".`" + TABLENAME + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }

    public static List<OpenDatabase> findForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return StandardDAO.findListWithWhereSQLAndParameters("where " + DATABASEID + "` = :" + DATABASEID, TABLENAME, openDatabaseRowMapper, namedParams);
    }

    public static OpenDatabase findById(int id) {
        return StandardDAO.findById(id, TABLENAME, openDatabaseRowMapper);
    }

    public static void removeById(OpenDatabase openDatabase) {
        StandardDAO.removeById(openDatabase, TABLENAME);
    }

    public static void store(OpenDatabase openDatabase) {
        StandardDAO.store(openDatabase, TABLENAME, getColumnNameValueMap(openDatabase));
    }
}