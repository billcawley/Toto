package com.azquo.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;


/**
 * Created by edward on 08/02/16.
 */

@Controller
@RequestMapping("/HBaseTest")

public class HBaseTestController {

    public static String COLUMN_FAMILY = "cf";

    @RequestMapping
    @ResponseBody
    public String handleRequest(HttpServletRequest request) throws IOException {
        /* successfully tested HDFS code. may be relevant later but for the moment commented and
        Configuration conf = new Configuration();
        conf.set("fs.default.name","hdfs://localhost:9000/");
        FileSystem fileSystem = FileSystem.get(conf);

        fileSystem.printStatistics();

       String dirName = "TestDirectory";

        Path src = new Path(fileSystem.getWorkingDirectory()+"/"+dirName);

        fileSystem.mkdirs(src);

        */

        /*
        // hbase test code
        Configuration conf = new Configuration();
        conf.addResource(new Path("/home/edward/Downloads/hbase-1.1.3/conf/hbase-site.xml"));
        createTable(conf, "test_value");
        createTable(conf, "test_name");
        createTable(conf, "test_provenance");

        HTable table = new HTable(conf, "test_value");

        // what about bulk inserting?
        Put p = new Put(Bytes.toBytes("theValueId")); // row index, the "name" of the row
// To set the value you'd like to update in the row 'myLittleRow',
// specify the column family, column qualifier, and value of the table
// cell you'd like to update. The column family must already exist
// in your table schema. The qualifier can be anything.
 // All must be specified as byte arrays as hbase is all about byte arrays
        p.add(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("provenance_id"),Bytes.toBytes("provenance_id_value"));
        table.put(p);

        Get g = new Get(Bytes.toBytes("theValueId"));
        Result r = table.get(g);
        byte[] value = r.getValue(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("provenance_id"));
        String valueStr = Bytes.toString(value);
        System.out.println("GET: " + valueStr);

        Scan s = new Scan();
        s.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes("provenance_id")); // edd to investigate - ranges here?
        try (ResultScanner scanner = table.getScanner(s)) {
// Scanners return Result instances.
// Now, for the actual iteration. One way is to use a while loop
// like so:
            for (Result rr = scanner.next(); rr != null; rr = scanner.next()) {
// print out the row we found and the columns we were looking
// for
                System.out.println("Found row: " + rr);
            }

// The other approach is to use a foreach loop. Scanners are
// iterable!
// for (Result rr : scanner) {
// System.out.println("Found row: " + rr);
// }
        }
// Make sure you close your scanners when you are done!
// Thats why we have it inside a try/finally clause


        return "";
    }

    public void createTable(Configuration conf, String tableName) throws IOException {
        HBaseAdmin admin = new HBaseAdmin(conf);
        if (admin.tableExists(tableName)) {
            System.out.println("table already exists!");
        } else {
            HTableDescriptor tableDesc = new HTableDescriptor(TableName.valueOf(tableName));
                tableDesc.addFamily(new HColumnDescriptor(COLUMN_FAMILY));
            admin.createTable(tableDesc);
            System.out.println("create table " + tableName + " ok.");
        }*/
        return "";
    }
}
