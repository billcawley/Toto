package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.Access;
import com.azquo.toto.adminentities.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 07/01/14.
 */
public class AccessDAO extends StandardDAO<Access>{

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "access";
    }

    public static final String ACTIVE = "active";
    public static final String STARTDATE = "start_date";
    public static final String USERID = "user_id";
    public static final String DATABASEID = "database_id";
    public static final String READLIST = "read_list";
    public static final String WRITELIST = "write_list";

    @Override
    public Map<String, Object> getColumnNameValueMap(Access access){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, access.getId());
        toReturn.put(ACTIVE, access.getActive());
        toReturn.put(STARTDATE, access.getStartDate());
        toReturn.put(USERID, access.getUserId());
        toReturn.put(DATABASEID, access.getDatabaseId());
        toReturn.put(READLIST, access.getReadList());
        toReturn.put(WRITELIST, access.getWriteList());
        return toReturn;
    }

    public static final class AccessRowMapper implements RowMapper<Access> {

        @Override
        public Access mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Access(rs.getInt(ID), rs.getBoolean(ACTIVE),rs.getDate(STARTDATE)
                        ,rs.getInt(USERID),rs.getInt(DATABASEID), rs.getString(READLIST), rs.getString(WRITELIST));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Access> getRowMapper() {
        return new AccessRowMapper();
    }

    public List<Access> findForUserId(int userId){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, USERID);
        return findListWithWhereSQLAndParameters("WHERE " + USERID + " = :" + USERID, namedParams, false);
    }

}