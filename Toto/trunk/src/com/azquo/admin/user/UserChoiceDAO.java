package com.azquo.admin.user;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 22/04/14.
 * <p>
 * Modified by edd to remove report specifity, this was per report and included standard options such as sorting etc. Now it does not and options are shared across reports.
 */
public final class UserChoiceDAO {

    // the default table name for this data.
    private static final String TABLENAME = "user_choice";

    // column names except ID which is in the superclass

    private static final String USERID = "user_id";
    private static final String CHOICENAME = "choice_name";
    private static final String CHOICEVALUE = "choice_value";
    private static final String TIME = "time";

    public static Map<String, Object> getColumnNameValueMap(UserChoice ucr) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, ucr.getId());
        toReturn.put(USERID, ucr.getUserId());
        toReturn.put(CHOICENAME, ucr.getChoiceName());
        toReturn.put(CHOICEVALUE, ucr.getChoiceValue());
        toReturn.put(TIME, DateUtils.getDateFromLocalDateTime(ucr.getTime()));
        return toReturn;
    }

    private static final class UserChoiceRowMapper implements RowMapper<UserChoice> {

        @Override
        public UserChoice mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new UserChoice(rs.getInt(StandardDAO.ID)
                        , rs.getInt(USERID)
                        , rs.getString(CHOICENAME)
                        , rs.getString(CHOICEVALUE)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(TIME)));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final UserChoiceRowMapper userChoiceRowMapper = new UserChoiceRowMapper();

    public static UserChoice findForUserIdAndChoice(final int userId, final String choiceName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        namedParams.addValue(CHOICENAME, choiceName);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID + " AND `" + CHOICENAME + "` = :" + CHOICENAME, TABLENAME, userChoiceRowMapper, namedParams);
    }

    public static List<UserChoice> findForUserId(final int userId) {
        //only used by the convert to Azquo_master;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID, TABLENAME, userChoiceRowMapper, namedParams);
    }

    public static UserChoice findById(int id) {
        return StandardDAO.findById(id, TABLENAME, userChoiceRowMapper);
    }

    public static void removeById(UserChoice userChoice) {
        StandardDAO.removeById(userChoice, TABLENAME);
    }

    public static void store(UserChoice userChoice) {
        StandardDAO.store(userChoice, TABLENAME, getColumnNameValueMap(userChoice));
    }
}