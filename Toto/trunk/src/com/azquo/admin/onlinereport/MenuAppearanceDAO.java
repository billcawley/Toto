package com.azquo.admin.onlinereport;

import com.azquo.admin.StandardDAO;
import de.rototor.pdfbox.graphics2d.IPdfBoxGraphics2DFontTextDrawer;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MenuAppearanceDAO {

    private static final String TABLENAME = "menu_appearance";

    // column names except ID which is in the superclass
    private static final String BUSINESSID = "business_id";
    private static final String REPORTID = "report_id";
    private static final String SUBMENUNAME = "submenu_name";
    private static final String IMPORTANCE = "importance";
     private static final String SHOWNAME = "showname";

    public static Map<String, Object> getColumnNameValueMap(final MenuAppearance menuAppearance) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, menuAppearance.getId());
        toReturn.put(BUSINESSID, menuAppearance.getBusinesId());
        toReturn.put(REPORTID, menuAppearance.getReportId());
        toReturn.put(SUBMENUNAME, menuAppearance.getSubmenuName());
        toReturn.put(IMPORTANCE, menuAppearance.getImportance());
         toReturn.put(SHOWNAME, menuAppearance.getShowname());
           return toReturn;
    }

     private static final class MenuAppearanceRowMapper implements RowMapper<MenuAppearance> {
        @Override
        public MenuAppearance mapRow(final ResultSet rs, final int row) {
            try {

                return new MenuAppearance(rs.getInt(StandardDAO.ID)
                         ,rs.getInt(BUSINESSID)
                        , rs.getInt(REPORTID)
                        , rs.getString(SUBMENUNAME)
                        , rs.getInt(IMPORTANCE)
                        , rs.getString(SHOWNAME)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final MenuAppearanceRowMapper menuAppearanceRowMapper = new MenuAppearanceRowMapper();

    public static List<MenuAppearance> findForReportId(final int reportId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(REPORTID, reportId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE "  +   REPORTID + " = :"+ REPORTID, TABLENAME, menuAppearanceRowMapper, namedParams);
    }

    public static List<MenuAppearance> findForNameAndBusinessId(final String name, final int businessId) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(SUBMENUNAME, name);
        namedParams.addValue(BUSINESSID, businessId);
        return StandardDAO.findListWithWhereSQLAndParameters(" WHERE "  +   SUBMENUNAME + " = :"+ SUBMENUNAME + " AND " + BUSINESSID + " = :" + BUSINESSID, TABLENAME, menuAppearanceRowMapper, namedParams);
    }



    public static void removeById(int maId) {
        MenuAppearance ma = findById(maId);
        StandardDAO.removeById(ma, TABLENAME);
    }

    public static void store(MenuAppearance connection) {
        StandardDAO.store(connection, TABLENAME, getColumnNameValueMap(connection));
    }

    public static MenuAppearance findById(int id) {
        return StandardDAO.findById(id, TABLENAME, menuAppearanceRowMapper);
    }


}