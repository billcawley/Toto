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

    public static final String PROVENANCEID = "proovenance_id";
    public static final String TYPE = "type";
    public static final String INT = "int";
    public static final String DOUBLE = "double";
    public static final String VARCHAR = "varchar";
    public static final String TEXT = "text";
    public static final String TIMESTAMP = "timestamp";
    public static final String DELETED = "deleted";

    // related table and column names

    public static String VALUELABEL = "value_label";
    public static String VALUEID = "value_id";
    public static String LABELID = "label_id";

    @Override
    public Map<String, Object> getColumnNameValueMap(Value value){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, value.getId());
        toReturn.put(PROVENANCEID, value.getProvenanceId());
        toReturn.put(TYPE, value.getType().ordinal());
        toReturn.put(INT, value.getIntValue());
        toReturn.put(DOUBLE, value.getDoubleValue());
        toReturn.put(VARCHAR, value.getVarChar());
        toReturn.put(TEXT, value.getText());
        toReturn.put(TIMESTAMP, value.getTimeStamp());
        toReturn.put(DELETED, value.getDeleted());
        return toReturn;
    }

    public static final class ValueRowMapper implements RowMapper<Value> {
        @Override
        public Value mapRow(final ResultSet rs, final int row) throws SQLException {
            final Value value = new Value();
            value.setId(rs.getInt(ID));
            value.setProvenanceId(rs.getInt(PROVENANCEID));
            value.setType(Value.Type.values()[rs.getInt(TYPE)]) ;
            value.setIntValue(rs.getInt(INT));
            value.setDoubleValue(rs.getInt(DOUBLE));
            value.setVarChar(rs.getString(VARCHAR));
            value.setText(rs.getString(TEXT));
            value.setTimeStamp(rs.getDate(TIMESTAMP));
            value.setDeleted(rs.getBoolean(DELETED));
            return value;
        }
    }

    @Override
    public RowMapper<Value> getRowMapper() {
        return new ValueRowMapper();
    }

    public boolean linkValueToLabel(final String databaseName, final Value value, final Label label) throws DataAccessException {
        if (valueLabelLinkExists(databaseName, value,label)){
            return true;
        } else {
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            namedParams.addValue(VALUEID, value.getId());
            namedParams.addValue(LABELID, label.getId());
            String updateSql = "INSERT INTO `" + databaseName + "`.``" + VALUELABEL + "` (`" + VALUEID + "`,`" + LABELID + "`) VALUES (:" + VALUEID + ",:" + LABELID+ ")";
            jdbcTemplate.update(updateSql, namedParams);
            return true;
        }
    }

    public boolean unlinkValueFromLabel(final String databaseName, final Value value, final Label label) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        namedParams.addValue(LABELID, label.getId());
        String updateSql = "Delete from `" + databaseName + "`.``" + VALUELABEL + "` where `" + VALUEID + "` = :" + VALUEID + " and `" + LABELID + "`:" + LABELID;
        jdbcTemplate.update(updateSql, namedParams);
        return true;
    }

    public boolean unlinkValueFromAnyLabel(final String databaseName, final Value value) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        String updateSql = "Delete from `" + databaseName + "`.``" + VALUELABEL + "` where `" + VALUEID + "` = :" + VALUEID;
        jdbcTemplate.update(updateSql, namedParams);
        return true;
    }

    public boolean valueLabelLinkExists(final String databaseName, final Value value, final Label label){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        namedParams.addValue(LABELID, label.getId());
        final String FIND_EXISTING_LINK = "Select count(*) from `" + databaseName + "`.`" + VALUELABEL + "` where `" + VALUEID + "` = :" + VALUEID + " AND `" + LABELID + "` = :" + LABELID;
        return jdbcTemplate.queryForObject(FIND_EXISTING_LINK, namedParams, Integer.class) != 0;
    }

    public void setDeleted(String databaseName, Value value){
        value.setDeleted(true);
        store(databaseName, value);
    }

    // for the moment this is a direct low level function, it doesn't pay attention to label structure

    public List<Value> findForLabels(final String databaseName, final List<Label> labels){
        if (labels.isEmpty()){
            return null;
        }
        // ok, this will be interesting, going to find the label with the smallest values list
        /* not going too use this for the mo, trying something different
        int minFound = -1;
        Label labelToUseForInitialSelect = null;
        for (Label label : labels){
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            namedParams.addValue(LABELID, label.getId());
            final String FIND_NUMBER_WITH_LABEL = "Select count(*) from `" + databaseName + "`.`" + VALUELABEL + "` where `" + LABELID + "` = :" + LABELID;
            int found = jdbcTemplate.queryForObject(FIND_NUMBER_WITH_LABEL, namedParams, Integer.class);
            if (minFound == -1 || found < minFound){
                minFound = found;
                labelToUseForInitialSelect = label;
            }
            if (found == 0){ // a label has no data at all, no point in continuing
                return null;
            }
        }*/

        // handing to MySQL to see what it thinks of it . . .

        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String whereCondition = ", `" + databaseName + "`.`" + VALUELABEL + "` where `" + getTableName() + "`." + ID + " = `" + VALUELABEL + "`.`" + VALUEID + "` AND `" + getTableName() + "`." + DELETED + " = :" + DELETED + "`";
        namedParams.addValue(DELETED, 0);
        int count = 1;
        for (Label label : labels){
            whereCondition += " and `" + VALUELABEL + "`.`" + LABELID + "` = :" + LABELID + count;
            namedParams.addValue(LABELID + count, label.getId());
        }
        return findListWithWhereSQLAndParameters(databaseName, whereCondition, namedParams, false);
    }

}
