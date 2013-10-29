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
        value.setNamesWillBePersisted(new HashSet<Name>());
    }

    public Value createValue(int provenanceId, double doubleValue, String text) throws Exception {
//        return totoMemoryDB.createValue(provenanceId,doubleValue,text);
        return new Value(totoMemoryDB,provenanceId,doubleValue,text,null);
    }

    public void linkValueToNames(Value value, Set<Name> names) throws Exception {
        value.setNamesWillBePersisted(names);
    }

    // TODO : is passing provenance the

    public String storeValueWithProvenanceAndNames(final String valueString, final Provenance provenance, final Set<String> names) throws Exception {
        String toReturn = "";
        Set<Name> validNames = new HashSet<Name>();
        Map<String, String> nameCheckResult = nameService.isAValidNameSet(names, validNames);
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
            Value value = createValue(provenance.getId(), 0,valueString);
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


}
