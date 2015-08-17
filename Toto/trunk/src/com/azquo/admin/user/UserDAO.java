package com.azquo.admin.user;

import com.azquo.admin.StandardDAO;
import com.azquo.admin.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 07/01/14.
 * Users as in those who login
 */
public class UserDAO extends StandardDAO<User> {

    @Autowired
    AdminService adminService;

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "user";
    }

    // column names except ID which is in the superclass

    public static final String STARTDATE = "start_date";
    public static final String ENDDATE = "end_date";
    public static final String BUSINESSID = "business_id";
    public static final String EMAIL = "email";
    public static final String NAME = "name";
    public static final String STATUS = "status";
    public static final String PASSWORD = "password";
    public static final String SALT = "salt";

    @Override
    public Map<String, Object> getColumnNameValueMap(final User user) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, user.getId());
        toReturn.put(STARTDATE, Date.from(user.getStartDate().atZone(ZoneId.systemDefault()).toInstant()));
        toReturn.put(ENDDATE, Date.from(user.getEndDate().atZone(ZoneId.systemDefault()).toInstant()));
        toReturn.put(BUSINESSID, user.getBusinessId());
        toReturn.put(EMAIL, user.getEmail());
        toReturn.put(NAME, user.getName());
        toReturn.put(STATUS, user.getStatus());
        toReturn.put(PASSWORD, user.getPassword());
        toReturn.put(SALT, user.getSalt());
        return toReturn;
    }

    public final class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new User(rs.getInt(ID)
                        , getLocalDateTimeFromDate(rs.getDate(STARTDATE))
                        , getLocalDateTimeFromDate(rs.getDate(ENDDATE))
                        , rs.getInt(BUSINESSID)
                        , rs.getString(EMAIL)
                        , rs.getString(NAME)
                        , rs.getString(STATUS)
                        , rs.getString(PASSWORD)
                        , rs.getString(SALT));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<User> getRowMapper() {
        return new UserRowMapper();
    }

    public User findByEmail(final String email) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(EMAIL, email);
        return findOneWithWhereSQLAndParameters(" WHERE `" + EMAIL + "` = :" + EMAIL, namedParams);
    }

    public List<User> findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID, namedParams, false);
    }

    public final void update(int id, int businessId, Map<String, Object> parameters) throws Exception{
        String email = (String) parameters.get("email");
        if (id == 0 && email.length() > 0) {
            User u = findByEmail(email);
            if (u != null) {
                id = u.getId();
            } else {
                u = new User(0, LocalDateTime.now(), LocalDateTime.now(), businessId, "", "", "", "", "");
                store(u);
                id = u.getId();
            }
        }
        String updateSql = "UPDATE `" + MASTER_DB + "`.`" + getTableName() + "` set ";
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        for (String columnName : parameters.keySet()) {
            if (columnName.equals(PASSWORD)) {
                String password = (String) parameters.get(columnName);
                if (password.length() > 0) {
                    final String salt = adminService.shaHash(System.currentTimeMillis() + "salt");
                    password = adminService.encrypt(password, salt);
                    namedParams.addValue(PASSWORD, password);
                    namedParams.addValue(SALT, salt);
                    updateSql += PASSWORD + "= :" + PASSWORD + ", " + SALT + " = :" + SALT + ", ";
                }
            } else {
                namedParams.addValue(columnName, parameters.get(columnName));
                updateSql += columnName + "= :" + columnName + ", ";

            }
        }
        namedParams.addValue(ID, id);
        updateSql = updateSql.substring(0, updateSql.length() - 2); //trim the last ", "
        updateSql += " where " + ID + " = :" + ID;
        jdbcTemplate.update(updateSql, namedParams);
    }
}