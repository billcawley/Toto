package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.AzquoMemoryDBEntity;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.PageFilter;
import org.apache.hadoop.hbase.util.Bytes;

import javax.print.attribute.standard.MediaSize;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by edward on 11/02/16.
 *
 * Initially more POC code for EMC who will want to store data on HDFS. Azquo eill do this via Hbase.
 *
 */
public class HBaseDAO {

    public static byte[] COLUMN_FAMILY = Bytes.toBytes("cf");
    public static final int UPDATELIMIT = 2500; // going to 2.5k as now some of the records can be quite large! Also simultaneous.


    protected void bulkDelete(final AzquoMemoryDB azquoMemoryDB, final List<? extends AzquoMemoryDBEntity> entities, String tableName) throws IOException {
        Configuration conf = new Configuration();
        conf.addResource(new Path("/home/edward/Downloads/hbase-1.1.3/conf/hbase-site.xml"));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getMySQLName() + "_" + tableName);
        List<Delete> list = new ArrayList<>();
        for (AzquoMemoryDBEntity azquoMemoryDBEntity : entities) {
            list.add(new Delete(Bytes.toBytes(azquoMemoryDBEntity.getId())));
        }
        table.delete(list);
        table.close();
    }

// I think irrelevant for hbase - it was being used to scan a number of rows, now from with limit should do it
//    public final int findMaxId(final AzquoMemoryDB azquoMemoryDB) throws DataAccessException {

    // I think deleting and recreating would be the simple thing
    public void clearTable(final AzquoMemoryDB azquoMemoryDB, String tableName) throws IOException {
        deleteTable(azquoMemoryDB, tableName);
        createTableIfItDoesntExist(azquoMemoryDB, tableName);
    }

    private static final String NAMETABLE = "name";
    private static final byte[] PROVENANCEID = Bytes.toBytes("provenance_id");
    private static final byte[] ADDITIVE = Bytes.toBytes("additive");
    private static final byte[] ATTRIBUTES = Bytes.toBytes("attributes");
    private static final byte[] CHILDREN = Bytes.toBytes("children");
    private static final byte[] NOPARENTS = Bytes.toBytes("no_parents");
    private static final byte[] NOVALUES = Bytes.toBytes("no_values");

    public void createTableIfItDoesntExist(final AzquoMemoryDB azquoMemoryDB, String tableName) throws IOException {
        tableName = azquoMemoryDB.getMySQLName() + "_" + tableName;
        Configuration conf = new Configuration();
        conf.addResource(new Path("/home/edward/Downloads/hbase-1.1.3/conf/hbase-site.xml"));
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

    public void deleteTable(final AzquoMemoryDB azquoMemoryDB, String tableName) throws IOException {
        tableName = azquoMemoryDB.getMySQLName() + "_" + tableName;
        Configuration conf = new Configuration();
        conf.addResource(new Path("/home/edward/Downloads/hbase-1.1.3/conf/hbase-site.xml"));
        HBaseAdmin admin = new HBaseAdmin(conf);
        admin.disableTable(tableName);
        admin.deleteTable(tableName);
        System.out.println("delete table " + tableName + " ok.");
    }

    private void putNames(final AzquoMemoryDB azquoMemoryDB, final Collection<Name> names) throws Exception {
        Configuration conf = new Configuration();
        conf.addResource(new Path("/home/edward/Downloads/hbase-1.1.3/conf/hbase-site.xml"));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getMySQLName() + "_" + NAMETABLE);
        List<Put> list = new ArrayList<>();
        for (Name name : names) {
            Put put = new Put(Bytes.toBytes(name.getId()));
            put.add(COLUMN_FAMILY, PROVENANCEID, Bytes.toBytes(name.getProvenance().getId()));
            put.add(COLUMN_FAMILY, ADDITIVE, Bytes.toBytes(name.getAdditive()));
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
        createTableIfItDoesntExist(azquoMemoryDB, NAMETABLE);
        //ExecutorService executor = AzquoMemoryDB.sqlThreadPool;
        List<Name> toDelete = new ArrayList<>(UPDATELIMIT);
        List<Name> toInsert = new ArrayList<>(UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        int counter = 0;
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
        if (!initialInsert){ // no point deleting on initial insert
            for (Name name : names) {
                if (!name.getNeedsInserting()) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(name);
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
                    putNames(azquoMemoryDB, toInsert);
                    //futureBatches.add(executor.submit(new BulkNameInserter(azquoMemoryDB, toInsert, counter%1_000_000 == 0 ? "bulk inserted " + counter + " remaining to add "  + insertCount: null)));
                    toInsert = new ArrayList<>(UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        putNames(azquoMemoryDB, toInsert);
        System.out.print("Name save complete.");
    }


    // this will be like the max/min rows in mysql, as I understand the scanning in HBase
    public final List<Name> getNamesFromWithLimitSkipStartId(final AzquoMemoryDB azquoMemoryDB, int startId, int limit, AtomicInteger namesLoaded) throws Exception {
        Scan s = new Scan();
        if (startId > 0) {
            s.setStartRow(Bytes.toBytes(startId));
        }
        s.setFilter(new PageFilter(limit));
        Configuration conf = new Configuration();
        conf.addResource(new Path("/home/edward/Downloads/hbase-1.1.3/conf/hbase-site.xml"));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getMySQLName() + "_" + NAMETABLE);

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
                    Bytes.toBoolean(r.getValue(COLUMN_FAMILY, ADDITIVE)),
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
        conf.addResource(new Path("/home/edward/Downloads/hbase-1.1.3/conf/hbase-site.xml"));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getMySQLName() + "_" + VALUETABLE);
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
        createTableIfItDoesntExist(azquoMemoryDB, VALUETABLE);
        //ExecutorService executor = AzquoMemoryDB.sqlThreadPool;
        List<Value> toDelete = new ArrayList<>(UPDATELIMIT);
        List<Value> toInsert = new ArrayList<>(UPDATELIMIT); // it's going to be this a lot of the time, save all the resizing
        int counter = 0;
        // ok since we're not doing updates but rather delete before reinsert the delete will have to go first. I don't think this will be a big problem
        if (!initialInsert){ // no point deleting on initial insert
            for (Value value : values) {
                if (!value.getNeedsInserting()) { // either flagged for delete or it exists already and will be reinserted, delete it
                    toDelete.add(value);
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
        int insertCount = 0;
        for (Value value : values){
            if (!value.getNeedsDeleting()) insertCount++;
        }
        counter = 0;
        for (Value value : values) {
            if (!value.getNeedsDeleting()) { // then we insert, either it's new or was updated hence deleted ready for reinsertion
                counter++;
                insertCount--;
                toInsert.add(value);
                if (toInsert.size() == UPDATELIMIT) {
                    putValues(azquoMemoryDB, toInsert);
                    toInsert = new ArrayList<>(UPDATELIMIT); // I considered using clear here but of course the object has been passed into the bulk inserter, bad idea!
                }
            }
        }
        putValues(azquoMemoryDB, toInsert);
        System.out.print("Value save complete.");
    }


    // this will be like the max/min rows in mysql, as I understand the scanning in HBase
    public final List<Value> getValuesFromWithLimitSkipStartId(final AzquoMemoryDB azquoMemoryDB, int startId, int limit, AtomicInteger namesLoaded) throws Exception {
        Scan s = new Scan();
        if (startId > 0) {
            s.setStartRow(Bytes.toBytes(startId));
        }
        s.setFilter(new PageFilter(limit));
        Configuration conf = new Configuration();
        conf.addResource(new Path("/home/edward/Downloads/hbase-1.1.3/conf/hbase-site.xml"));
        // use the memory db mysql name for the moment as the table name prefix? Possible clashes if a load of databases on the same Hbase
        HTable table = new HTable(conf, azquoMemoryDB.getMySQLName() + "_" + VALUETABLE);

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
            namesLoaded.incrementAndGet();
        }
        table.close();
        return toReturn;
    }
}
