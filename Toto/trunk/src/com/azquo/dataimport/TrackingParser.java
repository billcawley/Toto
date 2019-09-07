package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import com.azquo.admin.user.User;
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
    private static final String TABLENAME = "trackingdb";

    // column names except ID which is in the superclass

    private static final String TRACKMESSKEY = "TrackMessKey";
    private static final String TMXMLDATA = "TMXMLData";

/*
    public static Map<String, Object> getColumnNameValueMap(final User user) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, user.getId());
        toReturn.put(ENDDATE, DateUtils.getDateFromLocalDateTime(user.getEndDate()));
        toReturn.put(BUSINESSID, user.getBusinessId());
        toReturn.put(EMAIL, user.getEmail());
        toReturn.put(NAME, user.getName());
        toReturn.put(STATUS, user.getStatus());
        toReturn.put(PASSWORD, user.getPassword());
        toReturn.put(SALT, user.getSalt());
        toReturn.put(CREATEDBY, user.getCreatedBy());
        toReturn.put(DATABASEID, user.getDatabaseId());
        toReturn.put(REPORTID, user.getReportId());
        toReturn.put(SELECTIONS, user.getSelections());
        toReturn.put(TEAM, user.getTeam());
        return toReturn;
    }

    private static final class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new User(rs.getInt(StandardDAO.ID)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(ENDDATE))
                        , rs.getInt(BUSINESSID)
                        , rs.getString(EMAIL)
                        , rs.getString(NAME)
                        , rs.getString(STATUS)
                        , rs.getString(PASSWORD)
                        , rs.getString(SALT)
                        , rs.getString(CREATEDBY)
                        , rs.getInt(DATABASEID)
                        , rs.getInt(REPORTID)
                        , rs.getString(SELECTIONS)
                        , rs.getString(TEAM)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final UserRowMapper userRowMapper = new UserRowMapper();

    public static User findByEmail(final String email) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(EMAIL, email);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE `" + EMAIL + "` = :" + EMAIL, TABLENAME, userRowMapper, namedParams);
    }

    public static List<User> findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, userRowMapper, namedParams);
    }

    // really to check if a report should be zapped
    public static List<User> findForReportId(final int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTID, reportId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + REPORTID + " = :" + REPORTID, TABLENAME, userRowMapper, namedParams);
    }

    // required when restoring a backup
    public static List<User> findForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + DATABASEID + " = :" + DATABASEID, TABLENAME, userRowMapper, namedParams);
    }


    public static List<User> findForBusinessIdAndCreatedBy(final int businessId, String createdBy) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(CREATEDBY, createdBy);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " and " + CREATEDBY + " = :" + CREATEDBY, TABLENAME, userRowMapper, namedParams);
    }

    public static User findById(int id) {
        return StandardDAO.findById(id, TABLENAME, userRowMapper);
    }

    public static void removeById(User user) {
        StandardDAO.removeById(user, TABLENAME);
    }

    public static void store(User user) {
        StandardDAO.store(user, TABLENAME, getColumnNameValueMap(user));
    }*/
}