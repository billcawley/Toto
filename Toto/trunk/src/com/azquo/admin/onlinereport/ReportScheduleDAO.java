package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
import com.azquo.admin.user.Permission;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 21/09/15.
 *
 */
public class ReportScheduleDAO {

    private static final String TABLENAME = "report_schedule";
    // column names except ID which is in the superclass
    private static final String PERIOD = "period";
    private static final String RECIPIENTS = "recipients";
    private static final String NEXTDUE = "next_due";
    private static final String DATABASEID = "database_id";
    private static final String REPORTID = "report_id";
    private static final String TYPE = "type";
    private static final String PARAMETERS = "parameters";
    private static final String EMAILSUBJECT = "email_subject";

    public static Map<String, Object> getColumnNameValueMap(final ReportSchedule reportSchedule) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, reportSchedule.getId());
        toReturn.put(PERIOD,  reportSchedule.getPeriod());
        toReturn.put(RECIPIENTS,  reportSchedule.getRecipients());
        toReturn.put(NEXTDUE,  Date.from(reportSchedule.getNextDue().atZone(ZoneId.systemDefault()).toInstant()));
        toReturn.put(DATABASEID, reportSchedule.getDatabaseId());
        toReturn.put(REPORTID, reportSchedule.getReportId());
        toReturn.put(TYPE, reportSchedule.getType());
        toReturn.put(PARAMETERS, reportSchedule.getParameters());
        toReturn.put(EMAILSUBJECT, reportSchedule.getEmailSubject());
        return toReturn;
    }

    private static final class ReportScheduleRowMapper implements RowMapper<ReportSchedule> {
        @Override
        public ReportSchedule mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new ReportSchedule(rs.getInt(StandardDAO.ID)
                        , rs.getString(PERIOD)
                        , rs.getString(RECIPIENTS)
                        , StandardDAO.getLocalDateTimeFromDate(rs.getDate(NEXTDUE))
                        , rs.getInt(DATABASEID)
                        , rs.getInt(REPORTID)
                        , rs.getString(TYPE)
                        , rs.getString(PARAMETERS)
                        , rs.getString(EMAILSUBJECT)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final ReportScheduleRowMapper reportScheduleRowMapper = new ReportScheduleRowMapper();

    public static List<ReportSchedule> findForDatabaseId(final int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + DATABASEID + " = :" + DATABASEID, TABLENAME, reportScheduleRowMapper, namedParams);
    }

    public static List<ReportSchedule> findWhereDueBefore(LocalDateTime due) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NEXTDUE, Date.from(due.atZone(ZoneId.systemDefault()).toInstant()));
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + NEXTDUE + " <= :" + NEXTDUE, TABLENAME, reportScheduleRowMapper, namedParams);
    }

    public static ReportSchedule findById(int id){
        return StandardDAO.findById(id, TABLENAME, reportScheduleRowMapper);
    }

    public static void removeById(ReportSchedule reportSchedule){
        StandardDAO.removeById(reportSchedule, TABLENAME);
    }

    public static void store(ReportSchedule reportSchedule){
        StandardDAO.store(reportSchedule, TABLENAME, getColumnNameValueMap(reportSchedule));
    }


}