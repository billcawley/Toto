package com.azquo.admin.user;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Copyright (C) 2022 Azquo Holdings Ltd.
 * <p>
 * Created by WFC on 22/02/22
 * An entry for event on a report with recording turned on
 */


public class UserEventDAO {

    private static final String TABLENAME = "user_event";

    // column names except ID which is in the superclass

    private static final String DATE = "date_created";
    private static final String BUSINESSID = "business_id";
    private static final String USERID = "user_id";
    private static final String REPORTID = "report_id";
    private static final String EVENT = "event";

    public static Map<String, Object> getColumnNameValueMap(final UserEvent userEvent) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, userEvent.getId());
        toReturn.put(DATE, DateUtils.getDateFromLocalDateTime(userEvent.getDate()));
        toReturn.put(BUSINESSID, userEvent.getBusinessId());
         toReturn.put(USERID, userEvent.getUserId());
        toReturn.put(REPORTID, userEvent.getReportId());
        toReturn.put(EVENT, userEvent.getEvent());
        return toReturn;
    }

    private static final class UserEventRowMapper implements RowMapper<UserEvent> {
        @Override
        public UserEvent mapRow(final ResultSet rs, final int row) {
            // not pretty, just make it work for the moment
            try {
                return new UserEvent(rs.getInt(StandardDAO.ID)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(DATE))
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(USERID)
                        , rs.getInt(REPORTID)
                        , rs.getString(EVENT)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final UserEventDAO.UserEventRowMapper eventRowMapper = new UserEventDAO.UserEventRowMapper();

    public static List<UserEvent> findForBusinessUserAndReport(final int businessId, int userId, int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(USERID, userId);
        namedParams.addValue(REPORTID, reportId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " and "+ USERID + " = :" + USERID + " and " + REPORTID + " = :" + REPORTID + " order by `id` desc", TABLENAME, eventRowMapper, namedParams, 0, 10000);
    }




    public static void removeForReportId(int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTID,reportId);
        StandardDAO.getJdbcTemplate().update("DELETE FROM " + StandardDAO.MASTER_DB + ".`" + TABLENAME + "` where " + REPORTID + " = :" + REPORTID, namedParams);
    }


    public static void store(UserEvent UserEvent) {
        StandardDAO.store(UserEvent, TABLENAME, getColumnNameValueMap(UserEvent));
    }


}
