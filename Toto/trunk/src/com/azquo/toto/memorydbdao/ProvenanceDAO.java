package com.azquo.toto.memorydbdao;

import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.jdbc.core.RowMapper;

import javax.swing.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 17:44
 * For persistence of provenance
 */
public final class ProvenanceDAO extends StandardDAO<Provenance>{

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "provenance";
    }

    // column names (except ID)

    @Override
    public Map<String, Object> getColumnNameValueMap(final Provenance provenance){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, provenance.getId());
        toReturn.put(JSON, provenance.getUser());
        return toReturn;
    }

    public static final class ProvenanceRowMapper implements RowMapper<Provenance> {

        TotoMemoryDB totoMemoryDB;

        public ProvenanceRowMapper(final TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }
        @Override
        public Provenance mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Provenance(totoMemoryDB, rs.getInt(ID), rs.getString(JSON));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Provenance> getRowMapper(final TotoMemoryDB TotoMemoryDB) {
        return new ProvenanceRowMapper(TotoMemoryDB);
    }


}
