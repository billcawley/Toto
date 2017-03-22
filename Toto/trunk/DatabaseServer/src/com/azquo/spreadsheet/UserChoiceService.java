package com.azquo.spreadsheet;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.service.NameQueryParser;
import com.azquo.memorydb.service.NameService;
import com.azquo.spreadsheet.transport.FilterTriple;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Extracted from DSSpreadsheetService by edward on 28/10/16.
 * <p>
 * Has functions relating to the user choices in drop downs and filters.
 */
public class UserChoiceService {

    // Filter set being a multi selection list

    public static void createFilterSet(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        List<String> justUserNameLanguages = new ArrayList<>();
        justUserNameLanguages.add(userName);
        Name filterSets = NameService.findOrCreateNameInParent(connectionFromAccessToken, "Filter sets", null, false); // no languages - typically the set will exist
        Name set = NameService.findOrCreateNameInParent(connectionFromAccessToken, setName, filterSets, true, justUserNameLanguages);//must be a local name in 'Filter sets' and be for this user
        set.setChildrenWillBePersisted(Collections.emptyList()); // easiest way to clear them
        for (Integer childId : childrenIds) {
            Name childName = NameService.findById(connectionFromAccessToken, childId);
            if (childName != null) { // it really should not be!
                set.addChildWillBePersisted(childName); // and that should be it!
            }
        }
    }

    // This class and two functions are to make qualified listings on a drop down, adding parents to qualify where necessary.
    private static class UniqueName {
        final Name bottomName;
        Name topName; // often topName is name and the description will just be left as the basic name
        String description; // when the name becomes qualified the description will become name, parent, parent of parent etc. And top name will be the highest parent, held in case we need to qualify up another level.

        UniqueName(Name topName, String description) {
            bottomName = topName; // topName may be changed but this won't
            this.topName = topName;
            this.description = description;
        }
    }

    private static boolean qualifyUniqueNameWithParent(UniqueName uName) {
        Name name = uName.topName;
        boolean changed = false;
        for (Name parent : name.getParents()) {
            // check that there are no names with the same name for this shared parent. Otherwise the description/name combo would be non unique.
            // If no viable parents were found then this function is out of luck so to speak
            boolean duplicate = false;
            for (Name sibling : parent.getChildren()) { // go through all siblings
                if (sibling != name && sibling.getDefaultDisplayName() != null
                        && sibling.getDefaultDisplayName().equals(name.getDefaultDisplayName())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                changed = true; // we actually did something
                // Edd added check for the parent name containing space and adding quotes
                // the key here is that whatever is in description can be used to look up a name. Used to be reverse order comma separated, now use new member of notation
                uName.description = parent.getDefaultDisplayName() + StringLiterals.MEMBEROF + uName.description;
                uName.topName = parent;
                break; // no point continuing I guess, the unique name has a further unambiguous qualifier
            }
        }
        return changed;
    }

    // for the drop down, essentially given a collection of names for a query need to give a meaningful list qualifying names with parents where they are duplicates (I suppose high streets in different towns)
    // it was assumed that names were sorted, one can't guarantee this though preserving the order is important. EFC going to rewrite, won't require ordering, now this returns the unique names to enable "selected" for filter lists
    private static List<UniqueName> getUniqueNames(Collection<Name> names, boolean forceFirstLevel) {
        List<UniqueName> toCheck;
        if (forceFirstLevel) {
            toCheck = new ArrayList<>();
            for (Name name : names) {
                if (name.hasParents()) { // qualify with the first parent
                    toCheck.add(new UniqueName(name, name.getParents().iterator().next().getDefaultDisplayName() + StringLiterals.MEMBEROF + name.getDefaultDisplayName()));
                } else {
                    toCheck.add(new UniqueName(name, name.getDefaultDisplayName()));
                }
            }
        } else {
            toCheck = names.stream().map(name -> new UniqueName(name, name.getDefaultDisplayName())).collect(Collectors.toList()); // java 8 should be ok here, basically copy the names to unique names to check
        }
        int triesLeft = 10; // just in case there's a chance of infinite loops
        boolean keepChecking = true;
        while (triesLeft >= 0 && keepChecking) {
            keepChecking = false; // set to false, only go around again if something below changes the list of unique names
            Set<String> descriptionDuplicateCheck = HashObjSets.newMutableSet();
            Set<String> duplicatedDescriptions = HashObjSets.newMutableSet();
            for (UniqueName uniqueName : toCheck) {
                if (descriptionDuplicateCheck.contains(uniqueName.description)) { // we have this description already
                    duplicatedDescriptions.add(uniqueName.description); // so add it to the list of descriptions to qualify further
                }
                descriptionDuplicateCheck.add(uniqueName.description);
            }
            if (!duplicatedDescriptions.isEmpty()) { // there are duplicates, try to sort it
                for (UniqueName uniqueName : toCheck) { // run through the list again
                    if (duplicatedDescriptions.contains(uniqueName.description)) { // ok this is one of the ones that needs sorting
                        if (qualifyUniqueNameWithParent(uniqueName)) { // try to sort the name
                            keepChecking = true; // something changed
                        }
                    }
                }
            }
            triesLeft--;
        }
        return toCheck;
    }

    private static List<String> getUniqueNameStrings(Collection<UniqueName> names) {
        return names.stream().map(uniqueName -> uniqueName.description).collect(Collectors.toList()); // return the descriptions, that's what we're after, in many cases this may have been copied into unique names, not modified and copied back but that's fine
    }

    private static List<FilterTriple> getFilterPairsFromUniqueNames(Collection<UniqueName> names, Name filterSet) {
        return names.stream().map(uniqueName -> new FilterTriple(uniqueName.bottomName.getId(), uniqueName.description, filterSet.getChildren().contains(uniqueName.bottomName))).collect(Collectors.toList()); // return the descriptions, that's what we're after, in many cases this may have been copied into unique names, not modified and copied back but that's fine
    }

    public static List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws Exception {
        //HACKING A CHECK FOR NAME.ATTRIBUTE (for default choices) - EFC, where is this used?
        int dotPos = query.indexOf(".");
        if (dotPos > 0) {//todo check that it's not part of a name
            Name possibleName = NameService.findByName(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), query.substring(0, dotPos));
            if (possibleName != null) {
                String result = possibleName.getAttribute(query.substring(dotPos + 1));
                List<String> toReturn = new ArrayList<>();
                toReturn.add(result);
                return toReturn;
            }
        }
        boolean forceFirstLevel = false;
        if (query.toLowerCase().trim().endsWith("showparents")) { // a hack to force simple showing of parents regardless
            query = query.substring(0, query.indexOf("showparents"));
            forceFirstLevel = true;
        }
        return getUniqueNameStrings(getUniqueNames(NameQueryParser.parseQuery(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), query, languages, false), forceFirstLevel));
    }

    public static List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName, List<String> languages) throws Exception {
        //HACKING A CHECK FOR NAME.ATTRIBUTE (for default choices) - EFC, where is this used?
        boolean forceFirstLevel = false;
        if (query.toLowerCase().trim().endsWith("showparents")) { // a hack to force simple showing of parents regardless
            query = query.substring(0, query.indexOf("showparents"));
            forceFirstLevel = true;
        }
        List<String> justUserNameLanguages = new ArrayList<>();
        justUserNameLanguages.add(userName);
        final AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name filterSets = NameService.findOrCreateNameInParent(connectionFromAccessToken, "Filter sets", null, false); // no languages - typically the set will exist
        Name filterSet = NameService.findOrCreateNameInParent(connectionFromAccessToken, filterName, filterSets, true, justUserNameLanguages);//must be a local name in 'Filter sets' and be for this user
        if (filterSet.getChildren() == null || filterSet.getChildren().size() == 0) {
            Collection<Name> possibleNames = NameQueryParser.parseQuery(connectionFromAccessToken, query, languages, true);
            for (Name possibleName : possibleNames) {
                filterSet.addChildWillBePersisted(possibleName);
            }
        }
        int dotPos = query.indexOf(".");
        if (dotPos > 0) {//todo check that it's not part of a name
            Name possibleName = NameService.findByName(connectionFromAccessToken, query.substring(0, dotPos));
            if (possibleName != null) {
                String result = possibleName.getAttribute(query.substring(dotPos + 1));
                List<FilterTriple> toReturn = new ArrayList<>();
                toReturn.add(new FilterTriple(possibleName.getId(), result, filterSet.getChildren().contains(possibleName)));
                return toReturn;
            }
        }
        //final Collection<Name> names = NameService.parseQuery(connectionFromAccessToken, query, languages);
        return getFilterPairsFromUniqueNames(getUniqueNames(NameQueryParser.parseQuery(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), query, languages, false), forceFirstLevel), filterSet);
    }

    // it doesn't return anything, for things like setting up "as" criteria
    public static void resolveQuery(DatabaseAccessToken databaseAccessToken, String query, List<String> languages) throws Exception {
        NameQueryParser.parseQuery(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), query, languages, true);
    }
}