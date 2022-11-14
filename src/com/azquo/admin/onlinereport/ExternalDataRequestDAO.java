package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExternalDataRequestDAO {

    private static final String TABLENAME = "external_data_request";

    // column names except ID which is in the superclass

    private static final String REPORTID = "report_id";
    private static final String SHEETRANGENAME = "sheet_range_name";
    private static final String CONNECTORID = "connector_id";
    private static final String READSQL = "read_SQL";
    private static final String SAVEKEYFIELD = "save_keyfield";
    private static final String SAVEFILENAME = "save_filename";
    private static final String SAVEINSERTKEYVALUE = "save_insertkey_value";
    private static final String ALLOWDELETE = "allow_delete";

    public static Map<String, Object> getColumnNameValueMap(final ExternalDataRequest externalDataRequest) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, externalDataRequest.getId());
        toReturn.put(REPORTID, externalDataRequest.getReportId());
        toReturn.put(SHEETRANGENAME, externalDataRequest.getSheetRangeName());
        toReturn.put(CONNECTORID, externalDataRequest.getConnectorId());
        toReturn.put(READSQL, externalDataRequest.getReadSQL());
        toReturn.put(SAVEKEYFIELD,externalDataRequest.getSaveKeyfield());
        toReturn.put(SAVEFILENAME, externalDataRequest.getSaveFilename());
        toReturn.put(SAVEINSERTKEYVALUE, externalDataRequest.getSaveInsertKeyValue());
        toReturn.put(ALLOWDELETE, externalDataRequest.getAllowDelete());
        return toReturn;
    }

    private static final class ExternalDataRequestRowMapper implements RowMapper<ExternalDataRequest> {
        @Override
        public ExternalDataRequest mapRow(final ResultSet rs, final int row) {
            try {

                return new ExternalDataRequest(rs.getInt(StandardDAO.ID)
                        , rs.getInt(REPORTID)
                        , rs.getString(SHEETRANGENAME)
                        , rs.getInt(CONNECTORID)
                        , rs.getString(READSQL)
                        , rs.getString(SAVEKEYFIELD)
                        , rs.getString(SAVEFILENAME)
                        , rs.getString(SAVEINSERTKEYVALUE)
                        , rs.getBoolean(ALLOWDELETE)

                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final ExternalDataRequestRowMapper externalDataRequestRowMapper = new ExternalDataRequestRowMapper();

    public static List<ExternalDataRequest> findForReportId(final int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTID, reportId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE "  +   REPORTID + " = :"+REPORTID, TABLENAME, externalDataRequestRowMapper, namedParams);
    }


    public static void removeById(int id) {
        ExternalDataRequest externalDataRequest = ExternalDataRequestDAO.findById(id);
        StandardDAO.removeById(externalDataRequest, TABLENAME);
    }

    public static void store(ExternalDataRequest connection) {
        StandardDAO.store(connection, TABLENAME, getColumnNameValueMap(connection));
    }

    public static ExternalDataRequest findById(int id) {
        return StandardDAO.findById(id, TABLENAME, externalDataRequestRowMapper);
    }


}