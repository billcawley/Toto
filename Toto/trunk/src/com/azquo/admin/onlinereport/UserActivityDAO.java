package com.azquo.admin.onlinereport;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2020 Azquo Ltd.
 * <p>
 * Created 12/05/2020 by EFC
 *
 "`id` int(11) NOT NULL AUTO_INCREMENT" +
 ",`business_id` int(11) NOT NULL" +
 ",`user` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL" +
 ",`activity` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL" +
 ",`parameters` text COLLATE utf8_unicode_ci  *
 ts timestamp added
 *
 */
public final class UserActivityDAO {

    private static final String TABLENAME = "user_activity";

    // column names except ID which is in the superclass

    private static final String BUSINESSID = "business_id";
    private static final String USER = "user";
    private static final String ACTIVITY = "activity";
    private static final String PARAMETERS = "parameters";
    private static final String TS = "ts";

    public static Map<String, Object> getColumnNameValueMap(final UserActivity userActivity) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, userActivity.getId());
        toReturn.put(BUSINESSID, userActivity.getBusinessId());
        toReturn.put(USER, userActivity.getUser());
        toReturn.put(ACTIVITY,userActivity.getActivity());
        // rip off the attribute code for the moment
        StringBuilder stringBuilder = new StringBuilder();
        Map<String, String> parameters = userActivity.getParameters();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(StringLiterals.ATTRIBUTEDIVIDER);
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append(StringLiterals.ATTRIBUTEDIVIDER);
            stringBuilder.append(entry.getValue());
        }
        toReturn.put(PARAMETERS, stringBuilder.toString());
        // note - I'm not going to save the timestamp for the mo, let Mysql deal with it . . .
        return toReturn;
    }

    private static final class UserActivityRowMapper implements RowMapper<UserActivity> {
        @Override
        public UserActivity mapRow(final ResultSet rs, final int row) {
            try {

                String[] attsArray = rs.getString(PARAMETERS).split(StringLiterals.ATTRIBUTEDIVIDER);
                Map<String, String> parameters = new HashMap<>();
                for (int i = 0; i < attsArray.length / 2; i++) {
                    parameters.put(attsArray[i * 2], attsArray[(i * 2) + 1]);
                }

                return new UserActivity(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , rs.getString(USER)
                        , rs.getString(ACTIVITY)
                        , parameters
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(TS))
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final UserActivityRowMapper userActivityRowMapper = new UserActivityRowMapper();

    public static UserActivity findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, userActivityRowMapper, namedParams);
    }

    public static UserActivity findMostRecentForUserAndBusinessId(final int businessId, String user) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(USER, user);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE " + BUSINESSID + " = :" + BUSINESSID + " and " + USER + " = :" + USER + " order by ts desc", TABLENAME, userActivityRowMapper, namedParams);
    }

    public static List<UserActivity> findForUserAndBusinessId(final int businessId, String user, int from, int limit) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(USER, user);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE " + BUSINESSID + " = :" + BUSINESSID + " and " + USER + " = :" + USER + " order by ts desc", TABLENAME, userActivityRowMapper, namedParams, from, limit);
    }

    public static void removeById(UserActivity comment) {
        StandardDAO.removeById(comment, TABLENAME);
    }

    public static void store(UserActivity comment) {
        StandardDAO.store(comment, TABLENAME, getColumnNameValueMap(comment));
    }

}