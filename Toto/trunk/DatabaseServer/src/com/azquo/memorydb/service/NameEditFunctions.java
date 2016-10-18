package com.azquo.memorydb.service;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from NameService by edward on 14/10/16.
 *
 * Databases can be modified in the queries, this is not used often, I've moved these functions in here.
 *
 */
class NameEditFunctions {
    static List<Name> handleEdit(AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> languages) throws Exception {
        List<Name> toReturn = new ArrayList<>();
        if (setFormula.startsWith("deduplicate")) {
            return deduplicate(azquoMemoryDBConnection, setFormula.substring(12));
        }
        if (setFormula.startsWith("findduplicates")) {
            return findDuplicateNames(azquoMemoryDBConnection, setFormula);
        }
        if (setFormula.startsWith("zap ")) {
            Collection<Name> names = NameQueryParser.parseQuery(azquoMemoryDBConnection, setFormula.substring(4), languages); // defaulting to list here
            if (names != null) {
                for (Name name : names) name.delete();
                azquoMemoryDBConnection.persist();
                return toReturn;
            }
        }
        throw new Exception(setFormula + " not understood");
    }

    // Edd note - I'm not completely clear on the deduplicate utility functions but they are not core functionality, more to do with importing (should they be in there?)
    // so happy to just check for code warnings and not understand 100%

    private static AtomicInteger dedupeOneCount = new AtomicInteger(0);

    private static void dedupeOne(Name name, Set<Name> possibles, Name rubbishBin) throws Exception {
        dedupeOneCount.incrementAndGet();
        for (Name child2 : possibles) {
            if (child2.getId() != name.getId()) {
                Set<Name> existingChildren = new HashSet<>(child2.getChildren());
                for (Name grandchild : existingChildren) {
                    name.addChildWillBePersisted(grandchild);
                    child2.removeFromChildrenWillBePersisted(grandchild);
                }
                Set<Name> existingParents = new HashSet<>(child2.getParents());
                for (Name parentInLaw : existingParents) {
                    parentInLaw.addChildWillBePersisted(name);
                    parentInLaw.removeFromChildrenWillBePersisted(child2);
                }

                for (Value v : new ArrayList<>(child2.getValues())) { // make a copy before iterating as values will be removed from the original
                    Set<Name> existingForValue = new HashSet<>(v.getNames());
                    existingForValue.add(name);
                    existingForValue.remove(child2);
                    v.setNamesWillBePersisted(existingForValue);
                }

                child2.setAttributeWillBePersisted(Constants.DEFAULT_DISPLAY_NAME, "duplicate-" + child2.getDefaultDisplayName());
                rubbishBin.addChildWillBePersisted(child2);
            }
        }
    }

    private static AtomicInteger deduplicateCount = new AtomicInteger(0);

    private static List<Name> findDuplicateNames(AzquoMemoryDBConnection azquoMemoryDBConnection, String instructions) {
        Set<String> attributeExceptions = new HashSet<>();
        if (instructions.toLowerCase().contains("except ")) {
            String exceptionList = instructions.toUpperCase().substring(instructions.toUpperCase().indexOf("EXCEPT ") + 7).trim();
            String[] eList = exceptionList.split(",");
            for (String exception : eList) {
                attributeExceptions.add(exception.trim());
            }
        }
       /*input syntax 'findduplicates`   probably need to add 'exception' list of cases where duplicates are expected (e.g.   Swimshop product categories)*/
        return azquoMemoryDBConnection.getAzquoMemoryDBIndex().findDuplicateNames(Constants.DEFAULT_DISPLAY_NAME, attributeExceptions);
    }

    private static List<Name> deduplicate(AzquoMemoryDBConnection azquoMemoryDBConnection, String formula) throws Exception {
        /*The syntax of the query is 'deduplicate <Set<Name>> to <Name>   Any duplicate names within the source set will be renamed and put in the destination name*/
        deduplicateCount.incrementAndGet();
        List<Name> toReturn = new ArrayList<>();
        int toPos = formula.indexOf(" to ");
        if (toPos < 0) return toReturn;
        String baseSet = formula.substring(0, toPos);
        String binSet = formula.substring(toPos + 4);
        Name rubbishBin = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, binSet, null, false);
        Collection<Name> names = NameQueryParser.parseQuery(azquoMemoryDBConnection, baseSet);
        if (names.size() == 0) return toReturn;
        if (names.size() > 1) {
            Map<String, Set<Name>> nameMap = new HashMap<>();
            for (Name name : names) {
                String nameString = name.getDefaultDisplayName();
                nameMap.putIfAbsent(nameString, new HashSet<>());
                nameMap.get(nameString).add(name);
            }
            for (String nameString : nameMap.keySet()) {
                if (nameMap.get(nameString).size() > 1) {
                    Set<Name> dups = nameMap.get(nameString);
                    dedupeOne(dups.iterator().next(), dups, rubbishBin);
                }
            }
            toReturn.add(rubbishBin);
            return toReturn;
        }
        Name name = names.iterator().next();
        List<String> languages = new ArrayList<>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        for (Name child : name.findAllChildren()) {
            if (!rubbishBin.getChildren().contains(child)) {
                Set<Name> possibles = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttributeNamesAndParent(languages, child.getDefaultDisplayName(), name);
                if (possibles.size() > 1) {
                    dedupeOne(child, possibles, rubbishBin);
                }
            }
        }
        toReturn.add(rubbishBin);
        azquoMemoryDBConnection.persist();
        return toReturn;
    }
}