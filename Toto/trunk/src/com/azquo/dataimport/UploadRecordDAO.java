package com.azquo.dataimport;

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
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 07/01/14.
 * record of uploaded files
 */
public final class UploadRecordDAO {

    private static final String TABLENAME = "upload_record";

    // column names except ID which is in the superclass

    private static final String DATE = "date";
    private static final String BUSINESSID = "business_id";
    private static final String DATABASEID = "database_id";
    private static final String USERID = "user_id";
    private static final String FILENAME = "file_name";
    private static final String FILETYPE = "file_type";
    private static final String COMMENTS = "comments";
    private static final String TEMPPATH = "temp_path";
    private static final String USERCOMMENT = "user_comment";

    public static Map<String, Object> getColumnNameValueMap(final UploadRecord uploadRecord) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, uploadRecord.getId());
        toReturn.put(DATE, DateUtils.getDateFromLocalDateTime(uploadRecord.getDate()));
        toReturn.put(BUSINESSID, uploadRecord.getBusinessId());
        toReturn.put(DATABASEID, uploadRecord.getDatabaseId());
        toReturn.put(USERID, uploadRecord.getUserId());
        toReturn.put(FILENAME, uploadRecord.getFileName());
        toReturn.put(FILETYPE, uploadRecord.getFileType());
        toReturn.put(COMMENTS, uploadRecord.getComments());
        toReturn.put(TEMPPATH, uploadRecord.getTempPath());
        toReturn.put(USERCOMMENT, uploadRecord.getUserComment());
        return toReturn;
    }

    private static final class UploadRecordRowMapper implements RowMapper<UploadRecord> {
        @Override
        public UploadRecord mapRow(final ResultSet rs, final int row) {
            // not pretty, just make it work for the moment
            try {
                return new UploadRecord(rs.getInt(StandardDAO.ID)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(DATE))
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(DATABASEID)
                        , rs.getInt(USERID)
                        , rs.getString(FILENAME)
                        , rs.getString(FILETYPE)
                        , rs.getString(COMMENTS)
                        , rs.getString(TEMPPATH)
                        , rs.getString(USERCOMMENT)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final UploadRecordRowMapper uploadRowMapper = new UploadRecordRowMapper();

    public static List<UploadRecord> findForBusinessId(final int businessId, boolean withAutos) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        if (withAutos){
            return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " order by `id` desc", TABLENAME, uploadRowMapper, namedParams, 0, 10000);
        } else {
            return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " and user_id > 0 order by `id` desc", TABLENAME, uploadRowMapper, namedParams, 0, 10000);
        }
    }

    public static List<UploadRecord> findForDatabaseIdWithFileType(final int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + DATABASEID + " = :" + DATABASEID + " and length(" + FILETYPE + ") > 0 order by `id` desc", TABLENAME, uploadRowMapper, namedParams, 0, 10000);
    }

/*    public static UploadRecord findForBusinessIdAndFileName(final int businessId, String fileName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(FILENAME, fileName);
        return StandardDAO.findOneWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID + " and " + FILENAME + " = :" + FILENAME + " order by `id` desc", TABLENAME, uploadRowMapper, namedParams);
    }


    public static List<UploadRecord> findForUserId(final int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(USERID, userId);
        return StandardDAO.findListWithWhereSQLAndParameters("WHERE " + USERID + " = :" + USERID + " order by `id` desc", TABLENAME, uploadRowMapper, namedParams, 0, 100);
    }*/

    public static void removeForDatabaseId(int databaseId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(DATABASEID, databaseId);
        StandardDAO.getJdbcTemplate().update("DELETE FROM " + StandardDAO.MASTER_DB + ".`" + TABLENAME + "` where " + DATABASEID + " = :" + DATABASEID, namedParams);
    }

    public static UploadRecord findById(int id) {
        return StandardDAO.findById(id, TABLENAME, uploadRowMapper);
    }

    public static void removeById(UploadRecord uploadRecord) {
        StandardDAO.removeById(uploadRecord, TABLENAME);
    }

    public static void store(UploadRecord uploadRecord) {
        StandardDAO.store(uploadRecord, TABLENAME, getColumnNameValueMap(uploadRecord));
    }
}