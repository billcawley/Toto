package com.azquo.admin.onlinereport;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 15/04/14.
 */
public class OnlineReportDAO {
    // the default table name for this data.
    private static String TABLENAME = "online_report";

    // column names except ID which is in the superclass
    private static final String DATECREATED = "date_created";

    private static final String BUSINESSID = "business_id";
    private static final String USERID = "user_id";
    private static final String REPORTNAME = "report_name";
    private static final String FILENAME = "filename";
    private static final String EXPLANATION = "explanation";
    private static final String CATEGORY = "category";

    public static Map<String, Object> getColumnNameValueMap(final OnlineReport onlineReport) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, onlineReport.getId());
        toReturn.put(DATECREATED, DateUtils.getDateFromLocalDateTime(onlineReport.getDateCreated()));
        toReturn.put(BUSINESSID, onlineReport.getBusinessId());
        toReturn.put(USERID, onlineReport.getUserId());
        toReturn.put(REPORTNAME, onlineReport.getReportName());
        toReturn.put(FILENAME, onlineReport.getFilename());
        toReturn.put(EXPLANATION, onlineReport.getExplanation());
        toReturn.put(CATEGORY, onlineReport.getCategory());
        return toReturn;
    }

    private static final class OnlineReportRowMapper implements RowMapper<OnlineReport> {
        @Override
        public OnlineReport mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new OnlineReport(rs.getInt(StandardDAO.ID)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(DATECREATED))
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(USERID)
                        , ""
                        , rs.getString(REPORTNAME)
                        , rs.getString(FILENAME)
                        , rs.getString(EXPLANATION)
                        , rs.getString(CATEGORY)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }


    private static OnlineReportRowMapper onlineReportRowMapper = new OnlineReportRowMapper();

    public static OnlineReport findForDatabaseIdAndName(final int databaseId, String reportName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseReportLinkDAO.DATABASE_ID, databaseId);
        namedParams.addValue(REPORTNAME, reportName);
        return StandardDAO.findOneWithWhereSQLAndParameters(", `" + StandardDAO.MASTER_DB + "`.`" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "` WHERE " + StandardDAO.ID + " = `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.REPORT_ID
                + "` AND `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.DATABASE_ID + "` = :" + DatabaseReportLinkDAO.DATABASE_ID
                + " and `" + REPORTNAME + "` = :" + REPORTNAME, TABLENAME, onlineReportRowMapper, namedParams);
    }

    public static OnlineReport findForIdAndBusinessId(final int id, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(StandardDAO.ID, id);
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + StandardDAO.ID + " = :" + StandardDAO.ID + " and " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, onlineReportRowMapper, namedParams);
    }

    public static OnlineReport findForIdAndUserId(final int id, int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(StandardDAO.ID, id);
        namedParams.addValue(USERID, userId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + StandardDAO.ID + " = :" + StandardDAO.ID + " and " + USERID + " = :" + USERID, TABLENAME, onlineReportRowMapper, namedParams);
    }

    // case insensetive - todo - is this a security concern??
    public static OnlineReport findForNameAndBusinessId(final String name, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTNAME, name);
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + REPORTNAME + " LIKE :" + REPORTNAME + " and " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, onlineReportRowMapper, namedParams);
    }

    public static OnlineReport findForNameAndUserId(final String name, int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTNAME, name);
        namedParams.addValue(USERID, userId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + REPORTNAME + " LIKE :" + REPORTNAME + " and " + USERID + " = :" + USERID, TABLENAME, onlineReportRowMapper, namedParams);
    }

    public static List<OnlineReport> findForBusinessIdWithNoDatabase(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters("  WHERE " + BUSINESSID + " = :" + BUSINESSID + " and " + StandardDAO.ID + " NOT IN (SELECT `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.REPORT_ID + "` FROM `" + StandardDAO.MASTER_DB + "`.`" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`)", TABLENAME, onlineReportRowMapper, namedParams);
    }

    public static List<OnlineReport> findForDatabaseId(final int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseReportLinkDAO.DATABASE_ID, databaseId);
        return StandardDAO.findListWithWhereSQLAndParameters(", `" + StandardDAO.MASTER_DB + "`.`" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "` WHERE " + StandardDAO.ID + " = `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.REPORT_ID
                + "` AND `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.DATABASE_ID + "` = :" + DatabaseReportLinkDAO.DATABASE_ID
                + " order by " + REPORTNAME, TABLENAME, onlineReportRowMapper, namedParams);
    }

    public static OnlineReport findById(int id) {
        return StandardDAO.findById(id, TABLENAME, onlineReportRowMapper);
    }

    public static void removeById(OnlineReport onlineReport) {
        StandardDAO.removeById(onlineReport, TABLENAME);
    }

    public static void store(OnlineReport onlineReport) {
        StandardDAO.store(onlineReport, TABLENAME, getColumnNameValueMap(onlineReport));
    }
}