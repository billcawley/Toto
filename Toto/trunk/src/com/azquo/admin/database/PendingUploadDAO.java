package com.azquo.admin.database;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2018 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 23/10/18.
 *
 `id` int(11) NOT NULL AUTO_INCREMENT,
 `business_id` int(11) NOT NULL,
 `date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `statusChangedDate` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `file_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
 `file_path` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
 `source` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
 `status` varchar(50) COLLATE utf8_unicode_ci NOT NULL,
 `parameters` text COLLATE utf8_unicode_ci NOT NULL,
 `database_id` int(11) NOT NULL,
 `user_id` int(11) NOT NULL,
 PRIMARY KEY (`id`)
 *
 *
 */
public final class PendingUploadDAO {

    private static final String TABLENAME = "pending_upload";

    // column names except ID which is in the superclass

    private static final String BUSINESSID = "business_id";
    private static final String DATE = "date";
    private static final String STATUSCHANGEDDATE = "status_changed_date";
    private static final String FILENAME = "file_name";
    private static final String FILEPATH = "file_path";
    private static final String SOURCE = "source";
    private static final String STATUS = "status";
    private static final String PARAMETERS = "parameters";
    private static final String DATABASEID = "database_id";
    private static final String USERID = "user_id";
    private static final String IMPORTRESULT = "import_result";

    public static Map<String, Object> getColumnNameValueMap(final PendingUpload pendingUpload) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, pendingUpload.getId());
        toReturn.put(BUSINESSID, pendingUpload.getBusinessId());
        toReturn.put(DATE, DateUtils.getDateFromLocalDateTime(pendingUpload.getDate()));
        toReturn.put(STATUSCHANGEDDATE, DateUtils.getDateFromLocalDateTime(pendingUpload.getStatusChangedDate()));
        toReturn.put(FILENAME, pendingUpload.getFileName());
        toReturn.put(FILEPATH, pendingUpload.getFilePath());
        toReturn.put(SOURCE, pendingUpload.getSource());
        toReturn.put(STATUS, pendingUpload.getSource());
        toReturn.put(PARAMETERS, pendingUpload.getParametersAsString());
        toReturn.put(DATABASEID, pendingUpload.getDatabaseId());
        toReturn.put(USERID, pendingUpload.getUserId());
        toReturn.put(IMPORTRESULT, pendingUpload.getImportResult());
        return toReturn;
    }

    private static final class PendingUploadRowMapper implements RowMapper<PendingUpload> {
        @Override
        public PendingUpload mapRow(final ResultSet rs, final int row) {
            // not pretty, just make it work for the moment
            try {
                return new PendingUpload(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(DATE))
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(STATUSCHANGEDDATE))
                        , rs.getString(FILENAME)
                        , rs.getString(FILEPATH)
                        , rs.getString(SOURCE)
                        , rs.getString(STATUS)
                        , rs.getString(PARAMETERS)
                        , rs.getInt(DATABASEID)
                        , rs.getInt(USERID)
                        , rs.getString(IMPORTRESULT)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final PendingUploadRowMapper pendingUploadRowMapper = new PendingUploadRowMapper();

    public static List<PendingUpload> findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " order by `id` desc", TABLENAME, pendingUploadRowMapper, namedParams, 0, 10000);
    }

    public static void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        StandardDAO.getJdbcTemplate().update("DELETE FROM " + StandardDAO.MASTER_DB + ".`" + TABLENAME + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }

    public static PendingUpload findById(int id) {
        return StandardDAO.findById(id, TABLENAME, pendingUploadRowMapper);
    }

    public static void removeById(PendingUpload pendingUpload) {
        StandardDAO.removeById(pendingUpload, TABLENAME);
    }

    public static void store(PendingUpload pendingUpload) {
        StandardDAO.store(pendingUpload, TABLENAME, getColumnNameValueMap(pendingUpload));
    }
}