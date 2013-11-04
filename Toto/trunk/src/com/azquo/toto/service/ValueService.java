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
 * Workhorse hammering away at the memory DB.

 */
public final class ValueService {

    public final static String VALUE = "VALUE";

    @Autowired
    private NameService nameService;

    // set the names in delete info and unlink - best I can come up with at the moment
    public void deleteValue(final Value value) throws Exception {
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

    public Value createValue(final LoggedInConnection loggedInConnection, final int provenanceId, final double doubleValue, final String text) throws Exception {
//        return totoMemoryDB.createValue(provenanceId,doubleValue,text);
        return new Value(loggedInConnection.getTotoMemoryDB(),provenanceId,doubleValue,text,null);
    }

    public void linkValueToNames(final Value value, final Set<Name> names) throws Exception {
        value.setNamesWillBePersisted(names);
    }

    // TODO : is passing provenance the

    public String storeValueWithProvenanceAndNames(final LoggedInConnection loggedInConnection, final String valueString, final Provenance provenance, final Set<String> names) throws Exception {
        String toReturn = "";
        final Set<Name> validNames = new HashSet<Name>();
        final Map<String, String> nameCheckResult = nameService.isAValidNameSet(loggedInConnection, names, validNames);
        final String error = nameCheckResult.get(NameService.ERROR);
        final String warning = nameCheckResult.get(NameService.ERROR);
        if (error != null){
            return  error;
        } else if(warning != null){
            toReturn += warning;
        }
        final List<Value> existingValues = findForNames(validNames);
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

    public List<Value> findForNames(final Set<Name> names){
        // ok here goes we want to get a value (or values!) for a given criteria, there may be much scope for optimisation
        //long track = System.nanoTime();
        final List<Value> values = new ArrayList<Value>();
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

    // this is slow relatively speaking


    long part1NanoCallTime1 = 0;
    long part2NanoCallTime1 = 0;
    long part3NanoCallTime1 = 0;
    int numberOfTimesCalled1 = 0;


    public List<Value> findForNamesIncludeChildren(final Set<Name> names){
        long start = System.nanoTime();

        final List<Value> values = new ArrayList<Value>();
        // first get the shortest value list taking into account children
        int smallestNameSetSize = -1;
        Name smallestName = null;
        for (Name name : names){
            int setSizeIncludingChildren = name.getValues().size();
            for (Name child : name.findAllChildren()){
                setSizeIncludingChildren += child.getValues().size();
            }
            if (smallestNameSetSize == -1 || setSizeIncludingChildren < smallestNameSetSize){
                smallestNameSetSize = setSizeIncludingChildren;
                smallestName = name;
            }
        }

        part1NanoCallTime1 += (System.nanoTime() - start);
        long point =System.nanoTime();
        assert smallestName != null; // make intellij happy :P
        final List<Value> valueList = findValuesForNameIncludeAllChildren(smallestName);
        part2NanoCallTime1 += (System.nanoTime() - point);
        point =System.nanoTime();
        for (Value value : valueList){
            boolean theValueIsOk = true;
            for (Name name : names){
                if (!name.equals(smallestName)){ // ignore the one we started with
                    if (!value.getNames().contains(name)){ // top name not in there check children also
                        boolean foundInChildList = false;
                        for (Name child : name.findAllChildren()){
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
        part3NanoCallTime1 += (System.nanoTime() - point);
        numberOfTimesCalled1++;
        //System.out.println("track b   : " + (System.nanoTime() - track) + "  checked " + count + " names");
        //track = System.nanoTime();

        return values;
    }

    public void printFindForNamesIncludeChildrenStats(){
        System.out.println("calls to  FindForNamesIncludeChildrenStats : " + numberOfTimesCalled1);
        System.out.println("part 1 average nano : " + (part1NanoCallTime1/numberOfTimesCalled1));
        System.out.println("part 2 average nano : " + (part2NanoCallTime1/numberOfTimesCalled1));
        System.out.println("part 3 average nano : " + (part3NanoCallTime1/numberOfTimesCalled1));
    }


    long totalNanoCallTime = 0;
    long part1NanoCallTime = 0;
    long part2NanoCallTime = 0;
    int numberOfTimesCalled = 0;

    public double findSumForNamesIncludeChildren(LoggedInConnection loggedInConnection, Set<Name> names){
        //System.out.println("findSumForNamesIncludeChildren");
        long start = System.nanoTime();

        List<Value> values = findForNamesIncludeChildren(names);
        part1NanoCallTime += (System.nanoTime() - start);
        long point = System.nanoTime();
        double sumValue = 0;
        for (Value value : values){
            if (value.getText() != null && value.getText().length() > 0){
                try{
/*                    if (names.contains(nameService.findByName(loggedInConnection, "www.bakerross.co.uk"))){
                        System.out.print("adding " + value.getText());
                        for (Name n : value.getNames()){
                            System.out.print(" " + n.getName());
                        }
                        System.out.println();
                    }                        */
                    sumValue += Double.parseDouble(value.getText());
                } catch (Exception ignored){
                }
            } else {
                sumValue += value.getDoubleValue();
            }
        }
        part2NanoCallTime += (System.nanoTime() - point);
        totalNanoCallTime += (System.nanoTime() - start);
        numberOfTimesCalled++;
        return sumValue;
    }

    public void printSumStats(){
        System.out.println("calls to  findSumForNamesIncludeChildren : " + numberOfTimesCalled);
        System.out.println("part 1 average nano : " + (part1NanoCallTime/numberOfTimesCalled));
        System.out.println("part 2 average nano : " + (part2NanoCallTime/numberOfTimesCalled));
        System.out.println("total average nano : " + (totalNanoCallTime/numberOfTimesCalled));
    }

    public List<Value> findValuesForNameIncludeAllChildren(Name name){
        List<Value> toReturn = new ArrayList<Value>();
        toReturn.addAll(name.getValues());
        for (Name child : name.findAllChildren()){
            toReturn.addAll(child.getValues());
        }
        return toReturn;
    }


}
