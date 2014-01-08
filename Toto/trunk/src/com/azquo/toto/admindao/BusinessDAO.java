package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.Business;
import com.azquo.toto.adminentities.StandardEntity;
import com.google.gson.Gson;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by cawley on 07/01/14.
 */
public class BusinessDAO extends StandardDAO<Business>{

    static final Gson gson = new Gson();

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "business";
    }

    // column names (except ID)

    public static final String ACTIVE = "active";
    public static final String STARTDATE = "start_date";
    public static final String BUSINESSNAME = "business_name";
    public static final String PARENTID = "parent_id";
    public static final String BUSINESSDETAILS = "business_details";

    @Override
    public Map<String, Object> getColumnNameValueMap(Business business){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, business.getId());
        toReturn.put(ACTIVE, business.getActive());
        toReturn.put(STARTDATE, business.getStartDate());
        toReturn.put(BUSINESSNAME, business.getBusinessName());
        toReturn.put(PARENTID, business.getParentId());
        toReturn.put(BUSINESSDETAILS, gson.toJson(business.getBusinessDetails()));
        return toReturn;
    }

    public static final class BusinessRowMapper implements RowMapper<Business> {

        @Override
        public Business mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Business(rs.getInt(ID), rs.getBoolean(ACTIVE),rs.getDate(STARTDATE)
                        ,rs.getString(BUSINESSNAME),rs.getInt(PARENTID), gson.fromJson(rs.getString(BUSINESSDETAILS), Business.BusinessDetails.class));
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


}
