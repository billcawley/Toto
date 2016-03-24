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
    private static final String DATABASEID = "database_id";
    // edd: hmm, what's going on with database?
    //public static final String DATABASE = "database";
    private static final String REPORTNAME = "report_name";
    private static final String DATABASETYPE = "database_type";
    private static final String REPORTCATEGORY = "report_category";
    private static final String USERSTATUS = "user_status";
    private static final String FILENAME = "filename";
    private static final String EXPLANATION = "explanation";
    private static final String RENDERER = "renderer";
    private static final String ACTIVE = "active";

    @Override
    public Map<String, Object> getColumnNameValueMap(final OnlineReport onlineReport) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, onlineReport.getId());
        toReturn.put(DATECREATED,  Date.from(onlineReport.getDateCreated().atZone(ZoneId.systemDefault()).toInstant()));
        toReturn.put(BUSINESSID, onlineReport.getBusinessId());
        toReturn.put(DATABASEID, onlineReport.getDatabaseId());
        toReturn.put(REPORTNAME, onlineReport.getReportName());
        toReturn.put(DATABASETYPE,onlineReport.getDatabaseType());
        toReturn.put(REPORTCATEGORY,onlineReport.getReportCategory());
        toReturn.put(USERSTATUS, onlineReport.getUserStatus());
        toReturn.put(FILENAME, onlineReport.getFilename());
        toReturn.put(EXPLANATION, onlineReport.getExplanation());
        toReturn.put(RENDERER, onlineReport.getRenderer());
        toReturn.put(ACTIVE, onlineReport.getActive());
        return toReturn;
    }

    private final class OnlineReportRowMapper implements RowMapper<OnlineReport> {
        @Override
        public OnlineReport mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new OnlineReport(rs.getInt(ID)
                        , getLocalDateTimeFromDate(rs.getDate(DATECREATED))
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(DATABASEID)
                        , ""
                        , rs.getString(REPORTNAME)
                        , rs.getString(DATABASETYPE)
                        , rs.getString(REPORTCATEGORY)
                        , rs.getString(USERSTATUS)
                        , rs.getString(FILENAME)
                        , ""
                        , rs.getString(EXPLANATION)
                        , rs.getInt(RENDERER)
                        , rs.getBoolean(ACTIVE));
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
        namedParams.addValue(DATABASEID, databaseId);
        namedParams.addValue(REPORTNAME, reportName);
        namedParams.addValue(ACTIVE, true);
        return findOneWithWhereSQLAndParameters(" WHERE `" + DATABASEID + "` = :" + DATABASEID + " and `" + REPORTNAME + "` = :" + REPORTNAME + " and `" + ACTIVE + "` = :" + ACTIVE, namedParams);
    }

    // EFC : I don't think the string splitting stuff should be in here (todo)
    public List<OnlineReport> findForDatabaseIdAndUserStatus(final int databaseId, String userStatus, String databaseType) {
        if (databaseType == null || databaseType.length() == 0){
            databaseType = "none";
        }
        String[] statuses = userStatus.split(",");
        StringBuilder statusSelect = new StringBuilder("(");
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        for (int count = 0; count < statuses.length; count++) {
            if (count > 0) {
                statusSelect.append(" or ");
            }
            namedParams.addValue(USERSTATUS + count, "%" + statuses[count].trim() + "%");
            statusSelect.append(USERSTATUS + " like :" + USERSTATUS).append(count);
        }
        statusSelect.append(")");
        namedParams.addValue(DATABASEID, databaseId);
        namedParams.addValue(DATABASETYPE, databaseType);
        namedParams.addValue(USERSTATUS, "%" + userStatus + "%");
        namedParams.addValue(ACTIVE, true);
        List<OnlineReport> possibles =  findListWithWhereSQLAndParameters(" WHERE (`" + DATABASETYPE + "` = :" + DATABASETYPE + " OR `" + DATABASEID + "` = :" + DATABASEID + ") and " + ACTIVE + " = :" + ACTIVE + " and " + statusSelect + " order by " + REPORTCATEGORY + ", " + REPORTNAME, namedParams, false);
        List<OnlineReport> toReturn = new ArrayList<>();
        for (OnlineReport possible:possibles){
            String[] foundStatuses = possible.getUserStatus().split(",");
            for (String foundStatus:foundStatuses){
                if (foundStatus.trim().equalsIgnoreCase(userStatus)){
                    toReturn.add(possible);
                    break;
                }
            }

        }
        return toReturn;
    }

    public List<OnlineReport> findForDatabaseId(final int databaseId, String databaseType) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        if (databaseType == null || databaseType.length() == 0){
            databaseType = "none";
        }
        namedParams.addValue(DATABASEID, databaseId);
        namedParams.addValue(DATABASETYPE, databaseType);
        namedParams.addValue(ACTIVE, true);
        return findListWithWhereSQLAndParameters("WHERE " + DATABASETYPE + " = :" + DATABASETYPE + " OR (" + DATABASEID + " = :" + DATABASEID + " and " + ACTIVE + " = :" + ACTIVE + ")", namedParams, false);
    }

    public void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        jdbcTemplate.update("DELETE FROM " + MASTER_DB + ".`" + getTableName() + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }
}