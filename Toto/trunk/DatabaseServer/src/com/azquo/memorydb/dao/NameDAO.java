package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.AzquoMemoryDBEntity;
import com.azquo.memorydb.core.Name;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by edward on 01/10/15.
 *
 * I want faster storing and loading of names, the old JSON won't cut it, too much garbage
 * adapted from standardDAO , should factor some stuff off at some point
 */
public class NameDAO extends FastDAO{


    // this value is not picked randomly, tests have it faster than 1k or 10k. It seems with imports bigger is not necessarily better. Possibly to do with query parsing overhead.

    //    public static final int UPDATELIMIT = 5000;
    private static final String FASTNAME = "fast_name";
    private static final String PROVENANCEID = "provenance_id";
    private static final String ADDITIVE = "additive";
    private static final String ATTRIBUTES = "attributes";
    private static final String CHILDREN = "children";
    private static final String NOPARENTS = "no_parents";
    private static final String NOVALUES = "no_values";

    @Override
    public String getTableName() {
        return FASTNAME;
    }

    private static final class NameRowMapper implements RowMapper<Name> {
        final AzquoMemoryDB azquoMemoryDB;

        public NameRowMapper(AzquoMemoryDB azquoMemoryDB) {
            this.azquoMemoryDB = azquoMemoryDB;
        }

        @Override
        public Name mapRow(final ResultSet rs, final int row) throws SQLException {
            Blob childrenBlob = rs.getBlob(CHILDREN);
            byte[] childrenBytes = childrenBlob.getBytes(1, (int)childrenBlob.length());
            childrenBlob.free(); // apparently a good idea
            // todo - address this, ergh!
            try{
                return new Name(azquoMemoryDB, rs.getInt(ID), rs.getInt(PROVENANCEID), rs.getBoolean(ADDITIVE), rs.getString(ATTRIBUTES), childrenBytes, rs.getInt(NOPARENTS), rs.getInt(NOVALUES));
            } catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }
    }


    private class BulkNameInserter implements Runnable {
        private final AzquoMemoryDB azquoMemoryDB;
        private final List<Name> namesToInsert;
        private final String message;

        public BulkNameInserter(final AzquoMemoryDB azquoMemoryDB, final List<Name> namesToInsert, String message) {
            this.azquoMemoryDB = azquoMemoryDB;
            this.namesToInsert = namesToInsert;
            this.message = message;
        }

        @Override
        public void run() {
            if (!namesToInsert.isEmpty()) {
                final MapSqlParameterSource namedParams = new MapSqlParameterSource();
                final StringBuilder insertSql = new StringBuilder("INSERT INTO `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTNAME + "` (`" + ID + "`,`" + PROVENANCEID + "`,`"
                        + ADDITIVE + "`,`" + ATTRIBUTES + "`,`" + CHILDREN + "`,`" + NOPARENTS + "`,`" + NOVALUES + "`) VALUES ");
                int count = 1;

                for (Name name : namesToInsert) {
                    insertSql.append("(:" + ID).append(count)
                            .append(",:").append(PROVENANCEID).append(count)
                            .append(",:").append(ADDITIVE).append(count)
                            .append(",:").append(ATTRIBUTES).append(count)
                            .append(",:").append(CHILDREN).append(count)
                            .append(",:").append(NOPARENTS).append(count)
                            .append(",:").append(NOVALUES).append(count)
                            .append("), ");
                    namedParams.addValue(ID + count, name.getId());
                    namedParams.addValue(PROVENANCEID + count, name.getProvenance().getId());
                    namedParams.addValue(ADDITIVE + count, name.getAdditive());
                    namedParams.addValue(ATTRIBUTES + count, name.getAttributesForFastStore());
                    namedParams.addValue(CHILDREN + count, name.getChildrenIdsAsBytes());
                    namedParams.addValue(NOPARENTS + count, name.getParents().size());
                    namedParams.addValue(NOVALUES + count, name.getValues().size());
                    count++;
                }
                insertSql.delete(insertSql.length() - 2, insertSql.length());
                //System.out.println(insertSql.toString());
                jdbcTemplate.update(insertSql.toString(), namedParams);
                if (message != null){
                    System.out.println(message);
                }
            }
        }
    }

    public void persistNames(final AzquoMemoryDB azquoMemoryDB, final Collection<Name> names, boolean initialInsert) throws Exception {
        // currently only the inserter is multithreaded, adding the others should not be difficult
        // old pattern had update, I think this is a pain and unnecessary, just delete than add to the insert
        ExecutorService executor = Executors.newFixedThreadPool(azquoMemoryDB.getLoadingThreads());
        List<Name> toDelete = new ArrayList<>(UPDATELIMIT);
        List<Name> toInsert = new ArrayList<>(UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        int counter = 0;
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
        if (!initialInsert){ // no point deleting on initial insert
            for (Name name : names) {
                if (!name.getNeedsInserting()) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(name);
                    if (toDelete.size() == UPDATELIMIT) {
                        bulkDelete(azquoMemoryDB, toDelete);
                        toDelete = new ArrayList<>(UPDATELIMIT);
                    }
                    counter++;
                    if (counter%1_000_000 == 0){
                        System.out.println("bulk deleted " + counter);
                    }
                }
            }
        }
        bulkDelete(azquoMemoryDB, toDelete);
        System.out.println("bulk deleted " + counter);
        int insertCount = 0;
        for (Name name:names){
            if (!name.getNeedsDeleting()) insertCount++;
        }
        counter = 0;
        for (Name name : names) {
            if (!name.getNeedsDeleting()) { // then we insert, either it's new or was updated hence deleted ready for reinsertion
                counter++;
                insertCount--;
                toInsert.add(name);
                if (toInsert.size() == UPDATELIMIT) {
                    executor.execute(new BulkNameInserter(azquoMemoryDB, toInsert, counter%1_000_000 == 0 ? "bulk inserted " + counter + " remaining to add "  + insertCount: null));
                    toInsert = new ArrayList<>(UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        executor.execute(new BulkNameInserter(azquoMemoryDB, toInsert,"bulk inserted " + counter));
        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
            throw new Exception("Database " + azquoMemoryDB.getMySQLName() + " took longer than an hour to persist");
        }
        System.out.print("Name save complete.");
    }

    public final List<Name> findForMinMaxId(final AzquoMemoryDB azquoMemoryDB, int minId, int maxId) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTNAME + "`.* from `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTNAME + "` where id > " + minId + " and id <= " + maxId; // should I prepare this? Ints safe I think
        return jdbcTemplate.query(SQL_SELECT_ALL, new NameRowMapper(azquoMemoryDB));
    }

    public void createFastTableIfItDoesntExist(final AzquoMemoryDB azquoMemoryDB){
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + azquoMemoryDB.getMySQLName() + "`.`" + FASTNAME + "` (\n" +
                "`id` int(11) NOT NULL,\n" +
                "  `provenance_id` int(11) NOT NULL,\n" +
                "  `additive` tinyint(1) NOT NULL,\n" +
                "  `attributes` text COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
                "  `children` longblob NOT NULL,\n" +
                "  `no_parents` int(11) NOT NULL,\n" +
                "  `no_values` int(11) NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci AUTO_INCREMENT=1;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);

    }

}
