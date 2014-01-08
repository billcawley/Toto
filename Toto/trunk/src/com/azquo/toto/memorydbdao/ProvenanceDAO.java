package com.azquo.toto.memorydbdao;

import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.TotoMemoryDB;
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
 * For persistence of provenance
 */
public final class ProvenanceDAO extends StandardDAO<Provenance>{

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
    public static final String ROWHEADINGS = "row_headings";
    public static final String COLUMNHEADINGS = "column_headings";
    public static final String CONTEXT = "context";

    @Override
    public Map<String, Object> getColumnNameValueMap(Provenance provenance){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, provenance.getId());
        toReturn.put(USER, provenance.getUser());
        toReturn.put(TIMESTAMP, provenance.getTimeStamp());
        toReturn.put(METHOD, provenance.getMethod());
        toReturn.put(NAME, provenance.getName());
        toReturn.put(ROWHEADINGS, provenance.getRowHeadings());
        toReturn.put(COLUMNHEADINGS, provenance.getColumnHeadings());
        toReturn.put(CONTEXT, provenance.getContext());
        return toReturn;
    }

    public static final class ProvenanceRowMapper implements RowMapper<Provenance> {

        TotoMemoryDB totoMemoryDB;

        public ProvenanceRowMapper(TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }
        @Override
        public Provenance mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Provenance(totoMemoryDB, rs.getInt(ID), rs.getString(USER),rs.getDate(TIMESTAMP)
                        ,rs.getString(METHOD),rs.getString(NAME),rs.getString(ROWHEADINGS),rs.getString(COLUMNHEADINGS),rs.getString(CONTEXT));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Provenance> getRowMapper(TotoMemoryDB TotoMemoryDB) {
        return new ProvenanceRowMapper(TotoMemoryDB);
    }


}
