package com.azquo.toto.controller;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.Value;
import com.azquo.toto.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 19/11/13
 * Time: 22:14
 * Excel should make individual calls for provenance. This may also deliver lists then
 */

@Controller
@RequestMapping("/Provenance")

public class ProvenanceController {

    @Autowired
    private LoginService loginService;
    @Autowired
    private NameService nameService;
    @Autowired
    private ValueService valueService;
//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "connectionid", required = false) String connectionId,
                                @RequestParam(value = "name", required = false) final String name,
                                @RequestParam(value = "region", required = false) final String region,
                                @RequestParam(value = "col", required = false) String col,
                                @RequestParam(value = "row", required = false) final String row,
                                @RequestParam(value = "searchnames", required = false) final String searchNames,
                                @RequestParam(value = "jsonfunction", required = false) final String jsonFunction,
                                @RequestParam(value = "user", required = false) final String user,
                                @RequestParam(value = "password", required = false) final String password,
                                @RequestParam(value = "database", required = false) final String database) throws Exception {
        try {

            if (connectionId == null) {
                LoggedInConnection loggedInConnection = loginService.login(database, user, password, 0);
                if (loggedInConnection == null) {
                    return "error:no connection id";
                }
                connectionId = loggedInConnection.getConnectionId();
            }

            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }


            if (name != null && name.length() > 0) {
                Name theName = nameService.findByName(loggedInConnection, name);
                if (theName != null) {
                    //System.out.println("In provenance controller name found : " + name);
                    return formatProvenanceForOutput(theName.getProvenance(), jsonFunction);
                } else {
                    return "error:name not found :" + name;
                }
            }
            if (searchNames != null && searchNames.length() > 0) {
                final Set<Name> names = nameService.decodeString(loggedInConnection, searchNames);
                if (!names.isEmpty()) {
                    if (names.size() == 1) {
                        return formatProvenanceForOutput(names.iterator().next().getProvenance(), jsonFunction);
                    } else {
                        final List<Value> values = valueService.findForNamesIncludeChildren(names, false);
                        if (values.size() == 1) {
                            return formatProvenanceForOutput(values.get(0).getProvenance(), jsonFunction);
                        }
                    }
                }
                return formatProvenanceForOutput(null, jsonFunction);
            }

            if (row != null && row.length() > 0 && col != null && col.length() > 0) {
                int rowInt;
                int colInt;
                try {
                    rowInt = Integer.parseInt(row);
                    colInt = Integer.parseInt(col);
                } catch (Exception e) {
                    return "error: row/col must be integers";
                }
                final List<List<List<Value>>> dataValueMap = loggedInConnection.getDataValueMap(region);

                // going to assume row and col start at 1 hence for index bits on here need to decrement
                if (dataValueMap != null) {
                    if (dataValueMap.get(rowInt) != null) {
                        final List<List<Value>> rowValues = dataValueMap.get(rowInt);
                        if (rowValues.get(colInt) != null) {
                            final List<Value> valuesForCell = rowValues.get(colInt);
                            if (valuesForCell.size() == 1) {
                                return formatProvenanceForOutput(valuesForCell.get(0).getProvenance(), jsonFunction);
                            }
                            // TODO : deal with provanence on a cell where there were mulitple values to make that cell's value

                            // TODO : the cell was made from no values? error or message
                            return "error: no values and hence provenance for : " + col + ", " + row;
                        } else {
                            return "error: col out of range : " + col;
                        }
                    } else {
                        return "error: row out of range : " + row;
                    }
                } else {
                    return "error: data has not been sent for that row/col/region";
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
        return "no action taken";
    }

    public String formatProvenanceForOutput(Provenance provenance, String jsonFunction){

           String output;
           if (provenance == null){
                output = "{provenance:{\"who\":\"no provenance\"}}";
            }else{
                output = "{\"provenance\":{\"who\":\"" + provenance.getUser() + "\",\"when\":\"" + provenance.getTimeStamp() + "\",\"how\":\"" + provenance.getMethod() + "\",\"where\":\"" + provenance.getName() + "\",\"context\":\"" + provenance.getContext() + "\"}}";
            }
           if (jsonFunction != null && jsonFunction.length() > 0){
               return jsonFunction + "(" + output + ")";
           }else{
               return output;
           }
       }


}
