package com.azquo.admin.database;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 05/08/15
 * <p/>
 * the find all and find by id from the superclass should be sufficient. There should be no need for java to modify these records.
 */
public class DatabaseServerDAO {

    private static final String TABLENAME = "database_server";

    // column names except ID which is in the superclass

    private static final String NAME = "name";
    private static final String IP = "ip";
    private static final String SFTPURL = "sftp_url";

    public static Map<String, Object> getColumnNameValueMap(DatabaseServer databaseServer) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, databaseServer.getId());
        toReturn.put(NAME, databaseServer.getName());
        toReturn.put(IP, databaseServer.getIp());
        toReturn.put(SFTPURL, databaseServer.getSftpUrl());
        return toReturn;
    }

    private static final class DatabaseServerRowMapper implements RowMapper<DatabaseServer> {
        @Override
        public DatabaseServer mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new DatabaseServer(rs.getInt(StandardDAO.ID)
                        , rs.getString(NAME)
                        , rs.getString(IP)
                        , rs.getString(SFTPURL));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static DatabaseServerRowMapper databaseServerRowMapper = new DatabaseServerRowMapper();

    public static DatabaseServer findById(int id) {
        return StandardDAO.findById(id, TABLENAME, databaseServerRowMapper);
    }

    public static void removeById(DatabaseServer databaseServer) {
        StandardDAO.removeById(databaseServer, TABLENAME);
    }

    public static void store(DatabaseServer databaseServer) {
        StandardDAO.store(databaseServer, TABLENAME, getColumnNameValueMap(databaseServer));
    }

    public static List<DatabaseServer> findAll() {
        return StandardDAO.findAll(TABLENAME, databaseServerRowMapper);
    }
}