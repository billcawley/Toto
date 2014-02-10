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
 * User access datails
 */
public final class AccessDAO extends StandardDAO<Access> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "access";
    }

    public static final String STARTDATE = "start_date";
    public static final String ENDDATE = "end_date";
    public static final String USERID = "user_id";
    public static final String DATABASEID = "database_id";
    public static final String READLIST = "read_list";
    public static final String WRITELIST = "write_list";

    @Override
    public Map<String, Object> getColumnNameValueMap(final Access access) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, access.getId());
        toReturn.put(STARTDATE, access.getStartDate());
        toReturn.put(ENDDATE, access.getEndDate());
        toReturn.put(USERID, access.getUserId());
        toReturn.put(DATABASEID, access.getDatabaseId());
        toReturn.put(READLIST, access.getReadList());
        toReturn.put(WRITELIST, access.getWriteList());
        return toReturn;
    }

    // ok need a bit of heavier sql here, probably need to reference some fields and

    public List<Access> findByBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseDAO.BUSINESSID, businessId);
        return findListWithWhereSQLAndParameters(", `master_db`.`database`  WHERE `master_db`.`database`.id = `master_db`.`access`.`database_id` and  `master_db`.`database`.`" + DatabaseDAO.BUSINESSID + "` = :" + DatabaseDAO.BUSINESSID, namedParams, false);
    }

    public static final class AccessRowMapper implements RowMapper<Access> {

        @Override
        public Access mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Access(rs.getInt(ID), rs.getDate(STARTDATE), rs.getDate(ENDDATE)
                        , rs.getInt(USERID), rs.getInt(DATABASEID), rs.getString(READLIST), rs.getString(WRITELIST));
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

    public List<Access> findForUserId(final int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return findListWithWhereSQLAndParameters("WHERE " + USERID + " = :" + USERID, namedParams, false);
    }

}