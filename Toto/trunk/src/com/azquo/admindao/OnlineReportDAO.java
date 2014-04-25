package com.azquo.admindao;

import com.azquo.adminentities.OnlineReport;
import com.azquo.adminentities.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bill on 15/04/14.
 */
public class OnlineReportDAO extends StandardDAO<OnlineReport>{


    // the default table name for this data.
    @Override
    public String getTableName() {
        return "online_report";
    }

    // column names except ID which is in the superclass

    public static final String BUSINESSID = "business_id";
    public static final String DATABASEID = "database_id";
    public static final String DATABASE = "database";
    public static final String REPORTNAME = "report_name";
    public static final String USERSTATUS = "user_status";
    public static final String FILENAME = "filename";

    @Override
    public Map<String, Object> getColumnNameValueMap(final OnlineReport onlineReport) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, onlineReport.getId());
        toReturn.put(BUSINESSID, onlineReport.getBusinessId());
        toReturn.put(DATABASEID, onlineReport.getDatabaseId());
        toReturn.put(REPORTNAME, onlineReport.getReportName());
        toReturn.put(USERSTATUS, onlineReport.getUserStatus());
        toReturn.put(FILENAME, onlineReport.getFilename());
        return toReturn;
    }

    public static final class OnlineReportRowMapper implements RowMapper<OnlineReport> {

        @Override
        public OnlineReport mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new OnlineReport(rs.getInt(ID)
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(DATABASEID)
                        , ""
                        , rs.getString(REPORTNAME)
                        , rs.getString(USERSTATUS)
                        , rs.getString(FILENAME));
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
        namedParams.addValue(REPORTNAME,reportName);
        return findOneWithWhereSQLAndParameters(" WHERE `" + DATABASEID + "` = :" + DATABASEID + " and `" + REPORTNAME + "` = :" + REPORTNAME, namedParams);
    }


    public List<OnlineReport> findForBusinessIdAndUserStatus(final int businessId, String userStatus) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(userStatus, userStatus);
        return findListWithWhereSQLAndParameters(" WHERE `" + BUSINESSID + "` = :" + BUSINESSID + " and " + USERSTATUS + " like '%:" + USERSTATUS + "%'", namedParams, false);
    }

    public List<OnlineReport> findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID, namedParams, false);
    }
}


