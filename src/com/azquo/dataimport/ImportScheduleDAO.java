package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import net.snowflake.client.jdbc.internal.org.checkerframework.checker.units.qual.C;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by bill on 15/04/14.
 */
public class ImportScheduleDAO {
    // the default table name for this data.
    private static String TABLENAME = "import_schedule";

    // column names except ID which is in the superclass
    private static final String NAME = "name";
    private static final String COUNT = "count";
    private static final String FREQUENCY = "frequency";
    private static final String NEXTDATE = "next_date";
    private static final String BUSINESSID = "business_id";
    private static final String DATABASEID = "database_id";
    private static final String CONNECTORID = "connector_id";
    private static final String USERID = "user_id";
    private static final String SQL = "sql";
    private static final String TEMPLATENAME = "template_name";
    private static final String OUTPUTCONNECTORID = "output_connector_id";
    private static final String NOTES = "notes";

    public static Map<String, Object> getColumnNameValueMap(final ImportSchedule importSchedule) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, importSchedule.getId());
        toReturn.put(NAME, importSchedule.getName());
        toReturn.put(COUNT, importSchedule.getCount());
        toReturn.put(FREQUENCY, importSchedule.getFrequency());
        toReturn.put(NEXTDATE, DateUtils.getDateFromLocalDateTime(importSchedule.getNextDate()));
        toReturn.put(BUSINESSID, importSchedule.getBusinessId());
        toReturn.put(DATABASEID, importSchedule.getDatabaseId());
        toReturn.put(CONNECTORID, importSchedule.getConnectorId());
        toReturn.put(USERID, importSchedule.getUserId());
        toReturn.put(SQL, importSchedule.getSql());
        toReturn.put(TEMPLATENAME, importSchedule.getTemplateName());
        toReturn.put(OUTPUTCONNECTORID, importSchedule.getOutputConnectorId());
        toReturn.put(NOTES, importSchedule.getNotes());
        return toReturn;
    }

    private static final class ImportScheduleRowMapper implements RowMapper<ImportSchedule> {
        @Override
        public ImportSchedule mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new ImportSchedule(rs.getInt(StandardDAO.ID)
                        , rs.getString(NAME)
                        , rs.getInt(COUNT)
                        , rs.getString(FREQUENCY)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(NEXTDATE))
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(DATABASEID)
                        , rs.getInt(CONNECTORID)
                        , rs.getInt(USERID)
                        , rs.getString(SQL)
                        , rs.getString(TEMPLATENAME)
                        , rs.getInt(OUTPUTCONNECTORID)
                        , rs.getString(NOTES)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }


    private static ImportScheduleRowMapper importScheduleRowMapper = new ImportScheduleRowMapper();

    public static ImportSchedule findForIdAndBusinessId(final int id, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(StandardDAO.ID, id);
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + StandardDAO.ID + " = :" + StandardDAO.ID + " and " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, importScheduleRowMapper, namedParams);
    }


    public static ImportSchedule findForNameAndBusinessId(final String name, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NAME, name);
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + NAME + " = :" + NAME + " and " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, importScheduleRowMapper, namedParams);
    }


    public static List<ImportSchedule> findForBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters("  WHERE " + BUSINESSID + " = :" + BUSINESSID + " ORDER BY NAME", TABLENAME, importScheduleRowMapper, namedParams);
    }


    public static List<ImportSchedule> findForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return StandardDAO.findListWithWhereSQLAndParameters("  WHERE " + DATABASEID + " = :" + DATABASEID + " ORDER BY NAME", TABLENAME, importScheduleRowMapper, namedParams);
    }





    public static ImportSchedule findById(int id) {
        return StandardDAO.findById(id, TABLENAME, importScheduleRowMapper);
    }

    public static void removeById(ImportSchedule importSchedule) {
        StandardDAO.removeById(importSchedule, TABLENAME);
    }

    public static void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        StandardDAO.getJdbcTemplate().update("DELETE FROM " + StandardDAO.MASTER_DB + ".`" + TABLENAME + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }


    public static void store(ImportSchedule importSchedule) {
        StandardDAO.store(importSchedule, TABLENAME, getColumnNameValueMap(importSchedule));
    }
}