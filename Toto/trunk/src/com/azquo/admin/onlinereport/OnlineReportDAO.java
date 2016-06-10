package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 15/04/14.
 *
 */
public class OnlineReportDAO extends StandardDAO<OnlineReport> {
    // the default table name for this data.
    @Override
    public String getTableName() {
        return "online_report";
    }

    // column names except ID which is in the superclass
    private static final String  DATECREATED = "date_created";

    private static final String BUSINESSID = "business_id";
    private static final String REPORTNAME = "report_name";
//    private static final String DATABASETYPE = "database_type";
    private static final String REPORTCATEGORY = "report_category";
//    private static final String USERSTATUS = "user_status";
    private static final String FILENAME = "filename";
    private static final String EXPLANATION = "explanation";
    private static final String RENDERER = "renderer";

    @Override
    public Map<String, Object> getColumnNameValueMap(final OnlineReport onlineReport) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, onlineReport.getId());
        toReturn.put(DATECREATED,  Date.from(onlineReport.getDateCreated().atZone(ZoneId.systemDefault()).toInstant()));
        toReturn.put(BUSINESSID, onlineReport.getBusinessId());
        toReturn.put(REPORTNAME, onlineReport.getReportName());
        toReturn.put(REPORTCATEGORY,onlineReport.getReportCategory());
        toReturn.put(FILENAME, onlineReport.getFilename());
        toReturn.put(EXPLANATION, onlineReport.getExplanation());
        toReturn.put(RENDERER, onlineReport.getRenderer());
        return toReturn;
    }

    private final class OnlineReportRowMapper implements RowMapper<OnlineReport> {
        @Override
        public OnlineReport mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new OnlineReport(rs.getInt(ID)
                        , getLocalDateTimeFromDate(rs.getDate(DATECREATED))
                        , rs.getInt(BUSINESSID)
                        , ""
                        , rs.getString(REPORTNAME)
                        , rs.getString(REPORTCATEGORY)
                        , rs.getString(FILENAME)
                        , ""
                        , rs.getString(EXPLANATION)
                        , rs.getInt(RENDERER));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<OnlineReport> getRowMapper() {
        return new OnlineReportRowMapper();
    }

    public OnlineReport findForDatabaseIdAndName(final int databaseId, String reportName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseReportLinkDAO.DATABASE_ID, databaseId);
        namedParams.addValue(REPORTNAME, reportName);
        return findOneWithWhereSQLAndParameters(", `" + StandardDAO.MASTER_DB + "`.`" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "` WHERE " + ID + " = `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.REPORT_ID
                +  "` AND `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.DATABASE_ID + "` = :" + DatabaseReportLinkDAO.DATABASE_ID
                + " and `" + REPORTNAME + "` = :" + REPORTNAME, namedParams);
    }

    public OnlineReport findForIdAndBusinessId(final int id, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(ID, id);
        namedParams.addValue(BUSINESSID, businessId);
        return findOneWithWhereSQLAndParameters("  WHERE " + ID + " = :" + ID + " and " + BUSINESSID + " = :" + BUSINESSID, namedParams);
    }

    // case insensetive - todo - is this a security concern??
    public OnlineReport findForNameAndBusinessId(final String name, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTNAME, name);
        namedParams.addValue(BUSINESSID, businessId);
        return findOneWithWhereSQLAndParameters("  WHERE " + REPORTNAME + " LIKE :" + REPORTNAME + " and " + BUSINESSID + " = :" + BUSINESSID, namedParams);
    }

    public List<OnlineReport> findForBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return findListWithWhereSQLAndParameters("  WHERE " + BUSINESSID + " = :" + BUSINESSID, namedParams, false);
    }

    public List<OnlineReport> findForDatabaseId(final int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseReportLinkDAO.DATABASE_ID, databaseId);
        return findListWithWhereSQLAndParameters(", `" + StandardDAO.MASTER_DB + "`.`" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "` WHERE " + ID + " = `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.REPORT_ID
                +  "` AND `" + DatabaseReportLinkDAO.DATABASE_REPORT_LINK + "`.`" + DatabaseReportLinkDAO.DATABASE_ID + "` = :" + DatabaseReportLinkDAO.DATABASE_ID
                + " order by " + REPORTNAME, namedParams, false);
    }
}