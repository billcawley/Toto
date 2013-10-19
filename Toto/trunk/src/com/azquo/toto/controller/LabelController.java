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

    public static final String LOWEST = "lowest";

    @Autowired
    private LabelService labelService;

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public String handleRequest(@RequestParam(value = "instructions", required = false) String instructions) throws Exception {

        if (instructions == null){
            return "no command passed";
        }
        instructions = instructions.trim();
        // typically a command will start with a label name

        if(instructions.indexOf(';') > 0){
            final String labelName = instructions.substring(0, instructions.indexOf(';')).trim();
            // now we have it strip off the label, use getInstructioon to see what we want to do with the label
            instructions = instructions.substring(instructions.indexOf(';') + 1).trim();

            String elements = getInstruction(instructions, ELEMENTS);
            String create = getInstruction(instructions, CREATE);
            String level = getInstruction(instructions, LEVEL);
            if (elements != null){
                if (create != null){
                    final Label label = labelService.findOrCreateLabel(labelName);
                    // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                    if(elements.startsWith("{")){ // array, typically when creating in the first place, the service call will insert after any existing
                        // EXAMPLE : 2013;elements {jan 2013,`feb 2013`,mar 2013, apr 2013, may 2013, jun 2013, jul 2013, aug 2013, sep 2013, oct 2013, nov 2013, dec 2013};create
                        if (elements.contains("}")){
                            elements = elements.substring(1, elements.indexOf("}"));
                            final StringTokenizer st = new StringTokenizer(elements, ",");
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
                        int levelInt = 1;
                        if (level != null){
                            if (level.equalsIgnoreCase(LOWEST)){
                                System.out.println("lowest");
                                levelInt = -1;
                            } else {
                                try{
                                    levelInt = Integer.parseInt(level);
                                } catch (NumberFormatException nfe){
                                    return "problem parsing level : " + level;
                                }
                            }

                        }
                        boolean sorted = false;
                        int from = 0;
                        int to = 0;
                        String fromString = null;
                        String toString = null;
                        // will collect parameters, for the moment just do vanilla
                        List<Label> labels = labelService.findChildrenAtLevel(label, levelInt);

                        // these next 10 lines or so could be considered the view . . . is it really necessary to abstract that? Worth bearing in mind.
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

    private String getInstruction(String command, String instructionName){
        if (command.toLowerCase().contains(instructionName.toLowerCase())){
            int commandStart = command.toLowerCase().indexOf(instructionName.toLowerCase()) + instructionName.length();
            if (command.indexOf(";", commandStart) != -1){
                return command.substring(commandStart, command.indexOf(";", commandStart)).trim();
            } else {
                return command.substring(commandStart).trim();
            }
        }
        return null;
    }


}
