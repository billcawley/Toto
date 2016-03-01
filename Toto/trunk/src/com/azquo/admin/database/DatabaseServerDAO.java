package com.azquo.admin.database;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 05/08/15
 * <p/>
 * the find all and find by id from the superclass should be sufficient. There should be no need for java to modify these records.
 */
public class DatabaseServerDAO extends StandardDAO<DatabaseServer> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "database_server";
    }

    // column names except ID which is in the superclass

    public static final String NAME = "name";
    public static final String IP = "ip";
    public static final String SFTPURL = "sftp_url";

    @Override
    public Map<String, Object> getColumnNameValueMap(DatabaseServer databaseServer) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, databaseServer.getId());
        toReturn.put(NAME, databaseServer.getName());
        toReturn.put(IP, databaseServer.getIp());
        toReturn.put(SFTPURL, databaseServer.getSftpUrl());
        return toReturn;
    }

    public static final class DatabaseServerRowMapper implements RowMapper<DatabaseServer> {
        @Override
        public DatabaseServer mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new DatabaseServer(rs.getInt(ID)
                        , rs.getString(NAME)
                        , rs.getString(IP)
                        , rs.getString(SFTPURL));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<DatabaseServer> getRowMapper() {
        return new DatabaseServerRowMapper();
    }
}