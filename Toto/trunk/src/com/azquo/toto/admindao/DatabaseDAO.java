package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.Database;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 07/01/14.
 */
public class DatabaseDAO extends StandardDAO<Database>{

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "database";
    }

    public static final String ACTIVE = "active";
    public static final String STARTDATE = "start_date";
    public static final String BUSINESSID = "business_id";
    public static final String NAME = "name";
    public static final String NAMECOUNT = "name_count";
    public static final String VALUECOUNT = "value_count";

    @Override
    public Map<String, Object> getColumnNameValueMap(Database database){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, database.getId());
        toReturn.put(ACTIVE, database.getActive());
        toReturn.put(STARTDATE, database.getStartDate());
        toReturn.put(BUSINESSID, database.getBusinessId());
        toReturn.put(NAME, database.getName());
        toReturn.put(NAMECOUNT, database.getNameCount());
        toReturn.put(VALUECOUNT, database.getValueCount());
        return toReturn;
    }

    public static final class DatabaseRowMapper implements RowMapper<Database> {

        @Override
        public Database mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Database(rs.getInt(ID), rs.getBoolean(ACTIVE),rs.getDate(STARTDATE)
                        ,rs.getInt(BUSINESSID),rs.getString(NAME),rs.getInt(NAMECOUNT), rs.getInt(VALUECOUNT));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Database> getRowMapper() {
        return new DatabaseRowMapper();
    }

    public List<Database> findForBusinessId(int businessId){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, BUSINESSID);
        return findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID, namedParams, false);
    }
}