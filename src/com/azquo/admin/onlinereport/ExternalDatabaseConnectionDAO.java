package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
import com.azquo.admin.user.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2020 Azquo Ltd.
 * <p>
 * Created 12/05/2020 by EFC
 *
 jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `master_db`.`external_database_connection` (" +
 "`id` int(11) NOT NULL AUTO_INCREMENT" +
 ",`business_id` int(11) NOT NULL" +
 ",`name` varchar(255) COLLATE utf8_unicode_ci not null" +
 ",`connection_string` varchar(1024) COLLATE utf8_unicode_ci not null" +
 ",`user` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL" +
 ",`password` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL" +
 ",`database` text COLLATE utf8_unicode_ci DEFAULT NULL,PRIMARY KEY (`id`)) ENGINE=InnoDB AUTO_INCREMENT=120 DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;", new HashMap<>());
 *
 */
public final class ExternalDatabaseConnectionDAO {

    private static final String TABLENAME = "external_database_connection";

    // column names except ID which is in the superclass

    private static final String BUSINESSID = "business_id";
    private static final String NAME = "name";
    private static final String CONNECTION_STRING = "connection_string";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String DATABASE = "database";

    public static Map<String, Object> getColumnNameValueMap(final ExternalDatabaseConnection externalDatabaseConnection) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, externalDatabaseConnection.getId());
        toReturn.put(BUSINESSID, externalDatabaseConnection.getBusinessId());
        toReturn.put(NAME, externalDatabaseConnection.getName());
        toReturn.put(CONNECTION_STRING, externalDatabaseConnection.getConnectionString());
        toReturn.put(USER, externalDatabaseConnection.getUser());
        toReturn.put(PASSWORD, externalDatabaseConnection.getPassword());
        toReturn.put(DATABASE, externalDatabaseConnection.getDatabase());
        return toReturn;
    }

    public static List<ExternalDatabaseConnection> findForBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE " + BUSINESSID + " = :"+BUSINESSID, TABLENAME, externalDatabaseConnectionRowMapper, namedParams);
    }

    private static final class ExternalDatabaseConnectionRowMapper implements RowMapper<ExternalDatabaseConnection> {
        @Override
        public ExternalDatabaseConnection mapRow(final ResultSet rs, final int row) {
            try {

                return new ExternalDatabaseConnection(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , rs.getString(NAME)
                        , rs.getString(CONNECTION_STRING)
                        , rs.getString(USER)
                        , rs.getString(PASSWORD)
                        , rs.getString(DATABASE)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final ExternalDatabaseConnectionRowMapper externalDatabaseConnectionRowMapper = new ExternalDatabaseConnectionRowMapper();

    public static ExternalDatabaseConnection findForNameAndBusinessId(String name,final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(NAME, name);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE " + NAME + " = :" + NAME + " and " + BUSINESSID + " = :"+BUSINESSID, TABLENAME, externalDatabaseConnectionRowMapper, namedParams);
    }

    public static void removeById(ExternalDatabaseConnection connection) {
        StandardDAO.removeById(connection, TABLENAME);
    }

    public static void store(ExternalDatabaseConnection connection) {
        StandardDAO.store(connection, TABLENAME, getColumnNameValueMap(connection));
    }

    public static ExternalDatabaseConnection findById(int id) {
        return StandardDAO.findById(id, TABLENAME, externalDatabaseConnectionRowMapper);
    }


}