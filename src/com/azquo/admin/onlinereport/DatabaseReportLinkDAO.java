package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

/**
 * Created by edward on 08/04/16.
 * <p>
 * Not much factored off due to its nature.
 */
public class DatabaseReportLinkDAO {

    static final String DATABASE_REPORT_LINK = "database_report_link";

    static final String DATABASE_ID = "database_id";
    static final String REPORT_ID = "report_id";

    private static void unLink(int databaseId, int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASE_ID, databaseId);
        namedParams.addValue(REPORT_ID, reportId);
        StandardDAO.getJdbcTemplate().update("delete from " + StandardDAO.MASTER_DB + "." + DATABASE_REPORT_LINK + " where " + DATABASE_ID + " =:" + DATABASE_ID + " and " + REPORT_ID + " =:" + REPORT_ID, namedParams);
    }

    public static void unLinkReport(int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORT_ID, reportId);
        StandardDAO.getJdbcTemplate().update("delete from " + StandardDAO.MASTER_DB + "." + DATABASE_REPORT_LINK + " where " + REPORT_ID + " =:" + REPORT_ID, namedParams);
    }

    public static void unLinkDatabase(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASE_ID, databaseId);
        StandardDAO.getJdbcTemplate().update("delete from " + StandardDAO.MASTER_DB + "." + DATABASE_REPORT_LINK + " where " + DATABASE_ID + " =:" + DATABASE_ID, namedParams);
    }

    public static void link(int databaseId, int reportId) {
        unLink(databaseId, reportId);
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASE_ID, databaseId);
        namedParams.addValue(REPORT_ID, reportId);
        StandardDAO.getJdbcTemplate().update("insert into " + StandardDAO.MASTER_DB + "." + DATABASE_REPORT_LINK + " values (:" + DATABASE_ID + " ,:" + REPORT_ID + ")", namedParams);
    }

    public static List<Integer> getDatabaseIdsForReportId(int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORT_ID, reportId);
        return StandardDAO.getJdbcTemplate().query("select `" + StandardDAO.MASTER_DB + "`.`" + DATABASE_REPORT_LINK + "`.`" + DATABASE_ID + "` from `" + StandardDAO.MASTER_DB + "`.`" + DATABASE_REPORT_LINK + "` where " + REPORT_ID + " =:" + REPORT_ID, namedParams, (rs, rowNum) -> rs.getInt(1));
    }
}