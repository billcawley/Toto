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
import java.util.ArrayList;
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
    public static final String TMXMLDATA = "TMXMLData";
    // extra fields Tony added in
    private static final String TMTRACKACT = "TMTrackAct";
    private static final String TMVARCAT3 = "TMVarCat3";
    private static final String TMVARCAT4 = "TMVarCat4";
    private static final String TMAPPKEY = "TMAppKey";


    private static final PairRowMapper userRowMapper = new PairRowMapper();

    // I know the Map is a bit hacky but it fine for this more simple use. See little help in rolling another object
    private static final class PairRowMapper implements RowMapper<Map<String, String>> {
        @Override
        public Map<String, String> mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                Map<String, String> toReturn = new HashMap<>();
                toReturn.put(TRACKMESSKEY, rs.getString(TRACKMESSKEY));
                toReturn.put(TMXMLDATA, rs.getString(TMXMLDATA));
                toReturn.put(TMTRACKACT, rs.getString(TMTRACKACT));
                toReturn.put(TMVARCAT3, rs.getString(TMVARCAT3));
                toReturn.put(TMVARCAT4, rs.getString(TMVARCAT4));
                toReturn.put(TMAPPKEY, rs.getString(TMAPPKEY));
                return toReturn;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    // ok the tracking db is going to be big. Unless I start zapping what's in there I need to select where TRACKMESSKEY > something or it will be selecting 2 million records
    public static List<Map<String, String>> findAll() {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(TRACKMESSKEY, 2_000_000); // greater than 2 million for the moment
//        namedParams.addValue(REPORTID, reportId);
        final String SQL_SELECT = "Select `" + SpreadsheetService.getTrackingDb() + "`.`" + TABLENAME + "`.* from `" + SpreadsheetService.getTrackingDb() + "`.`" + TABLENAME + "` where " + TRACKMESSKEY + " >  :" + TRACKMESSKEY;
        return StandardDAO.getJdbcTemplate().query(SQL_SELECT, namedParams, userRowMapper);
    }

    /*
    public static List<User> findForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + DATABASEID + " = :" + DATABASEID, TABLENAME, userRowMapper, namedParams);
    }*/
}