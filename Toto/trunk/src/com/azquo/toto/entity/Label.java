package com.azquo.toto.entity;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * To change this template use File | Settings | File Templates.
 */
public class Label extends StandardEntity {

    // convention is to put the column names in as strings

    public static final String NAME = "name";

    // leaving here as a reminder to consider proper logging

    //private static final Logger logger = Logger.getLogger(Merchant.class.getName());

    private String name;

    public Label() {
        id = 0;
        name = null;
    }

    // clone may be required here is we cache

    @Override
    public String getTableName() {
        return "label";
    }

    @Override
    public Map<String, Object> getColumnNameValueMap(){
        Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, getId());
        toReturn.put(NAME, getName());
        return toReturn;
    }

    public static final class LabelRowMapper implements RowMapper<StandardEntity> {
        @Override
        public Label mapRow(final ResultSet rs, final int row) throws SQLException {
            final Label label = new Label();
            label.setId(rs.getInt(ID));
            label.setName(rs.getString(NAME));
            return label;
        }
    }

    @Override
    public RowMapper<StandardEntity> getRowMapper() {
        return new LabelRowMapper();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
