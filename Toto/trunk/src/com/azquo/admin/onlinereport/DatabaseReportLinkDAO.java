package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

/**
 * Created by edward on 08/04/16.
 *
 */
public class DatabaseReportLinkDAO {

    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    static final String DATABASE_REPORT_LINK = "database_report_link";

    static final String DATABASE_ID = "database_id";
    static final String REPORT_ID = "report_id";

    private void unLink(int databaseId, int reportId){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASE_ID, databaseId);
        namedParams.addValue(REPORT_ID, reportId);
        jdbcTemplate.update("delete from " + StandardDAO.MASTER_DB + "." + DATABASE_REPORT_LINK + " where " + DATABASE_ID + " =:" + DATABASE_ID + " and "  + REPORT_ID + " =:" + REPORT_ID, namedParams);
    }

    public void unLinkReport(int reportId){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORT_ID, reportId);
        jdbcTemplate.update("delete from " + StandardDAO.MASTER_DB + "." + DATABASE_REPORT_LINK + " where "  + REPORT_ID + " =:" + REPORT_ID, namedParams);
    }

    public void unLinkDatabase(int databaseId){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASE_ID, databaseId);
        jdbcTemplate.update("delete from " + StandardDAO.MASTER_DB + "." + DATABASE_REPORT_LINK + " where " + DATABASE_ID + " =:" + DATABASE_ID, namedParams);
    }

    public void link(int databaseId, int reportId){
        unLink(databaseId,reportId);
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASE_ID, databaseId);
        namedParams.addValue(REPORT_ID, reportId);
        jdbcTemplate.update("insert into " + StandardDAO.MASTER_DB + "." + DATABASE_REPORT_LINK + " values (:" + DATABASE_ID + " ,:" + REPORT_ID + ")", namedParams);
    }

    public List<Integer> getDatabaseIdsForReportId(int reportId){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORT_ID, reportId);
        return jdbcTemplate.query("select `" + StandardDAO.MASTER_DB + "`.`" + DATABASE_REPORT_LINK + "`.`" + DATABASE_ID + "` from `" + StandardDAO.MASTER_DB + "`.`" + DATABASE_REPORT_LINK + "` where "  + REPORT_ID + " =:" + REPORT_ID, namedParams, (rs, rowNum) -> {
                return rs.getInt(1);
        });
    }
}