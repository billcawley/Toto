package com.azquo.admindao;

import com.azquo.adminentities.Permission;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 07/01/14.
 * User permission datails
 */
public final class PermissionDAO extends StandardDAO<Permission> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "permission";
    }

    // column names except ID which is in the superclass

    public static final String STARTDATE = "start_date";
    public static final String ENDDATE = "end_date";
    public static final String USERID = "user_id";
    public static final String DATABASEID = "database_id";
    public static final String READLIST = "read_list";
    public static final String WRITELIST = "write_list";

    @Override
    public Map<String, Object> getColumnNameValueMap(final Permission permission) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, permission.getId());
        toReturn.put(STARTDATE, permission.getStartDate());
        toReturn.put(ENDDATE, permission.getEndDate());
        toReturn.put(USERID, permission.getUserId());
        toReturn.put(DATABASEID, permission.getDatabaseId());
        toReturn.put(READLIST, permission.getReadList());
        toReturn.put(WRITELIST, permission.getWriteList());
        return toReturn;
    }

    // ok need a bit of heavier sql here, probably need to reference some fields and
    // TODO : sort out hard coded field names

    public List<Permission> findByBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseDAO.BUSINESSID, businessId);
        return findListWithWhereSQLAndParameters(", `master_db`.`database`  WHERE `master_db`.`database`.id = `master_db`.`permission`.`database_id` and  `master_db`.`database`.`" + DatabaseDAO.BUSINESSID + "` = :" + DatabaseDAO.BUSINESSID, namedParams, false);
    }

    public static final class PermissionRowMapper implements RowMapper<Permission> {

        @Override
        public Permission mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Permission(rs.getInt(ID)
                        , rs.getDate(STARTDATE)
                        , rs.getDate(ENDDATE)
                        , rs.getInt(USERID)
                        , rs.getInt(DATABASEID)
                        , rs.getString(READLIST)
                        , rs.getString(WRITELIST));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Permission> getRowMapper() {
        return new PermissionRowMapper();
    }

    public List<Permission> findForUserId(final int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return findListWithWhereSQLAndParameters("WHERE " + USERID + " = :" + USERID, namedParams, false);
    }

}