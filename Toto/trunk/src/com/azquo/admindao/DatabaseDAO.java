package com.azquo.admindao;

import com.azquo.adminentities.Database;
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
 * Created by cawley on 07/01/14.
 * Details on each database
 */
public final class DatabaseDAO extends StandardDAO<Database> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "database";
    }

    // column names except ID which is in the superclass

    public static final String STARTDATE = "start_date";
    public static final String ENDDATE = "end_date";
    public static final String BUSINESSID = "business_id";
    public static final String NAME = "name";
    public static final String MYSQLNAME = "mysql_name";
    public static final String NAMECOUNT = "name_count";
    public static final String VALUECOUNT = "value_count";

    @Override
    public Map<String, Object> getColumnNameValueMap(Database database) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, database.getId());
        toReturn.put(STARTDATE, Date.from(database.getStartDate().atZone(ZoneId.systemDefault()).toInstant()));
        toReturn.put(ENDDATE, Date.from(database.getEndDate().atZone(ZoneId.systemDefault()).toInstant()));
        toReturn.put(BUSINESSID, database.getBusinessId());
        toReturn.put(NAME, database.getName());
        toReturn.put(MYSQLNAME, database.getMySQLName());
        toReturn.put(NAMECOUNT, database.getNameCount());
        toReturn.put(VALUECOUNT, database.getValueCount());
        return toReturn;
    }

    public final class DatabaseRowMapper implements RowMapper<Database> {

        @Override
        public Database mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new Database(rs.getInt(ID)
                        , getLocalDateTimeFromDate(rs.getDate(STARTDATE))
                        , getLocalDateTimeFromDate(rs.getDate(ENDDATE))
                        , rs.getInt(BUSINESSID)
                        , rs.getString(NAME)
                        , rs.getString(MYSQLNAME)
                        , rs.getInt(NAMECOUNT)
                        , rs.getInt(VALUECOUNT));
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

    public List<Database> findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return findListWithWhereSQLAndParameters(" WHERE `" + BUSINESSID + "` = :" + BUSINESSID, namedParams, false);
    }

    public Database findForName(final int businessID, final String name) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessID);
        namedParams.addValue(NAME, name);
        return findOneWithWhereSQLAndParameters(" WHERE `" + BUSINESSID + "` =:" + BUSINESSID + " and `" + NAME + "` = :" + NAME, namedParams);
    }
}