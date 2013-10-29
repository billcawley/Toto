package com.azquo.toto.service;

import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.TotoMemoryDB;
import com.azquo.toto.memorydb.Value;
import com.azquo.toto.memorydb.Name;
import com.csvreader.CsvReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 23:17
 * Test for the value service, an important business
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})

public class ValueServiceTest {



    @Autowired
    ValueService valueService;
    @Autowired
    NameService nameService;
    @Autowired
    ProvenanceService provenanceService;
    @Autowired
    ValueDAO valueDao;
    @Autowired
    TotoMemoryDB totoMemoryDB;


    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCsvImport() throws Exception {
        // skip file opening time . . .
        long track = System.currentTimeMillis();
        // for initial attempts at running it
        // going to write coode here foor CSV import that will be factored off into a function later
        String nameWithPeersName = "Measure";
        CsvReader csvReader = new CsvReader(new InputStreamReader(new FileInputStream("/home/cawley/Downloads/totosample.csv"), "8859_1"), ',');
        csvReader.readHeaders();
        String[] headers = csvReader.getHeaders();
        Name nameWithPeers = null;
        for (String header : headers){
            if (!header.equalsIgnoreCase(ValueService.VALUE)){
                Name name = nameService.findOrCreateName(header);
                if (header.equalsIgnoreCase(nameWithPeersName)){
                    nameWithPeers = name;
                }
            }
        }

        if(nameWithPeers != null){ // run through again linking peers. OK not that efficient but doesn't matter for the moment
            for (String header : headers){
                if (!header.equalsIgnoreCase(ValueService.VALUE) && !header.equalsIgnoreCase(nameWithPeers.getName())){
                    nameService.createPeer(nameWithPeers, header);
                }
            }
        }

        // now sort out the name sets
        Map<String, Map<String, String>> setMap = new HashMap<String, Map<String, String>>();

        for (String header : headers){
            if (!header.equalsIgnoreCase(ValueService.VALUE)){
                setMap.put(header, new HashMap<String, String>());
            }
        }

        // ok header maps prepared,now need to find all possible name values for each

        while (csvReader.readRecord()){
            for (String header : headers){
                if (!header.equalsIgnoreCase(ValueService.VALUE)){
                    Map<String, String> namesInThisColumn = setMap.get(header);
                    if (namesInThisColumn.get(csvReader.get(header)) == null){ // not seen this name yet
                        namesInThisColumn.put(csvReader.get(header),"");
                    }
                }
            }
        }

        for (String parentNameName : setMap.keySet()){
            Name parentName = nameService.findByName(parentNameName);
            Map<String, String> childNames = setMap.get(parentNameName);
            System.out.println("creating child labels for : " + parentName + " chidren : " + childNames.keySet().size());
            nameService.createMembers(parentName, new ArrayList<String>(childNames.keySet()));
        }
        csvReader.close();
        // now put the data in  . . .watch out!
        // open the file again.
        csvReader = new CsvReader(new InputStreamReader(new FileInputStream("/home/cawley/Downloads/totosample.csv"), "8859_1"), ',');
        csvReader.readHeaders();
        headers = csvReader.getHeaders();

        Provenance provenance = provenanceService.getTestProvenance();

        while (csvReader.readRecord()){
            Set<String> names = new HashSet<String>();
            String value = null;
            for (String header : headers){
                if (header.equalsIgnoreCase(ValueService.VALUE)){
                    value = csvReader.get(header);
                } else {
                    names.add(csvReader.get(header));
                }
            }
            valueService.storeValueWithProvenanceAndNames(value, provenance, names);
        }
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms");


        Name test1 = nameService.findByName("Time Activity");
        Name test2 = nameService.findByName("Total All Methods");
//        Name test3 = nameService.findByName("Primary Strategy - Targeted Support");
//        Name test4 = nameService.findByName("Lynne Swainston");

        Set<Name> searchCriteria = new HashSet<Name>();
        searchCriteria.add(test1);
        searchCriteria.add(test2);
//        searchCriteria.add(test3);
//        searchCriteria.add(test4);
        track = System.currentTimeMillis();
        List<Value> searchResults = valueService.findForNames(searchCriteria);
        track = System.currentTimeMillis() - track;
        System.out.println(searchResults.size() +  " records in " + track + "ms");
        track = System.currentTimeMillis();
        searchResults = valueService.findForNames(searchCriteria);
        track = System.currentTimeMillis() - track;
        System.out.println(searchResults.size() +  " records in " + track + "ms");
        track = System.currentTimeMillis();
        searchResults = valueService.findForNames(searchCriteria);
        track = System.currentTimeMillis() - track;
        System.out.println(searchResults.size() +  " records in " + track + "ms");

        System.out.println("going too try to save some data!");
        totoMemoryDB.saveDataToMySQL();

    }

}
