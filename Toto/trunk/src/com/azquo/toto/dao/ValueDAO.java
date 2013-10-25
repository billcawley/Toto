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

    public static final String PROVENANCEID = "provenance_id";
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
    public Map<String, Object> getColumnNameValueMap(Value value) {
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
            value.setType(Value.Type.values()[rs.getInt(TYPE)]);
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
        if (valueLabelLinkExists(databaseName, value, label)) {
            return true;
        } else {
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            namedParams.addValue(VALUEID, value.getId());
            namedParams.addValue(LABELID, label.getId());
//            String updateSql = "INSERT INTO `" + databaseName + "`.`" + setDefinitionTable + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES (:" + PARENTID + ",:" + CHILDID + ",:" + POSITION + ")";

            String updateSql = "INSERT INTO `" + databaseName + "`.`" + VALUELABEL + "` (`" + VALUEID + "`,`" + LABELID + "`) VALUES (:" + VALUEID + ",:" + LABELID + ")";
            long track = System.currentTimeMillis();
            jdbcTemplate.update(updateSql, namedParams);
            System.out.println("value label link time : " + (System.currentTimeMillis() - track));
            return true;
        }
    }

    // for speed
    public boolean linkValueToLabels(final String databaseName, final Value value, final List<Label> labels) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "INSERT INTO `" + databaseName + "`.`" + VALUELABEL + "` (`" + VALUEID + "`,`" + LABELID + "`) VALUES ";
        int count = 1;
        for (Label label : labels) {
            // I'm taking off the check - I think it's so rare we'll just let the DB complain
//            if (!valueLabelLinkExists(databaseName, value, label)) {
                updateSql += "(:" + VALUEID + count + ",:" + LABELID + count + "),";
                namedParams.addValue(VALUEID + count, value.getId());
                namedParams.addValue(LABELID + count, label.getId());
                count++;
//            }
        }
        updateSql = updateSql.substring(0, updateSql.length() - 1);
        long track = System.currentTimeMillis();
        jdbcTemplate.update(updateSql, namedParams);
        System.out.println("value label link time : " + (System.currentTimeMillis() - track));
        return true;
    }

    public boolean unlinkValueFromLabel(final String databaseName, final Value value, final Label label) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        namedParams.addValue(LABELID, label.getId());
        String updateSql = "Delete from `" + databaseName + "`.`" + VALUELABEL + "` where `" + VALUEID + "` = :" + VALUEID + " and `" + LABELID + "`:" + LABELID;
        jdbcTemplate.update(updateSql, namedParams);
        return true;
    }

    public boolean unlinkValueFromAnyLabel(final String databaseName, final Value value) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        String updateSql = "Delete from `" + databaseName + "`.`" + VALUELABEL + "` where `" + VALUEID + "` = :" + VALUEID;
        jdbcTemplate.update(updateSql, namedParams);
        return true;
    }

    public boolean valueLabelLinkExists(final String databaseName, final Value value, final Label label) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(VALUEID, value.getId());
        namedParams.addValue(LABELID, label.getId());
        final String FIND_EXISTING_LINK = "Select count(*) from `" + databaseName + "`.`" + VALUELABEL + "` where `" + VALUEID + "` = :" + VALUEID + " AND `" + LABELID + "` = :" + LABELID;
        return jdbcTemplate.queryForObject(FIND_EXISTING_LINK, namedParams, Integer.class) != 0;
    }

    public void setDeleted(String databaseName, Value value) {
        value.setDeleted(true);
        store(databaseName, value);
    }

    // for the moment this is a direct low level function, it doesn't pay attention to label structure

    public List<Value> findForLabels(final String databaseName, final List<Label> labels) {

        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String whereCondition = " where `" + getTableName() + "`." + DELETED + " = :" + DELETED
                + " and `" + getTableName() + "`." + ID + " IN (SELECT `" + VALUEID + "` from `" + databaseName + "`.`" + VALUELABEL + "` where (";
        namedParams.addValue(DELETED, 0);
        int count = 1;
        for (Label label : labels) {
            whereCondition += "`" + LABELID + "` = :" + LABELID + count + " OR ";
            namedParams.addValue(LABELID + count, label.getId());
            count++;
        }
        whereCondition = whereCondition.substring(0, whereCondition.length() - 4);
        whereCondition += ") GROUP BY `" + VALUEID + "` HAVING COUNT(*)>" + labels.size() + ")";
        return findListWithWhereSQLAndParameters(databaseName, whereCondition, namedParams, false);

    }

    // for the moment this is a direct low level function, it doesn't pay attention to label structure

    public List<Value> findForLabels2(final String databaseName, final List<Label> labels) {
        if (labels.isEmpty()) {
            return null;
        }
        // ok, this will be interesting, going to find the label with the smallest values list
        /* actually wait for a moment,
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

        //SELECT sql_no_cache value_id FROM `value_label` WHERE (label_id = 18 or label_id = 62 or label_id = 81 or label_id = 82 or label_id = 104 or label_id = 106 or label_id = 109) group by value_id having count(*)>6
        // another one!
        //SELECT sql_no_cache value.* FROM value WHERE value.id in (select value_id from value_label where label_id = 62 and value_id in (select value_id from value_label where label_id = 81 and value_id in (select value_id from value_label where label_id = 82 and value_id in (select value_id from value_label where label_id = 104 and value_id in (select value_id from value_label where label_id = 109)))))

        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String whereCondition = " where `" + getTableName() + "`." + DELETED + " = :" + DELETED
                + " and `" + getTableName() + "`." + ID + " IN ";
        namedParams.addValue(DELETED, 0);
        int count = 0;
        for (Label label : labels) {
            count++;
            //(select value_id from value_label where label_id = 109)
            if (count == labels.size()){ // the last one
                whereCondition += "(select `" + VALUEID + "` from  `" + databaseName + "`.`" + VALUELABEL + "` where `" + LABELID + "` = :" + LABELID + count + ")";
                namedParams.addValue(LABELID + count, label.getId());
            } else {
                whereCondition += "(select `" + VALUEID + "` from  `" + databaseName + "`.`" + VALUELABEL + "` where `" + LABELID + "` = :" + LABELID + count + " and `" + VALUEID + "` in ";
                namedParams.addValue(LABELID + count, label.getId());
            }
        }
        // need to add the brackets . . .
        for (int i = 1; i < labels.size(); i++){// one less than number of labels
            whereCondition += ")";
        }
        return findListWithWhereSQLAndParameters(databaseName, whereCondition, namedParams, false);


    }

}
