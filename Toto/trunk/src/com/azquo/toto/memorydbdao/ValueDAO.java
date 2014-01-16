package com.azquo.toto.memorydbdao;

import com.azquo.toto.memorydb.Value;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 10:28
 * DAO for Values, under new model just used for persistence,will hopefully be pretty simple
 * */
public final class ValueDAO extends StandardDAO<Value> {
    // the default table name for this data.
    @Override
    public String getTableName() {
        return "value";
    }

    // column names (except ID)

    private static final class ValueRowMapper implements RowMapper<Value> {
        final TotoMemoryDB totoMemoryDB;
        public ValueRowMapper(TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }
        @Override
        public Value mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Value(totoMemoryDB,rs.getInt(ID), rs.getString(JSON));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Value> getRowMapper(TotoMemoryDB totoMemoryDB) {
        return new ValueRowMapper(totoMemoryDB);
    }

}
