package com.azquo.admin.user;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 22/04/14.
 *
 */
public final class UserRegionOptionsDAO extends StandardDAO<UserRegionOptions> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "user_region_options";
    }

    // column names except ID which is in the superclass

    private static final String USERID = "user_id";
    private static final String REPORTID = "report_id";
    private static final String REGION = "region";
    private static final String HIDE_ROWS = "hide_rows";
    private static final String SORTABLE = "sortable";
    private static final String ROW_LIMIT = "row_limit";
    private static final String COLUMN_LIMIT = "column_limit";
    private static final String SORT_ROW = "sort_row";
    private static final String SORT_ROW_ASC = "sort_row_asc";
    private static final String SORT_COLUMN = "sort_column";
    private static final String SORT_COLUMN_ASC = "sort_column_asc";
    private static final String HIGHLIGHT_DAYS = "highlight_days";

    @Override
    public Map<String, Object> getColumnNameValueMap(UserRegionOptions uro) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, uro.getId());
        toReturn.put(USERID, uro.getUserId());
        toReturn.put(REPORTID, uro.getReportId());
        toReturn.put(REGION, uro.getRegion());
        toReturn.put(HIDE_ROWS, uro.getHideRows());
        toReturn.put(SORTABLE, uro.getSortable());
        toReturn.put(ROW_LIMIT, uro.getRowLimit());
        toReturn.put(COLUMN_LIMIT, uro.getColumnLimit());
        toReturn.put(SORT_ROW, uro.getSortRow());
        toReturn.put(SORT_ROW_ASC, uro.getSortRowAsc());
        toReturn.put(SORT_COLUMN, uro.getSortColumn());
        toReturn.put(SORT_COLUMN_ASC, uro.getSortColumnAsc());
        toReturn.put(HIGHLIGHT_DAYS, uro.getHighlightDays());
        return toReturn;
    }

    private static final class UserRegionOptionRowMapper implements RowMapper<UserRegionOptions> {

        @Override
        public UserRegionOptions mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new UserRegionOptions(rs.getInt(ID)
                        , rs.getInt(USERID)
                        , rs.getInt(REPORTID)
                        , rs.getString(REGION)
                        , rs.getInt(HIDE_ROWS)
                        , rs.getBoolean(SORTABLE)
                        , rs.getInt(ROW_LIMIT)
                        , rs.getInt(COLUMN_LIMIT)
                        , rs.getString(SORT_ROW)
                        , rs.getBoolean(SORT_ROW_ASC)
                        , rs.getString(SORT_COLUMN)
                        , rs.getBoolean(SORT_COLUMN_ASC)
                        , rs.getInt(HIGHLIGHT_DAYS)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<UserRegionOptions> getRowMapper() {
        return new UserRegionOptionRowMapper();
    }

    public UserRegionOptions findForUserIdReportIdAndRegion(final int userId, final int reportId, final String region) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        namedParams.addValue(REPORTID, reportId);
        namedParams.addValue(REGION, region);
        return findOneWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID + " AND `" + REPORTID + "` = :" + REPORTID + " AND `" + REGION + "` = :" + REGION, namedParams);
    }

    public void deleteForReportId(final int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTID, reportId);
        jdbcTemplate.update("DELETE FROM " + MASTER_DB + ".`" + getTableName() + "` where " + REPORTID + " = :" + REPORTID, namedParams);
    }
}