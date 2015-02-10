package com.azquo.controller;

import com.azquo.memorydb.Name;
import com.azquo.memorydb.Value;
import com.azquo.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashSet;
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
                                @RequestParam(value = "spreadsheetName", required = false) final String spreadsheetName,
                                @RequestParam(value = "database", required = false) final String database) throws Exception {
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


            if (name != null && name.length() > 0) {
                Name theName = nameService.findByName(loggedInConnection, name, loggedInConnection.getLanguages());
                if (theName != null) {
                    //System.out.println("In provenance controller name found : " + name);
                    return valueService.formatProvenanceForOutput(theName.getProvenance(), jsonFunction);
                } else {
                    return "error:name not found :" + name;
                }
            }
            if (searchNames != null && searchNames.length() > 0) {
                // not trying to catch an exception we assume no error
                final List<Set<Name>> nameSet = nameService.decodeString(loggedInConnection, searchNames, loggedInConnection.getLanguages());

                //assumes here that each set is a single element
                final Set<Name> names = new HashSet<Name>();
                for (Set<Name> nameFound : nameSet) {
                    if (nameFound.size() > 1) {
                        return "error: " + searchNames + " is not a list of names";
                    }
                    names.addAll(nameFound);
                }
                if (!names.isEmpty()) {

                    if (names.size() == 1) {
                        return valueService.formatProvenanceForOutput(names.iterator().next().getProvenance(), jsonFunction);
                    } else {
                        final List<Value> values = valueService.findForNamesIncludeChildren(names, false, null);
                        return valueService.formatCellProvenanceForOutput(loggedInConnection, names, values, jsonFunction);
                    }
                }
                // should maybe be an error here?
                return valueService.formatProvenanceForOutput(null, jsonFunction);
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
                return valueService.formatDataRegionProvenanceForOutput(loggedInConnection, region, rowInt, colInt, jsonFunction);
               }

        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
        return "no action taken";
    }


}