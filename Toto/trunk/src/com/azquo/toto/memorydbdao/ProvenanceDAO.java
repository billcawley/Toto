package com.azquo.toto.memorydbdao;

import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

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
