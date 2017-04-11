package com.azquo.admin.user;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 22/04/14.
 * <p>
 * Only supports a subset of the objects parameters. Might need to rethink this at some point.
 */
public final class UserRegionOptionsDAO {

    private static final String TABLENAME = "user_region_options";

    // column names except ID which is in the superclass

    private static final String USERID = "user_id";
    private static final String REPORTID = "report_id";
    private static final String REGION = "region";
    private static final String SORT_ROW = "sort_row";
    private static final String SORT_ROW_ASC = "sort_row_asc";
    private static final String SORT_COLUMN = "sort_column";
    private static final String SORT_COLUMN_ASC = "sort_column_asc";
    private static final String HIGHLIGHT_DAYS = "highlight_days";
    // todo use these
    private static final String ROW_LANGUAGE = "row_language";
    private static final String COLUMN_LANGUAGE = "column_language";

    public static Map<String, Object> getColumnNameValueMap(UserRegionOptions uro) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, uro.getId());
        toReturn.put(USERID, uro.getUserId());
        toReturn.put(REPORTID, uro.getReportId());
        toReturn.put(REGION, uro.getRegion());
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
                // set some defaults for the data not saved in mysql. As mentioned above this feels a little hacky
                return new UserRegionOptions(rs.getInt(StandardDAO.ID)
                        , rs.getInt(USERID)
                        , rs.getInt(REPORTID)
                        , rs.getString(REGION)
                        , 0
                        , 0
                        , 0
                        , false
                        , 0
                        , 0
                        , rs.getString(SORT_ROW)
                        , rs.getBoolean(SORT_ROW_ASC)
                        , rs.getString(SORT_COLUMN)
                        , rs.getBoolean(SORT_COLUMN_ASC)
                        , rs.getInt(HIGHLIGHT_DAYS)
                        , false
                        , null
                        , "" // todo - actually load this from the database!
                        , ""
                        , false
                        , false
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final UserRegionOptionRowMapper userRegionOptionsRowMapper = new UserRegionOptionRowMapper();

    public static UserRegionOptions findForUserIdReportIdAndRegion(final int userId, final int reportId, final String region) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        namedParams.addValue(REPORTID, reportId);
        namedParams.addValue(REGION, region);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID + " AND `" + REPORTID + "` = :" + REPORTID + " AND `" + REGION + "` = :" + REGION, TABLENAME, userRegionOptionsRowMapper, namedParams);
    }

    public static List<UserRegionOptions> findForUserId(final int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID, TABLENAME, userRegionOptionsRowMapper, namedParams);
    }

    public static UserRegionOptions findById(int id) {
        return StandardDAO.findById(id, TABLENAME, userRegionOptionsRowMapper);
    }

    public static void removeById(UserRegionOptions userRegionOptions) {
        StandardDAO.removeById(userRegionOptions, TABLENAME);
    }

    public static void store(UserRegionOptions userRegionOptions) {
        StandardDAO.store(userRegionOptions, TABLENAME, getColumnNameValueMap(userRegionOptions));
    }
}