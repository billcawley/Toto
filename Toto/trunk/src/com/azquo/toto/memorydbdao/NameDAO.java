package com.azquo.toto.memorydbdao;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:19
 * DAO for Names, under new model just used for persistence,will hopefully be pretty simple
 */
public final class NameDAO extends StandardDAO<Name> {


    //    public <T>  T readValue(java.lang.String content, java.lang.Class<T> valueType)
    // the syntax we may be interested in :)

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "name";
    }

    @Override
    public Map<String, Object> getColumnNameValueMap(final Name name){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, name.getId());
        // should we allow null provenance??
        //toReturn.put(PROVENANCEID, (name.getProvenance() != null ? name.getProvenance().getId(): 0));
        //toReturn.put(ADDITIVE, name.getAdditive());
        toReturn.put(JSON, name.getAsJson());
        return toReturn;
    }

    public static class NameRowMapper implements RowMapper<Name> {

        private final TotoMemoryDB totoMemoryDB;

        public NameRowMapper(TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }
        @Override
        public final Name mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Name(totoMemoryDB, rs.getInt(ID), rs.getString(JSON));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Name> getRowMapper(final TotoMemoryDB totoMemoryDB) {
        return new NameRowMapper(totoMemoryDB);
    }
}
