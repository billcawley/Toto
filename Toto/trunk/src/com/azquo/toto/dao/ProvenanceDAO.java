package com.azquo.toto.dao;

import com.azquo.toto.entity.Provenance;
import com.azquo.toto.entity.Value;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 17:44
 * To change this template use File | Settings | File Templates.
 */
public class ProvenanceDAO extends StandardDAO<Provenance>{

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "provenance";
    }

    // column names (except ID)

    public static final String USER = "user";
    public static final String TIMESTAMP = "timestamp";
    public static final String METHOD = "method";
    public static final String NAME = "name";

    @Override
    public Map<String, Object> getColumnNameValueMap(Provenance provenance){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, provenance.getId());
        toReturn.put(USER, provenance.getUser());
        toReturn.put(TIMESTAMP, provenance.getTimeStamp());
        toReturn.put(METHOD, provenance.getMethod());
        toReturn.put(NAME, provenance.getName());
        return toReturn;
    }

    public static final class ProvenanceRowMapper implements RowMapper<Provenance> {
        @Override
        public Provenance mapRow(final ResultSet rs, final int row) throws SQLException {
            return new Provenance(rs.getInt(ID), rs.getString(USER),rs.getDate(TIMESTAMP),rs.getString(METHOD),rs.getString(NAME));
        }
    }

    @Override
    public RowMapper<Provenance> getRowMapper() {
        return new ProvenanceRowMapper();
    }


}
