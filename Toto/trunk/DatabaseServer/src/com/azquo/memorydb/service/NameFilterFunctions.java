package com.azquo.memorydb.service;

import com.azquo.DateUtils;
import com.azquo.StringLiterals;
import com.azquo.memorydb.core.Name;
import com.azquo.spreadsheet.StringUtils;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracted from NameQueryParser by edward on 26/10/16.
 * <p>
 * Functions to filter a NameSetList, shouldn't require DB lookups or heavy parsing, parameters should have been gathered.
 */
class NameFilterFunctions {
    // since what it's passed could be immutable need to return
    static NameSetList filter(NameSetList nameSetList, String condition, List<String> strings, List<String> attributeNames) {
        NameSetList toReturn = nameSetList.mutable ? nameSetList : new NameSetList(nameSetList); // make a new mutable NameSetList if the one passed wasn't mutable
        //NOT HANDLING 'OR' AT PRESENT
        int andPos = condition.toLowerCase().indexOf(" and ");
        // get the correct now mutable member collection to filter
        Collection<Name> namesToFilter = toReturn.getAsCollection();
        int lastPos = 0;
        while (lastPos < condition.length()) {
            if (andPos < 0) {
                andPos = condition.length();
            }
            String clause = condition.substring(lastPos, andPos).trim();
            Pattern p = Pattern.compile("[<=>]+");
            Matcher m = p.matcher(clause);

            if (m.find()) {
                String opfound = m.group();
                int pos = m.start();
                String clauseLhs = clause.substring(0, pos).trim();
                String clauseRhs = clause.substring(m.end()).trim();

                // note, given the new parser these clauses will either be literals or begin .
                // there may be code improvements that can be made knowing this
                if (clauseLhs.charAt(0) == StringLiterals.ATTRIBUTEMARKER) {// we need to replace it
                    clauseLhs = attributeNames.get(Integer.parseInt(clauseLhs.substring(1, 3)));
                }
                if (clauseRhs.charAt(0) == StringLiterals.ATTRIBUTEMARKER) {// we need to replace it
                    clauseRhs = attributeNames.get(Integer.parseInt(clauseRhs.substring(1, 3)));
                }

                String valRhs = "";
                boolean fixed = false;
                boolean isADate = false;
                if (clauseRhs.charAt(0) == '"') {
                    valRhs = strings.get(Integer.parseInt(clauseRhs.substring(1, 3)));// anything left in quotes is referenced in the strings list
                    fixed = true;
                    //assume here that date will be of the form yyyy-mm-dd
                    if (DateUtils.isADate(valRhs) != null) {
                        isADate = true;
                    }
                }

                Set<Name> namesToRemove = HashObjSets.newMutableSet();
                for (Name name : namesToFilter) {
                    String valLhs = name.getAttribute(clauseLhs);
                    if (valLhs == null) {
                        valLhs = "";
                    }
                    if (isADate) {
                        valLhs = StringUtils.standardizeDate(valLhs);
                    }
                    if (!fixed) {
                        valRhs = name.getAttribute(clauseRhs);
                        if (valRhs == null) {
                            valRhs = "";
                        }
                    }
                    boolean OK = false;
                    int comp = valLhs.compareTo(valRhs);
                    for (int i = 0; i < opfound.length(); i++) {
                        char op = opfound.charAt(i);
                        switch (op) {
                            case '=':
                                if (comp == 0) OK = true;
                                break;
                            case '<':
                                if (comp < 0) OK = true;
                                break;
                            case '>':
                                if (comp > 0) OK = true;
                        }
                    }
                    if (!OK) {
                        namesToRemove.add(name);
                    }
                }
                // outside the loop, iterator shouldn't get shirty
                namesToFilter.removeAll(namesToRemove);
            }
            lastPos = andPos + 5;
            andPos = condition.toLowerCase().indexOf(" and ", lastPos);
        }
        return toReturn; // its appropriate member collection should have been modified via namesToFilter above, return it
    }

    static NameSetList constrainNameListFromToCount(NameSetList nameSetList, String fromString, String toString, final String countString, final String offsetString, final String compareWithString, List<Name> referencedNames) throws Exception {
        int count = NameQueryParser.parseInt(countString, -1);
        if (nameSetList.list == null) {
            // new criteria - can add a count to unordered
            if (count != -1 && nameSetList.set != null && nameSetList.set.size() > count){ // so constrain the list
                Set<Name> newSet = HashObjSets.newMutableSet(count);
                for (Name n : nameSetList.set){
                    if (newSet.size() == count){ // here to allow count 0
                        break;
                    }
                    newSet.add(n);
                }
                nameSetList = new NameSetList(newSet, null, true);// then make it mutable
            }
            return nameSetList; // don't bother trying to constrain a non list
        }
        final ArrayList<Name> toReturn = new ArrayList<>();
        int to = -10000;
        int from = 1;
        int offset =  NameQueryParser.parseInt(offsetString, 0);
        int compareWith = NameQueryParser.parseInt(compareWithString, 0);
        int space = 1; //spacing between 'compare with' fields
        //first look for integers and encoded names...

        if (toString.length() > 0 && fromString.length()==0) {
            if (!nameSetList.mutable) {
                nameSetList = new NameSetList(null, new ArrayList<>(nameSetList.list), true);// then make it mutable
            }
            //invert the list
            Collections.reverse(nameSetList.list);
            fromString = toString;
            toString = "";
        }

        if (fromString.length() > 0) {
            from = -1;
            try {
                from = Integer.parseInt(fromString);
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (fromString.charAt(0) == StringLiterals.NAMEMARKER) {
                    Name fromName = NameQueryParser.getNameFromListAndMarker(fromString, referencedNames);
                    fromString = fromName.getDefaultDisplayName();
                }
            }
        }
        if (toString.length() > 0) {
            boolean fromEnd = false;
            if (toString.toLowerCase().endsWith("from end")) {
                fromEnd = true;
                toString = toString.substring(0, toString.length() - 9);
            }
            try {
                to = Integer.parseInt(toString);
                if (fromEnd) to = nameSetList.list.size() - to;
            } catch (NumberFormatException nfe) {// may be a number, may not . . .
                if (toString.charAt(0) == StringLiterals.NAMEMARKER) {
                    Name toName = NameQueryParser.getNameFromListAndMarker(toString, referencedNames);
                    toString = toName.getDefaultDisplayName();
                }
            }
        }
        int position = 1;
        boolean inSet = false;
        if (to != -1000 && to < 0) {
            to = nameSetList.list.size() + to;
        }
        int added = 0;
        for (int i =  - offset; i < nameSetList.list.size() - offset; i++) {
            if (position == from || (i >= 0 && i < nameSetList.list.size() && nameSetList.list.get(i).getDefaultDisplayName().equals(fromString))) {
                inSet = true;
            }
            if (inSet && i + offset < nameSetList.list.size()) {
                toReturn.add(nameSetList.list.get(i + offset));
                if (compareWith != 0) {
                    toReturn.add(nameSetList.list.get(i + offset + compareWith));
                    for (int j = 0; j < space; j++) {
                        toReturn.add(null);
                    }
                }
                added++;
            }
            if (position == to || (i >= 0 && i < nameSetList.list.size() && nameSetList.list.get(i).getDefaultDisplayName().equals(toString)) || added == count) {
                inSet = false;
            }
            position++;
        }
        while (added++ < count) {
            toReturn.add(null);
        }
        return new NameSetList(null, toReturn, true);
    }
}