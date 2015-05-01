package com.azquo.admin.user;

import com.azquo.admin.StandardDAO;
import com.azquo.admin.database.DatabaseDAO;
import com.azquo.admin.database.Database;
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
 * User permission details
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
        toReturn.put(STARTDATE, Date.from(permission.getStartDate().atZone(ZoneId.systemDefault()).toInstant()));
        toReturn.put(ENDDATE, Date.from(permission.getEndDate().atZone(ZoneId.systemDefault()).toInstant()));
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

    public Permission findByBusinessUserAndDatabase(User user, Database database) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, user.getId());
        namedParams.addValue(DATABASEID, database.getId());
        return findOneWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID + " and " + DATABASEID + " =:" + DATABASEID, namedParams);
    }

/*    public void deleteForBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseDAO.BUSINESSID, businessId);
        final String SQL_DELETE = "DELETE  from `" + MASTER_DB + "`.`" + getTableName() + "` where `" + DATABASEID + "` =:" + DATABASEID;
        jdbcTemplate.update(SQL_DELETE, namedParams);
    }*/

    public final class PermissionRowMapper implements RowMapper<Permission> {

        @Override
        public Permission mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Permission(rs.getInt(ID)
                        , getLocalDateTimeFromDate(rs.getDate(STARTDATE))
                        , getLocalDateTimeFromDate(rs.getDate(ENDDATE))
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

    public Permission findForUserIdAndDatabaseId(final int userId, int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        namedParams.addValue(DATABASEID, databaseId);
        return findOneWithWhereSQLAndParameters("WHERE " + USERID + " = :" + USERID + " and " + DATABASEID + "= :" + DATABASEID, namedParams);
    }

    public void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        jdbcTemplate.update("DELETE FROM " + MASTER_DB + ".`" + getTableName() + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }

    public final void update(int id, Map<String, Object> parameters) {
        if (id == 0) {
            int dbId = (Integer) parameters.get("database_id");
            int userId = (Integer) parameters.get("user_id");
            Permission p = findForUserIdAndDatabaseId(userId, dbId);
            if (p != null) {
                id = p.getId();
            } else {
                p = new Permission(0, null, null, 0, 0, "", "");
                store(p);
                id = p.getId();
            }
        }
        String updateSql = "UPDATE `" + MASTER_DB + "`.`" + getTableName() + "` set ";
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        for (String columnName : parameters.keySet()) {
            namedParams.addValue(columnName, parameters.get(columnName));
            updateSql += columnName + "= :" + columnName + ", ";
        }
        namedParams.addValue(ID, id);
        updateSql = updateSql.substring(0, updateSql.length() - 2); //trim the last ", "
        updateSql += " where " + ID + " = :" + ID;
        jdbcTemplate.update(updateSql, namedParams);
    }
}