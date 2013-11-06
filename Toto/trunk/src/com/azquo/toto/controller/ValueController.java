package com.azquo.toto.controller;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
import com.azquo.toto.service.ValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 24/10/13
 * Time: 13:46
 *
 * instructions to manipulate the data itself, will become more complex and deal with a connection to instructions can be in state
 *
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

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "rowheadings", required = false)final String rowheadings, @RequestParam(value = "columnheadings", required = false) final String columnheadings,
                                @RequestParam(value = "context", required = false)final String context, @RequestParam(value = "connectionid", required = false)final String connectionId,
                                @RequestParam(value = "region", required = false)final String region) throws Exception {

        // these 3 statements copied, should factor

        if (connectionId == null){
            return "error:no connection id";
        }

        final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

        if (loggedInConnection == null){
            return "error:invalid or expired connection id";
        }


        if (rowheadings != null && rowheadings.length() > 0){
            // ok we'll assume a list or command. First just a basic children
            if (rowheadings.contains(";")){
                final String nameString = rowheadings.substring(0, rowheadings.indexOf(";")).trim();
                final Name parent = nameService.findByName(loggedInConnection, nameString);
                if (parent != null){
                    loggedInConnection.setRowHeadings(region,new ArrayList<Name>(parent.getChildren()));
                    final StringBuilder sb = new StringBuilder();
                    int count = 1;
                    for (Name child : parent.getChildren()){
                        sb.append(child.getName());
                        if (count < parent.getChildren().size()){
                            sb.append("\n");
                        }
                        count++;
                    }
                    return sb.toString();
                } else {
                    return "error:cannot find name : " + nameString;
                }
            } else {
                return "error:cannot parse row headings string!";
            }
        }

        if (columnheadings != null && columnheadings.length() > 0){
            // ok we'll assume a list or command. First just a basic children
            if (columnheadings.contains(";")){
                final String nameString = columnheadings.substring(0, columnheadings.indexOf(";")).trim();
                final Name parent = nameService.findByName(loggedInConnection, nameString);
                if (parent != null){
                    loggedInConnection.setColumnHeadings(region,new ArrayList<Name>(parent.getChildren()));
                    final StringBuilder sb = new StringBuilder();
                    int count = 1;
                    for (Name child : parent.getChildren()){
                        sb.append(child.getName());
                        if (count < parent.getChildren().size()){
                            sb.append("\t");
                        }
                        count++;
                    }
                    return sb.toString();
                } else {
                    return "error:cannot find name : " + nameString;
                }
            } else {
                return "error:cannot parse column headings string!";
            }
        }
        if (context != null && context.length() > 0){
            final Name contextName = nameService.findByName(loggedInConnection, context);
            if (contextName == null){
                return "error:I can't find a name for the context : " + context;
            }
            long track = System.currentTimeMillis();
            if (loggedInConnection.getRowHeadings(region) != null && loggedInConnection.getRowHeadings(region).size() > 0 && loggedInConnection.getColumnHeadings(region) != null && loggedInConnection.getColumnHeadings(region).size() > 0 ){
                final StringBuilder sb = new StringBuilder();
                for (Name rowName : loggedInConnection.getRowHeadings(region)){ // make it like a document
                    int count = 1;
                    for (Name columnName : loggedInConnection.getColumnHeadings(region)){
                        final Set<Name> namesForThisCell = new HashSet<Name>();
                        namesForThisCell.add(contextName);
                        namesForThisCell.add(columnName);
                        namesForThisCell.add(rowName);
                        sb.append(valueService.findSumForNamesIncludeChildren(loggedInConnection, namesForThisCell));
                        if (count < loggedInConnection.getColumnHeadings(region).size()){
                            sb.append("\t");
                        } else {
                            sb.append("\r");
                        }
                        count++;
                    }
                }
                valueService.printSumStats();
                valueService.printFindForNamesIncludeChildrenStats();
                System.out.println("time to execute : " + (System.currentTimeMillis() - track));
                return sb.toString();
            } else {
                return "error:Column and/or row headings are not defined for use with context" + (region != null ? " and region " + region : "");
            }

        }
        return "error:no action taken";
    }

}