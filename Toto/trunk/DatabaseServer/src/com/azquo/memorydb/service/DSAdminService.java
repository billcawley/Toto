package com.azquo.memorydb.service;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.dao.MySQLDatabaseManager;
import org.apache.commons.lang.math.NumberUtils;

import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 * <p>
 * Created by cawley on 20/05/15.
 * <p>
 * Basic DB side admin functions.
 * The copy functions have not been used in a long time.
 * <p>
 */
public class DSAdminService {

    public static void emptyDatabase(String persistenceName) throws Exception {
        if (AzquoMemoryDB.isDBLoaded(persistenceName)) { // then persist via the loaded db, synchronizes thus solving the "delete or empty while persisting" problem
            final AzquoMemoryDB azquoMemoryDB = AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null);
            azquoMemoryDB.synchronizedClear();
            AzquoMemoryDB.removeDBFromMap(persistenceName);
        } else {
            emptyDatabaseInPersistence(persistenceName);
        }
    }

    public static void checkDatabase(String persistenceName) throws Exception {
        final AzquoMemoryDB azquoMemoryDB = AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null);
        System.out.println("Database check for : " + persistenceName);
        int count = 0;
        for (Integer nameId : azquoMemoryDB.getAllNameIds()){
            Name check = azquoMemoryDB.getNameById(nameId);
            if (check == null){
                System.out.println("Null name with id : " + nameId);
            } else {
                for (Name parent : check.getParents()){
                    if (parent == null){
                        System.out.println("Name  " + check + " null parent");
                        break;
                    }
                }
                for (Name child : check.getChildren()){
                    if (child == null){
                        System.out.println("Name  " + check + " null child");
                        break;
                    }
                }
                for (Value value : check.getValues()){
                    if (value == null){
                        System.out.println("Name  " + check + " null value");
                        break;
                    }
                }
            }
            count++;
            if (count%1000 == 0){
                System.out.println("Name count " + count);
            }
        }
        count = 0;
        for (Integer valueId : azquoMemoryDB.getAllValueIds()){
            Value check = azquoMemoryDB.getValueById(valueId);
            if (check == null){
                System.out.println("Null value with id : " + valueId);
            } else {
                for (Name name : check.getNames()){
                    if (name == null){
                        System.out.println("Value " + check + " null name");
                        break;
                    }
                }
            }
            count++;
            if (count%1000 == 0){
                System.out.println("Value count " + count);
            }
        }
    }

    public static void emptyDatabaseInPersistence(String persistenceName) throws Exception {
        MySQLDatabaseManager.emptyDatabase(persistenceName);
    }

    public static void dropDatabase(String persistenceName) throws Exception {
        if (AzquoMemoryDB.isDBLoaded(persistenceName)) { // then persist via the loaded db, synchronizes thus solving the "delete or empty while persisting" problem
            final AzquoMemoryDB azquoMemoryDB = AzquoMemoryDB.getAzquoMemoryDB(persistenceName, null);
            azquoMemoryDB.synchronizedDrop();
            AzquoMemoryDB.removeDBFromMap(persistenceName);
        } else {
            dropDatabaseInPersistence(persistenceName);
        }
    }

    public static void dropDatabaseInPersistence(String persistenceName) throws Exception {
        MySQLDatabaseManager.dropDatabase(persistenceName);
    }

    public static void createDatabase(final String persistenceName) throws Exception {
        if (AzquoMemoryDB.isDBLoaded(persistenceName)) {
            throw new Exception("cannot create new memory database one attached to that mysql database " + persistenceName + " already exists");
        }
        MySQLDatabaseManager.createNewDatabase(persistenceName);
    }

    // simple copy
    public static void copyDatabase(final String from, final String to) throws Exception {
        MySQLDatabaseManager.createNewDatabase(to);
        MySQLDatabaseManager.copyDatabase(from, to);
    }

    // The remaining functions are related to database copying, It's more complex than a simple copy due to the ability to copy at a level
    // EFC note : I'm re-enabling this code but I'm not going to look too closely until it's being used again
    public static void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws Exception {
        AzquoMemoryDBConnection sourceConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(source);
        AzquoMemoryDBConnection targetConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(target);
        if (targetConnection == null) {
            throw new Exception("cannot log in to " + target.getPersistenceName());
        }
        targetConnection.setProvenance("generic admin", "transfer from", source.getPersistenceName(), "");
        //can't use 'nameService.decodeString as this may have multiple values in each list
        List<Set<Name>> namesToTransfer = NameQueryParser.decodeString(sourceConnection, nameList, readLanguages);
        //find the data to transfer
        Map<Set<Name>, Set<Value>> showValues = getSearchValues(namesToTransfer);

        //extract the names from this data
        final Set<Name> namesFound = new HashSet<>();
        for (Set<Name> nameValues : showValues.keySet()) {
            for (Name name : nameValues) {
                namesFound.add(name);
            }
        }
        // todo, check why we have a different language lists, can't we use the same?
        List<String> languages = new ArrayList<>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        //transfer each name and its parents.
        Map<Name, Name> dictionary = new HashMap<>();
        for (Name name : namesFound) {
            Collection<Name> allowed = name.findAllParents();
            allowed.add(name);
            for (Name parent : name.findAllParents()) {
                if (parent.getParents() == null) {//we need to start from the top
                    //copyname copies all allowed children, and avoids endless loops.
                    copyName(targetConnection, parent, null, languages, allowed, dictionary);
                }
            }

        }
        for (Set<Name> nameValues : showValues.keySet()) {
            Set<Name> names2 = new HashSet<>();
            for (Name name : nameValues) {
                names2.add(dictionary.get(name));

            }
            ValueService.storeValueWithProvenanceAndNames(targetConnection, addValues(showValues.get(nameValues)), names2);
        }
        targetConnection.persist();
    }

    private static Name copyName(AzquoMemoryDBConnection toDB, Name name, Name parent, List<String> languages, Collection<Name> allowed, Map<Name, Name> dictionary) throws Exception {
        Name name2 = dictionary.get(name);
        if (name2 != null) {
            return name2;
        }
        //consider ALL names as local.  Global names will be found from dictionary
        name2 = NameService.findOrCreateNameInParent(toDB, name.getDefaultDisplayName(), parent, true, languages);
        for (String attName : name.getAttributes().keySet()) {
            name2.setAttributeWillBePersisted(attName, name.getAttribute(attName));
        }
        // no peers stuff any more
        for (Name child : name.getChildren()) {
            if (allowed == null || allowed.contains(child)) {
                copyName(toDB, child, name2, languages, allowed, dictionary);
            }
        }
        return name2;
    }

    private static Name sumName(Name name, List<Set<Name>> searchNames) {
        for (Set<Name> searchName : searchNames) {
            Name maybeParent = NameService.inParentSet(name, searchName);
            if (maybeParent != null) {
                return maybeParent;
            }
        }
        return name;
    }

    // for searches, the Names are a List of sets rather than a set, and the result need not be ordered
    private static Set<Value> findForSearchNamesIncludeChildren(final List<Set<Name>> names) {
        final Set<Value> values = new HashSet<>();
        // assume that the first set of names is the most restrictive
        Set<Name> smallestNames = names.get(0);
        final Set<Value> valueSet = new HashSet<>();
        for (Name name : smallestNames) {
            valueSet.addAll(name.findValuesIncludingChildren());
        }
        // this seems a fairly crude implementation, list all values for the name sets then check that that values list is in all the name sets
        for (Value value : valueSet) {
            boolean theValueIsOk = true;
            for (Set<Name> nameSet : names) {
                if (!nameSet.equals(smallestNames)) { // ignore the one we started with
                    boolean foundInChildList = false;
                    for (Name valueNames : value.getNames()) {
                        if (NameService.inParentSet(valueNames, nameSet) != null) {
                            foundInChildList = true;
                            break;
                        }
                    }
                    if (!foundInChildList) {
                        theValueIsOk = false;
                        break;
                    }
                }
            }
            if (theValueIsOk) { // it was in all the names :)
                values.add(value);
            }
        }
        return values;
    }

    private static Map<Set<Name>, Set<Value>> getSearchValues(final List<Set<Name>> searchNames) throws Exception {
        if (searchNames == null) return null;
        Set<Value> values = findForSearchNamesIncludeChildren(searchNames);
        //The names on the values have been moved 'up' the tree to the name that was searched
        // e.g. if the search was 'England' and the name was 'London' then 'London' has been replaced with 'England'
        // so there may be duplicates in an unordered search - hence the consolidation below.
        final Map<Set<Name>, Set<Value>> showValues = new HashMap<>();
        for (Value value : values) {
            Set<Name> sumNames = new HashSet<>();
            for (Name name : value.getNames()) {
                sumNames.add(sumName(name, searchNames));
            }
            Set<Value> alreadyThere = showValues.get(sumNames);
            if (alreadyThere != null) {
                alreadyThere.add(value);
            } else {
                Set<Value> newValues = new HashSet<>();
                newValues.add(value);
                showValues.put(sumNames, newValues);
            }
        }
        return showValues;
    }

    private static String addValues(Set<Value> values) {
        String stringVal = null;
        Double doubleVal = 0.0;
        boolean percentage = false;
        for (Value value : values) {
            String thisVal = value.getText();
            Double thisNum = 0.0;
            if (NumberUtils.isNumber(thisVal)) {
                thisNum = Double.parseDouble(thisVal);
            } else {
                if (thisVal.endsWith("%") && NumberUtils.isNumber(thisVal.substring(0, thisVal.length() - 1))) {
                    thisNum = Double.parseDouble(thisVal.substring(0, thisVal.length() - 1)) / 100;
                    percentage = true;
                }
            }
            doubleVal += thisNum;
            if (stringVal == null) {
                stringVal = thisVal;
            }
        }
        if (doubleVal != 0.0) {
            if (percentage) doubleVal *= 100;
            stringVal = doubleVal + "";
            if (stringVal.endsWith(".0")) {
                stringVal = stringVal.substring(0, stringVal.length() - 2);
            }
            if (percentage) stringVal += "%";
        }
        return stringVal;
    }
}