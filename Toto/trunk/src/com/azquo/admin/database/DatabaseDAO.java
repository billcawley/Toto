package com.azquo.admin.database;

import com.azquo.DateUtils;
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
 * <p>
 * Created by cawley on 07/01/14.
 * Details on each database
 */
public final class DatabaseDAO {

    // column names except ID which is in the superclass

    private static final String DATABASE = "database"; // need to see it externally

    private static final String BUSINESSID = "business_id";
    private static final String USERID = "user_id";
    private static final String NAME = "name";
    private static final String MYSQLNAME = "mysql_name"; // needs renaming! todo
    private static final String NAMECOUNT = "name_count";
    private static final String VALUECOUNT = "value_count";
    private static final String DATABASESERVERID = "database_server_id";
    private static final String CREATED = "created";
    private static final String LASTPROVENANCE = "last_provenance";
    private static final String AUTOBACKUP = "auto_backup";

    public static Map<String, Object> getColumnNameValueMap(Database database) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, database.getId());
        toReturn.put(BUSINESSID, database.getBusinessId());
        toReturn.put(USERID, database.getUserId());
        toReturn.put(NAME, database.getName());
        toReturn.put(MYSQLNAME, database.getPersistenceName());
        toReturn.put(NAMECOUNT, database.getNameCount());
        toReturn.put(VALUECOUNT, database.getValueCount());
        toReturn.put(DATABASESERVERID, database.getDatabaseServerId());
        toReturn.put(CREATED, DateUtils.getDateFromLocalDateTime(database.getCreated()));
        toReturn.put(LASTPROVENANCE, database.getLastProvenance());
        toReturn.put(AUTOBACKUP, database.getAutoBackup());
        return toReturn;
    }

    private static final class DatabaseRowMapper implements RowMapper<Database> {
        @Override
        public Database mapRow(final ResultSet rs, final int row) {
            try {
                return new Database(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(USERID)
                        , rs.getString(NAME)
                        , rs.getString(MYSQLNAME)
                        , rs.getInt(NAMECOUNT)
                        , rs.getInt(VALUECOUNT)
                        , rs.getInt(DATABASESERVERID),
                        DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(CREATED)),
                        rs.getString(LASTPROVENANCE), rs.getBoolean(AUTOBACKUP));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static DatabaseRowMapper databaseRowMapper = new DatabaseRowMapper();

    public static List<Database> findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE `" + BUSINESSID + "` = :" + BUSINESSID + " order by " + NAME, DATABASE, databaseRowMapper, namedParams);
    }

    public static List<Database> findForUserId(final int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE `" + USERID + "` = :" + USERID + " order by " + NAME, DATABASE, databaseRowMapper, namedParams);
    }

    public static List<Database> findForAutoBackup() {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE `" + AUTOBACKUP + "` = true", DATABASE, databaseRowMapper, namedParams);
    }

    public static Database findForNameAndBusinessId(final String name, final int businessID) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessID);
        namedParams.addValue(NAME, name);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE `" + BUSINESSID + "` =:" + BUSINESSID + " and `" + NAME + "` = :" + NAME, DATABASE, databaseRowMapper, namedParams);
    }

    public static Database findForPersistenceName(final String persistenceName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(MYSQLNAME, persistenceName);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE `" + MYSQLNAME + "` =:" + MYSQLNAME, DATABASE, databaseRowMapper, namedParams);
    }

    public static Database findById(int id) {
        return StandardDAO.findById(id, DATABASE, databaseRowMapper);
    }

    public static void removeById(Database database) {
        StandardDAO.removeById(database, DATABASE);
    }

    public static void store(Database database) {
        StandardDAO.store(database, DATABASE, getColumnNameValueMap(database));
    }
}