package com.azquo.toto.controller;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 11:41
 * We're going to try for spring annotation based controllers. Might look into some rest specific spring stuff later.
 * For the moment it parses instructions for manipulating the label set and calls the label service if the instructions seem correctly formed.
 */

import com.azquo.toto.entity.Label;
import com.azquo.toto.service.LabelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

@Controller
@RequestMapping("/Label")
public class LabelController {

    public static final String ELEMENTS = "elements";
    public static final String PEERS = "peers";
    public static final String CREATE = "create";
    public static final String REMOVE = "remove";
    public static final String LEVEL = "level";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String SORTED = "sorted";
    public static final String AFTER = "after";
    public static final String RENAMEAS = "rename as";

    public static final String LOWEST = "lowest";

    @Autowired
    private LabelService labelService;

//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
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
            String peers = getInstruction(instructions, PEERS);
            String create = getInstruction(instructions, CREATE);
            String levelString = getInstruction(instructions, LEVEL);
            String fromString = getInstruction(instructions, FROM);
            String toString = getInstruction(instructions, TO);
            String afterString = getInstruction(instructions, AFTER);
            String remove = getInstruction(instructions, REMOVE);
            String renameas = getInstruction(instructions, RENAMEAS);
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
                                String name = st.nextToken().trim();
                                if (name.startsWith("`")){
                                    name = name.substring(1, name.length() - 1); // trim escape chars
                                }
                                namesToAdd.add(name);
                            }
                            labelService.createMembers(label, namesToAdd);
                            return "Create array saved " + namesToAdd.size() + " names";
                        } else{
                            return "Unclosed }";
                        }
                    } else { // insert after a certain position
                        // currently won't support before and after on create arrays, probably could later
                        int after = -1;
                        try{
                            after = Integer.parseInt(afterString);
                        } catch (NumberFormatException ignored){
                        }
                        labelService.createMember(label, elements, afterString, after);
                        return elements + " added to " + label.getName();
                    }
                } else if (remove != null){ // delete
                    final Label label = labelService.findByName(labelName);
                    if (label != null){
                        labelService.removeMember(label, elements);
                        return elements + " deleted";
                    }
                } else {// they want to read data
                    final Label label = labelService.findByName(labelName);
                    if (label != null){
                        /* examples
                          2013;elements
                          is a basic example
                          2013;elements;level 2
                          with level
                          2013; elements; from 4; to 6;
                          with from and to
                          */
                        int level = 1;
                        if (levelString != null){
                            if (levelString.equalsIgnoreCase(LOWEST)){
                                System.out.println("lowest");
                                level = -1;
                            } else {
                                try{
                                    level = Integer.parseInt(levelString);
                                } catch (NumberFormatException nfe){
                                    return "problem parsing level : " + levelString;
                                }
                            }

                        }
                        int from = -1;
                        int to = -1;
                        if (fromString != null){
                            try{
                                from = Integer.parseInt(fromString);
                            } catch (NumberFormatException nfe){// may be a number, may not . . .
                            }

                        }
                        if (toString != null){
                            try{
                                to = Integer.parseInt(toString);
                            } catch (NumberFormatException nfe){// may be a number, may not . . .
                            }

                        }
                        List<Label> labels;
                        if (from != -1 || to != -1){ // numeric, I won't allow mixed for the moment
                            labels = labelService.findChildrenFromTo(label, from, to);
                        } else if (fromString != null || toString != null){
                            labels = labelService.findChildrenFromTo(label, fromString, toString);
                        } else { // also won't allow from/to/level mixes either
                            // sorted means level won't work
                            if (getInstruction(instructions, SORTED) != null){
                                labels = labelService.findChildrenSorted(label);
                            } else {
                                labels = labelService.findChildrenAtLevel(label, level);
                            }
                        }
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
            } else if (peers != null){
                if (create != null){
                    final Label label = labelService.findOrCreateLabel(labelName);
                    // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                    if(peers.startsWith("{")){ // array, typically when creating in the first place, the service call will insert after any existing
                        // EXAMPLE : 2013;elements {jan 2013,`feb 2013`,mar 2013, apr 2013, may 2013, jun 2013, jul 2013, aug 2013, sep 2013, oct 2013, nov 2013, dec 2013};create
                        if (peers.contains("}")){
                            peers = peers.substring(1, peers.indexOf("}"));
                            final StringTokenizer st = new StringTokenizer(peers, ",");
                            final List<String> namesToAdd = new ArrayList<String>();
                            while (st.hasMoreTokens()){
                                String name = st.nextToken().trim();
                                if (name.startsWith("`")){
                                    name = name.substring(1, name.length() - 1); // trim escape chars
                                }
                                namesToAdd.add(name);
                            }
                            labelService.createPeers(label, namesToAdd);
                            return "Create array saved " + namesToAdd.size() + " names";
                        } else{
                            return "Unclosed }";
                        }
                    } else { // insert after a certain position
                        // currently won't support before and after on create arrays, probably could later
                        int after = -1;
                        try{
                            after = Integer.parseInt(afterString);
                        } catch (NumberFormatException ignored){
                        }
                        // TODO labelService.createPeer(label, elements, afterString, after);
                        return elements + " added to " + label.getName();
                    }
                } else if (remove != null){ // delete
                    final Label label = labelService.findByName(labelName);
                    if (label != null){
                        labelService.removePeer(label, elements);
                        return elements + " deleted";
                    }
                } else {// they want to read data
                    final Label label = labelService.findByName(labelName);
                    if (label != null){
                        //  Fees; peers {Period, Analysis, Merchant};create;
                        // TODO return getLabelsFormattedForOutput(labelService.findPeers(label));
                    } else {
                        return "label : " + labelName + "not found";
                    }
                }

            } else if (renameas != null){ // not specific to peers or elements
                labelService.renameLabel(labelName, renameas);
                return "rename " + labelName + " to " + renameas;
            }
        }

        return "No action taken";
    }

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

    private String getLabelsFormattedForOutput(List<Label> labels){
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
    }


}
