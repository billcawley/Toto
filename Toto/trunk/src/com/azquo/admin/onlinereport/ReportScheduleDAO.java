package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
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
 * Created by edward on 21/09/15.
 *
 */
public class ReportScheduleDAO extends StandardDAO<ReportSchedule> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "report_schedule";
    }

    // column names except ID which is in the superclass
    public static final String PERIOD = "period";
    public static final String RECIPIENTS = "recipients";
    public static final String NEXTDUE = "next_due";
    public static final String DATABASEID = "database_id";
    public static final String REPORTID = "report_id";
    public static final String TYPE = "type";
    public static final String PARAMETERS = "parameters";
    public static final String EMAILSUBJECT = "email_subject";

    @Override
    public Map<String, Object> getColumnNameValueMap(final ReportSchedule reportSchedule) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, reportSchedule.getId());
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

    public final class ReportScheduleRowMapper implements RowMapper<ReportSchedule> {

        @Override
        public ReportSchedule mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new ReportSchedule(rs.getInt(ID)
                        , rs.getString(PERIOD)
                        , rs.getString(RECIPIENTS)
                        , getLocalDateTimeFromDate(rs.getDate(NEXTDUE))
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

    @Override
    public RowMapper<ReportSchedule> getRowMapper() {
        return new ReportScheduleRowMapper();
    }

    public List<ReportSchedule> findForDatabaseId(final int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return findListWithWhereSQLAndParameters("WHERE " + DATABASEID + " = :" + DATABASEID, namedParams, false);
    }

    public List<ReportSchedule> findWhereDueBefore(LocalDateTime due) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NEXTDUE, Date.from(due.atZone(ZoneId.systemDefault()).toInstant()));
        return findListWithWhereSQLAndParameters("WHERE " + NEXTDUE + " <= :" + NEXTDUE, namedParams, false);
    }

}