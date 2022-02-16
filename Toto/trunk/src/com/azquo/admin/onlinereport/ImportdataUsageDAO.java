package com.azquo.admin.onlinereport;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2020 Azquo Ltd.
 * <p>
 * Created 12/05/2020 by EFC
 *
 "`id` int(11) NOT NULL AUTO_INCREMENT" +
 ",`business_id` int(11) NOT NULL" +
 ",`user` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL" +
 ",`activity` varchar(255) COLLATE utf8_unicode_ci DEFAULT NULL" +
 ",`parameters` text COLLATE utf8_unicode_ci  *
 ts timestamp added
 *
 */
public final class ImportdataUsageDAO {

    private static final String TABLENAME = "importdata_usage";

    // column names except ID which is in the superclass

    private static final String BUSINESSID = "business_id";
    private static final String IMPORTDATANAME = "importdata_name";
    private static final String REPORTID = "report_id";

    public static Map<String, Object> getColumnNameValueMap(final ImportdataUsage importdataUsage) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, importdataUsage.getId());
        toReturn.put(BUSINESSID, importdataUsage.getBusinessId());
        toReturn.put(IMPORTDATANAME, importdataUsage.getImportdataName());
        toReturn.put(REPORTID,importdataUsage.getReportId());
        return toReturn;
    }

    private static final class ImportdataUsageRowMapper implements RowMapper<ImportdataUsage> {
        @Override
        public ImportdataUsage mapRow(final ResultSet rs, final int row) {
            try {

                    return new ImportdataUsage(rs.getInt(StandardDAO.ID)
                          , rs.getInt(BUSINESSID)
                          , rs.getString(IMPORTDATANAME)
                          , rs.getInt(REPORTID)
                  );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final ImportdataUsageRowMapper importdataUsageRowMapper = new ImportdataUsageRowMapper();

    public static List<ImportdataUsage> findForBusinessAndReportID(final int businessId, final int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(REPORTID, reportId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE " + BUSINESSID + " = :" + BUSINESSID + " AND " + REPORTID + " = :" + REPORTID, TABLENAME, importdataUsageRowMapper, namedParams);
    }

    public static List<ImportdataUsage> findForBusinessAndImportdataName(final int businessId,final String importdataName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        namedParams.addValue(REPORTID, importdataName);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE " + BUSINESSID + " = :" + BUSINESSID + " AND " + IMPORTDATANAME + " = :" + IMPORTDATANAME, TABLENAME, importdataUsageRowMapper, namedParams);
    }



    public static void removeById(ImportdataUsage importdataUsage) {
        StandardDAO.removeById(importdataUsage, TABLENAME);
    }

    public static void store(ImportdataUsage comment) {
        StandardDAO.store(comment, TABLENAME, getColumnNameValueMap(comment));
    }

}