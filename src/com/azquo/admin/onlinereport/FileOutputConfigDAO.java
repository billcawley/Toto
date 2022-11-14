package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * created 26/10/22
 *
 */
public final class FileOutputConfigDAO {

    private static final String TABLENAME = "file_output_config";

    // column names except ID which is in the superclass

    private static final String BUSINESSID = "business_id";
    private static final String NAME = "name";
    private static final String CONNECTION_STRING = "connection_string";
    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String DATABASE = "database";

    public static Map<String, Object> getColumnNameValueMap(final FileOutputConfig fileOutputConfig) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, fileOutputConfig.getId());
        toReturn.put(BUSINESSID, fileOutputConfig.getBusinessId());
        toReturn.put(NAME, fileOutputConfig.getName());
        toReturn.put(CONNECTION_STRING, fileOutputConfig.getConnectionString());
        toReturn.put(USER, fileOutputConfig.getUser());
        toReturn.put(PASSWORD, fileOutputConfig.getPassword());
        return toReturn;
    }

    public static List<FileOutputConfig> findForBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE " + BUSINESSID + " = :"+BUSINESSID, TABLENAME, fileOutputConfigRowMapper, namedParams);
    }

    private static final class FileOutputConfigRowMapper implements RowMapper<FileOutputConfig> {
        @Override
        public FileOutputConfig mapRow(final ResultSet rs, final int row) {
            try {

                return new FileOutputConfig(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , rs.getString(NAME)
                        , rs.getString(CONNECTION_STRING)
                        , rs.getString(USER)
                        , rs.getString(PASSWORD)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final FileOutputConfigRowMapper fileOutputConfigRowMapper = new FileOutputConfigRowMapper();

    public static FileOutputConfig findForNameAndBusinessId(String name,final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(NAME, name);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE " + NAME + " = :" + NAME + " and " + BUSINESSID + " = :"+BUSINESSID, TABLENAME, fileOutputConfigRowMapper, namedParams);
    }

    public static void removeById(FileOutputConfig connection) {
        StandardDAO.removeById(connection, TABLENAME);
    }

    public static void store(FileOutputConfig connection) {
        StandardDAO.store(connection, TABLENAME, getColumnNameValueMap(connection));
    }

    public static FileOutputConfig findById(int id) {
        return StandardDAO.findById(id, TABLENAME, fileOutputConfigRowMapper);
    }


}