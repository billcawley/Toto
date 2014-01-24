package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.UploadRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 07/01/14.
 * record of uploaded files :P
 */
public final class UploadRecordDAO extends StandardDAO<UploadRecord> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "upload_record";
    }


    public static final String DATE = "date";
    public static final String BUSINESSID = "business_id";
    public static final String DATABASEID = "database_id";
    public static final String USERID = "user_id";
    public static final String FILENAME = "file_name";
    public static final String FILETYPE = "file_type";
    public static final String COMMENTS = "comments";

    @Override
    public Map<String, Object> getColumnNameValueMap(final UploadRecord uploadRecord) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, uploadRecord.getId());
        toReturn.put(DATE, uploadRecord.getDate());
        toReturn.put(BUSINESSID, uploadRecord.getBusinessId());
        toReturn.put(DATABASEID, uploadRecord.getDatabaseId());
        toReturn.put(USERID, uploadRecord.getUserId());
        toReturn.put(FILENAME, uploadRecord.getFileName());
        toReturn.put(FILETYPE, uploadRecord.getFileType());
        toReturn.put(COMMENTS, uploadRecord.getComments());
        return toReturn;
    }

    public static final class UploadRecordRowMapper implements RowMapper<UploadRecord> {

        @Override
        public UploadRecord mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new UploadRecord(rs.getInt(ID), rs.getDate(DATE), rs.getInt(BUSINESSID)
                        , rs.getInt(DATABASEID), rs.getInt(USERID), rs.getString(FILENAME), rs.getString(FILETYPE), rs.getString(COMMENTS));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<UploadRecord> getRowMapper() {
        return new UploadRecordRowMapper();
    }

    public List<UploadRecord> findForBusinessId(final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return findListWithWhereSQLAndParameters("WHERE " + BUSINESSID + " = :" + BUSINESSID, namedParams, false);
    }


}