package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:18
 *
 * Used to have sub classes but has morphed over time to be for saving/loading JSON, hence the class rename to JsonRecordDAO.
 *
 * Multi threading is beginning to be added for loads and saves - for loading it's taken care of externally but for saving it seems fine that it's in here. Can concurrently throw
 * updates/inserts/deletes at MySQL. Currently only inserts.
 *
 * Also currently only for Provenance
 */
public class JsonRecordDAO {

    // this value is not picked randomly, tests have it faster than 1k or 10k. It seems with imports bigger is not necessarily better. Possibly to do with query parsing overhead.

//    public static final int UPDATELIMIT = 5000;
    private static final int UPDATELIMIT = 2500; // going to 2.5k as now some of the records can be quite large! Also simultaneous.

    // the basic data persistence tables have but two columns. Well suited to a key pair store.
    private static final String ID = "id";
    private static final String JSON = "json";

    static final Map<String,?> EMPTY_PARAMETERS_MAP = new HashMap<>();

    private static final class JsonRecordTransportRowMapper implements RowMapper<JsonRecordTransport> {
        @Override
        public JsonRecordTransport mapRow(final ResultSet rs, final int row) throws SQLException {
            return new JsonRecordTransport(rs.getInt(ID), rs.getString(JSON), JsonRecordTransport.State.LOADED);
        }
    }


    private static class BulkInserter implements Callable<Void> {
        private final AzquoMemoryDB azquoMemoryDB;
        private final String tableName;
        private final List<JsonRecordTransport> records;
        private int totalCount;

        BulkInserter(final AzquoMemoryDB azquoMemoryDB, final String tableName, final List<JsonRecordTransport> records, int totalCount) {
            this.azquoMemoryDB = azquoMemoryDB;
            this.tableName = tableName;
            this.records = records;
            this.totalCount = totalCount;
        }

        @Override
        public Void call() {
                if (!records.isEmpty()) {
                    long track = System.currentTimeMillis();
                    final MapSqlParameterSource namedParams = new MapSqlParameterSource();
                    final StringBuilder insertSql = new StringBuilder("INSERT INTO `" + azquoMemoryDB.getPersistenceName() + "`.`" + tableName + "` (" + ID + "," + JSON + ") VALUES ");
                    int count = 1;

                    for (JsonRecordTransport record : records) {
                        insertSql.append("(:" + ID).append(count).append(",:").append(JSON).append(count).append("), ");
                        namedParams.addValue(JSON + count, record.json);
                        namedParams.addValue(ID + count, record.id);
                        count++;
                    }
                    insertSql.delete(insertSql.length() - 2, insertSql.length());
                    //System.out.println(insertSql.toString());
                    JdbcTemplateUtils.update(insertSql.toString(), namedParams);
                    System.out.println("bulk inserted " + DecimalFormat.getInstance().format(records.size()) + " into " + tableName + " in " + (System.currentTimeMillis() - track) + " remaining " + totalCount);
                }
            return null;
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

    private static void bulkUpdate(final AzquoMemoryDB azquoMemoryDB, final String tableName, final List<JsonRecordTransport> records) throws DataAccessException {
        if (!records.isEmpty()) {
            long track = System.currentTimeMillis();
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            final StringBuilder updateSql = new StringBuilder("update `" + azquoMemoryDB.getPersistenceName() + "`.`" + tableName + "` SET " + JSON + " = CASE " + ID);

            int count = 1;

            for (JsonRecordTransport record : records) {
                updateSql.append(" when :" + ID).append(count).append(" then :").append(JSON).append(count).append(" ");
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
                    updateSql.append(",").append(record.id);
                }
                count++;
            }
            updateSql.append(")");
            //System.out.println(updateSql.toString());
            JdbcTemplateUtils.update(updateSql.toString(), namedParams);
            System.out.println("bulk updated " + records.size() + " into " + tableName + " in " + (System.currentTimeMillis() - track));
        }
    }

    private static void bulkDelete(final AzquoMemoryDB azquoMemoryDB, final String tableName, final List<JsonRecordTransport> records) throws DataAccessException {
        if (!records.isEmpty()) {
            long track = System.currentTimeMillis();
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            final StringBuilder updateSql = new StringBuilder("delete from `" + azquoMemoryDB.getPersistenceName() + "`.`" + tableName + "` where " + ID + " in (");

            int count = 1;
            for (JsonRecordTransport record : records) {
                if (count == 1) {
                    updateSql.append(record.id);
                } else {
                    updateSql.append(",").append(record.id);
                }
                count++;
            }
            updateSql.append(")");
            //System.out.println(updateSql.toString());
            JdbcTemplateUtils.update(updateSql.toString(), namedParams);
            System.out.println("bulk deleted " + records.size() + " from " + tableName + " in " + (System.currentTimeMillis() - track));
        }
    }

    public static void persistJsonRecords(final AzquoMemoryDB azquoMemoryDB, final String tableName, final List<JsonRecordTransport> records) throws Exception {
        // currently only the inserter is multithreaded, adding the others should not be difficult - todo, perhaps for update and possible list optimisation there?
        ExecutorService executor = AzquoMemoryDB.sqlThreadPool;
        List<JsonRecordTransport> toInsert = new ArrayList<>(UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        int totalCount = records.size();
        List<Future> futureBatches = new ArrayList<>();
        for (JsonRecordTransport record : records) {
            if (record.state == JsonRecordTransport.State.INSERT) {
                toInsert.add(record);
                if (toInsert.size() == UPDATELIMIT) {
                    totalCount -= toInsert.size();
                    futureBatches.add(executor.submit(new BulkInserter(azquoMemoryDB, tableName, toInsert, totalCount)));
                    toInsert = new ArrayList<>(UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        futureBatches.add(executor.submit(new BulkInserter(azquoMemoryDB, tableName, toInsert, 0)));
        for (Future<?> futureBatch : futureBatches){
            futureBatch.get(1, TimeUnit.HOURS);
        }

        // don't want the update at the same time as insert, I think it can cause problems. Insert first then update/delete

        List<JsonRecordTransport> toDelete = new ArrayList<>();
        List<JsonRecordTransport> toUpdate = new ArrayList<>();
        for (JsonRecordTransport record : records) {
            if (record.state == JsonRecordTransport.State.DELETE) {
                toDelete.add(record);
                if (toDelete.size() == UPDATELIMIT) {
                    bulkDelete(azquoMemoryDB, tableName, toDelete);
                    toDelete = new ArrayList<>();
                }
            }
            if (record.state == JsonRecordTransport.State.UPDATE) {
                toUpdate.add(record);
                if (toUpdate.size() == UPDATELIMIT) {
                    bulkUpdate(azquoMemoryDB, tableName, toUpdate);
                    toUpdate = new ArrayList<>();
                }
            }
        }
        bulkDelete(azquoMemoryDB, tableName, toDelete);
        bulkUpdate(azquoMemoryDB, tableName, toUpdate);

    }

    public static List<JsonRecordTransport> findFromTableMinMaxId(final AzquoMemoryDB azquoMemoryDB, final String tableName, int minId, int maxId) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + azquoMemoryDB.getPersistenceName() + "`.`" + tableName + "`.* from `" + azquoMemoryDB.getPersistenceName() + "`.`" + tableName + "` where id > " + minId + " and id <= " + maxId; // should I prepare this? Ints safe I think
        return JdbcTemplateUtils.query(SQL_SELECT_ALL, new JsonRecordTransportRowMapper());
    }

    public static int findMaxId(final AzquoMemoryDB azquoMemoryDB, final String tableName) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select max(id) from `" + azquoMemoryDB.getPersistenceName() + "`.`" + tableName + "`";
        Integer toReturn = JdbcTemplateUtils.queryForObject(SQL_SELECT_ALL, new HashMap<>(), Integer.class);
        return toReturn != null ? toReturn : 0; // otherwise we'll get a null pinter boxing to int!
    }

/*    private static final class TableStatusRowMapper implements RowMapper<TableStatus> {
        @Override
        public TableStatus mapRow(final ResultSet rs, final int row) throws SQLException {
            return new TableStatus(rs.getInt(ROWS));
        }
    }

    // lots of info could be in here but for the moment we just want rows. Doesn't need to be completely accurate, it's for guessing map sizes
    private static final String ROWS = "Rows";
    private static class TableStatus {
        public final int rows;

        private TableStatus(int rows) {
            this.rows = rows;
        }
    }


    // trusting of input
    public final int findNumberOfRows(final String dbName, final String tableName) throws DataAccessException {
        final String SQL_SELECT_STATUS = "SHOW TABLE STATUS from "+ dbName  + " LIKE '" + tableName + "'";
        final List<TableStatus> query = jdbcTemplate.query(SQL_SELECT_STATUS, new TableStatusRowMapper());
        if (query.isEmpty()){
            return -1;
        } else {
            return query.iterator().next().rows;
        }
    }*/
}