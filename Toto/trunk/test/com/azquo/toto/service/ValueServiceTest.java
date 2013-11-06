package com.azquo.toto.service;

import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.memorydb.Provenance;
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
    LoginService loginService;


    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCsvImport() throws Exception {

        LoggedInConnection loggedInConnection = loginService.login("imftest", "edd", "edd123",0);
//        LoggedInConnection loggedInConnection = loginService.login("tototest", "bill", "thew1password",0);

        String filePath = "/home/cawley/Downloads/imf.txt";
//        String filePath = "/home/cawley/Downloads/10daysinjan.csv";
        // skip file opening time . . .
        long track = System.currentTimeMillis();
        // for initial attempts at running it
        // going to write code here for CSV import that will be factored off into a function later


        // the header after which the headers stop being top parent names and become from a subset e.g. dates and then all the cells below are values
        String valuesAfter = "Country";
//        String valuesAfter = null;

        String valuesAfterParentName = "Period";
//        String valuesAfterParentName = null;

        String nameWithPeersName = "Data Item";
//        String nameWithPeersName = "vendorstatsname";
        CsvReader csvReader = new CsvReader(new InputStreamReader(new FileInputStream(filePath), "utf-8"), '\t');
        csvReader.readHeaders();
        String[] headers = csvReader.getHeaders();
        // are the following few lines necessary??
        ArrayList<String> checkedHeaders = new ArrayList<String>();
        for(String header : headers){
            if (header.trim().length() > 0){ // I don't know if the csv reader checks for this
                checkedHeaders.add(header);
            }
            if (header.equalsIgnoreCase(valuesAfter)){
                break; // stop scanning the headers when we're into the data bits
            }
        }
        // seems a bit of a complex way around it!
        headers = checkedHeaders.toArray(new String[checkedHeaders.size()]);
        Name nameWithPeers = null;
        for (String header : headers){
            if (!header.equalsIgnoreCase(ValueService.VALUE)){
                Name name = nameService.findOrCreateName(loggedInConnection, header);
                if (header.equalsIgnoreCase(nameWithPeersName)){
                    nameWithPeers = name;
                    System.out.println("name with peers : " + nameWithPeers);
                }
            }
        }

        if (nameWithPeers == null){
            throw new Exception("unable to find name with peers" + nameWithPeersName);
        }

        if(nameWithPeers != null){ // run through again linking peers. OK not that efficient but doesn't matter for the moment
            for (String header : headers){
                if (!header.equalsIgnoreCase(ValueService.VALUE) && !header.equalsIgnoreCase(nameWithPeers.getName())){
                    nameService.createPeer(loggedInConnection,nameWithPeers, header);
                }
            }
        }

        // now sort out the name sets directly under each header
        // we're using arraylists here in case the order in the file is important
        Map<String, List<String>> setMap = new HashMap<String, List<String>>();

        for (String header : headers){
            if (!header.equalsIgnoreCase(ValueService.VALUE)){
                setMap.put(header, new ArrayList<String>());
            }
        }

        /* ok header maps prepared,now need to find all possible name values for each - note we now need support
        for labels with / in them soo right here we're just looking for the top level
         */
        int recordcount = 0;
        while (csvReader.readRecord()){
            for (String header : headers){
                if (!header.equalsIgnoreCase(ValueService.VALUE)){
                    List<String> namesInThisColumn = setMap.get(header);
                    if (csvReader.get(header).trim().length() > 0 && !namesInThisColumn.contains(csvReader.get(header))){ // not seen this name yet
                        //System.out.println("adding to names in the column : " + csvReader.get(header).trim());
                        namesInThisColumn.add(csvReader.get(header).trim());
                    }
                }
            }
            recordcount++;
            if (recordcount%5000 == 0){
                System.out.println("reading record" + recordcount);
            }
        }

        // manually fix up the final top name and children
        if (valuesAfterParentName != null && valuesAfter != null){
            nameService.createPeer(loggedInConnection,nameWithPeers, valuesAfterParentName);
            // and the set of names which are column headings
            String[] rawHeaders = csvReader.getHeaders();
            boolean afterValuesAfter = false;
            List<String> namesHeadingValues = new ArrayList<String>();
            for (String rawHeader : rawHeaders){
                if (afterValuesAfter){
                    // ok we're in teh values bit of the headers so add
                    namesHeadingValues.add(rawHeader);
                }
                if (rawHeader.equalsIgnoreCase(valuesAfter)){
                    afterValuesAfter = true;
                }
            }
            setMap.put(valuesAfterParentName, namesHeadingValues);
        }

        for (String parentNameName : setMap.keySet()){
            Name parentName = nameService.findByName(loggedInConnection,parentNameName);
            List<String> rawChildNames = setMap.get(parentNameName);
            // ok here need to get clever to deal with slashes
            int count = 0;
            for (String rawChildName : rawChildNames){
                rawChildName = rawChildName.replace("//", "/");
                if (rawChildName.endsWith("/")){
                    rawChildName = rawChildName.substring(0, rawChildName.length() -1);
                }
                if (rawChildName.startsWith("/")){
                    rawChildName = rawChildName.substring(1, rawChildName.length());
                }
                if (rawChildName.contains("/")){
                    Name justAdded = null;
                    while (rawChildName.contains("/")){
                        // lest work from the top
                        String remainingTop = rawChildName.substring(0, rawChildName.indexOf("/"));
                        rawChildName = rawChildName.substring(rawChildName.indexOf("/") + 1); // chop off the name we just extracted
                        if (justAdded == null){ // the first of the directory string so to speak
                            justAdded = nameService.addOrCreateMember(loggedInConnection,parentName, remainingTop);
                            //System.out.println("parent : " + parentName + " child " + remainingTop);
                            count++;
                        } else {
                                justAdded = nameService.addOrCreateMember(loggedInConnection,justAdded, remainingTop);
                            //System.out.println("parent : " + justAdded + " child " + remainingTop);
                            count++;
                        }
                        if (!rawChildName.contains("/")){ // the final one
                                nameService.addOrCreateMember(loggedInConnection,justAdded, rawChildName);
                            //System.out.println("parent : " + justAdded + " child " + rawChildName);
                            count++;
                        }
                    }
                } else {
                    nameService.addOrCreateMember(loggedInConnection,parentName,rawChildName);
                    count++;
                }
            }
            System.out.println("created child labels for : " + parentName + " chidren : " + count);
        }
        csvReader.close();
        // now put the data in  . . .watch out!
        // open the file again.
        csvReader = new CsvReader(new InputStreamReader(new FileInputStream(filePath), "8859_1"), '\t');
        csvReader.readHeaders(); // might be necessary for init

        Provenance provenance = provenanceService.getTestProvenance();
        int valuecount = 0;
        while (csvReader.readRecord()){
            Set<String> names = new HashSet<String>();
            String value = null;
            for (String header : headers){
                if (header.equalsIgnoreCase(ValueService.VALUE)){
                    value = csvReader.get(header);
                } else {
                    String rawName = csvReader.get(header).trim();
                    if (rawName.endsWith("/")){
                        rawName = rawName.substring(0, rawName.length() -1);
                    }
                    if (rawName.startsWith("/")){
                        rawName = rawName.substring(1, rawName.length());
                    }
                    // after trimming we want the last lowest level once against the value
                    if (rawName.contains("/")){
                        rawName = rawName.substring(rawName.lastIndexOf("/") + 1);
                    }
                    names.add(rawName.trim());
                }
            }
            if (valuesAfterParentName != null && valuesAfter != null){ // we need to get sets of values for each line
                List<String> namesHeadingValues = new ArrayList<String>();
                boolean afterValuesAfter = false;
                for (String rawHeader : csvReader.getHeaders()){
                    if (afterValuesAfter){
                        // copy names so far and add the column heading we're on
                        Set<String> namesForValue = new HashSet<String>();
                        namesForValue.addAll(names);
                        namesForValue.add(rawHeader);
                        value = csvReader.get(rawHeader);
                        // copied from below, maybe factor later
                        if (value.trim().length() > 0){ // no point storing if there's no value!
                            valuecount++;
                            valueService.storeValueWithProvenanceAndNames(loggedInConnection,value, provenance, namesForValue);
                            if (valuecount%5000 == 0){
                                System.out.println("storing value " + valuecount);
                            }
                        }
                    }
                    if (rawHeader.equalsIgnoreCase(valuesAfter)){
                        afterValuesAfter = true;
                    }
                }
            } else { // single value per line
                if (value.trim().length() > 0){ // no point storing if there's no value!
                    valuecount++;
                    valueService.storeValueWithProvenanceAndNames(loggedInConnection,value, provenance, names);
                    if (valuecount%5000 == 0){
                        System.out.println("storing value " + valuecount);
                    }
                }
            }
        }
        System.out.println("csv import took " + (System.currentTimeMillis() - track) + "ms");


/*        Name test1 = nameService.findByName(loggedInConnection,"S++");
        Name test2 = nameService.findByName(loggedInConnection,"www.ctshirts.co.uk");
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
        System.out.println(searchResults.size() +  " records in " + track + "ms");*/

        // this will we dealt with differently later!

        nameService.persist(loggedInConnection);

        /*System.out.println("search results expanded");
        for (Value v : searchResults){
            System.out.print(v.getText() + " ");
            for (Name n : v.getNames()){
                System.out.print(n.getName() + " ");
            }
            System.out.println("");
        } */

        //System.out.println("going too try to save some data!");
        //totoMemoryDB.saveDataToMySQL();

/*        System.out.println("name hierarchy from this uploaded file :");
        for (String header : headers){
            if (!header.equalsIgnoreCase(ValueService.VALUE)){
                nameService.logNameHierarchy(nameService.findByName(header), 0);
            }
        }*/

    }

}
