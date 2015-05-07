package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
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
public class OnlineReportDAO extends StandardDAO<OnlineReport> {
    // the default table name for this data.
    @Override
    public String getTableName() {
        return "online_report";
    }

    // column names except ID which is in the superclass

    public static final String BUSINESSID = "business_id";
    public static final String DATABASEID = "database_id";
    // edd: hmm, what's going on with database?
    //public static final String DATABASE = "database";
    public static final String REPORTNAME = "report_name";
    public static final String REPORTCATEGORY = "report_category";
    public static final String USERSTATUS = "user_status";
    public static final String FILENAME = "filename";
    public static final String EXPLANATION = "explanation";

    @Override
    public Map<String, Object> getColumnNameValueMap(final OnlineReport onlineReport) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, onlineReport.getId());
        toReturn.put(BUSINESSID, onlineReport.getBusinessId());
        toReturn.put(DATABASEID, onlineReport.getDatabaseId());
        toReturn.put(REPORTNAME, onlineReport.getReportName());
        toReturn.put(REPORTCATEGORY,onlineReport.getReportCategory());
        toReturn.put(USERSTATUS, onlineReport.getUserStatus());
        toReturn.put(FILENAME, onlineReport.getFilename());
        toReturn.put(EXPLANATION, onlineReport.getExplanation());
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
                        , rs.getString(REPORTCATEGORY)
                        , rs.getString(USERSTATUS)
                        , rs.getString(FILENAME)
                        , ""
                        , rs.getString(EXPLANATION));
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
        return findOneWithWhereSQLAndParameters(" WHERE `" + DATABASEID + "` = :" + DATABASEID + " and `" + REPORTNAME + "` = :" + REPORTNAME, namedParams);
    }

    public List<OnlineReport> findForBusinessIdAndUserStatus(final int businessId, String userStatus) {
        String[] statuses = userStatus.split(",");
        StringBuilder statusSelect = new StringBuilder("(");
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        for (int count = 0; count < statuses.length; count++) {
            if (count > 0) {
                statusSelect.append(" or ");
            }
            namedParams.addValue(USERSTATUS + count, "%" + statuses[count].trim() + "%");
            statusSelect.append(USERSTATUS + " like :" + USERSTATUS + count);
        }
        statusSelect.append(")");

        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(USERSTATUS, "%" + userStatus + "%");
        return findListWithWhereSQLAndParameters(" WHERE `" + BUSINESSID + "` = :" + BUSINESSID + " and " + statusSelect + " order by " + REPORTCATEGORY + ", " + REPORTNAME, namedParams, false);
    }

    public List<OnlineReport> findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID, namedParams, false);
    }


    public List<OnlineReport> findForDatabaseId(final int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return findListWithWhereSQLAndParameters("WHERE " + DATABASEID + " = :" + DATABASEID, namedParams, false);
    }

    public void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        jdbcTemplate.update("DELETE FROM " + MASTER_DB + ".`" + getTableName() + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }

    public final void update(int id, Map<String, Object> parameters) {
        String updateSql = "UPDATE `" + MASTER_DB + "`.`" + getTableName() + "` set ";
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        for (Map.Entry<String, Object> columnNameValue : parameters.entrySet()) {
            updateSql += "`" + columnNameValue.getKey() + "` = :" + columnNameValue.getKey() + ", ";
            namedParams.addValue(columnNameValue.getKey(), columnNameValue.getValue());
        }
        updateSql = updateSql.substring(0, updateSql.length() - 2); //trim the last ", "
        updateSql += " where " + ID + " = :" + ID;
        namedParams.addValue(ID, id);
        jdbcTemplate.update(updateSql, namedParams);
    }
}


