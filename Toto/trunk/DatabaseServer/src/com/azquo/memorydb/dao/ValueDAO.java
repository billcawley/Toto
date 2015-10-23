package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by edward on 01/10/15.
 *
 * I want faster storing and loading of values, adapted from the NameDAO
 *
 * todo - factor off common bits?
 */
public class ValueDAO {

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    // this value is not picked randomly, tests have it faster than 1k or 10k. It seems with imports bigger is not necessarily better. Possibly to do with query parsing overhead.

    //    public static final int UPDATELIMIT = 5000;
    public static final int UPDATELIMIT = 2500; // going to 2.5k as now some of the records can be quite large! Also simultaneous.

    private static final String FASTVALUE = "fast_value";
    private static final String ID = "id";
    private static final String PROVENANCEID = "provenance_id";
    private static final String TEXT = "text";
    private static final String NAMES = "names";

    private static final class ValueRowMapper implements RowMapper<Value> {
        final AzquoMemoryDB azquoMemoryDB;

        public ValueRowMapper(AzquoMemoryDB azquoMemoryDB) {
            this.azquoMemoryDB = azquoMemoryDB;
        }

        @Override
        public Value mapRow(final ResultSet rs, final int row) throws SQLException {
            Blob namesBlob = rs.getBlob(NAMES);
            byte[] namesBytes = namesBlob.getBytes(1, (int)namesBlob.length()); // from 1, seems non standard!
            namesBlob.free(); // apparently a good idea
            try{
                return new Value(azquoMemoryDB, rs.getInt(ID), rs.getInt(PROVENANCEID), rs.getString(TEXT), namesBytes);
            } catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }
    }


    private class BulkValuesInserter implements Runnable {
        private final AzquoMemoryDB azquoMemoryDB;
        private final List<Value> valuesToInsert;
        private final int totalCount;

        public BulkValuesInserter(final AzquoMemoryDB azquoMemoryDB, final List<Value> valuesToInsert, int totalCount) {
            this.azquoMemoryDB = azquoMemoryDB;
            this.valuesToInsert = valuesToInsert;
            this.totalCount = totalCount;
        }

        @Override
        public void run() {
            if (!valuesToInsert.isEmpty()) {
                long track = System.currentTimeMillis();
                final MapSqlParameterSource namedParams = new MapSqlParameterSource();
                final StringBuilder insertSql = new StringBuilder("INSERT INTO `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTVALUE + "` (`" + ID + "`,`" + PROVENANCEID + "`,`"
                        + TEXT + "`,`" + NAMES + "`) VALUES ");
                int count = 1;

                for (Value value : valuesToInsert) {
                    insertSql.append("(:" + ID).append(count)
                            .append(",:").append(PROVENANCEID).append(count)
                            .append(",:").append(TEXT).append(count)
                            .append(",:").append(NAMES).append(count)
                            .append("), ");
                    namedParams.addValue(ID + count, value.getId());
                    namedParams.addValue(PROVENANCEID + count, value.getProvenance().getId());
                    namedParams.addValue(TEXT + count, value.getText());
                    namedParams.addValue(NAMES + count, value.getNameIdsAsBytes());
                    count++;
                }
                insertSql.delete(insertSql.length() - 2, insertSql.length());
                //System.out.println(insertSql.toString());
                jdbcTemplate.update(insertSql.toString(), namedParams);
                System.out.println("bulk inserted " + DecimalFormat.getInstance().format(valuesToInsert.size()) + " into " + FASTVALUE + " in " + (System.currentTimeMillis() - track) + " remaining " + totalCount);
            }
        }
    }

    private void bulkDelete(final AzquoMemoryDB azquoMemoryDB, final List<Value> values) throws DataAccessException {
        if (!values.isEmpty()) {
            long track = System.currentTimeMillis();
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            final StringBuilder updateSql = new StringBuilder("delete from `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTVALUE + "` where " + ID + " in (");

            int count = 1;
            for (Value value : values) {
                if (count == 1) {
                    updateSql.append(value.getId());
                } else {
                    updateSql.append(",").append(value.getId());
                }
                count++;
            }
            updateSql.append(")");
            //System.out.println(updateSql.toString());
            jdbcTemplate.update(updateSql.toString(), namedParams);
            System.out.println("bulk deleted " + values.size() + " from " + FASTVALUE + " in " + (System.currentTimeMillis() - track));
        }
    }

    public void persistValues(final AzquoMemoryDB azquoMemoryDB, final Collection<Value> values, boolean initialInsert) throws Exception {
        // currently only the inserter is multithreaded, adding the others should not be difficult
        // old pattern had update, I think this is a pain and unnecessary, just delete than add to the insert
        ExecutorService executor = Executors.newFixedThreadPool(azquoMemoryDB.getLoadingThreads());
        List<Value> toDelete = new ArrayList<>(UPDATELIMIT);
        List<Value> toInsert = new ArrayList<>(UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
        if (!initialInsert){ // no point deleting on initial insert
            for (Value value : values) {
                if (!value.getNeedsInserting()) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(value);
                    if (toDelete.size() == UPDATELIMIT) {
                        bulkDelete(azquoMemoryDB, toDelete);
                        toDelete = new ArrayList<>(UPDATELIMIT);
                    }
                }
            }
        }
        bulkDelete(azquoMemoryDB, toDelete);
        int insertCount = 0;
        for (Value value: values){
            if (!value.getNeedsDeleting()) insertCount++;
        }

        for (Value value : values) {
            if (!value.getNeedsDeleting()) { // then we insert, either it's new or was updated hence deleted ready for reinsertion
                toInsert.add(value);
                if (toInsert.size() == UPDATELIMIT) {
                    insertCount -= UPDATELIMIT;
                    executor.execute(new BulkValuesInserter(azquoMemoryDB, toInsert, insertCount));
                    toInsert = new ArrayList<>(UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        executor.execute(new BulkValuesInserter(azquoMemoryDB, toInsert, 0));
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
            throw new Exception("Database " + azquoMemoryDB.getMySQLName() + " took longer than an hour to persist");
        }
    }

    public final List<Value> findForMinMaxId(final AzquoMemoryDB azquoMemoryDB, int minId, int maxId) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTVALUE + "`.* from `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTVALUE + "` where id > " + minId + " and id <= " + maxId; // should I prepare this? Ints safe I think
        return jdbcTemplate.query(SQL_SELECT_ALL, new ValueRowMapper(azquoMemoryDB));
    }

    public final int findMaxId(final AzquoMemoryDB azquoMemoryDB) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select max(id) from `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTVALUE + "`";
        Integer toReturn = jdbcTemplate.queryForObject (SQL_SELECT_ALL, StandardDAO.EMPTY_PARAMETERS_MAP, Integer.class);
        return toReturn != null ? toReturn : 0; // otherwise we'll get a null pinter boxing to int!
    }

    public void createFastTableIfItDoesntExist(final AzquoMemoryDB azquoMemoryDB){
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTVALUE + "` (\n" +
                "`id` int(11) NOT NULL,\n" +
                "  `provenance_id` int(11) NOT NULL,\n" +
                "  `text` VARCHAR(255) COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
                "  `names` blob NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci AUTO_INCREMENT=1;", StandardDAO.EMPTY_PARAMETERS_MAP);
    }

    public void clearFastValueTable(final AzquoMemoryDB azquoMemoryDB){
        jdbcTemplate.update("delete from `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTVALUE + "`", StandardDAO.EMPTY_PARAMETERS_MAP);

    }

    public boolean checkFastTableExists(final AzquoMemoryDB azquoMemoryDB){
        final List<Map<String, Object>> maps = jdbcTemplate.queryForList("show tables from  `" + azquoMemoryDB.getMySQLName() + "` like 'fast_value' ", StandardDAO.EMPTY_PARAMETERS_MAP);
        return !maps.isEmpty();
    }

}
