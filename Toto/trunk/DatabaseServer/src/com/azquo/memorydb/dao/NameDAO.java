package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 01/10/15.
 *
 * I want faster storing and loading of names, the old JSON won't cut it, too much garbage
 * adapted from standardDAO , should factor some stuff off at some point
 */
public class NameDAO {

    // this value is not picked randomly, tests have it faster than 1k or 10k. It seems with imports bigger is not necessarily better. Possibly to do with query parsing overhead.

    //    public static final int UPDATELIMIT = 5000;
    static final String FASTNAME = "fast_name";
    private static final String PROVENANCEID = "provenance_id";
    private static final String ATTRIBUTES = "attributes";
    private static final String CHILDREN = "children";
    private static final String NOPARENTS = "no_parents";
    private static final String NOVALUES = "no_values";

    private static final class NameRowMapper implements RowMapper<Name> {
        final AzquoMemoryDB azquoMemoryDB;

        NameRowMapper(AzquoMemoryDB azquoMemoryDB) {
            this.azquoMemoryDB = azquoMemoryDB;
        }

        @Override
        public Name mapRow(final ResultSet rs, final int row) throws SQLException {
            Blob childrenBlob = rs.getBlob(CHILDREN);
            byte[] childrenBytes = childrenBlob.getBytes(1, (int)childrenBlob.length());
            childrenBlob.free(); // apparently a good idea
            // todo - address this, ergh!
            try{
                return new Name(azquoMemoryDB, rs.getInt(FastDAO.ID), rs.getInt(PROVENANCEID), rs.getString(ATTRIBUTES), childrenBytes, rs.getInt(NOPARENTS), rs.getInt(NOVALUES));
            } catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }
    }

    // I think sticking to callable even if the return type is null is more consistent
    private static class BulkNameInserter implements Callable<Void> {
        private final AzquoMemoryDB azquoMemoryDB;
        private final List<Name> namesToInsert;
        private final String message;

        BulkNameInserter(final AzquoMemoryDB azquoMemoryDB, final List<Name> namesToInsert, String message) {
            this.azquoMemoryDB = azquoMemoryDB;
            this.namesToInsert = namesToInsert;
            this.message = message;
        }

        @Override
        public Void call() {
            if (!namesToInsert.isEmpty()) {
                final MapSqlParameterSource namedParams = new MapSqlParameterSource();
                final StringBuilder insertSql = new StringBuilder("INSERT INTO `" + azquoMemoryDB.getPersistenceName() + "`.`" + FASTNAME + "` (`" + FastDAO.ID + "`,`" + PROVENANCEID + "`,`"
                        + ATTRIBUTES + "`,`" + CHILDREN + "`,`" + NOPARENTS + "`,`" + NOVALUES + "`) VALUES ");
                int count = 1;

                for (Name name : namesToInsert) {
                    insertSql.append("(:" + FastDAO.ID).append(count)
                            .append(",:").append(PROVENANCEID).append(count)
                            .append(",:").append(ATTRIBUTES).append(count)
                            .append(",:").append(CHILDREN).append(count)
                            .append(",:").append(NOPARENTS).append(count)
                            .append(",:").append(NOVALUES).append(count)
                            .append("), ");
                    namedParams.addValue(FastDAO.ID + count, name.getId());
                    namedParams.addValue(PROVENANCEID + count, name.getProvenance().getId());
                    namedParams.addValue(ATTRIBUTES + count, name.getAttributesForFastStore());
                    namedParams.addValue(CHILDREN + count, name.getChildrenIdsAsBytes());
                    namedParams.addValue(NOPARENTS + count, name.getParents().size());
                    namedParams.addValue(NOVALUES + count, name.getValues().size());
                    count++;
                }
                insertSql.delete(insertSql.length() - 2, insertSql.length());
                //System.out.println(insertSql.toString());
                JdbcTemplateUtils.update(insertSql.toString(), namedParams);
                if (message != null){
                    System.out.println(message);
                }
            }
            return null;
        }
    }

    public static void persistNames(final AzquoMemoryDB azquoMemoryDB, final Collection<Name> names) throws Exception {
        // currently only the inserter is multithreaded, adding the others should not be difficult
        // old pattern had update, I think this is a pain and unnecessary, just delete than add to the insert
        ExecutorService executor = AzquoMemoryDB.sqlThreadPool;
        List<Name> toDelete = new ArrayList<>(FastDAO.UPDATELIMIT);
        List<Name> toInsert = new ArrayList<>(FastDAO.UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        int counter = 0;
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
            for (Name name : names) {
                if (!name.getNeedsInserting()) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(name);
                    if (toDelete.size() == FastDAO.UPDATELIMIT) {
                        FastDAO.bulkDelete(azquoMemoryDB, toDelete, FASTNAME);
                        toDelete = new ArrayList<>(FastDAO.UPDATELIMIT);
                    }
                    counter++;
                    if (counter%1_000_000 == 0){
                        System.out.println("bulk deleted " + counter);
                    }
                }
            }
            FastDAO.bulkDelete(azquoMemoryDB, toDelete, FASTNAME);
        System.out.println("bulk deleted " + counter);
        int insertCount = 0;
        for (Name name:names){
            if (!name.getNeedsDeleting()) insertCount++;
        }
        counter = 0;
        List<Future> futureBatches = new ArrayList<>();
        for (Name name : names) {
            if (!name.getNeedsDeleting()) { // then we insert, either it's new or was updated hence deleted ready for reinsertion
                counter++;
                insertCount--;
                toInsert.add(name);
                if (toInsert.size() == FastDAO.UPDATELIMIT) {
                    futureBatches.add(executor.submit(new BulkNameInserter(azquoMemoryDB, toInsert, counter%1_000_000 == 0 ? "bulk inserted " + counter + " remaining to add "  + insertCount: null)));
                    toInsert = new ArrayList<>(FastDAO.UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        futureBatches.add(executor.submit(new BulkNameInserter(azquoMemoryDB, toInsert,"bulk inserted " + counter)));
        for (Future<?> futureBatch : futureBatches){
            futureBatch.get(1, TimeUnit.HOURS);
        }
        System.out.println("Name save complete.");
    }

    public static List<Name> findForMinMaxId(final AzquoMemoryDB azquoMemoryDB, int minId, int maxId) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select `" + azquoMemoryDB.getPersistenceName() + "`.`" + FASTNAME + "`.* from `" + azquoMemoryDB.getPersistenceName() + "`.`" + FASTNAME + "` where id > " + minId + " and id <= " + maxId; // should I prepare this? Ints safe I think
        return JdbcTemplateUtils.query(SQL_SELECT_ALL, new NameRowMapper(azquoMemoryDB));
    }

    static void createFastTableIfItDoesntExist(final String databaseName){
        JdbcTemplateUtils.update("CREATE TABLE IF NOT EXISTS `" + databaseName + "`.`" + FASTNAME + "` (\n" +
                "`id` int(11) NOT NULL,\n" +
                "  `provenance_id` int(11) NOT NULL,\n" +
                "  `attributes` text COLLATE utf8mb4_unicode_ci NOT NULL,\n" +
                "  `children` longblob NOT NULL,\n" +
                "  `no_parents` int(11) NOT NULL,\n" +
                "  `no_values` int(11) NOT NULL,\n" +
                "  PRIMARY KEY (`id`)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci AUTO_INCREMENT=1;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);

    }

    public static void zapAdditive(final String databaseName){
        JdbcTemplateUtils.updateNoException("ALTER TABLE `" + databaseName + "`.`" + FASTNAME + "` DROP `additive`;", JsonRecordDAO.EMPTY_PARAMETERS_MAP);
    }

    public static int findMaxId(final AzquoMemoryDB azquoMemoryDB) throws DataAccessException {
        return FastDAO.findMaxId(azquoMemoryDB, FASTNAME);
    }
}
