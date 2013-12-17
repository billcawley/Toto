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

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@Controller
@RequestMapping("/Name")
public class NameController {

    public static final String CHILDREN = "CHILDREN";
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
    private NameService nameService = new NameService();

    @Autowired
    private LoginService loginService = new LoginService();
//    private static final Logger logger = Logger.getLogger(TestController.class);

    @RequestMapping
    @ResponseBody
    public String handleRequest(@RequestParam(value = "connectionid", required = false) final String connectionId, @RequestParam(value = "instructions", required = false) String instructions) throws Exception {
        String result = null;
        try {

            if (connectionId == null) {
                return "error:no connection id";
            }

            final LoggedInConnection loggedInConnection = loginService.getConnection(connectionId);

            if (loggedInConnection == null) {
                return "error:invalid or expired connection id";
            }
            result = handleRequest(loggedInConnection, instructions);
        }catch(Exception e){
            e.printStackTrace();
            return "error:" + e.getMessage();
        }
        return result;
    }

    public String handleRequest(LoggedInConnection loggedInConnection, String instructions)
        throws Exception{
        try{
            String nameString = instructions;
            if (instructions == null) {
                return "error:no instrucitons passed";
            }

            System.out.println("instructions : |" + instructions + "|");
            if (instructions.equals("lognames")) {
                for (Name topName : nameService.findTopNames(loggedInConnection)) {
                    nameService.logNameHierarchy(topName, 0);
                }
            }

            // this certainly will NOT stay here :)
            if (instructions.equals("persist")) {
                nameService.persist(loggedInConnection);
            }
            instructions = instructions.trim();
            // typically a command will start with a name

            String search = getInstruction(instructions, SEARCH);
            if (instructions.indexOf(';') > 0) {
                nameString = instructions.substring(0, instructions.indexOf(';')).trim();
                // now we have it strip off the name, use getInstruction to see what we want to do with the name
                instructions = instructions.substring(instructions.indexOf(';') + 1).trim();

                String children = getInstruction(instructions, CHILDREN);
                String peers = getInstruction(instructions, PEERS);
                String structure = getInstruction(instructions, STRUCTURE);
                String create = getInstruction(instructions, CREATE);
                String levelString = getInstruction(instructions, LEVEL);
                String fromString = getInstruction(instructions, FROM);
                String toString = getInstruction(instructions, TO);
                String afterString = getInstruction(instructions, AFTER);
                String remove = getInstruction(instructions, REMOVE);
                String renameas = getInstruction(instructions, RENAMEAS);
                // since children can be part of structure definition we do structure first
                if (structure != null) {
                    final Name name = nameService.findByName(loggedInConnection, nameString);
                    if (name != null) {
                        return getParentStructureFormattedForOutput(name, true) + getChildStructureFormattedForOutput(name, false);
                    } else {
                        return "error:name not found:`" + nameString + "`";
                    }

                } else if (children != null) {
                    if (children.length() > 0) { // we want to affect the structure, add, remove, create
                        if (remove != null) { // remove a child form the set
                            final Name name = nameService.findByName(loggedInConnection, nameString);
                            if (name != null) {
                                if (nameService.removeChild(loggedInConnection, name, children)) {
                                    return children + " removed";
                                } else {
                                    return "error:name not found:`" + children + "`";
                                }
                            } else {
                                return "error:name not found:`" + nameString + "`";
                            }
                        } else { // some kind of create or set add
                            Name name;
                            String notFoundError = "";
                            if (create != null) {
                                name = nameService.findOrCreateName(loggedInConnection, nameString);
                            } else {
                                name = nameService.findByName(loggedInConnection, nameString);
                                if (name == null) {
                                    notFoundError = "error:name not found:`" + nameString + "`";
                                }
                            }
                            // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                            if (children.startsWith("{")) { // array, typically when creating in the first place, the service call will insert after any existing
                                // EXAMPLE : 2013;children {jan 2013,`feb 2013`,mar 2013, apr 2013, may 2013, jun 2013, jul 2013, aug 2013, sep 2013, oct 2013, nov 2013, dec 2013};create
                                if (children.contains("}")) {
                                    children = children.substring(1, children.indexOf("}"));
                                    final StringTokenizer st = new StringTokenizer(children, ",");
                                    final List<String> namesToAdd = new ArrayList<String>();
                                    while (st.hasMoreTokens()) {
                                        String childName = st.nextToken().trim();
                                        if (childName.startsWith("`")) {
                                            childName = childName.substring(1, childName.length() - 1); // trim escape chars
                                        }
                                        if (create == null && nameService.findByName(loggedInConnection, childName) == null) {
                                            if (notFoundError.isEmpty()) {
                                                notFoundError = childName;
                                            } else {
                                                notFoundError += (", `" + childName + "`");
                                            }
                                        }
                                        namesToAdd.add(childName);
                                    }
                                    if (notFoundError.isEmpty()) {
                                        nameService.createChildren(loggedInConnection, name, namesToAdd);
                                        System.out.println("created children : " + name + " " + namesToAdd);
                                    } else {
                                        return "error:name not found:`" + notFoundError + "`";
                                    }
                                    return "array saved " + namesToAdd.size() + " names";
                                } else {
                                    return "error:Unclosed }";
                                }
                            } else { // insert after a certain position
                                // currently won't support before and after on create arrays, probably could later
                                int after = -1;
                                try {
                                    after = Integer.parseInt(afterString);
                                } catch (NumberFormatException ignored) {
                                }
                                if (create == null && nameService.findByName(loggedInConnection, children) == null) {
                                    return "error:name not found:`" + children + "`";
                                }
                                nameService.createChild(loggedInConnection, name, children, afterString, after);
                                assert name != null; // just to shut intellij up
                                return children + " added to " + name.getName();
                            }

                        }

                    } else {// they want to read data
                        final Name name = nameService.findByName(loggedInConnection, nameString);
                        if (name != null) {
                            int level = 1;
                            if (levelString != null) {
                                if (levelString.equalsIgnoreCase(LOWEST)) {
                                    System.out.println("lowest");
                                    level = -1;
                                } else {
                                    try {
                                        level = Integer.parseInt(levelString);
                                    } catch (NumberFormatException nfe) {
                                        return "error:problem parsing level : " + levelString;
                                    }
                                }

                            }
                            int from = -1;
                            int to = -1;
                            if (fromString != null) {
                                try {
                                    from = Integer.parseInt(fromString);
                                } catch (NumberFormatException nfe) {// may be a number, may not . . .
                                }

                            }
                            if (toString != null) {
                                try {
                                    to = Integer.parseInt(toString);
                                } catch (NumberFormatException nfe) {// may be a number, may not . . .
                                }

                            }
                            List<Name> names; // could be a set or list, sine all we want too do is iterate is no prob
                            if (from != -1 || to != -1) { // numeric, I won't allow mixed for the moment
                                names = nameService.findChildrenFromTo(name, from, to);
                            } else if (fromString != null || toString != null) {
                                names = nameService.findChildrenFromTo(name, fromString, toString);
                            } else { // also won't allow from/to/level mixes either
                                // sorted means level won't work
                                if (getInstruction(instructions, SORTED) != null) {
                                    names = nameService.findChildrenSortedAlphabetically(name);
                                } else {
                                    names = nameService.findChildrenAtLevel(name, level);
                                }
                            }
                            // these next 10 lines or so could be considered the view . . . is it really necessary to abstract that? Worth bearing in mind.
                            return getNamesFormattedForOutput(names);
                        } else {
                            return "error:name not found:`" + nameString + "`";
                        }
                    }
                } else if (peers != null) {
                    System.out.println("peers : |" + peers + "|");
                    if (peers.length() > 0) { // we want to affect the structure, add, remove, create
                        if (remove != null) { // remove a peer form the set
                            final Name name = nameService.findByName(loggedInConnection, nameString);
                            if (name != null) {
                                if (nameService.removePeer(loggedInConnection, name, peers)) {
                                    return peers + " removed";
                                } else {
                                    return "error:name not found:`" + peers + "`";
                                }
                            }
                        } else { // copied from above but for peers, probably should factor at some point
                            Name name;
                            if (create != null) {
                                name = nameService.findOrCreateName(loggedInConnection, nameString);
                            } else {
                                name = nameService.findByName(loggedInConnection, nameString);
                                if (name == null) {
                                    return "error:name not found:`" + nameString + "`";
                                }
                            }
                            // now I understand two options. One is an insert after a certain position the other an array, let's deal with the array
                            if (peers.startsWith("{")) { // array, typically when creating in the first place, the service call will insert after any existing
                                if (peers.contains("}")) {
                                    peers = peers.substring(1, peers.indexOf("}"));
                                    final StringTokenizer st = new StringTokenizer(peers, ",");
                                    final List<String> peersToAdd = new ArrayList<String>();
                                    String notFoundError = "";
                                    while (st.hasMoreTokens()) {
                                        String peerName = st.nextToken().trim();
                                        if (peerName.startsWith("`")) {
                                            peerName = peerName.substring(1, peerName.length() - 1); // trim escape chars
                                        }
                                        if (create == null && nameService.findByName(loggedInConnection, peerName) == null) {
                                            if (notFoundError.isEmpty()) {
                                                notFoundError = peerName;
                                            } else {
                                                notFoundError += (",`" + peerName + "`");
                                            }
                                        }
                                        peersToAdd.add(peerName);
                                    }
                                    if (notFoundError.isEmpty()) {
                                        nameService.createPeers(loggedInConnection, name, peersToAdd);
                                    } else {
                                        return "error:name not found:`" + notFoundError + "`";
                                    }
                                    return "array saved " + peersToAdd.size() + " names";
                                } else {
                                    return "error:Unclosed }";
                                }
                            } else { // insert after a certain position
                                // currently won't support before and after on create arrays, probably could later
                                int after = -1;
                                try {
                                    after = Integer.parseInt(afterString);
                                } catch (NumberFormatException ignored) {
                                }
                                if (create == null && nameService.findByName(loggedInConnection, peers) == null) {
                                    return "error:name not found:`" + nameString + "`";
                                }
                                nameService.createPeer(loggedInConnection, name, peers, afterString, after);
                                return peers + " added to " + name.getName();
                            }

                        }

                    } else {// they want to read data
                        final Name name = nameService.findByName(loggedInConnection, nameString);
                        if (name != null) {
                            //  Fees; peers {Period, Analysis, Merchant};create;
                            // TODO, how to deal with additive?
                            return getNamesFormattedForOutput(nameService.getPeersIncludeParents(name).keySet());
                        } else {
                            return "error:name not found:`" + nameString + "`";
                        }
                    }


                } else if (renameas != null) { // not specific to peers or children
                    nameService.renameName(loggedInConnection, nameString, renameas);
                    return "rename " + nameString + " to " + renameas;
                } else if (search != null) { // search
                    return getNamesFormattedForOutput(nameService.searchNames(loggedInConnection, nameString));
                }
            }
            if (search != null) { // blank search
                return getNamesFormattedForOutput(nameService.findTopNames(loggedInConnection));
            }
            return nameString;
        } catch (Exception e) {
            e.printStackTrace();
            return "error:" + e.getMessage();
        }

    }

    private String getInstruction(final String instructions, final String instructionName) {
        String toReturn = null;
        if (instructions.toLowerCase().contains(instructionName.toLowerCase())) {
            int commandStart = instructions.toLowerCase().indexOf(instructionName.toLowerCase()) + instructionName.length();
            if (instructions.indexOf(";", commandStart) != -1) {
                toReturn = instructions.substring(commandStart, instructions.indexOf(";", commandStart)).trim();
            } else {
                toReturn = instructions.substring(commandStart).trim();
            }
            if (toReturn.startsWith("`")) {
                toReturn = toReturn.substring(1, toReturn.length() - 1); // trim escape chars
            }
        }
        return toReturn;
    }

    private String getNamesFormattedForOutput(final Collection<Name> names) {
        // these next 10 lines or so could be considered the view . . . is it really necessary to abstract that? Worth bearing in mind.
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        sb.append("{");
        for (Name n : names) {
            if (!first) {
                sb.append(", ");
            }
            sb.append("`").append(n.getDisplayName()).append("`");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String getParentStructureFormattedForOutput(final Name name, final boolean showParent) {
        StringBuilder sb = new StringBuilder();
        if (showParent) {
            sb.append("`").append(name.getDisplayName()).append("`");
        }
        Set<Name> parents = name.getParents();
        if (!parents.isEmpty()) {
            sb.append("; parents {");
            int count = 1;
            for (Name parent : parents) {
                sb.append(getParentStructureFormattedForOutput(parent, true));
                if (count < parents.size()) {
                    sb.append(",");
                }
                count++;
            }
            sb.append("}");
        }
        return sb.toString();
    }


    private String getChildStructureFormattedForOutput(final Name name, final boolean showChild) {
        StringBuilder sb = new StringBuilder();
        if (showChild) {
            sb.append("`").append(name.getDisplayName()).append("`");
        }
        final Set<Name> children = name.getChildren();
        if (!children.isEmpty()) {
            sb.append("; children {");
            int count = 1;
            for (Name child : children) {
                sb.append(getChildStructureFormattedForOutput(child, true));
                if (count < children.size()) {
                    sb.append(",");
                }
                count++;
            }
            sb.append("}");
        }
        return sb.toString();
    }

}
