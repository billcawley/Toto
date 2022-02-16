package com.azquo.admin.onlinereport;

import com.azquo.DateUtils;
import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by bill on 15/04/14.
 */
public class MenuItemDAO {
    // the default table name for this data.
    private static String TABLENAME = "menuitem";

    // column names except ID which is in the superclass
    private static final String DATECREATED = "date_created";

    private static final String BUSINESSID = "business_id";
    private static final String REPORTID = "report_id";
    private static final String SUBMENUNAME = "submenu_name";
    private static final String MENUITEMNAME = "menuitem_name";
    private static final String EXPLANATION = "explanation";
    private static final String POSITION= "position_id";
    private static final String DATABASE_ID= "database_id";

    public static Map<String, Object> getColumnNameValueMap(final MenuItem menuItem) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, menuItem.getId());
        toReturn.put(DATECREATED, DateUtils.getDateFromLocalDateTime(menuItem.getDateCreated()));
        toReturn.put(BUSINESSID, menuItem.getBusinessId());
        toReturn.put(REPORTID, menuItem.getReportId());
        toReturn.put(SUBMENUNAME, menuItem.getSubmenuName());
        toReturn.put(MENUITEMNAME, menuItem.getMenuItemName());
        toReturn.put(EXPLANATION, menuItem.getExplanation());
        toReturn.put(POSITION, menuItem.getPosition());
        toReturn.put(DATABASE_ID, menuItem.getDatabaseID());
        return toReturn;
    }

    private static final class MenuItemRowMapper implements RowMapper<MenuItem> {
        @Override
        public MenuItem mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new MenuItem(rs.getInt(StandardDAO.ID)
                        , DateUtils.getLocalDateTimeFromDate(rs.getTimestamp(DATECREATED))
                        , rs.getInt(BUSINESSID)
                        , rs.getInt(REPORTID)
                        , rs.getString(SUBMENUNAME)
                        , rs.getString(MENUITEMNAME)
                        , rs.getString(EXPLANATION)
                        , rs.getInt(POSITION)
                        , rs.getInt(DATABASE_ID)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }


    private static MenuItemRowMapper menuItemRowMapper = new MenuItemRowMapper();



    // changed to straight equals, like seemed tripped up by ampersand. Still case insensitive it seems, useful though maybe a security bug for some things
    public static List<MenuItem> findForNameAndBusinessId(final String name, int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(SUBMENUNAME, name);
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters("  WHERE " + SUBMENUNAME + " =:" + SUBMENUNAME + " and " + BUSINESSID + " = :" + BUSINESSID+ " order by " + POSITION, TABLENAME, menuItemRowMapper, namedParams);
    }

    public static List<MenuItem> findForReportId(int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTID, reportId);
        return StandardDAO.findListWithWhereSQLAndParameters("  WHERE " + REPORTID + " = :" + REPORTID, TABLENAME, menuItemRowMapper, namedParams);
    }



    public static void removeById(MenuItem menuItem) {
        StandardDAO.removeById(menuItem, TABLENAME);
    }

    public static void store(MenuItem menuItem) {
        StandardDAO.store(menuItem, TABLENAME, getColumnNameValueMap(menuItem));
    }
}