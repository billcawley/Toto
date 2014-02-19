package com.azquo.toto.memorydbdao;

import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:18
 * Most data tables in the database have common features such as an id and a simple place that they're stored which means we can,
 * factor things off here
 * <p/>
 * Note : for building SQL I'm veering away from StringBuilder as IntelliJ complains about it and string concatenation etc is heavily optimised by the compiler
 * <p/>
 * This used to be an abstract class with classes for each entity extending it. Now after full json it's just used for moving the very standard json records about.
 */
public class StandardDAO {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    // 10 million select limit for the moment, this class is used for bulk loading and saving.
    public static final int SELECTLIMIT = 10000000;

    // this value is not picked randomly, tests have it faster than 1k or 10k. It seems with imports bigger is not necessarily better. Possibly to do with query parsing overhead.

    public static final int UPDATELIMIT = 5000;

    // the basic data persistence tables have but two columns, these two :)
    private static final String ID = "id";
    private static final String JSON = "json";

    // put the table names in here, seems as good a place as any
    public static final String PROVENANCE = "provenance";
    public static final String NAME = "name";
    public static final String VALUE = "value";

    private final class JsonRecordTransportRowMapper implements RowMapper<JsonRecordTransport> {
        @Override
        public JsonRecordTransport mapRow(final ResultSet rs, final int row) throws SQLException {
            return new JsonRecordTransport(rs.getInt(ID), rs.getString(JSON), JsonRecordTransport.State.LOADED);
        }
    }

    private void bulkInsert(final TotoMemoryDB totoMemoryDB, final String tableName, final List<JsonRecordTransport> records) throws DataAccessException {
        if (!records.isEmpty()) {
            long track = System.currentTimeMillis();
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            final StringBuilder insertSql = new StringBuilder();

            insertSql.append("INSERT INTO `" + totoMemoryDB.getMySQLName() + "`.`" + tableName + "` (" + ID + "," + JSON + ") VALUES ");

            int count = 1;

            for (JsonRecordTransport record : records) {
                insertSql.append("(:" + ID + count + ",:" + JSON + count + "), ");
                namedParams.addValue(JSON + count, record.json);
                namedParams.addValue(ID + count, record.id);
                count++;
            }
            insertSql.delete(insertSql.length() - 2, insertSql.length());
            System.out.println(insertSql.toString());
            jdbcTemplate.update(insertSql.toString(), namedParams);
            System.out.println("bulk inserted " + records.size() + " into " + tableName + " in " + (System.currentTimeMillis() - track));
        }
    }

    /* example of the sql to do bulk updates.

     UPDATE categories
    SET display_order = CASE id
        WHEN 1 THEN 3
        WHEN 2 THEN 4
        WHEN 3 THEN 5
    END
WHERE id IN (1,2,3)

     */

    private void bulkUpdate(final TotoMemoryDB totoMemoryDB, final String tableName, final List<JsonRecordTransport> records) throws DataAccessException {
        if (!records.isEmpty()) {
            long track = System.currentTimeMillis();
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            final StringBuilder updateSql = new StringBuilder();

            updateSql.append("update `" + totoMemoryDB.getMySQLName() + "`.`" + tableName + "` SET " + JSON + " = CASE " + ID);

            int count = 1;

            for (JsonRecordTransport record : records) {
                updateSql.append(" when :" + ID + count + " then :" + JSON + count + " ");
                namedParams.addValue(ID + count, record.id);
                namedParams.addValue(JSON + count, record.json);
                count++;
            }
            updateSql.append(" END where " + ID + " in (");
            count = 1;
            for (JsonRecordTransport record : records) {
                if (count == 1) {
                    updateSql.append(record.id);
                } else {
                    updateSql.append("," + record.id);
                }
                count++;
            }
            updateSql.append(")");
            System.out.println(updateSql.toString());
            jdbcTemplate.update(updateSql.toString(), namedParams);
            System.out.println("bulk updated " + records.size() + " into " + tableName + " in " + (System.currentTimeMillis() - track));
        }
    }

    private void bulkDelete(final TotoMemoryDB totoMemoryDB, final String tableName, final List<JsonRecordTransport> records) throws DataAccessException {
        if (!records.isEmpty()) {
            long track = System.currentTimeMillis();
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            final StringBuilder updateSql = new StringBuilder();

            updateSql.append("delete from `" + totoMemoryDB.getMySQLName() + "`.`" + tableName + "` where " + ID + " in (");
            int count = 1;
            for (JsonRecordTransport record : records) {
                if (count == 1) {
                    updateSql.append(record.id);
                } else {
                    updateSql.append("," + record.id);
                }
                count++;
            }
            updateSql.append(")");
            System.out.println(updateSql.toString());
            jdbcTemplate.update(updateSql.toString(), namedParams);
            System.out.println("bulk deleted " + records.size() + " from " + tableName + " in " + (System.currentTimeMillis() - track));
        }
    }

    public void persistJsonRecords(final TotoMemoryDB totoMemoryDB, final String tableName, final List<JsonRecordTransport> records) throws DataAccessException {
        List<JsonRecordTransport> toDelete = new ArrayList<JsonRecordTransport>();
        List<JsonRecordTransport> toInsert = new ArrayList<JsonRecordTransport>();
        List<JsonRecordTransport> toUpdate = new ArrayList<JsonRecordTransport>();
        for (JsonRecordTransport record : records) {
            if (record.state == JsonRecordTransport.State.DELETE) {
                toDelete.add(record);
                if (toDelete.size() == UPDATELIMIT) {
                    bulkDelete(totoMemoryDB, tableName, toDelete);
                    toDelete = new ArrayList<JsonRecordTransport>();
                }
            }
            if (record.state == JsonRecordTransport.State.INSERT) {
                toInsert.add(record);
                if (toInsert.size() == UPDATELIMIT) {
                    bulkInsert(totoMemoryDB, tableName, toInsert);
                    toInsert = new ArrayList<JsonRecordTransport>();
                }
            }
            if (record.state == JsonRecordTransport.State.UPDATE) {
                toUpdate.add(record);
                if (toUpdate.size() == UPDATELIMIT) {
                    bulkUpdate(totoMemoryDB, tableName, toUpdate);
                    toUpdate = new ArrayList<JsonRecordTransport>();
                }
            }
        }
        bulkDelete(totoMemoryDB, tableName, toDelete);
        bulkInsert(totoMemoryDB, tableName, toInsert);
        bulkUpdate(totoMemoryDB, tableName, toUpdate);
    }

    public final List<JsonRecordTransport> findFromTable(final TotoMemoryDB totoMemoryDB, final String tableName) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + totoMemoryDB.getMySQLName() + "`.`" + tableName + "`.* from `" + totoMemoryDB.getMySQLName() + "`.`" + tableName + "` LIMIT 0," + SELECTLIMIT;
        return jdbcTemplate.query(SQL_SELECT_ALL, new JsonRecordTransportRowMapper());
    }
}