package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.User;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
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
    public static final String EMAIL = "email";
    public static final String NAME = "name";
    public static final String STATUS = "status";
    public static final String PASSWORD = "password";
    public static final String SEED = "seed";

    @Override
    public Map<String, Object> getColumnNameValueMap(User user){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, user.getId());
        toReturn.put(ACTIVE, user.getActive());
        toReturn.put(STARTDATE, user.getStartDate());
        toReturn.put(EMAIL, user.getEmail());
        toReturn.put(NAME, user.getName());
        toReturn.put(STATUS, user.getStatus());
        toReturn.put(PASSWORD, user.getPassword());
        toReturn.put(SEED, user.getSeed());
        return toReturn;
    }

    public static final class UserRowMapper implements RowMapper<User> {

        @Override
        public User mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new User(rs.getInt(ID), rs.getBoolean(ACTIVE),rs.getDate(STARTDATE)
                        ,rs.getString(EMAIL),rs.getString(NAME), rs.getString(STATUS), rs.getString(PASSWORD), rs.getString(SEED));
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
}