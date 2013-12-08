package com.azquo.toto.controller;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.Value;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
import com.azquo.toto.service.ValueService;
import com.csvreader.CsvReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.StringReader;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 13:46
 * <p/>
 * instructions to manipulate the data itself, will become more complex and deal with a connection to instructions can be in state
 */
@Controller
@RequestMapping("/Value")
public class ValueController {

    @Autowired
    private NameService nameService;
    @Autowired
    private LoginService loginService;
    @Autowired
    private ValueService valueService;

    // TODO : break up into separate functions

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "rowheadings", required = false) final String rowheadings, @RequestParam(value = "columnheadings", required = false) final String columnheadings,
                                @RequestParam(value = "context", required = false) final String context, @RequestParam(value = "connectionid", required = false) final String connectionId,
                                @RequestParam(value = "region", required = false) String region, @RequestParam(value = "lockmap", required = false) final String lockMap,
                                @RequestParam(value = "editeddata", required = false) final String editedData, @RequestParam(value = "searchbynames", required = false) String searchByNames) throws Exception {

        // these 3 statements copied, should factor

        if (region != null && region.length() == 0){
            region = null; // make region null and blank the same . . .   , maybe change later???
        }

        try {

            if (connectionId == null) {
                return "error:no connection id";
            }

            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }

            if (rowheadings != null && rowheadings.length() > 0) {
                // ok we'll assume a list or command. First just a basic children
                if (rowheadings.contains(";")) {
                    final String nameString = rowheadings.substring(0, rowheadings.indexOf(";")).trim();
                    final Name forName = nameService.findByName(loggedInConnection, nameString);
                    if (forName != null) {
                        return valueService.getRowHeadings(loggedInConnection, region, forName);
                    } else {
                        return "error:cannot find name : " + nameString;
                    }
                } else {
                    return "error:cannot parse row headings string!";
                }
            }

            if (columnheadings != null && columnheadings.length() > 0) {
                // ok we'll assume a list or command. First just a basic children
                if (columnheadings.contains(";")) {
                    final String nameString = columnheadings.substring(0, columnheadings.indexOf(";")).trim();
                    final Name forName = nameService.findByName(loggedInConnection, nameString);
                    if (forName != null) {
                        return valueService.getColumnHeadings(loggedInConnection, region, forName);
                    } else {
                        return "error:cannot find name : " + nameString;
                    }
                } else {
                    return "error:cannot parse column headings string!";
                }
            }

            if (context != null && context.length() > 0) {
                //System.out.println("passed context : " + context);
                StringTokenizer st = new StringTokenizer(context, ";");
                List<Name> contextNames = new ArrayList<Name>();
                while (st.hasMoreTokens()){
                    final Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim());
                    if (contextName == null) {
                        return "error:I can't find a name for the context : " + context;
                    }
                    contextNames.add(contextName);
                }
                if (loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0 && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0) {
                    return valueService.getExcelDataForColumnsRowsAndContext(loggedInConnection, contextNames, region);
                } else {
                    return "error:Column and/or row headings are not defined for use with context" + (region != null ? " and region " + region : "");
                }
            }

            if (lockMap != null) {
                return loggedInConnection.getLockMap(region);
            }

            System.out.println("edited data " + editedData);
            if (editedData != null && editedData.length() > 0) {
                System.out.println("------------------");
                System.out.println(loggedInConnection.getLockMap(region));
                System.out.println(loggedInConnection.getRowHeadings(region));
                System.out.println(loggedInConnection.getColumnHeadings(region));
                System.out.println(loggedInConnection.getSentDataMap(region));
                System.out.println(loggedInConnection.getContext(region));
                if (loggedInConnection.getLockMap(region) != null &&
                        loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0
                        && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0
                        && loggedInConnection.getSentDataMap(region) != null && loggedInConnection.getContext(region) != null) {
                    // oh-kay, need to compare against the sent data
                    // going to parse the data here for the moment as parsing is controller stuff
                    // I need to track column and Row
                    int rowCounter = 0;
                    CsvReader originalReader = new CsvReader(new StringReader(loggedInConnection.getSentDataMap(region)), '\t');
                    CsvReader editedReader = new CsvReader(new StringReader(editedData), '\t');
                    CsvReader lockMapReader = new CsvReader(new StringReader(loggedInConnection.getLockMap(region)), '\t');
                    // rows, columns, value lists
                    List<List<List<Value>>> dataValuesMap = loggedInConnection.getDataValueMap(region);
                    List<List<Set<Name>>> dataNamesMap = loggedInConnection.getDataNamesMap(region);
                    // TODO : deal with mismatched column and row counts
                    int numberOfValuesModified = 0;
                    while (lockMapReader.readRecord()) {
                        int columnCounter = 0;
                        List<List<Value>> rowValues = dataValuesMap.get(rowCounter);
                        List<Set<Name>> rowNames = dataNamesMap.get(rowCounter);
                        originalReader.readRecord();
                        editedReader.readRecord();
                        String[] originalValues = originalReader.getValues();
                        String[] editedValues = editedReader.getValues();
                        for (String locked : lockMapReader.getValues()) {
                            //System.out.println("on " + columnCounter + ", " + rowCounter + " locked : " + locked);
                            // and here we get to the crux, the values do NOT match
                            if (!originalValues[columnCounter].trim().equals(editedValues[columnCounter])) {
                                if (!locked.equalsIgnoreCase("locked")) { // it wasn't locked, good to go, check inside the different values bit to error if the excel tries something it should not
                                    // for the moment we'll only play ball if there's an existing value, will add new value possibility tomorrow
                                    System.out.println(columnCounter + ", " + rowCounter + " not locked and modified");
                                    List<Value> valuesForCell = rowValues.get(columnCounter);
                                    Set<Name> namesForCell = rowNames.get(columnCounter);
                                    if (valuesForCell.size() == 1) {
                                        Value theValue = valuesForCell.get(0);
                                        System.out.println("trying to overwrite");
                                        valueService.overWriteExistingValue(loggedInConnection, region, theValue, editedValues[columnCounter]);
                                        numberOfValuesModified++;
                                    } else if (valuesForCell.isEmpty()) {
                                        System.out.println("storing new value here . . .");
                                        valueService.storeNewValueFromEdit(loggedInConnection, region, namesForCell, editedValues[columnCounter]);
                                    }
                                } else {
                                    // should this add on for a list???
                                    return "error:cannot edit locked cell " + columnCounter + ", " + rowCounter + " in region " + region;
                                }
                            }
                            columnCounter++;
                        }
                        rowCounter++;
                    }
                    return numberOfValuesModified + " values modified";
                } else {
                    return "error:cannot deal with edited data as there is no sent data/rows/columns/context";
                }
            }

            if (searchByNames != null && searchByNames.length() > 0) {


                System.out.println("search by names : " + searchByNames);
                if (searchByNames.startsWith("`")){
                    searchByNames = searchByNames.substring(1);
                }
                if (searchByNames.endsWith("`")){
                    searchByNames = searchByNames.substring(0, searchByNames.length() - 1);
                }
                StringTokenizer st = new StringTokenizer(searchByNames, "`,`");
                Set<Name> names = new HashSet<Name>();
                while (st.hasMoreTokens()){
                    String nameName = st.nextToken().trim();
                    Name name = nameService.findByName(loggedInConnection, nameName);
                    if (name != null){
                        names.add(name);
                    }
                }
                if (!names.isEmpty()){
                    String result = valueService.getExcelDataForNamesSearch(loggedInConnection,names);
                    System.out.println("search by names result : " + result);
                    return result;
                }
            }


            return "no action taken";
        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }


    }

}