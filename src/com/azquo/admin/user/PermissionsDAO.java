package com.azquo.admin.user;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import javax.servlet.http.HttpServletRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2022 Azquo Holdings Ltd.
 * <p>
 * Created by WFC on 15/11/22.
 * Roles as in those who login
 */
public class PermissionsDAO {

    // the default table name for this data.
    private static final String TABLENAME = "Permissions";

    // column names except ID which is in the superclass

    private static final String BUSINESSID = "business_id";
    private static final String ROLENAME = "role_name";
    private static final String SECTION = "section";
    private static final String FIELDNAME = "field_name";
    private static final String NAMEONFILE = "name_on_file";
    private static final String FIELDTYPE = "field_type";
    private static final String FIELDVALUE = "field_value";
    private static final String READONLY = "readonly";


    public static Map<String, Object> getColumnNameValueMap(final Permissions permission) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, permission.getId());
        toReturn.put(BUSINESSID, permission.getBusinessId());
        toReturn.put(ROLENAME, permission.getRoleName());
        toReturn.put(SECTION, permission.getSection());
        toReturn.put(FIELDNAME, permission.getFieldName());
        toReturn.put(NAMEONFILE, permission.getNameOnFile());
        toReturn.put(FIELDTYPE, permission.getFieldType());
        toReturn.put(FIELDVALUE,permission.getFieldValue());
        toReturn.put(READONLY, permission.getIsReadOnly());
        return toReturn;
    }

    private static final class PermissionsRowMapper implements RowMapper<Permissions> {
        @Override
        public Permissions mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new Permissions(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , rs.getString(ROLENAME)
                        , rs.getString(SECTION)
                        , rs.getString(FIELDNAME)
                        , rs.getString(NAMEONFILE)
                        , rs.getString(FIELDTYPE)
                        , rs.getString(FIELDVALUE)
                        , rs.getString(READONLY)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final PermissionsRowMapper permissionsRowMapper = new PermissionsRowMapper();

    public static List<Permissions> findForRoleNameAndBusiness(final String roleName, int businessId) {
        //only used by the convert to Azquo_master;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(ROLENAME, roleName);
        namedParams.addValue(BUSINESSID,businessId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE `" + ROLENAME + "` =:" + ROLENAME + " AND `" + BUSINESSID + "` =:"+ BUSINESSID, TABLENAME, permissionsRowMapper, namedParams);
    }


    public static void deleteForRoleNameAndBusinessId(final String roleName, int businessId) {
        //only used by the convert to Azquo_master;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(ROLENAME, roleName);
        namedParams.addValue(BUSINESSID,businessId);
        StandardDAO.getJdbcTemplate().update("DELETE FROM " + StandardDAO.MASTER_DB + ".`" + TABLENAME + " WHERE `" + ROLENAME + "` =:" + ROLENAME+ " AND `" + BUSINESSID + "` =:"+ BUSINESSID, namedParams);
    }
    public static Permissions findById(int id) {
        return StandardDAO.findById(id, TABLENAME, permissionsRowMapper);
    }

    public static void removeById(Permissions permission) {
        StandardDAO.removeById(permission, TABLENAME);
    }



    public static void store(Permissions permission) {
        StandardDAO.store(permission, TABLENAME, getColumnNameValueMap(permission));
    }


}

