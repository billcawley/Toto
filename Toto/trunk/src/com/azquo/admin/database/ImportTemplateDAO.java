package com.azquo.admin.database;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import com.azquo.admin.onlinereport.DatabaseReportLinkDAO;
import com.azquo.admin.onlinereport.OnlineReport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by bill on 15/04/14.
 */
public class ImportTemplateDAO {
    // the default table name for this data.
    private static String TABLENAME = "import_template";

    // column names except ID which is in the superclass
    private static final String DATECREATED = "date_created";

    private static final String BUSINESSID = "business_id";
    private static final String USERID = "user_id";
    private static final String TEMPLATE_NAME = "template_name";
    private static final String FILENAME = "filename";
    private static final String NOTES = "notes";

    public static Map<String, Object> getColumnNameValueMap(final ImportTemplate importTemplate) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, importTemplate.getId());
        toReturn.put(DATECREATED, DateUtils.getDateFromLocalDateTime(importTemplate.getDateCreated()));
        toReturn.put(BUSINESSID, importTemplate.getBusinessId());
        toReturn.put(USERID, importTemplate.getUserId());
        toReturn.put(TEMPLATE_NAME, importTemplate.getTemplateName());
        toReturn.put(FILENAME, importTemplate.getFilename());
        toReturn.put(NOTES, importTemplate.getNotes());
        return toReturn;
    }

    private static final class ImportTemplateRowMapper implements RowMapper<ImportTemplate> {
        @Override
        public ImportTemplate mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new ImportTemplate(rs.getInt(StandardDAO.ID)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(DATECREATED))
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(USERID)
                        , rs.getString(TEMPLATE_NAME)
                        , rs.getString(FILENAME)
                        , rs.getString(NOTES)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }


    private static ImportTemplateRowMapper importTemplateRowMapper = new ImportTemplateRowMapper();

    public static ImportTemplate findForIdAndBusinessId(final int id, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(StandardDAO.ID, id);
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + StandardDAO.ID + " = :" + StandardDAO.ID + " and " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, importTemplateRowMapper, namedParams);
    }

    public static ImportTemplate findForIdAndUserId(final int id, int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(StandardDAO.ID, id);
        namedParams.addValue(USERID, userId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + StandardDAO.ID + " = :" + StandardDAO.ID + " and " + USERID + " = :" + USERID, TABLENAME, importTemplateRowMapper, namedParams);
    }

    // case insensetive - todo - is this a security concern??
    public static ImportTemplate findForNameAndBusinessId(final String name, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(TEMPLATE_NAME, name + "%");
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + TEMPLATE_NAME + " LIKE :" + TEMPLATE_NAME + " and " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, importTemplateRowMapper, namedParams);
    }

    public static ImportTemplate findForNameAndUserId(final String name, int userId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(TEMPLATE_NAME, name + "%");
        namedParams.addValue(USERID, userId);
        return StandardDAO.findOneWithWhereSQLAndParameters("  WHERE " + TEMPLATE_NAME + " LIKE :" + TEMPLATE_NAME + " and " + USERID + " = :" + USERID, TABLENAME, importTemplateRowMapper, namedParams);
    }

    public static List<ImportTemplate> findForBusinessId(int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters("  WHERE " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, importTemplateRowMapper, namedParams);
    }



    public static ImportTemplate findById(int id) {
        return StandardDAO.findById(id, TABLENAME, importTemplateRowMapper);
    }

    public static void removeById(ImportTemplate importTemplate) {
        StandardDAO.removeById(importTemplate, TABLENAME);
    }

    public static void store(ImportTemplate importTemplate) {
        StandardDAO.store(importTemplate, TABLENAME, getColumnNameValueMap(importTemplate));
    }
}