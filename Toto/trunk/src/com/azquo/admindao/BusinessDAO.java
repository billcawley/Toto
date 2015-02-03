package com.azquo.admindao;

import com.azquo.adminentities.Business;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by cawley on 07/01/14.
 * Like vendor from Feefo I suppose
 */
public final class BusinessDAO extends StandardDAO<Business> {

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @Override
    public String getTableName() {
        return "business";
    }

    // column names (except ID)

    public static final String STARTDATE = "start_date";
    public static final String ENDDATE = "end_date";
    public static final String BUSINESSNAME = "business_name";
    public static final String PARENTID = "parent_id";
    public static final String BUSINESSDETAILS = "business_details";

    @Override
    public Map<String, Object> getColumnNameValueMap(final Business business) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, business.getId());
        toReturn.put(STARTDATE, business.getStartDate());
        toReturn.put(ENDDATE, business.getEndDate());
        toReturn.put(BUSINESSNAME, business.getBusinessName());
        toReturn.put(PARENTID, business.getParentId());
        try {
            toReturn.put(BUSINESSDETAILS, jacksonMapper.writeValueAsString(business.getBusinessDetails()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return toReturn;
    }

    public static final class BusinessRowMapper implements RowMapper<Business> {
        @Override
        public Business mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new Business(rs.getInt(ID)
                        , rs.getDate(STARTDATE)
                        , rs.getDate(ENDDATE)
                        , rs.getString(BUSINESSNAME)
                        , rs.getInt(PARENTID)
                        , jacksonMapper.readValue(rs.getString(BUSINESSDETAILS)
                        , Business.BusinessDetails.class));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Business> getRowMapper() {
        return new BusinessRowMapper();
    }

    public Business findByName(final String businessName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSNAME, businessName);
        return findOneWithWhereSQLAndParameters(" WHERE `" + BUSINESSNAME + "` = :" + BUSINESSNAME, namedParams);
    }
}
