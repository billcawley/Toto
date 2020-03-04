package com.azquo.dataimport;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2018 Azquo Ltd.
 * <p>
 * Created by cawley on 23/10/18.
 *
 `id` int(11) NOT NULL,
 `business_id` int(11) NOT NULL,
 `created_date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `processed_date` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 `file_name` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
 `file_path` varchar(255) COLLATE utf8_unicode_ci NOT NULL,
 `created_by_user_id` int(11) NOT NULL,
 `processed_by_user_id` int(11) DEFAULT NULL,
 `database_id` int(11) NOT NULL,
 `import_result_path` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL,
 *
 *
 */
public final class PendingUploadDAO {

    private static final String TABLENAME = "pending_upload";

    // column names except ID which is in the superclass

    private static final String BUSINESSID = "business_id";
    private static final String CREATEDDATE = "created_date";
    private static final String PROCESSEDDATE = "processed_date";
    private static final String FILENAME = "file_name";
    private static final String FILEPATH = "file_path";
    private static final String CREATEDBYUSERID = "created_by_user_id";
    private static final String PROCESSEDBYUSERID = "processed_by_user_id";
    private static final String DATABASEID = "database_id";
    private static final String IMPORTRESULTPATH = "import_result_path";
    private static final String TEAM = "team";

    public static Map<String, Object> getColumnNameValueMap(final PendingUpload pendingUpload) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, pendingUpload.getId());
        toReturn.put(BUSINESSID, pendingUpload.getBusinessId());
        toReturn.put(CREATEDDATE, DateUtils.getDateFromLocalDateTime(pendingUpload.getCreatedDate()));
        toReturn.put(PROCESSEDDATE, DateUtils.getDateFromLocalDateTime(pendingUpload.getProcessedDate()));
        toReturn.put(FILENAME, pendingUpload.getFileName());
        toReturn.put(FILEPATH, pendingUpload.getFilePath());
        toReturn.put(CREATEDBYUSERID, pendingUpload.getCreatedByUserId());
        toReturn.put(PROCESSEDBYUSERID, pendingUpload.getProcessedByUserId());
        toReturn.put(DATABASEID, pendingUpload.getDatabaseId());
        toReturn.put(IMPORTRESULTPATH, pendingUpload.getImportResultPath());
        toReturn.put(TEAM, pendingUpload.getTeam());
        return toReturn;
    }

    private static final class PendingUploadRowMapper implements RowMapper<PendingUpload> {
        @Override
        public PendingUpload mapRow(final ResultSet rs, final int row) {
            // not pretty, just make it work for the moment
            try {
                return new PendingUpload(rs.getInt(StandardDAO.ID)
                        , rs.getInt(BUSINESSID)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(CREATEDDATE))
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(PROCESSEDDATE))
                        , rs.getString(FILENAME)
                        , rs.getString(FILEPATH)
                        , rs.getInt(CREATEDBYUSERID)
                        , rs.getInt(PROCESSEDBYUSERID)
                        , rs.getInt(DATABASEID)
                        , rs.getString(IMPORTRESULTPATH)
                        , rs.getString(TEAM)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final PendingUploadRowMapper pendingUploadRowMapper = new PendingUploadRowMapper();

    public static List<PendingUpload> findForBusinessIdNotProcessed(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " AND " + IMPORTRESULTPATH + " is null order by `id` desc", TABLENAME, pendingUploadRowMapper, namedParams, 0, 10000);
    }

    public static List<PendingUpload> findForBusinessIdAndTeamNotProcessed(final int businessId, String team) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(TEAM, team);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " AND " + TEAM + " = :" + TEAM + " AND " + IMPORTRESULTPATH + " is null order by `id` desc", TABLENAME, pendingUploadRowMapper, namedParams, 0, 10000);
    }

    public static List<PendingUpload> findForBusinessIdAndProcessed(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " AND " + IMPORTRESULTPATH + " is not null order by `" + PROCESSEDDATE + "` desc", TABLENAME, pendingUploadRowMapper, namedParams, 0, 10000);
    }

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