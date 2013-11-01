package com.azquo.toto.controller;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Value;
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
import java.util.List;
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
    public String handleRequest(@RequestParam(value = "rowheadings", required = false) String rowheadings, @RequestParam(value = "columnheadings", required = false) String columnheadings,
                                @RequestParam(value = "context", required = false) String context, @RequestParam(value = "connectionid", required = false) String connectionId) throws Exception {

        // these 3 statements copied, should factor

        LoggedInConnection loggedInConnection;

        if (connectionId == null){
            return "no connection Id";
        }

        loggedInConnection = loginService.getConnection(connectionId);

        if (loggedInConnection == null){
            return "invalid or expired connection id";
        }


        if (rowheadings != null && rowheadings.length() > 0){
            // ok we'll assume a list or command. First just a basic elements
            if (rowheadings.contains(";")){
                String nameString = rowheadings.substring(0, rowheadings.indexOf(";")).trim();
                Name parent = nameService.findByName(loggedInConnection, nameString);
                if (parent != null){
                    loggedInConnection.setRowHeadings(new ArrayList<Name>(parent.getChildren()));
                    StringBuilder sb = new StringBuilder();
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
                    return "cannot find name : " + nameString;
                }
            } else {
                return "cannot parse row headings string!";
            }
        }

        if (columnheadings != null && columnheadings.length() > 0){
            // ok we'll assume a list or command. First just a basic elements
            if (columnheadings.contains(";")){
                String nameString = columnheadings.substring(0, columnheadings.indexOf(";")).trim();
                Name parent = nameService.findByName(loggedInConnection, nameString);
                if (parent != null){
                    loggedInConnection.setColumnHeadings(new ArrayList<Name>(parent.getChildren()));
                    StringBuilder sb = new StringBuilder();
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
                    return "cannot find name : " + nameString;
                }
            } else {
                return "cannot parse column headings string!";
            }
        }
        if (context != null && context.length() > 0){
            Name contextName = nameService.findByName(loggedInConnection, context);
            if (contextName == null){
                return "I can't find a name for the context : " + context;
            }
            long track = System.currentTimeMillis();
            if (loggedInConnection.getRowHeadings() != null && loggedInConnection.getRowHeadings().size() > 0 && loggedInConnection.getColumnHeadings() != null && loggedInConnection.getColumnHeadings().size() > 0 ){
                StringBuilder sb = new StringBuilder();
                for (Name rowName : loggedInConnection.getRowHeadings()){ // make it like a document
                    int count = 1;
                    for (Name columnName : loggedInConnection.getColumnHeadings()){
                        Set<Name> namesForThisCell = new HashSet<Name>();
                        namesForThisCell.add(contextName);
                        namesForThisCell.add(columnName);
                        namesForThisCell.add(rowName);
                        sb.append(valueService.findSumForNamesIncludeChildren(namesForThisCell));
                        if (count < loggedInConnection.getColumnHeadings().size()){
                            sb.append("\t");
                        } else {
                            sb.append("\r");
                        }
                        count++;
                    }
                }
                System.out.println("time to execute : " + (System.currentTimeMillis() - track));
                return sb.toString();
            } else {
                return "Column and/or row headings are not defined for use with context";
            }

        }
        return "value controller here!";
    }

    // copied. Should factor.

    private String getInstruction(String instructions, String instructionName){
        String toReturn = null;
        if (instructions.toLowerCase().contains(instructionName.toLowerCase())){
            int commandStart = instructions.toLowerCase().indexOf(instructionName.toLowerCase()) + instructionName.length();
            if (instructions.indexOf(";", commandStart) != -1){
                toReturn = instructions.substring(commandStart, instructions.indexOf(";", commandStart)).trim();
            } else {
                toReturn = instructions.substring(commandStart).trim();
            }
            if (toReturn.startsWith("`")){
                toReturn = toReturn.substring(1, toReturn.length() - 1); // trim escape chars
            }
        }
        return toReturn;
    }


}