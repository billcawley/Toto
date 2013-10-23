package com.azquo.toto.dao;

import com.azquo.toto.entity.Label;
import com.azquo.toto.entity.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 10:28
 * Data access for value which is the data value Toto deals with. This class may very much become the workhorse of Toto along with the label DAO.
 * Maybe an equivalent oof the SaleListFactory?
 */
public class ValueDAO extends StandardDAO<Value> {
    // the default table name for this data.
    @Override
    public String getTableName() {
        return "value";
    }

    // column names (except ID)

    public static final String TIMECHANGED = "time_changed";
    public static final String CHANGEID = "change_id";
    public static final String INT = "int";
    public static final String DOUBLE = "double";
    public static final String VARCHAR = "varchar";
    public static final String TEXT = "text";
    public static final String TIMESTAMP = "timestamp";

    // related table and column names

    public static String VALUELABEL = "value_label";
    public static String VALUEID = "value_id";
    public static String LABELID = "label_id";

    @Override
    public Map<String, Object> getColumnNameValueMap(Value value){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, value.getId());
        toReturn.put(TIMECHANGED, value.getTimeChanged());
        toReturn.put(CHANGEID, value.getChangeId());
        toReturn.put(INT, value.getIntValue());
        toReturn.put(DOUBLE, value.getDoubleValue());
        toReturn.put(VARCHAR, value.getVarChar());
        toReturn.put(TEXT, value.getText());
        toReturn.put(TIMESTAMP, value.getTimeStamp());
        return toReturn;
    }

    public static final class ValueRowMapper implements RowMapper<Value> {
        @Override
        public Value mapRow(final ResultSet rs, final int row) throws SQLException {
            final Value value = new Value();
            value.setId(rs.getInt(ID));
            value.setTimeChanged(rs.getDate(TIMECHANGED));
            value.setChangeId(rs.getInt(CHANGEID));
            value.setIntValue(rs.getInt(INT));
            value.setDoubleValue(rs.getInt(DOUBLE));
            value.setVarChar(rs.getString(VARCHAR));
            value.setText(rs.getString(TEXT));
            value.setTimeStamp(rs.getDate(TIMESTAMP));
            return value;
        }
    }

    @Override
    public RowMapper<Value> getRowMapper() {
        return new ValueRowMapper();
    }
/*
    public boolean linkValueToLabel(final Value value, final Label label) throws DataAccessException {
        if (valueLabelLinkExists(value,label)){
            return true;
        } else {
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            namedParams.addValue(VALUEID, value.getId());
            namedParams.addValue(LABELID, label.getId());
            String updateSql = "INSERT INTO `" + VALU + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES (:" + PARENTID + ",:" + CHILDID + ",:" + POSITION + ")";
            jdbcTemplate.update(updateSql, namedParams);
            return true;
        }
    }

    public boolean valueLabelLinkExists(final Value value, final Label label){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        namedParams.addValue(LABELID, label.getId());
        final String FIND_EXISTING_LINK = "Select count(*) from `" + VALUELABEL + "` where `" + VALUEID + "` = :" + VALUEID + " AND `" + LABELID + "` = :" + LABELID;
        return jdbcTemplate.queryForObject(FIND_EXISTING_LINK, namedParams, Integer.class) != 0;
    }
*/

}
