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
 * Created by bill on 22/04/14.
 *
 * Modified by edd to remove report specifity, this was per report and included standard options such as sorting etc. Now it does not and options are shared across reports.
 */
public final class UserChoiceDAO extends StandardDAO<UserChoice> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "user_choice";
    }

    // column names except ID which is in the superclass

    public static final String USERID = "user_id";
    public static final String CHOICENAME = "choice_name";
    public static final String CHOICEVALUE = "choice_value";
    public static final String TIME = "time";

    @Override
    public Map<String, Object> getColumnNameValueMap(UserChoice ucr) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, ucr.getId());
        toReturn.put(USERID, ucr.getUserId());
        toReturn.put(CHOICENAME, ucr.getChoiceName());
        toReturn.put(CHOICEVALUE, ucr.getChoiceValue());
        toReturn.put(TIME, ucr.getTime());
        return toReturn;
    }

    public static final class UserChoiceRowMapper implements RowMapper<UserChoice> {

        @Override
        public UserChoice mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new UserChoice(rs.getInt(ID)
                        , rs.getInt(USERID)
                        , rs.getString(CHOICENAME)
                        , rs.getString(CHOICEVALUE)
                        , rs.getDate(TIME));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<UserChoice> getRowMapper() {
        return new UserChoiceRowMapper();
    }

    public UserChoice findForUserIdAndChoice(final int userId, final String choiceName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        namedParams.addValue(CHOICENAME, choiceName);
        return findOneWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID + " AND `" + CHOICENAME + "` = :" + CHOICENAME, namedParams);
    }

    public List<UserChoice> findForUserId(final int userId) {
        //only used by the convert to Azquo_master;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return findListWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID, namedParams, false);
    }

}
