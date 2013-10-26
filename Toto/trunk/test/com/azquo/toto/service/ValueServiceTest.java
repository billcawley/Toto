package com.azquo.toto.service;

import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.*;
import com.azquo.toto.entity.Label;
import com.csvreader.CsvReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.awt.*;
import java.io.FileInputStream;
import java.io.FileReader;
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



    String databaseName = "tototest"; // hard code here for the moment
    @Autowired
    ValueService valueService;
    @Autowired
    LabelService labelService;
    @Autowired
    ValueDAO valueDao;


    @Before
    public void setUp() throws Exception {
        valueService.setDatabaseName(databaseName);
    }

    @Test
    public void testFindByLabels() throws Exception {

    }

    @Test
    public void testStoreValueWithLabels() throws Exception {

    }

    @Test
    public void testCsvImport() throws Exception {
        // skip file opening time . . .
        long track = System.currentTimeMillis();
        // for initial attempts at running it
        valueService.setDatabaseName("toto");
        // going to write coode here foor CSV import that will be factored off into a function later
        String nameWithPeers = "Measure";
        CsvReader csvReader = new CsvReader(new InputStreamReader(new FileInputStream("/home/cawley/Downloads/totosample.csv"), "8859_1"), ',');
        csvReader.readHeaders();
        String[] headers = csvReader.getHeaders();
        Label labelWithPeers = null;
        for (String header : headers){
            if (!header.equalsIgnoreCase(ValueService.VALUE)){
                Label label = labelService.findOrCreateLabel(header);
                if (header.equalsIgnoreCase(nameWithPeers)){
                    labelWithPeers = label;
                }
            }
        }

        if(labelWithPeers != null){ // run through again linking peers. OK not that efficient but doesn't matter for the moment
            for (String header : headers){
                if (!header.equalsIgnoreCase(ValueService.VALUE) && !header.equalsIgnoreCase(labelWithPeers.getName())){
                    labelService.createPeer(labelWithPeers, header);
                }
            }
        }

        // now sort out the label sets
        Map<String, Map<String, String>> setMap = new HashMap<String, Map<String, String>>();

        for (String header : headers){
            if (!header.equalsIgnoreCase(ValueService.VALUE)){
                setMap.put(header, new HashMap<String, String>());
            }
        }

        // ok header maps prepared,now need to find all possible label values for each

        while (csvReader.readRecord()){
            for (String header : headers){
                if (!header.equalsIgnoreCase(ValueService.VALUE)){
                    Map<String, String> labelsInThisColumn = setMap.get(header);
                    if (labelsInThisColumn.get(csvReader.get(header)) == null){ // not seen this label yet
                        labelsInThisColumn.put(csvReader.get(header),"");
                    }
                }
            }
        }

        for (String parentLabelName : setMap.keySet()){
            Label parentLabel = labelService.findByName(parentLabelName);
            Map<String, String> childLabels = setMap.get(parentLabelName);
            labelService.createMembers(parentLabel, new ArrayList<String>(childLabels.keySet()));
        }
        csvReader.close();
        // now put the data in  . . .watch out!
        // open the file again.
        csvReader = new CsvReader(new InputStreamReader(new FileInputStream("/home/cawley/Downloads/totosample.csv"), "8859_1"), ',');
        csvReader.readHeaders();
        headers = csvReader.getHeaders();

        while (csvReader.readRecord()){
            Set<String> labels = new HashSet<String>();
            // hyst a
            String value = null;
            for (String header : headers){
                if (header.equalsIgnoreCase(ValueService.VALUE)){
                    value = csvReader.get(header);
                } else {
                    labels.add(csvReader.get(header));
                }
            }
            System.out.println(valueService.storeValueWithLabels(value, labels));
        }
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms");


        Label test1 = labelService.findByName("Time Activity");
        Label test2 = labelService.findByName("Total All Methods");
        Label test3 = labelService.findByName("Primary Strategy - Targeted Support");
        Label test4 = labelService.findByName("Lynne Swainston");
//        Label test3 = labelService.findByName("Primary Strategy - Targeted Support");
//        Label test3 = labelService.findByName("Primary Strategy - Targeted Support");

        Set<Label> searchCriteria = new HashSet<Label>();
        searchCriteria.add(test1);
        searchCriteria.add(test2);
//        searchCriteria.add(test3);
//        searchCriteria.add(test4);
        track = System.currentTimeMillis();
        Set<Value> searchResults = valueService.findForLabels(searchCriteria);
        track = System.currentTimeMillis() - track;
        System.out.println(searchResults.size() +  " records in " + track + "ms");
    }

}
