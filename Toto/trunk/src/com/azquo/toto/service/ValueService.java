package com.azquo.toto.service;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.Value;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 16:49
 * Workhorse hammering away at the memory DB. Will later be used in context of a toto session - instead we'll just
 * have a memory db here for the moment
 */
public class ValueService {

    public static String VALUE = "VALUE";

    @Autowired
    private NameService nameService;

    // set the names in delete info and unlink - best I can come up with at the moment
    public void deleteValue(Value value) throws Exception {
        String names = "";
        for (Name n : value.getNames()){
            names += ", `" + n.getName() + "`";
        }
        if (names.length() > 0){
            names = names.substring(2);
        }
        value.setDeletedInfoWillBePersisted(names);
        value.setNamesWillBePersisted(new HashSet<Name>());
    }

    public Value createValue(LoggedInConnection loggedInConnection, int provenanceId, double doubleValue, String text) throws Exception {
//        return totoMemoryDB.createValue(provenanceId,doubleValue,text);
        return new Value(loggedInConnection.getTotoMemoryDB(),provenanceId,doubleValue,text,null);
    }

    public void linkValueToNames(Value value, Set<Name> names) throws Exception {
        value.setNamesWillBePersisted(names);
    }

    // TODO : is passing provenance the

    public String storeValueWithProvenanceAndNames(LoggedInConnection loggedInConnection, final String valueString, final Provenance provenance, final Set<String> names) throws Exception {
        String toReturn = "";
        Set<Name> validNames = new HashSet<Name>();
        Map<String, String> nameCheckResult = nameService.isAValidNameSet(loggedInConnection, names, validNames);
        String error = nameCheckResult.get(NameService.ERROR);
        String warning = nameCheckResult.get(NameService.ERROR);
        if (error != null){
            return  error;
        } else if(warning != null){
            toReturn += warning;
        }
        List<Value> existingValues = findForNames(validNames);
        boolean alreadyInDatabase = false;
        for (Value existingValue : existingValues){ // really should only be one
            if (existingValue.getText().equals(valueString)){
                toReturn += "  that value already exists, skipping";
                alreadyInDatabase = true;
            } else {
                deleteValue(existingValue);
                // provenance table : person, time, method, name
                toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
            }
        }
        if(!alreadyInDatabase){
            Value value = createValue(loggedInConnection, provenance.getId(), 0,valueString);
            toReturn += "  stored";
            // and link to names
            linkValueToNames(value, validNames);
        }
        return toReturn;
    }

    public List<Value> findForNames(Set<Name> names){
        // ok here goes we want to get a value (or values!) for a given criteria, there may be much scope for optimisation
        //long track = System.nanoTime();
        List<Value> values = new ArrayList<Value>();
        // first get the shortest value list
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names){
            if (smallestNameSetSize == -1 || name.getValues().size() < smallestNameSetSize){
                smallestNameSetSize = name.getValues().size();
                smallestName = name;
            }
        }

        //System.out.println("track a   : " + (System.nanoTime() - track) + "  ---   ");
        //track = System.nanoTime();
        // changing to sets for speed (hopefully!)
        //int count = 0;


        assert smallestName != null; // make intellij happy :P
        for (Value value : smallestName.getValues()){
            boolean theValueIsOk = true;
            for (Name name : names){
                if (!name.equals(smallestName)){ // ignore the one we started with
                    if (!value.getNames().contains(name)){
//                        count++;
                        theValueIsOk = false;
                        break; // important, stop checking that that value contains he names we're interested in as, we didn't find one no point checking for the rest
                    }
                }
            }
            if (theValueIsOk){ // it was in all the names :)
                values.add(value);
            }
        }

        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    // while the above is what would be used to check if data exists for a specific label combination (e.g. when inserting data) this will navigate down through the labels
    // I'm going to try for similar logic but using the lists of children for each label rather than just the label if that makes sense
    // I wonder if it should be a list or set returned?

    public List<Value> findForNamesIncludeChildren(Set<Name> names){
        List<Value> values = new ArrayList<Value>();
        // first get the shortest value list taking into account children
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names){
            int setSizeIncludingChildren = name.getValues().size();
            for (Name child : nameService.findAllChildren(name)){
                setSizeIncludingChildren += child.getValues().size();
            }
            if (smallestNameSetSize == -1 || setSizeIncludingChildren < smallestNameSetSize){
                smallestNameSetSize = setSizeIncludingChildren;
                smallestName = name;
            }
        }

        assert smallestName != null; // make intellij happy :P
        for (Value value : findValuesForNameIncludeAllChildren(smallestName)){
            boolean theValueIsOk = true;
            for (Name name : names){
                if (!name.equals(smallestName)){ // ignore the one we started with
                    if (!value.getNames().contains(name)){ // top name not in there check children also
                        boolean foundInChildList = false;
                        for (Name child : nameService.findAllChildren(name)){
                            if (value.getNames().contains(child)){
                                foundInChildList = true;
                                break;
                            }
                        }
                        if (!foundInChildList){
//                        count++;
                            theValueIsOk = false;
                            break;
                        }
                    }
                }
            }
            if (theValueIsOk){ // it was in all the names :)
                values.add(value);
            }
        }

        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    public List<Value> findValuesForNameIncludeAllChildren(Name name){
        List<Value> toReturn = new ArrayList<Value>();
        for (Name child : nameService.findAllChildren(name)){
            toReturn.addAll(child.getValues());
        }
        return toReturn;
    }


}
