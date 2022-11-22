package com.azquo.admin.user;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
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
 * Copyright (C) 2022 Azquo Holdings Ltd.
 * <p>
 * Created by WFC on 15/11/22.
 * Roles as in those who login
 */
public class RoleDAO {

    // the default table name for this data.
    private static final String TABLENAME = "Role";

    // column names except ID which is in the superclass

     private static final String BUSINESSID = "business_id";
     private static final String ROLENAME = "role_name";
     private static final String JSONDETAILS = "json_details";
 

    public static Map<String, Object> getColumnNameValueMap(final Role role) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, role.getId());
         toReturn.put(BUSINESSID, role.getBusinessId());
        toReturn.put(ROLENAME, role.getRoleName());
        toReturn.put(JSONDETAILS, role.getJsonDetails());
        return toReturn;
    }

    private static final class RoleRowMapper implements RowMapper<Role> {
        @Override
        public Role mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new Role(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , rs.getString(ROLENAME)
                        , rs.getString(JSONDETAILS)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final RoleRowMapper roleRowMapper = new RoleRowMapper();



    // really to check if a report should be zapped
    public static Role findForRoleName(final int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(ROLENAME, reportId);
        return StandardDAO.findOneWithWhereSQLAndParameters("WHERE " + ROLENAME + " = :" + ROLENAME, TABLENAME, roleRowMapper, namedParams);
    }



    public static Role findById(int id) {
        return StandardDAO.findById(id, TABLENAME, roleRowMapper);
    }

    public static void removeById(Role Role) {
        StandardDAO.removeById(Role, TABLENAME);
    }

    public static void store(Role Role) {
        StandardDAO.store(Role, TABLENAME, getColumnNameValueMap(Role));
    }
}