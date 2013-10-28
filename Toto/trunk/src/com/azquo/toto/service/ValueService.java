package com.azquo.toto.service;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.Value;
import com.azquo.toto.memorydb.TotoMemoryDB;
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

    @Autowired
    private TotoMemoryDB totoMemoryDB;

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
        unlinkAllNamesFromValue(value);
    }

    public void unlinkAllNamesFromValue(Value value) throws Exception {
        for (Name name : value.getNames()){
            name.removeFromValuesWillBePersisted(value);
        }
        value.setNamesWillBePersisted(new HashSet<Name>()); // zap the names against this value.
    }

    public Value createValue(int provenanceId, double doubleValue, String text) throws Exception {
        // TODO : provenance
//        return totoMemoryDB.createValue(provenanceId,doubleValue,text);
        return new Value(totoMemoryDB,provenanceId,doubleValue,text,null);
    }

    public void linkValueToNames(Value value, Set<Name> names) throws Exception {
        unlinkAllNamesFromValue(value);
        value.setNamesWillBePersisted(names);
        for (Name name : names){
            name.addToValuesWillBePersisted(value);
        }
    }

    // TODO : is passing provenance the

    public String storeValueWithProvenanceAndNames(final String valueString, final Provenance provenance, final Set<String> names) throws Exception {
        String toReturn = "";
        Set<Name> validNames = new HashSet<Name>();
        //long track = System.nanoTime();
        Map<String, String> nameCheckResult = nameService.isAValidNameSet(names, validNames);
        String error = nameCheckResult.get(NameService.ERROR);
        String warning = nameCheckResult.get(NameService.ERROR);
        if (error != null){
            return  error;
        } else if(warning != null){
            toReturn += warning;
        }
        //System.out.println("track 1   : " + (System.nanoTime() - track) + "  ---   ");
        //track = System.nanoTime();
        List<Value> existingValues = findForNames(validNames);
        //System.out.println("track 2-1 : " + (System.nanoTime() - track) + "  ---   ");
        //track = System.nanoTime();

        for (Value existingValue : existingValues){
            deleteValue(existingValue);
            // provenance table : person, time, method, name
            toReturn += "  deleting old value entered on put old timestamp here, need provenance table";
        }

        //System.out.println("track 2-2 : " + (System.nanoTime() - track) + "  ---   ");
        //track = System.nanoTime();
        Value value = createValue(provenance.getId(), 0,valueString);
        // now add the value??
        //System.out.println("track 2-3 : " + (System.nanoTime() - track) + "  ---   ");
        //track = System.nanoTime();
        toReturn += "  stored";
        // and link to names
        linkValueToNames(value, validNames);
        //System.out.println("track 3   : " + (System.nanoTime() - track) + "  ---   ");

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


}
