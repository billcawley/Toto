package com.azquo.toto.controller;

import com.azquo.toto.memorydb.Name;
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

import java.util.ArrayList;
import java.util.List;

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
                                @RequestParam(value = "region", required = false) final String region, @RequestParam(value = "lockmap", required = false) final String lockMap,
                                @RequestParam(value = "editeddata", required = false) final String editedData) throws Exception {

        // these 3 statements copied, should factor

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
                    return valueService.getRowHeadings(loggedInConnection,region,forName);
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
                    valueService.getColumnHeadings(loggedInConnection,region,forName);
                } else {
                    return "error:cannot find name : " + nameString;
                }
            } else {
                return "error:cannot parse column headings string!";
            }
        }

        if (context != null && context.length() > 0) {
            final Name contextName = nameService.findByName(loggedInConnection, context);
            if (contextName == null) {
                return "error:I can't find a name for the context : " + context;
            }
            if (loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0 && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0) {
                return valueService.getExcelDataForColumnsRowsAndContext(loggedInConnection,contextName,region);
            } else {
                return "error:Column and/or row headings are not defined for use with context" + (region != null ? " and region " + region : "");
            }
        }

        if (lockMap != null) {
            return loggedInConnection.getLockMap(region);
        }

        if (editedData != null && editedData.length() > 0) {
            // may as well be thorough
            if (loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0
                    && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0
                    && loggedInConnection.getSentDataMap(region) != null && loggedInConnection.getContext(region) != null) {
                // oh-kay, need to compare against the sent data
                // going to parse the data here for the moment as parsing is controller stuff
                // I need to track column and Row
                int rowCounter = 0;
                CsvReader originalReader = new CsvReader(loggedInConnection.getSentDataMap(region), '\t');
                CsvReader editedReader = new CsvReader(loggedInConnection.getSentDataMap(region), '\t');
                // rows, columns, value lists
                List<List<List<Value>>> dataValuesMap = loggedInConnection.getDataValueMap(region);
                // TODO : deal with mismatched column and row counts
                while (originalReader.readRecord()){
                    int columnCounter = 0;
                    List<List<Value>> rowValues = dataValuesMap.get(rowCounter);
                    editedReader.readRecord();
                    String[] editedValues = editedReader.getValues();
                    for (String originalValue : originalReader.getValues()){
                        // and here we get to the crux, the values do NOT match
                        if (!originalValue.trim().equals(editedValues[columnCounter])){
                            // for the moment we'll only play ball if there's an existing value, will add new value possibility tomorrow
                            List<Value> valuesForCell = rowValues.get(columnCounter);
                            if (valuesForCell.size() == 1){
                                Value theValue = valuesForCell.get(0);
                                valueService.overWriteExistingValue(loggedInConnection,region,theValue,editedValues[columnCounter]);
                            }
                        }
                        columnCounter++;
                    }
                    rowCounter++;
                }



            } else {
                return "error:cannot deal with edited data as there is no sent data/rows/columns/context";
            }
        }


        return "error:no action taken";
    }

}