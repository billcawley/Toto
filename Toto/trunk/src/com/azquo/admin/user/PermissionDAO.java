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
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 07/01/14.
 * User permission details
 */
public final class PermissionDAO {

    private static final String PERMISSION = "permission";

    // column names except ID which is in the superclass

    private static final String REPORTID = "report_id";
    public static final String USERID = "user_id";
    private static final String DATABASEID = "database_id";
    private static final String READLIST = "read_list";
    private static final String WRITELIST = "write_list";

    private static Map<String, Object> getColumnNameValueMap(final Permission permission) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, permission.getId());
        toReturn.put(REPORTID, permission.getReportId());
        toReturn.put(USERID, permission.getUserId());
        toReturn.put(DATABASEID, permission.getDatabaseId());
        toReturn.put(READLIST, permission.getReadList());
        toReturn.put(WRITELIST, permission.getWriteList());
        return toReturn;
    }

    // ok need a bit of heavier sql here, a little join

    public static List<Permission> findByBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseDAO.BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters(", `" + StandardDAO.MASTER_DB + "`.`" + DatabaseDAO.DATABASE + "`  WHERE `" + StandardDAO.MASTER_DB + "`.`" + DatabaseDAO.DATABASE + "`." + StandardDAO.ID
                + " = `" + StandardDAO.MASTER_DB + "`.`" + PERMISSION + "`.`" + DATABASEID + "` and  `" + StandardDAO.MASTER_DB + "`.`" + DatabaseDAO.DATABASE + "`.`" + DatabaseDAO.BUSINESSID + "` = :" + DatabaseDAO.BUSINESSID
                ,PERMISSION, permissionRowMapper, namedParams);
    }

    public static Permission findByBusinessUserAndDatabase(User user, Database database) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, user.getId());
        namedParams.addValue(DATABASEID, database.getId());
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE `" + USERID + "` =:" + USERID + " and " + DATABASEID + " =:" + DATABASEID,PERMISSION, permissionRowMapper, namedParams);
    }

/*    public void deleteForBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DatabaseDAO.BUSINESSID, businessId);
        final String SQL_DELETE = "DELETE  from `" + MASTER_DB + "`.`" + getTableName() + "` where `" + DATABASEID + "` =:" + DATABASEID;
        jdbcTemplate.update(SQL_DELETE, namedParams);
    }*/

    private static final class PermissionRowMapper implements RowMapper<Permission> {
        @Override
        public Permission mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Permission(rs.getInt(StandardDAO.ID)
                        , rs.getInt(REPORTID)
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

    private static final PermissionRowMapper permissionRowMapper = new PermissionRowMapper();

    public static List<Permission> findForUserId(final int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + USERID + " = :" + USERID, PERMISSION, permissionRowMapper, namedParams);
    }

    private static Permission findForUserIdAndDatabaseId(final int userId, int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        namedParams.addValue(DATABASEID, databaseId);
        return StandardDAO.findOneWithWhereSQLAndParameters("WHERE " + USERID + " = :" + USERID + " and " + DATABASEID + "= :" + DATABASEID, PERMISSION, permissionRowMapper, namedParams);
    }

    public static void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        StandardDAO.getJdbcTemplate().update("DELETE FROM " + StandardDAO.MASTER_DB + ".`" + PERMISSION + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }

    public final void update(int id, Map<String, Object> parameters) {
        if (id == 0) {
            int dbId = (Integer) parameters.get(DATABASEID);
            int userId = (Integer) parameters.get(USERID);
            Permission p = findForUserIdAndDatabaseId(userId, dbId);
            if (p != null) {
                id = p.getId();
            } else {
                p = new Permission(0, 0, 0, 0, "", "");
                StandardDAO.store(p, PERMISSION, getColumnNameValueMap(p));
                id = p.getId();
            }
        }
        String updateSql = "UPDATE `" + StandardDAO.MASTER_DB + "`.`" + PERMISSION + "` set ";
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        for (String columnName : parameters.keySet()) {
            namedParams.addValue(columnName, parameters.get(columnName));
            updateSql += columnName + "= :" + columnName + ", ";
        }
        namedParams.addValue(StandardDAO.ID, id);
        updateSql = updateSql.substring(0, updateSql.length() - 2); //trim the last ", "
        updateSql += " where " + StandardDAO.ID + " = :" + StandardDAO.ID;
        StandardDAO.getJdbcTemplate().update(updateSql, namedParams);
    }

    public static Permission findById(int id){
        return StandardDAO.findById(id, PERMISSION, permissionRowMapper);
    }

    public static void removeById(Permission permission){
        StandardDAO.removeById(permission, PERMISSION);
    }

    public static void store(Permission permission){
        StandardDAO.store(permission, PERMISSION, getColumnNameValueMap(permission));
    }

}