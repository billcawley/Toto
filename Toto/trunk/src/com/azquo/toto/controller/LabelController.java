package com.azquo.toto.controller;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 11:41
 * We're going to try for spring annotation based controllers
 */

import com.azquo.toto.entity.Label;
import com.azquo.toto.service.LabelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Controller
@RequestMapping("/Label")
public class LabelController {

    public static final String ELEMENTS = "elements";
    public static final String CREATE = "create";
    public static final String LEVEL = "level";

    @Autowired
    private LabelService labelService;

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public String handleRequest(@RequestParam(value = "command", required = false) String command) throws Exception {

        if (command == null){
            return "no command passed";
        }
        command = command.trim();
        // typically a command will start with a label name

        if(command.indexOf(';') > 0){
            final String labelName = command.substring(0, command.indexOf(';')).trim();
            // now we have it strip off the label, what do we want to do with it?
            command = command.substring(command.indexOf(';') + 1).trim();
            if (command.toLowerCase().startsWith(ELEMENTS)){ // make it case insensitive
                command = command.substring(ELEMENTS.length()).trim();
                if (command.toLowerCase().contains(CREATE)){
                    final Label label = labelService.findOrCreateLabel(labelName);
                    command = command.substring(0, command.toLowerCase().indexOf(CREATE)).trim();
                    // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                    if(command.startsWith("{")){ // array, typically when creating in the first place, the service call will insert after any existing
                        // EXAMPLE : 2013;elements {jan 2013,`feb 2013`,mar 2013, apr 2013, may 2013, jun 2013, jul 2013, aug 2013, sep 2013, oct 2013, nov 2013, dec 2013};create
                        if (command.contains("}")){
                            command = command.substring(1, command.indexOf("}"));
                            final StringTokenizer st = new StringTokenizer(command, ",");
                            final List<String> namesToAdd = new ArrayList<String>();
                            while (st.hasMoreTokens()){
                                String name = st.nextToken();
                                if (name.startsWith("`")){
                                    name = name.substring(1, name.length() - 1); // trim escape chars
                                }
                                namesToAdd.add(name);
                            }
                            labelService.createMembers(label, namesToAdd);
                            return "Create array saved " + namesToAdd.size() + " names";
                        }
                    } else { // insert after a certain position

                    }
                } else {// they want to read data
                    final Label label = labelService.findByName(labelName);
                    if (label != null){
                        /* examples
                          2013;elements
                          is a basic example
                          2013;elements;level 2
                          with level
                          */
                        int level = 1;
                        if (command.toLowerCase().contains(LEVEL)){
                            try{
                                int levelStart = command.toLowerCase().indexOf(LEVEL) + LEVEL.length();
                                if (command.indexOf(";", levelStart) != -1){
                                    level = Integer.parseInt(command.substring(levelStart, command.indexOf(";", levelStart)).trim());
                                } else {
                                    level = Integer.parseInt(command.substring(levelStart).trim());
                                }
                            } catch (NumberFormatException nfe){
                                return "I can't seem to parse the level";
                            }
                        }
                        boolean sorted = false;
                        int from = 0;
                        int to = 0;
                        String fromString = null;
                        String toString = null;
                        // will collect parameters, for the moment just do vanilla
                        List<Label> labels = labelService.findChildrenAtLevel(label, level);
                        StringBuilder sb = new StringBuilder();
                        boolean first = true;
                        for (Label l : labels){
                            if (!first){
                                sb.append(", ");
                            }
                            sb.append("`").append(l.getName()).append("`");
                            first = false;
                        }
                        return sb.toString();
                    } else {
                        return "label : " + labelName + "not found";
                    }
                }
            }
        }

        return "No action taken";
    }


}
