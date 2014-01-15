package com.azquo.toto.memorydbdao;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Value;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Override
    protected Map<String, Object> getColumnNameValueMap(Value value) {
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, value.getId());
        toReturn.put(JSON, value.getAsJson());
        return toReturn;
    }

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

    public static class ValueIdNameId{
        public final int valueId;
        public final int nameId;

        public ValueIdNameId(int valueId, int nameId) {
            this.valueId = valueId;
            this.nameId = nameId;
        }
    }

    @Override
    public RowMapper<Value> getRowMapper(TotoMemoryDB totoMemoryDB) {
        return new ValueRowMapper(totoMemoryDB);
    }

}
