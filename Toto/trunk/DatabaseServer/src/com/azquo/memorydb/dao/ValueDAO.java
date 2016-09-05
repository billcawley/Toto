package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.core.ValueHistory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 01/10/15.
 *
 * I want faster storing and loading of values, adapted from the NameDAO
 *
 */
public class ValueDAO {

    static final String FASTVALUE = "fast_value";
    static final String VALUEHISTORY = "value_history";
    private static final String PROVENANCEID = "provenance_id";
    private static final String TEXT = "text";
    private static final String NAMES = "names";

    private static final class ValueRowMapper implements RowMapper<Value> {
        final AzquoMemoryDB azquoMemoryDB;

        ValueRowMapper(AzquoMemoryDB azquoMemoryDB) {
            this.azquoMemoryDB = azquoMemoryDB;
        }

        @Override
        public Value mapRow(final ResultSet rs, final int row) throws SQLException {
            Blob namesBlob = rs.getBlob(NAMES);
            byte[] namesBytes = namesBlob.getBytes(1, (int)namesBlob.length()); // from 1, seems non standard!
            namesBlob.free(); // apparently a good idea
            try{
                return new Value(azquoMemoryDB, rs.getInt(FastDAO.ID), rs.getInt(PROVENANCEID), rs.getString(TEXT), namesBytes);
            } catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }
    }

    // very similar to the above, different type. Could abstracting a type help? Would it be worth it?

    private static final class ValueHistoryRowMapper implements RowMapper<ValueHistory> {
        final AzquoMemoryDB azquoMemoryDB;

        ValueHistoryRowMapper(AzquoMemoryDB azquoMemoryDB) {
            this.azquoMemoryDB = azquoMemoryDB;
        }

        @Override
        public ValueHistory mapRow(final ResultSet rs, final int row) throws SQLException {
            Blob namesBlob = rs.getBlob(NAMES);
            byte[] namesBytes = namesBlob.getBytes(1, (int)namesBlob.length()); // from 1, seems non standard!
            namesBlob.free(); // apparently a good idea
            try{
                return new ValueHistory(azquoMemoryDB, rs.getInt(FastDAO.ID), rs.getInt(PROVENANCEID), rs.getString(TEXT), namesBytes);
            } catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }
    }

    private static class BulkValuesInserter implements Callable<Void> {
        private final AzquoMemoryDB azquoMemoryDB;
        private final List<Value> valuesToInsert;
        private final int totalCount;
        private final String insertTable;
        private final boolean sortNameIds; // for the history

        BulkValuesInserter(final AzquoMemoryDB azquoMemoryDB, final List<Value> valuesToInsert, String insertTable, int totalCount, boolean sortNameIds) {
            this.azquoMemoryDB = azquoMemoryDB;
            this.valuesToInsert = valuesToInsert;
            this.totalCount = totalCount;
            this.insertTable = insertTable;
            this.sortNameIds = sortNameIds;
        }

        @Override
        public Void call() {
            if (!valuesToInsert.isEmpty()) {
                long track = System.currentTimeMillis();
                final MapSqlParameterSource namedParams = new MapSqlParameterSource();
                final StringBuilder insertSql = new StringBuilder("INSERT INTO `" + azquoMemoryDB.getPersistenceName() + "`.`" + insertTable + "` (`" + FastDAO.ID + "`,`" + PROVENANCEID + "`,`"
                        + TEXT + "`,`" + NAMES + "`) VALUES ");
                int count = 1;

                for (Value value : valuesToInsert) {
                    insertSql.append("(:" + FastDAO.ID).append(count)
                            .append(",:").append(PROVENANCEID).append(count)
                            .append(",:").append(TEXT).append(count)
                            .append(",:").append(NAMES).append(count)
                            .append("), ");
                    namedParams.addValue(FastDAO.ID + count, value.getId());
                    namedParams.addValue(PROVENANCEID + count, value.getProvenance().getId());
                    namedParams.addValue(TEXT + count, value.getText());
                    if (sortNameIds){
                        namedParams.addValue(NAMES + count, value.getNameIdsAsBytesSorted());
                    } else {
                        namedParams.addValue(NAMES + count, value.getNameIdsAsBytes());
                    }
                    count++;
                }
                insertSql.delete(insertSql.length() - 2, insertSql.length());
                //System.out.println(insertSql.toString());
                JdbcTemplateUtils.update(insertSql.toString(), namedParams);
                System.out.println("bulk inserted " + DecimalFormat.getInstance().format(valuesToInsert.size()) + " into " + insertTable + " in " + (System.currentTimeMillis() - track) + " remaining " + totalCount);
            }
            return null;
        }
    }

    // could this be factored between this class and the NameDao class? Not sure if it's the biggest deal.

    public static void persistValues(final AzquoMemoryDB azquoMemoryDB, final Collection<Value> values) throws Exception {
        // currently only the inserter is multithreaded, adding the others should not be difficult
        // old pattern had update, I think this is a pain and unnecessary, just delete than add to the insert
        ExecutorService executor = AzquoMemoryDB.sqlThreadPool;
        List<Value> toDelete = new ArrayList<>(FastDAO.UPDATELIMIT); // not full stop, as part of this process. Those that will stay deleted need to go into the history table
        List<Value> toInsert = new ArrayList<>(FastDAO.UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        List<Value> toHistory = new ArrayList<>(FastDAO.UPDATELIMIT); // to move to this history table
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
            for (Value value : values) {
                if (!value.getNeedsInserting()) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(value);
                    if (toDelete.size() == FastDAO.UPDATELIMIT) {
                        FastDAO.bulkDelete(azquoMemoryDB, toDelete, FASTVALUE);
                        toDelete = new ArrayList<>(FastDAO.UPDATELIMIT);
                    }
                }
            }
        FastDAO.bulkDelete(azquoMemoryDB, toDelete, FASTVALUE);
        int insertCount = 0;
        for (Value value: values){
            if (!value.getNeedsDeleting()) insertCount++;
        }
        List<Future> futureBatches = new ArrayList<>();
        for (Value value : values) {
            if (!value.getNeedsDeleting()) { // then we insert, either it's new or was updated hence deleted ready for reinsertion
                toInsert.add(value);
                if (toInsert.size() == FastDAO.UPDATELIMIT) {
                    insertCount -= FastDAO.UPDATELIMIT;
                    futureBatches.add(executor.submit(new BulkValuesInserter(azquoMemoryDB, toInsert, FASTVALUE, insertCount, false)));
                    toInsert = new ArrayList<>(FastDAO.UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            } else {
                toHistory.add(value);
                if (toHistory.size() == FastDAO.UPDATELIMIT) {
                    futureBatches.add(executor.submit(new BulkValuesInserter(azquoMemoryDB, toInsert, VALUEHISTORY, insertCount, true)));
                    toHistory = new ArrayList<>(FastDAO.UPDATELIMIT);
                }
            }
        }
        futureBatches.add(executor.submit(new BulkValuesInserter(azquoMemoryDB, toInsert, FASTVALUE, 0, false)));
        futureBatches.add(executor.submit(new BulkValuesInserter(azquoMemoryDB, toHistory, VALUEHISTORY, 0, true)));
        for (Future<?> futureBatch : futureBatches){
            futureBatch.get(1, TimeUnit.HOURS);
        }
    }

    static void createFastTableIfItDoesntExist(final String databaseName){
        JdbcTemplateUtils.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`" + FASTVALUE + "` (\n" +
                "`id` int(11) NOT NULL,\n" +
                "  `provenance_id` int(11) NOT NULL,\n" +
                "  `text` VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
                "  `names` blob NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci AUTO_INCREMENT=1;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

    // currently a copy of the value table with additional index but for the purposes of lookup the name ids need to be ordered

    public static void createValueHistoryTableIfItDoesntExist(final String databaseName){
        JdbcTemplateUtils.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`" + VALUEHISTORY + "` (\n" +
                "`id` int(11) NOT NULL,\n" +
                "  `provenance_id` int(11) NOT NULL,\n" +
                "  `text` VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
                "  `names` blob NOT NULL,\n" +
                "  KEY (`names`(255)),\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci AUTO_INCREMENT=1;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

    public static List<Value> findForMinMaxId(final AzquoMemoryDB azquoMemoryDB, int minId, int maxId) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + azquoMemoryDB.getPersistenceName() + "`.`" + FASTVALUE + "`.* from `" + azquoMemoryDB.getPersistenceName() + "`.`" + FASTVALUE + "` where id > " + minId + " and id <= " + maxId; // should I prepare this? Ints safe I think
        return JdbcTemplateUtils.query(SQL_SELECT_ALL, new ValueRowMapper(azquoMemoryDB));
    }

    public static int findMaxId(final AzquoMemoryDB azquoMemoryDB) throws DataAccessException {
        return FastDAO.findMaxId(azquoMemoryDB, FASTVALUE);
    }

    public static List<ValueHistory> getHistoryForValue(final AzquoMemoryDB azquoMemoryDB, Value value){
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NAMES, value.getNameIdsAsBytesSorted());
        return JdbcTemplateUtils.query("Select `" + azquoMemoryDB.getPersistenceName() + "`.`" + VALUEHISTORY + "`.* from `" + azquoMemoryDB.getPersistenceName() + "`.`" + VALUEHISTORY + "` where " + NAMES + " = :" + NAMES, namedParams, new ValueHistoryRowMapper(azquoMemoryDB));
    }
}