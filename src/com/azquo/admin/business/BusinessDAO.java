package com.azquo.admin.business;

import com.azquo.admin.StandardDAO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd.
 *
 * Created by cawley on 07/01/14.
 */
public final class BusinessDAO {

    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    private static final String TABLENAME = "business";

    // column names (except ID)

    private static final String BUSINESSNAME = "business_name";
    private static final String BUSINESSDETAILS = "business_details";
    private static final String BANNERCOLOR = "banner_color";
    private static final String RIBBONCOLOR = "ribbon_color";
    private static final String RIBBONLINKCOLOR = "ribbon_link_color";
    private static final String SIDEMENUCOLOR = "side_menu_color";
    private static final String SIDEMENULINKCOLOR = "side_menu_link_color";
    private static final String LOGO = "logo";
    private static final String CORNERLOGO = "corner_logo";
    private static final String SERVERNAME = "server_name";
    private static final String NEWDESIGN = "new_design";

    public static Map<String, Object> getColumnNameValueMap(final Business business) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, business.getId());
        toReturn.put(BUSINESSNAME, business.getBusinessName());
        try {
            toReturn.put(BUSINESSDETAILS, jacksonMapper.writeValueAsString(business.getBusinessDetails()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        toReturn.put(BANNERCOLOR, business.getBannerColor());
        toReturn.put(RIBBONCOLOR, business.getRibbonColor());
        toReturn.put(RIBBONLINKCOLOR, business.getRibbonLinkColor());
        toReturn.put(SIDEMENUCOLOR, business.getSideMenuColor());
        toReturn.put(SIDEMENULINKCOLOR, business.getSideMenuLinkColor());
        toReturn.put(LOGO, business.getLogo());
        toReturn.put(CORNERLOGO, business.getCornerLogo());
        toReturn.put(SERVERNAME, business.getServerName());
        toReturn.put(NEWDESIGN, business.isNewDesign());
        return toReturn;
    }

    private static class BusinessRowMapper implements RowMapper<Business> {
        @Override
        public Business mapRow(final ResultSet rs, final int row) {
            try {
                return new Business(rs.getInt(StandardDAO.ID)
                        , rs.getString(BUSINESSNAME)
                        , jacksonMapper.readValue(rs.getString(BUSINESSDETAILS)
                        , Business.BusinessDetails.class)
                        , rs.getString(BANNERCOLOR)
                        , rs.getString(RIBBONCOLOR)
                        , rs.getString(RIBBONLINKCOLOR)
                        , rs.getString(SIDEMENUCOLOR)
                        , rs.getString(SIDEMENULINKCOLOR)
                        , rs.getString(LOGO)
                        , rs.getString(CORNERLOGO)
                        , rs.getString(SERVERNAME)
                        , rs.getBoolean(NEWDESIGN)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final BusinessRowMapper businessRowMapper = new BusinessRowMapper();

    public static Business findByName(final String businessName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSNAME, businessName);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE `" + BUSINESSNAME + "` = :" + BUSINESSNAME, TABLENAME, businessRowMapper, namedParams);
    }

    public static Business findByServerName(final String serverName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(SERVERNAME, serverName);
        return StandardDAO.findOneWithWhereSQLAndParameters(" WHERE `" + SERVERNAME + "` = :" + SERVERNAME, TABLENAME, businessRowMapper, namedParams);
    }

    public static Business findById(int id){
        return StandardDAO.findById(id, TABLENAME, businessRowMapper);
    }

    public static List<Business> findAll(){
        return StandardDAO.findAll(TABLENAME, businessRowMapper);
    }

    public static void removeById(Business business){
        StandardDAO.removeById(business, TABLENAME);
    }

    public static void store(Business business){
        StandardDAO.store(business, TABLENAME, getColumnNameValueMap(business));
    }
}