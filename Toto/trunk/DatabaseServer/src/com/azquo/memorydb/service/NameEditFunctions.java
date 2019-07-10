package com.azquo.memorydb.service;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extracted from NameService by edward on 14/10/16.
 * <p>
 * Databases can be modified in the queries, this is not used often, I've moved these functions in here.
 */
class NameEditFunctions {

    static List<Name> handleEdit(AzquoMemoryDBConnection azquoMemoryDBConnection, String setFormula, List<String> languages) throws Exception {
        List<Name> toReturn = new ArrayList<>();
        if (setFormula.toLowerCase().startsWith("namesfromattribute")) {
            return namesFromAttribute(azquoMemoryDBConnection, setFormula.substring("namesFromAttribute".length()).replace("`","").trim());
        }
        if (setFormula.startsWith("deduplicate")) {
            return deduplicate(azquoMemoryDBConnection, setFormula.substring(12));
        }
        if (setFormula.startsWith("findduplicates")) {
            return findDuplicateNames(azquoMemoryDBConnection, setFormula);
        }
        if (setFormula.startsWith("zap ")) {
            Collection<Name> names = NameQueryParser.parseQuery(azquoMemoryDBConnection, setFormula.substring(4), languages, true); // defaulting to list here
            if (names != null) {
                for (Name name : names) name.delete(azquoMemoryDBConnection);
                azquoMemoryDBConnection.persist();
                return toReturn;
            }
        }
        if (setFormula.startsWith("zapdata ")) {
            //expecting a list of queries separated by |  e.g.   `Mens products`|2018
            String nameList = setFormula.substring(8);
            Collection<Value> toZap = null;
            String[] foundList = nameList.split("\\*");
            for (String partList : foundList) {
                Collection<Name> found = NameQueryParser.parseQuery(azquoMemoryDBConnection, partList.trim());
                Collection<Value> values = new HashSet<>();
                for (Name name : found) {
                    values.addAll(name.findValuesIncludingChildren());
                }
                if (toZap == null) {
                    toZap = values;
                } else {
                    toZap.retainAll(values);
                }
            }
            if (toZap != null) {
                for (Value value : toZap) {
                    value.delete();
                }
                azquoMemoryDBConnection.persist();
                return toReturn;
            }
        }
        if (setFormula.startsWith("saveset")){
            int formulaPos = 8;
            String setName = extractName(setFormula.substring(formulaPos));
            if (setName==null) return null;
            Name set = NameService.findByName(azquoMemoryDBConnection,setName);
            formulaPos += skipTwoQuotes(setFormula.substring(formulaPos));
            List<String> children = new ArrayList<>();
            int rowNo = 1;
            StringBuilder displayRows = new StringBuilder();
            boolean needsDisplayRows = false;
            boolean hasBlanks = false;
            while (formulaPos < setFormula.length()) {
                String child = extractName(setFormula.substring(formulaPos));
                if (child == null) {
                    formulaPos = setFormula.length();
                } else {
                    formulaPos = formulaPos + skipTwoQuotes(setFormula.substring(formulaPos));
                    if (child.length() > 0) {
                        children.add(child);
                        displayRows.append(",").append(rowNo);
                        if (hasBlanks) {
                            needsDisplayRows = true;
                        }
                    } else {
                        hasBlanks = true;
                    }
                    rowNo++;
                }
            }
            String dRows = "";
            if (needsDisplayRows){
                dRows = displayRows.toString().substring(1);
            }
            String oldDRows = set.getAttribute("DISPLAYROWS");
            if (oldDRows == null) oldDRows = "";
            boolean changed = false;
            if (!oldDRows.equals(dRows)){
                changed = true;
            }else{
                Collection<Name> childNames = set.getChildren();
                if (childNames.size()!= children.size()){
                    changed = true;
                }else{
                    if (childNames.size() > 0) {
                        List<Name> childList = new ArrayList<>(set.getChildren());
                        for (int childNo = 0; childNo < childNames.size(); childNo++) {
                            if (!childList.get(childNo).getDefaultDisplayName().equals(children.get(childNo))) {
                                changed = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (changed){
                List<Name>newChildren = new ArrayList<>();
                for (String child:children){
                    newChildren.add(NameService.findOrCreateNameInParent(azquoMemoryDBConnection,child,set,true));//make local name
                }
                //Collection<Name> redundantNames = new ArrayList<>(set.getChildren());
                //redundantNames.removeAll(newChildren);
                //newChildren.addAll(redundantNames);//must ensure that there are no 'floating names'
                set.setChildrenWillBePersisted(newChildren,azquoMemoryDBConnection);
                set.setAttributeWillBePersisted("DISPLAYROWS",dRows,azquoMemoryDBConnection);
                toReturn.add(set);
            }
            return toReturn;
        }

        if (setFormula.startsWith("check")) {
            // check db if required . . .
            return new ArrayList<>();
        }

        throw new Exception(setFormula + " not understood");
    }

    private static List<Name> namesFromAttribute(AzquoMemoryDBConnection azquoMemoryDBConnection, String attribute) throws Exception {
        if (attribute == null || attribute.isEmpty()){
            return Collections.emptyList();
        }
        // first order of business is to create/find the attribute name at the top level and delete all children
        Name attributeName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, attribute, null, false);
        // clear children
        for (Name child : attributeName.getChildren()){
            child.delete(azquoMemoryDBConnection);
        }
        Set<String> valuesForAttribute = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getValuesForAttribute(attribute);
        List<Name> toReturn = new ArrayList<>(valuesForAttribute.size());
        for (String attValue : valuesForAttribute){
            Name attributeValueName = NameService.findOrCreateNameInParent(azquoMemoryDBConnection, attValue, attributeName, true); // local child of the name we just cleared/created
            attributeValueName.setChildrenWillBePersisted(azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttribute(attribute, attValue),azquoMemoryDBConnection);
            toReturn.add(attributeValueName);
        }
        azquoMemoryDBConnection.persist();
        return toReturn;
    }

    private static int skipTwoQuotes(String source){
        int firstQuote = source.indexOf(StringLiterals.QUOTE);
        return source.indexOf(StringLiterals.QUOTE, firstQuote+1) + 1;
    }


    private static String extractName(String source){
        int firstQuote = source.indexOf(StringLiterals.QUOTE);
        if (firstQuote++ >=0){
            int secondQuote = source.indexOf(StringLiterals.QUOTE, firstQuote);
            if (secondQuote> 0) return source.substring(firstQuote, secondQuote);
        }
        return null;
    }
    // Edd note - I'm not completely clear on the deduplicate utility functions but they are not core functionality, more to do with importing (should they be in there?)
    // so happy to just check for code warnings and not understand 100%

    private static AtomicInteger dedupeOneCount = new AtomicInteger(0);

    private static void dedupeOne(Name name, Set<Name> possibles, Name rubbishBin, AzquoMemoryDBConnection azquoMemoryDBConnection) throws Exception {
        dedupeOneCount.incrementAndGet();
        for (Name child2 : possibles) {
            if (child2.getId() != name.getId()) {
                Set<Name> existingChildren = new HashSet<>(child2.getChildren());
                for (Name grandchild : existingChildren) {
                    name.addChildWillBePersisted(grandchild, azquoMemoryDBConnection);
                    child2.removeFromChildrenWillBePersisted(grandchild,azquoMemoryDBConnection);
                }
                Set<Name> existingParents = new HashSet<>(child2.getParents());
                for (Name parentInLaw : existingParents) {
                    parentInLaw.addChildWillBePersisted(name, azquoMemoryDBConnection);
                    parentInLaw.removeFromChildrenWillBePersisted(child2, azquoMemoryDBConnection);
                }

                for (Value v : new ArrayList<>(child2.getValues())) { // make a copy before iterating as values will be removed from the original
                    Set<Name> existingForValue = new HashSet<>(v.getNames());
                    existingForValue.add(name);
                    existingForValue.remove(child2);
                    v.setNamesWillBePersisted(existingForValue);
                }

                child2.setAttributeWillBePersisted(StringLiterals.DEFAULT_DISPLAY_NAME, "duplicate-" + child2.getDefaultDisplayName(),azquoMemoryDBConnection);
                rubbishBin.addChildWillBePersisted(child2, azquoMemoryDBConnection);
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
        return azquoMemoryDBConnection.getAzquoMemoryDBIndex().findDuplicateNames(StringLiterals.DEFAULT_DISPLAY_NAME, attributeExceptions);
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
                // todo - pretty sure we should not be using putifabsent
                nameMap.putIfAbsent(nameString, new HashSet<>());
                nameMap.get(nameString).add(name);
            }
            for (Set<Name> dupeNames : nameMap.values()) {
                if (dupeNames.size() > 1) {
                    dedupeOne(dupeNames.iterator().next(), dupeNames, rubbishBin, azquoMemoryDBConnection);
                }
            }
            toReturn.add(rubbishBin);
            return toReturn;
        }
        Name name = names.iterator().next();
        for (Name child : name.findAllChildren()) {
            if (!rubbishBin.getChildren().contains(child)) {
                Set<Name> possibles = azquoMemoryDBConnection.getAzquoMemoryDBIndex().getNamesForAttributeNamesAndParent(StringLiterals.DEFAULT_DISPLAY_NAME_AS_LIST, child.getDefaultDisplayName(), name);
                if (possibles.size() > 1) {
                    dedupeOne(child, possibles, rubbishBin, azquoMemoryDBConnection);
                }
            }
        }
        toReturn.add(rubbishBin);
        azquoMemoryDBConnection.persist();
        return toReturn;
    }
}