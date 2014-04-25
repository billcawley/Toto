package com.azquo.admindao;


import com.azquo.adminentities.UserChoice;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bill on 22/04/14.
 */
public final class UserChoiceDAO extends  StandardDAO<UserChoice> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "user_choices";
    }

    // column names except ID which is in the superclass

    public static final String USERID = "user_id";
    public static final String REPORTID = "report_id";
    public static final String CHOICENAME = "choice_name";
    public static final String CHOICEVALUE = "choice_value";
    public static final String TIME = "time";

    @Override
    public Map<String, Object> getColumnNameValueMap(UserChoice ucr) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, ucr.getId());
        toReturn.put(USERID, ucr.getUserId());
        toReturn.put(REPORTID, ucr.getReportId());
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
                        , rs.getInt(REPORTID)
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

    public UserChoice findForUserIdReportIdAndChoice(final int userId, final int reportId, final String choiceName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        namedParams.addValue(REPORTID, reportId);
        namedParams.addValue(CHOICENAME, choiceName);
        return findOneWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID + " AND `" + REPORTID + "` = :" + REPORTID + " AND `" + CHOICENAME + "` = :" + CHOICENAME, namedParams);
     }

    public UserChoice findForUserIdAndChoice(final int userId, final String choiceName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        namedParams.addValue(CHOICENAME, choiceName);
        return findOneWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID + " and `" + CHOICENAME + "` = :" + CHOICENAME, namedParams);
    }


}
