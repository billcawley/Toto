package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.Business;
import com.azquo.toto.adminentities.Database;
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
public class UserDAO extends StandardDAO<User>{

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "user";
    }

    public static final String ACTIVE = "active";
    public static final String STARTDATE = "start_date";
    public static final String BUSINESSID = "business_id";
    public static final String EMAIL = "email";
    public static final String NAME = "name";
    public static final String STATUS = "status";
    public static final String PASSWORD = "password";
    public static final String SALT = "salt";

    @Override
    public Map<String, Object> getColumnNameValueMap(User user){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, user.getId());
        toReturn.put(ACTIVE, user.getActive());
        toReturn.put(STARTDATE, user.getStartDate());
        toReturn.put(BUSINESSID, user.getBusinessId());
        toReturn.put(EMAIL, user.getEmail());
        toReturn.put(NAME, user.getName());
        toReturn.put(STATUS, user.getStatus());
        toReturn.put(PASSWORD, user.getPassword());
        toReturn.put(SALT, user.getSalt());
        return toReturn;
    }

    public static final class UserRowMapper implements RowMapper<User> {

        @Override
        public User mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new User(rs.getInt(ID), rs.getBoolean(ACTIVE),rs.getDate(STARTDATE),rs.getInt(BUSINESSID)
                        ,rs.getString(EMAIL),rs.getString(NAME), rs.getString(STATUS), rs.getString(PASSWORD), rs.getString(SALT));
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

    public User findByEmail(String email){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(EMAIL, email);
        return findOneWithWhereSQLAndParameters(" WHERE `" + MASTER_DB + "`.`" + getTableName() + "`." + EMAIL + "` = :" + EMAIL, namedParams);
    }

    public List<User> findForBusinessId(int businessId){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, BUSINESSID);
        return findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID, namedParams, false);
    }

}