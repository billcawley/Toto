package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.core.Value;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 11/02/16.
 *
 * To enable persisting to HBase, currently (early 2016) an SQL database is still required for the report server
 *
 * This perhaps could be factored and/or broken off into different classes.
 *
 */
public class HBaseDAO {

    private static byte[] COLUMN_FAMILY = Bytes.toBytes("cf");
    private static final int UPDATELIMIT = 2500; // not entirely sure what's relevant for hbase
    private final String confFile;

    public HBaseDAO(String confFile) {
        this.confFile = confFile;
    }

    private void bulkDelete(final AzquoMemoryDB azquoMemoryDB, final List<Integer> ids, String tableName) throws IOException {
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        // use the memory db persistence for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getPersistenceName() + "_" + tableName);
        List<Delete> list = new ArrayList<>();
        for (Integer id : ids) {
            list.add(new Delete(Bytes.toBytes(id)));
        }
        table.delete(list);
        table.close();
    }

    // I think deleting and recreating would be the simple thing
    private void clearTable(final String persistenceName, String tableName) throws IOException {
        deleteTable(persistenceName, tableName);
        createTableIfItDoesntExist(persistenceName, tableName);
    }

    private void createTableIfItDoesntExist(final String persistenceName, String tableName) throws IOException {
        tableName = persistenceName + "_" + tableName;
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        HBaseAdmin admin = new HBaseAdmin(conf);
        if (admin.tableExists(tableName)) {
            System.out.println("table already exists!");
        } else {
            HTableDescriptor tableDesc = new HTableDescriptor(TableName.valueOf(tableName));
            tableDesc.addFamily(new HColumnDescriptor(COLUMN_FAMILY));
            admin.createTable(tableDesc);
            System.out.println("create table " + tableName + " ok.");
        }
    }

    public void createRequiredTables(final String persistenceName) throws IOException {
        createTableIfItDoesntExist(persistenceName, VALUETABLE);
        createTableIfItDoesntExist(persistenceName, NAMETABLE);
        createTableIfItDoesntExist(persistenceName, Provenance.PERSIST_TABLE);
    }

    public void removeRequiredTables(final String persistenceName) throws IOException {
        deleteTable(persistenceName, VALUETABLE);
        deleteTable(persistenceName, NAMETABLE);
        deleteTable(persistenceName, Provenance.PERSIST_TABLE);
    }

    public void clearTables(final String persistenceName) throws IOException {
        clearTable(persistenceName, VALUETABLE);
        clearTable(persistenceName, NAMETABLE);
        clearTable(persistenceName, Provenance.PERSIST_TABLE);
    }

    private void deleteTable(final String persistenceName, String tableName) throws IOException {
        tableName = persistenceName + "_" + tableName;
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        HBaseAdmin admin = new HBaseAdmin(conf);
        admin.disableTable(tableName);
        admin.deleteTable(tableName);
        System.out.println("delete table " + tableName + " ok.");
    }

    private static final String NAMETABLE = "name";
    private static final byte[] PROVENANCEID = Bytes.toBytes("provenance_id");
    private static final byte[] ATTRIBUTES = Bytes.toBytes("attributes");
    private static final byte[] CHILDREN = Bytes.toBytes("children");
    private static final byte[] NOPARENTS = Bytes.toBytes("no_parents");
    private static final byte[] NOVALUES = Bytes.toBytes("no_values");

    private void putNames(final AzquoMemoryDB azquoMemoryDB, final Collection<Name> names) throws Exception {
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getPersistenceName() + "_" + NAMETABLE);
        List<Put> list = new ArrayList<>();
        for (Name name : names) {
            Put put = new Put(Bytes.toBytes(name.getId()));
            put.add(COLUMN_FAMILY, PROVENANCEID, Bytes.toBytes(name.getProvenance().getId()));
            put.add(COLUMN_FAMILY, ATTRIBUTES, Bytes.toBytes(name.getAttributesForFastStore()));
            put.add(COLUMN_FAMILY, CHILDREN, name.getChildrenIdsAsBytes());
            put.add(COLUMN_FAMILY, NOPARENTS, Bytes.toBytes(name.getParents().size()));
            put.add(COLUMN_FAMILY, NOVALUES, Bytes.toBytes(name.getChildren().size()));
            list.add(put);
        }
        table.put(list);
        table.close();
    }

    public void persistNames(final AzquoMemoryDB azquoMemoryDB, final Collection<Name> names, boolean initialInsert) throws Exception {
        createTableIfItDoesntExist(azquoMemoryDB.getPersistenceName(), NAMETABLE); // necessary?
        //ExecutorService executor = AzquoMemoryDB.sqlThreadPool;
        List<Integer> toDelete = new ArrayList<>(UPDATELIMIT);
        List<Name> toInsert = new ArrayList<>(UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        int counter = 0;
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
        if (!initialInsert){ // no point deleting on initial insert
            for (Name name : names) {
                if (!name.getNeedsInserting()) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(name.getId());
                    if (toDelete.size() == UPDATELIMIT) {
                        bulkDelete(azquoMemoryDB, toDelete, NAMETABLE);
                        toDelete = new ArrayList<>(UPDATELIMIT);
                    }
                    counter++;
                    if (counter%1_000_000 == 0){
                        System.out.println("bulk deleted " + counter);
                    }
                }
            }
        }
        bulkDelete(azquoMemoryDB, toDelete, NAMETABLE);
        System.out.println("bulk deleted " + counter);
        counter = 0;
        for (Name name : names) {
            if (!name.getNeedsDeleting()) { // then we insert, either it's new or was updated hence deleted ready for reinsertion
                counter++;
                toInsert.add(name);
                if (toInsert.size() == UPDATELIMIT) {
                    putNames(azquoMemoryDB, toInsert);
                    //futureBatches.add(executor.submit(new BulkNameInserter(azquoMemoryDB, toInsert, counter%1_000_000 == 0 ? "bulk inserted " + counter + " remaining to add "  + insertCount: null)));
                    toInsert = new ArrayList<>(UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        putNames(azquoMemoryDB, toInsert);
        System.out.println("Name save complete.");
    }


    // this will be like the max/min rows in mysql, as I understand the scanning in HBase
    public final List<Name> getNamesFromWithLimitSkipStartId(final AzquoMemoryDB azquoMemoryDB, int startId, int limit, AtomicInteger namesLoaded) throws Exception {
        Scan s = new Scan();
        if (startId > 0) {
            s.setStartRow(Bytes.toBytes(startId));
        }
        s.setFilter(new PageFilter(limit));
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getPersistenceName() + "_" + NAMETABLE);

        ResultScanner ss = table.getScanner(s);
        List<Name> toReturn = new ArrayList<>();
        Iterator<Result> iterator = ss.iterator();
        if (startId > 0 && iterator.hasNext()){ // skip the firstm it will already have been instantiated, we have to do this here in the DAO or the name constructor will be called twice.
            // Quirk of Hbase, we start from an index, skip it
            iterator.next();
        }

        while (iterator.hasNext()) {
            Result r = iterator.next();
            toReturn.add(new Name(azquoMemoryDB,
                    Bytes.toInt(r.getRow()),
                    Bytes.toInt(r.getValue(COLUMN_FAMILY, PROVENANCEID)),
                    Bytes.toString(r.getValue(COLUMN_FAMILY, ATTRIBUTES)),
                    r.getValue(COLUMN_FAMILY, CHILDREN),
                    Bytes.toInt(r.getValue(COLUMN_FAMILY, NOPARENTS)),
                    Bytes.toInt(r.getValue(COLUMN_FAMILY, NOVALUES))));
            namesLoaded.incrementAndGet();
        }
        table.close();
        return toReturn;
    }

    private static final String VALUETABLE = "value";
    // provenanceid above
    private static final byte[] TEXT = Bytes.toBytes("text");
    private static final byte[] NAMES = Bytes.toBytes("names");

    private void putValues(final AzquoMemoryDB azquoMemoryDB, final Collection<Value> values) throws Exception {
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getPersistenceName() + "_" + VALUETABLE);
        List<Put> list = new ArrayList<>();
        for (Value value : values) {
            Put put = new Put(Bytes.toBytes(value.getId()));
            put.add(COLUMN_FAMILY, PROVENANCEID, Bytes.toBytes(value.getProvenance().getId()));
            put.add(COLUMN_FAMILY, TEXT, Bytes.toBytes(value.getText()));
            put.add(COLUMN_FAMILY, NAMES, value.getNameIdsAsBytes());
            list.add(put);
        }
        table.put(list);
        table.close();
    }

    public void persistValues(final AzquoMemoryDB azquoMemoryDB, final Collection<Value> values, boolean initialInsert) throws Exception {
        createTableIfItDoesntExist(azquoMemoryDB.getPersistenceName(), VALUETABLE); //necessary?
        //ExecutorService executor = AzquoMemoryDB.sqlThreadPool;
        List<Integer> toDelete = new ArrayList<>(UPDATELIMIT);
        List<Value> toInsert = new ArrayList<>(UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        int counter = 0;
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
        if (!initialInsert){ // no point deleting on initial insert
            for (Value value : values) {
                if (!value.getNeedsInserting()) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(value.getId());
                    if (toDelete.size() == UPDATELIMIT) {
                        bulkDelete(azquoMemoryDB, toDelete, VALUETABLE);
                        toDelete = new ArrayList<>(UPDATELIMIT);
                    }
                    counter++;
                    if (counter%1_000_000 == 0){
                        System.out.println("bulk deleted " + counter);
                    }
                }
            }
        }
        bulkDelete(azquoMemoryDB, toDelete, VALUETABLE);
        System.out.println("bulk deleted " + counter);
        counter = 0;
        for (Value value : values) {
            if (!value.getNeedsDeleting()) { // then we insert, either it's new or was updated hence deleted ready for reinsertion
                counter++;
                toInsert.add(value);
                if (toInsert.size() == UPDATELIMIT) {
                    putValues(azquoMemoryDB, toInsert);
                    toInsert = new ArrayList<>(UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        putValues(azquoMemoryDB, toInsert);
        System.out.println("Value save complete.");
    }


    // this will be like the max/min rows in mysql, as I understand the scanning in HBase
    public final List<Value> getValuesFromWithLimitSkipStartId(final AzquoMemoryDB azquoMemoryDB, int startId, int limit, AtomicInteger tableName) throws Exception {
        Scan s = new Scan();
        if (startId > 0) {
            s.setStartRow(Bytes.toBytes(startId));
        }
        s.setFilter(new PageFilter(limit));
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getPersistenceName() + "_" + VALUETABLE);

        ResultScanner ss = table.getScanner(s);
        List<Value> toReturn = new ArrayList<>();
        Iterator<Result> iterator = ss.iterator();
        if (startId > 0 && iterator.hasNext()){ // skip the firstm it will already have been instantiated, we have to do this here in the DAO or the name constructor will be called twice.
            // Quirk of Hbase, we start from an index, skip it
            iterator.next();
        }

        while (iterator.hasNext()) {
            Result r = iterator.next();
            toReturn.add(new Value(azquoMemoryDB,
                    Bytes.toInt(r.getRow()),
                    Bytes.toInt(r.getValue(COLUMN_FAMILY, PROVENANCEID)),
                    Bytes.toString(r.getValue(COLUMN_FAMILY, TEXT)),
                    r.getValue(COLUMN_FAMILY, NAMES)));
            tableName.incrementAndGet();
        }
        table.close();
        return toReturn;
    }


    // initially just provenance but things like diagnostic info might be in there

    private static final byte[] JSON = Bytes.toBytes("json");

    private void putJsonBasedEntities(final String tableName, final AzquoMemoryDB azquoMemoryDB, final Collection<JsonRecordTransport> jsonRecordTransports) throws Exception {
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getPersistenceName() + "_" + tableName);
        List<Put> list = new ArrayList<>();
        for (JsonRecordTransport jsonRecordTransport : jsonRecordTransports) {
            Put put = new Put(Bytes.toBytes(jsonRecordTransport.id));
            put.add(COLUMN_FAMILY, JSON, Bytes.toBytes(jsonRecordTransport.json));
            list.add(put);
        }
        table.put(list);
        table.close();
    }

    public void persistJsonEntities(final String tableName, final AzquoMemoryDB azquoMemoryDB, final Collection<JsonRecordTransport> jsonRecordTransports) throws Exception {
        createTableIfItDoesntExist(azquoMemoryDB.getPersistenceName(), tableName); // again, necessary?
        //ExecutorService executor = AzquoMemoryDB.sqlThreadPool;
        List<Integer> toDelete = new ArrayList<>(UPDATELIMIT);
        List<JsonRecordTransport> toInsert = new ArrayList<>(UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        int counter = 0;
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
            for (JsonRecordTransport jsonRecordTransport : jsonRecordTransports) {
                if (jsonRecordTransport.state == JsonRecordTransport.State.UPDATE || jsonRecordTransport.state == JsonRecordTransport.State.DELETE) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(jsonRecordTransport.id);
                    if (toDelete.size() == UPDATELIMIT) {
                        bulkDelete(azquoMemoryDB, toDelete, tableName);
                        toDelete = new ArrayList<>(UPDATELIMIT);
                    }
                    counter++;
                    if (counter%1_000_000 == 0){
                        System.out.println("bulk deleted " + counter);
                    }
                }
            }
        bulkDelete(azquoMemoryDB, toDelete, tableName);
        System.out.println("bulk deleted " + counter);
        counter = 0;
        for (JsonRecordTransport jsonRecordTransport : jsonRecordTransports) {
            if (jsonRecordTransport.state != JsonRecordTransport.State.DELETE) { // then we insert, either it's new or was updated hence deleted ready for reinsertion
                counter++;
                toInsert.add(jsonRecordTransport);
                if (toInsert.size() == UPDATELIMIT) {
                    putJsonBasedEntities(tableName, azquoMemoryDB, toInsert);
                    toInsert = new ArrayList<>(UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        putJsonBasedEntities(tableName, azquoMemoryDB, toInsert);
        System.out.println("Json entities " + tableName + " save complete.");
    }


    // this will be like the max/min rows in mysql, as I understand the scanning in HBase
    public final List<JsonRecordTransport> initJsonEntitiesFromWithLimitSkipStartId(final String tableName, final AzquoMemoryDB azquoMemoryDB, int startId, int limit,
                                                                                    AzquoMemoryDB.JsonSerializableEntityInitializer entityInitializer, AtomicInteger entitiesLoaded) throws Exception {
        Scan s = new Scan();
        if (startId > 0) {
            s.setStartRow(Bytes.toBytes(startId));
        }
        s.setFilter(new PageFilter(limit));
        Configuration conf = new Configuration();
        conf.addResource(new Path(confFile));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getPersistenceName() + "_" + tableName);

        ResultScanner ss = table.getScanner(s);
        List<JsonRecordTransport> toReturn = new ArrayList<>();
        Iterator<Result> iterator = ss.iterator();
        if (startId > 0 && iterator.hasNext()){ // skip the first it will already have been instantiated, we have to do this here in the DAO or the name constructor will be called twice.
            // Quirk of Hbase, we start from an index, skip it
            iterator.next();
        }

        while (iterator.hasNext()) {
            Result r = iterator.next();
            final JsonRecordTransport jsonRecordTransport = new JsonRecordTransport(Bytes.toInt(r.getRow()),
                    Bytes.toString(r.getValue(COLUMN_FAMILY, JSON)),
                    JsonRecordTransport.State.LOADED);
            entityInitializer.initializeEntity(azquoMemoryDB, jsonRecordTransport); // feels a little hacky but will do the job, here is where the entity is actaully added to the database
            toReturn.add(jsonRecordTransport);
            entitiesLoaded.incrementAndGet();
        }
        table.close();
        return toReturn;
    }
}