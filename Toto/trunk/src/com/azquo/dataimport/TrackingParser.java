package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.TypedPair;
import com.azquo.admin.StandardDAO;
import com.azquo.admin.user.User;
import com.azquo.spreadsheet.SpreadsheetService;
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
 * Created by cawley on 07/01/14.
 * Users as in those who login
 */
public class TrackingParser {

    // the default table name for this data.
    private static final String TABLENAME = "tracking";

    // column names except ID which is in the superclass

    private static final String TRACKMESSKEY = "TrackMessKey";
    private static final String TMXMLDATA = "TMXMLData";

    // maybe move from TypedPair but this is a bit of a hack so maybe neither here nor there . . .
    private static final class PairRowMapper implements RowMapper<TypedPair<String, String>> {
        @Override
        public TypedPair<String, String> mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new TypedPair<>(rs.getString(TRACKMESSKEY)
                        , rs.getString(TMXMLDATA)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final PairRowMapper userRowMapper = new PairRowMapper();

    // really to check if a report should be zapped
    public static List<TypedPair<String, String>> findAll() {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
//        namedParams.addValue(REPORTID, reportId);
        final String SQL_SELECT = "Select `" + SpreadsheetService.getTrackingDb() + "`.`" + TABLENAME + "`.* from `" + SpreadsheetService.getTrackingDb() + "`.`" + TABLENAME + "`";
        return StandardDAO.getJdbcTemplate().query(SQL_SELECT, namedParams, userRowMapper);
    }

    /*
    public static List<User> findForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + DATABASEID + " = :" + DATABASEID, TABLENAME, userRowMapper, namedParams);
    }*/
}