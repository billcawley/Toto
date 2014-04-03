package com.azquo.controller;

import com.azquo.jsonrequestentities.NameJsonRequest;
import com.azquo.jsonrequestentities.ValueJsonRequest;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Value;
import com.azquo.service.*;
import com.csvreader.CsvReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.StringReader;
import java.net.URLDecoder;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 13:46
 * <p/>
 * instructions to manipulate the data itself. Getting data and row/column heading here is pretty simple
 * It does currently parse the incoming data with CSV readers . . .should this be moved to the service??
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

    private static final Logger logger = Logger.getLogger(ValueController.class);
    private static final ObjectMapper jacksonMapper = new ObjectMapper();

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "rowheadings", required = false) String rowheadings
            , @RequestParam(value = "columnheadings", required = false) String columnheadings
            , @RequestParam(value = "context", required = false) String context
            , @RequestParam(value = "connectionid", required = false) String connectionId
            , @RequestParam(value = "region", required = false) String region
            , @RequestParam(value = "lockmap", required = false) String lockMap
            , @RequestParam(value = "editeddata", required = false) String editedData
            , @RequestParam(value = "searchbynames", required = false) String searchByNames
            , @RequestParam(value = "jsonfunction", required = false) String jsonfunction
            , @RequestParam(value = "user", required = false)  String user
            , @RequestParam(value = "password", required = false) String password
            , @RequestParam(value = "filtercount", required = false) String filterString
            , @RequestParam(value = "spreadsheetName", required = false) String spreadsheetName
            , @RequestParam(value = "json", required = false) String json
            , @RequestParam(value = "database", required = false) String database) throws Exception {


        long startTime = System.currentTimeMillis();
        if (region != null && region.length() == 0) {
            region = null; // make region null and blank the same . . .   , maybe change later???
        }
        if (json!=null && json.length() > 0){
            // for Google sheets, better to send all parameters as JSON
           ValueJsonRequest valueJsonRequest;
           try {
               valueJsonRequest = jacksonMapper.readValue(json, ValueJsonRequest.class);
           } catch (Exception e) {
               logger.error("name json parse problem", e);
               return "error:badly formed json " + e.getMessage();
           }
            if (valueJsonRequest.rowheadings != null){
                rowheadings = URLDecoder.decode(valueJsonRequest.rowheadings);
            }
            if (valueJsonRequest.columnheadings != null){
                columnheadings = URLDecoder.decode(valueJsonRequest.columnheadings);
            }
            if (valueJsonRequest.context != null) context = URLDecoder.decode(valueJsonRequest.context);
            if (valueJsonRequest.connectionid != null) connectionId = valueJsonRequest.connectionid;
            if (valueJsonRequest.region != null) region = valueJsonRequest.region;
            if (valueJsonRequest.lockmap != null) lockMap = valueJsonRequest.lockmap;
            if (valueJsonRequest.editeddata != null) editedData = URLDecoder.decode(valueJsonRequest.editeddata);
            if (valueJsonRequest.searchbynames != null) searchByNames = valueJsonRequest.searchbynames;
            if (valueJsonRequest.jsonfunction != null) jsonfunction = valueJsonRequest.jsonfunction;
            if (valueJsonRequest.user != null) user = valueJsonRequest.user;
            if (valueJsonRequest.password != null) password = valueJsonRequest.password;
            if (valueJsonRequest.filtercount != null) filterString = valueJsonRequest.filtercount;
            if (valueJsonRequest.spreadsheetname != null) spreadsheetName = valueJsonRequest.spreadsheetname;
            if (valueJsonRequest.database != null) database = valueJsonRequest.database;
        }


        try {

            if (connectionId == null) {
                LoggedInConnection loggedInConnection = loginService.login(database, user, password, 0, spreadsheetName, false);
                if (loggedInConnection == null) {
                    return "error:no connection id";
                }
                connectionId = loggedInConnection.getConnectionId();

            }
            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }
             String result = "error: no action taken";

            /* expand the row and column headings.
            the result is jammed into result but may not be needed - getrowheadings is still important as it sets up the bits in the logged in connection

            ok, one could send the row and column headings at the same time as the data but looking at the export demo it's asking for rows headings then column headings then the context

             */
             int filterCount = 0;
             if (filterString != null){
                 filterCount = Integer.parseInt(filterString);
             }
              if (rowheadings != null && (rowheadings.length() > 0 || filterCount !=0)) {
                result =  valueService.getRowHeadings(loggedInConnection, region, rowheadings, filterCount);
                 logger.info("time for row headings in region " + region + " is " + (System.currentTimeMillis() - startTime) + " on database " + loggedInConnection.getCurrentDBName() + " in language " + loggedInConnection.getLanguage());
              }

            if (columnheadings != null && columnheadings.length() > 0) {
                result =  valueService.getColumnHeadings(loggedInConnection, region, columnheadings);
                logger.info("time for column headings in region " + region + " is " + (System.currentTimeMillis() - startTime));
            }
             if (context != null) {
                //System.out.println("passed context : " + context);
                 loggedInConnection.getProvenance().setContext(context);
                 final StringTokenizer st = new StringTokenizer(context, "\n");
                final List<Name> contextNames = new ArrayList<Name>();
                while (st.hasMoreTokens()) {
                    final Name contextName = nameService.findByName(loggedInConnection, st.nextToken().trim());
                     if (contextName == null) {
                        result =  "error:I can't find a name for the context : " + context;
                        return result;
                    }
                    contextNames.add(contextName);
                    // I guess this is a trick to check the context is correct? Not sure what sense this makes
                    // ok this is to create the RP calc if needed (if there is a calculation attribute)
                    // this will be moved to on saving of a name,
                    result = nameService.calcReversePolish(loggedInConnection, contextName);
                    if (result.startsWith("error:")){
                        return result;
                    }
                }
                if (loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0 && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0) {
                    result =  valueService.getExcelDataForColumnsRowsAndContext(loggedInConnection, contextNames, region, filterCount);
                    logger.info("time for data in region " + region + " is " + (System.currentTimeMillis() - startTime));
                 } else {
                    result = "error:Column and/or row headings are not defined for use with context" + (region != null ? " and region " + region : "");
                }
            }

            if (lockMap != null) {
                result = loggedInConnection.getLockMap(region);
            }

            // this edit bit has a fair bit of what might be considered business logic,wonder if it should be moved to the service layer
            loggedInConnection.getProvenance().setTimeStamp();
            logger.info("edited data " + editedData);
            if (editedData != null && editedData.length() > 0) {
                logger.info("------------------");
                logger.info(loggedInConnection.getLockMap(region));
                logger.info(loggedInConnection.getRowHeadings(region));
                logger.info(loggedInConnection.getColumnHeadings(region));
                logger.info(loggedInConnection.getSentDataMap(region));
                logger.info(loggedInConnection.getContext(region));
                // I'm not sure if these conditions are quite correct maybe check for getDataValueMap and getDataNamesMap instead of columns and rows etc?
                if (loggedInConnection.getLockMap(region) != null &&
                        loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0
                        && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0
                        && loggedInConnection.getSentDataMap(region) != null && loggedInConnection.getContext(region) != null) {
                    // oh-kay, need to compare against the sent data
                    // going to parse the data here for the moment as parsing is controller stuff
                    // I need to track column and Row
                    int rowCounter = 0;
                    final CsvReader originalReader = new CsvReader(new StringReader(loggedInConnection.getSentDataMap(region)), '\t');
                    final CsvReader editedReader = new CsvReader(new StringReader(editedData), '\t');
                    final CsvReader lockMapReader = new CsvReader(new StringReader(loggedInConnection.getLockMap(region)), '\t');
                    // rows, columns, value lists
                    final List<List<List<Value>>> dataValuesMap = loggedInConnection.getDataValueMap(region);
                    final List<List<Set<Name>>> dataNamesMap = loggedInConnection.getDataNamesMap(region);
                    // TODO : deal with mismatched column and row counts
                    int numberOfValuesModified = 0;
                    while (lockMapReader.readRecord()) {
                        int columnCounter = 0;
                        final List<List<Value>> rowValues = dataValuesMap.get(rowCounter);
                        final List<Set<Name>> rowNames = dataNamesMap.get(rowCounter);
                        originalReader.readRecord();
                        editedReader.readRecord();
                        final String[] originalValues = originalReader.getValues();
                        final String[] editedValues = editedReader.getValues();
                        for (String locked : lockMapReader.getValues()) {
                            //System.out.println("on " + columnCounter + ", " + rowCounter + " locked : " + locked);
                            // and here we get to the crux, the values do NOT match
                            // ok assign these two then deal with doubvle formatting stuff that can trip things up
                            String orig = originalValues[columnCounter].trim();
                            String edited = editedValues[columnCounter].trim();
                            if (orig.endsWith(".0")){
                                orig = orig.substring(0, orig.length() - 2);
                            }
                            if (edited.endsWith(".0")){
                                edited = edited.substring(0, edited.length() - 2);
                            }
                            if (!orig.equals(edited)) {
                                if (!locked.equalsIgnoreCase("locked")) { // it wasn't locked, good to go, check inside the different values bit to error if the excel tries something it should not
                                    logger.info(columnCounter + ", " + rowCounter + " not locked and modified");
                                    logger.info(orig + "|" + edited + "|");

                                    final List<Value> valuesForCell = rowValues.get(columnCounter);
                                    final Set<Name> namesForCell = rowNames.get(columnCounter);
                                    // one thing about these store functions to the value service, they expect the provenance on the logged in connection to be appropriate
                                    if (valuesForCell.size() == 1) {
                                        final Value theValue = valuesForCell.get(0);
                                        logger.info("trying to overwrite");
                                        valueService.overWriteExistingValue(loggedInConnection, theValue, edited);
                                        numberOfValuesModified++;
                                    } else if (valuesForCell.isEmpty()) {
                                        logger.info("storing new value here . . .");
                                        valueService.storeValueWithProvenanceAndNames(loggedInConnection, edited, namesForCell);
                                        numberOfValuesModified++;
                                    }
                                } else {
                                    // should this add on for a list???
                                    result = "error:cannot edit locked cell " + columnCounter + ", " + rowCounter + " in region " + region;
                                    return result;
                                }
                            }
                            columnCounter++;
                        }
                        rowCounter++;
                    }
                    result =  numberOfValuesModified + " values modified";
                    //putting in a 'persist' here for security.
                    if (numberOfValuesModified > 0){
                        nameService.persist(loggedInConnection);
                    }
                } else {
                    result =  "error:cannot deal with edited data as there is no sent data/rows/columns/context";
                }
            }

            if (searchByNames != null && searchByNames.length() > 0) {


                logger.info("search by names : " + searchByNames);
                final List<Set<Name>> names = new ArrayList<Set<Name>>();
                String error = nameService.decodeString(loggedInConnection, searchByNames, names);
                if (error.length() > 0){
                    return error;
                }

                if (!names.isEmpty()) {
                    result = valueService.getExcelDataForNamesSearch(names);
                    if (jsonfunction != null && jsonfunction.length() > 0) {
                        result = jsonfunction + "({\"data\": [[\"" + result.replace("\t", "\",\"").replace("\n", "\"],[\"") + "\"]]})";
                    }
                }
            }
            /*
            BufferedReader br = new BufferedReader(new StringReader(result));
            String line;
            logger.error("----- sent result");
            while ((line = br.readLine()) != null) {
                logger.info(line);
            }*/

             return result;
        } catch (Exception e) {
            logger.error("value controller error", e);
            return "error:" + e.getMessage();
        }


    }

}