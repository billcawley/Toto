package com.azquo.toto.controller;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 11:41
 * We're going to try for spring annotation based controllers. Might look into some rest specific spring stuff later.
 * For the moment it parses instructions for manipulating the name set and calls the name service if the instructions seem correctly formed.
 */

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.service.LoggedInConnection;
import com.azquo.toto.service.LoginService;
import com.azquo.toto.service.NameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

@Controller
@RequestMapping("/Name")
public class NameController {

    public static final String ELEMENTS = "elements";
    public static final String PEERS = "peers";
    public static final String STRUCTURE = "structure";
    public static final String CREATE = "create";
    public static final String REMOVE = "remove";
    public static final String SEARCH = "search";
    public static final String LEVEL = "level";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String SORTED = "sorted";
    public static final String AFTER = "after";
    public static final String RENAMEAS = "rename as";

    public static final String LOWEST = "lowest";

    @Autowired
    private NameService nameService;

    @Autowired
    private LoginService loginService;
//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "connectionid", required = false)final String connectionId, @RequestParam(value = "instructions", required = false) String instructions) throws Exception {

        if (connectionId == null){
            return "no connection Id";
        }

        final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

        if (loggedInConnection == null){
            return "invalid or expired connection id";
        }

        // this certainly will NOT stay here :)
        if (instructions.equals("persist")){
            nameService.persist(loggedInConnection);
        }
        instructions = instructions.trim();
        // typically a command will start with a name

        if(instructions.indexOf(';') > 0){
            final String nameString = instructions.substring(0, instructions.indexOf(';')).trim();
            // now we have it strip off the name, use getInstruction to see what we want to do with the name
            instructions = instructions.substring(instructions.indexOf(';') + 1).trim();

            String elements = getInstruction(instructions, ELEMENTS);
            String peers = getInstruction(instructions, PEERS);
            String structure = getInstruction(instructions, STRUCTURE);
            String create = getInstruction(instructions, CREATE);
            String levelString = getInstruction(instructions, LEVEL);
            String fromString = getInstruction(instructions, FROM);
            String toString = getInstruction(instructions, TO);
            String afterString = getInstruction(instructions, AFTER);
            String remove = getInstruction(instructions, REMOVE);
            String search = getInstruction(instructions, SEARCH);
            String renameas = getInstruction(instructions, RENAMEAS);
            // since elements can be part of structure definition we do structure first
            if (structure != null){
                final Name name = nameService.findByName(loggedInConnection, nameString);
                if (name != null){
                    return getParentStructureFormattedForOutput(name, true) + getChildStructureFormattedForOutput(name, false);
                } else {
                    return "name : " + nameString + "not found";
                }

            } else if (elements != null){
                if (create != null){
                    final Name name = nameService.findOrCreateName(loggedInConnection, nameString);
                    // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                    if(elements.startsWith("{")){ // array, typically when creating in the first place, the service call will insert after any existing
                        // EXAMPLE : 2013;elements {jan 2013,`feb 2013`,mar 2013, apr 2013, may 2013, jun 2013, jul 2013, aug 2013, sep 2013, oct 2013, nov 2013, dec 2013};create
                        if (elements.contains("}")){
                            elements = elements.substring(1, elements.indexOf("}"));
                            final StringTokenizer st = new StringTokenizer(elements, ",");
                            final List<String> namesToAdd = new ArrayList<String>();
                            while (st.hasMoreTokens()){
                                String childName = st.nextToken().trim();
                                if (childName.startsWith("`")){
                                    childName = childName.substring(1, childName.length() - 1); // trim escape chars
                                }
                                namesToAdd.add(childName);
                            }
                            nameService.createMembers(loggedInConnection, name, namesToAdd);
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
                        nameService.createMember(loggedInConnection, name, elements, afterString, after);
                        return elements + " added to " + name.getName();
                    }
                } else if (remove != null){ // delete
                    final Name name = nameService.findByName(loggedInConnection, nameString);
                    if (name != null){
                        nameService.removeMember(loggedInConnection, name, elements);
                        return elements + " removed";
                    }
                } else {// they want to read data
                    final Name name = nameService.findByName(loggedInConnection, nameString);
                    if (name != null){
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
                        List<Name> names; // could be a set or list, sine all we want too do is iterate is no prob
                        if (from != -1 || to != -1){ // numeric, I won't allow mixed for the moment
                            names = nameService.findChildrenFromTo(name, from, to);
                        } else if (fromString != null || toString != null){
                            names = nameService.findChildrenFromTo(name, fromString, toString);
                        } else { // also won't allow from/to/level mixes either
                            // sorted means level won't work
                            if (getInstruction(instructions, SORTED) != null){
                                names = nameService.findChildrenSortedAlphabetically(name);
                            } else {
                                names = nameService.findChildrenAtLevel(name, level);
                            }
                        }
                        // these next 10 lines or so could be considered the view . . . is it really necessary to abstract that? Worth bearing in mind.
                        return getNamesFormattedForOutput(names);
                    } else {
                        return "name : " + nameString + "not found";
                    }
                }
            } else if (peers != null){
                if (create != null){
                    final Name name = nameService.findOrCreateName(loggedInConnection, nameString);
                    // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                    if(peers.startsWith("{")){ // array, typically when creating in the first place, the service call will insert after any existing
                        // EXAMPLE : 2013;elements {jan 2013,`feb 2013`,mar 2013, apr 2013, may 2013, jun 2013, jul 2013, aug 2013, sep 2013, oct 2013, nov 2013, dec 2013};create
                        if (peers.contains("}")){
                            peers = peers.substring(1, peers.indexOf("}"));
                            final StringTokenizer st = new StringTokenizer(peers, ",");
                            final List<String> namesToAdd = new ArrayList<String>();
                            while (st.hasMoreTokens()){
                                String peerName = st.nextToken().trim();
                                if (peerName.startsWith("`")){
                                    peerName = peerName.substring(1, peerName.length() - 1); // trim escape chars
                                }
                                namesToAdd.add(peerName);
                            }
                            nameService.createPeers(loggedInConnection, name, namesToAdd);
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
                        nameService.createPeer(loggedInConnection, name, elements, afterString, after);
                        return elements + " added to " + name.getName();
                    }
                } else if (remove != null){ // delete
                    final Name name = nameService.findByName(loggedInConnection, nameString);
                    if (name != null){
                        nameService.removePeer(loggedInConnection, name, elements);
                        return elements + " deleted";
                    }
                } else {// they want to read data
                    final Name name = nameService.findByName(loggedInConnection, nameString);
                    if (name != null){
                        //  Fees; peers {Period, Analysis, Merchant};create;
                        return getNamesFormattedForOutput(nameService.getPeersIncludeParents(name));
                    } else {
                        return "name : " + nameString + "not found";
                    }
                }

            } else if (renameas != null){ // not specific to peers or elements
                nameService.renameName(loggedInConnection, nameString, renameas);
                return "rename " + nameString + " to " + renameas;
            } else if (search != null){ // search
                return getNamesFormattedForOutput(nameService.searchNames(loggedInConnection, nameString));
            }
        }

        return "No action taken";
    }

    private String getInstruction(final String instructions, final String instructionName){
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

    private String getNamesFormattedForOutput(final Collection<Name> names){
        // these next 10 lines or so could be considered the view . . . is it really necessary to abstract that? Worth bearing in mind.
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append("{");
        for (Name n : names){
            if (!first){
                sb.append(", ");
            }
            sb.append("`").append(n.getName()).append("`");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String getParentStructureFormattedForOutput(final Name name, final boolean showParent){
        StringBuilder sb = new StringBuilder();
        if (showParent){
            sb.append("`").append(name.getName()).append("`");
        }
        Set<Name> parents = name.getParents();
        if (!parents.isEmpty()) {
            sb.append("; in {");
            int count = 1;
            for (Name parent : parents) {
                sb.append(getParentStructureFormattedForOutput(parent, true));
                if (count < parents.size()){
                    sb.append(",");
                }
                count++;
            }
            sb.append("}");
        }
        return sb.toString();
    }


    private String getChildStructureFormattedForOutput(final Name name, final boolean showChild){
        StringBuilder sb = new StringBuilder();
        if (showChild){
            sb.append("`").append(name.getName()).append("`");
        }
        final Set<Name> children = name.getChildren();
        if (!children.isEmpty()) {
            sb.append("; elements {");
            int count = 1;
            for (Name child : children) {
                sb.append(getChildStructureFormattedForOutput(child, true));
                if (count < children.size()){
                    sb.append(",");
                }
                count++;
            }
            sb.append("}");
        }
        return sb.toString();
    }



}
