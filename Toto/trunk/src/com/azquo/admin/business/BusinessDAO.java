package com.azquo.admin.business;

import com.azquo.admin.StandardDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 07/01/14.
 */
public final class BusinessDAO extends StandardDAO<Business> {

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @Override
    public String getTableName() {
        return "business";
    }

    // column names (except ID)

    private static final String BUSINESSNAME = "business_name";
    private static final String BUSINESSDETAILS = "business_details";

    @Override
    public Map<String, Object> getColumnNameValueMap(final Business business) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, business.getId());
        toReturn.put(BUSINESSNAME, business.getBusinessName());
        try {
            toReturn.put(BUSINESSDETAILS, jacksonMapper.writeValueAsString(business.getBusinessDetails()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return toReturn;
    }

    private final class BusinessRowMapper implements RowMapper<Business> {
        @Override
        public Business mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new Business(rs.getInt(ID)
                        , rs.getString(BUSINESSNAME)
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