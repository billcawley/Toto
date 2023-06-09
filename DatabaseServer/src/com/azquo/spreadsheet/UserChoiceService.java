package com.azquo.spreadsheet;

import com.azquo.StringLiterals;
import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Provenance;
import com.azquo.memorydb.core.Value;
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

    public static void createFilterSetWithQuery(DatabaseAccessToken databaseAccessToken, String setName, String userName, List<Integer> childrenIds) throws Exception {
        createFilterSetWithQuery(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), setName,userName, childrenIds);
    }

     public static Name createFilterSetWithQuery(AzquoMemoryDBConnection connectionFromAccessToken, String setName, String userName, List<Integer> childrenIds) throws Exception {
        List<String> justUserNameLanguages = new ArrayList<>();
        justUserNameLanguages.add(userName);
        connectionFromAccessToken.suggestProvenance(userName, "multi select", "", "");
        Name filterSets = NameService.findOrCreateNameInParent(connectionFromAccessToken, "Filter sets", null, false); // no languages - typically the set will exist
        Name set = NameService.findOrCreateNameInParent(connectionFromAccessToken, setName, filterSets, true, justUserNameLanguages);//must be a local name in 'Filter sets' and be for this user
        set.setAttributeWillBePersisted(StringLiterals.DEFAULT_DISPLAY_NAME, null, connectionFromAccessToken);
        if (childrenIds != null) { // it may be if we're just confirming sets exist, in that case don't modify contents
            set.setChildrenWillBePersisted(Collections.emptyList(), connectionFromAccessToken); // easiest way to clear them
            if (childrenIds.isEmpty()){ // if empty then fill it. Used to reset sets and if the user selected none then they may as well have selected all
                // like code below, populate the set
                Name sourceSet = NameService.findByName(connectionFromAccessToken,setName.substring(3));
                if (sourceSet!=null){
                    for (Name child:sourceSet.getChildren()){
                        set.addChildWillBePersisted(child, connectionFromAccessToken);
                    }
                }
            } else {
                for (Integer childId : childrenIds) {
                    Name childName = NameService.findById(connectionFromAccessToken, childId);
                    if (childName != null) { // it really should not be!
                        set.addChildWillBePersisted(childName, connectionFromAccessToken); // and that should be it!
                    }
                }
            }
        }else{
            //the default for new temporary sets is to be full.
            if (set.getChildren().size()==0 && setName.startsWith("az_")){
                Name sourceSet = NameService.findByName(connectionFromAccessToken,setName.substring(3));
                if (sourceSet!=null){
                    for (Name child:sourceSet.getChildren()){
                        set.addChildWillBePersisted(child, connectionFromAccessToken);
                    }
                }
            }
        }
        return set;
    }

    // support for passing a query, for creating [all] sets on initial report load

    public static void createFilterSetWithQuery(DatabaseAccessToken databaseAccessToken, String setName, String userName, String query) throws Exception {
        final AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        List<String> justUserNameLanguages = new ArrayList<>();
        justUserNameLanguages.add(userName);
        Name filterSets = NameService.findOrCreateNameInParent(connectionFromAccessToken, "Filter sets", null, false); // no languages - typically the set will exist
        Name set = NameService.findOrCreateNameInParent(connectionFromAccessToken, setName, filterSets, true, justUserNameLanguages);//must be a local name in 'Filter sets' and be for this user
        set.setAttributeWillBePersisted(StringLiterals.DEFAULT_DISPLAY_NAME, null, connectionFromAccessToken);
        // EFC modified to use NameService.getDefaultLanguagesList(userName), a multi may be mased on another multi from the same user, make sure you can find it . . .
        set.setChildrenWillBePersisted(NameQueryParser.parseQuery(connectionFromAccessToken, query, NameService.getDefaultLanguagesList(userName), false), connectionFromAccessToken);
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

    public static List<String> getDropDownListForQuery(DatabaseAccessToken databaseAccessToken, String query, String user, String searchTerm, boolean justUser, int provenanceId) throws Exception {
        //HACKING A CHECK FOR NAME.ATTRIBUTE (for default choices) - EFC, where is this used?
        AzquoMemoryDBConnection azquoMemoryDBConnection = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        query = NameQueryParser.replaceAttributes(azquoMemoryDBConnection, query);
        Name possibleName = NameService.findByName(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), query);
        if (possibleName != null) {
            List<String> toReturn = new ArrayList<>();
            toReturn.add(possibleName.getDefaultDisplayName());
            return toReturn;
        }
        boolean forceFirstLevel = false;
        if (query.toLowerCase().trim().endsWith("showparents")) { // a hack to force simple showing of parents regardless
            query = query.substring(0, query.indexOf("showparents"));
            forceFirstLevel = true;
        }
        // another hack to say if we should use the provenance id . . .
        // of course we need to actually have the provenance id for this to work
        boolean constrainToUpdated = false;
        if (query.toLowerCase().trim().endsWith(" updated")) { // a hack to force simple showing of parents regardless
            query = query.substring(0, query.indexOf(" updated"));
            constrainToUpdated = true;
        }

        List<String> languages = new ArrayList<>();
        languages.add(user);
        if (!justUser) {
            languages.add(StringLiterals.DEFAULT_DISPLAY_NAME);
        }
        Collection<Name> names = NameQueryParser.parseQuery(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), query, languages, false);
        if (searchTerm != null && !searchTerm.isEmpty()){ // new thing for autocomplete - maybe move later todo
            List<Name> newNames = new ArrayList<>();
            for (Name name : names){
                if (name.getDefaultDisplayName().toLowerCase().startsWith(searchTerm.toLowerCase())){
                    newNames.add(name);
                }
                if (newNames.size() > 100){ // limiting 100 server side, might chop more client side
                    break;
                }
            }
            names = newNames;
        } else if (names.size() > 4000) { // EFC, making this 4k for bonza but it's not the best idea. Needs to be back to 500 sooner rather than later
            List<Name> newNames = new ArrayList<>();
            Iterator it = names.iterator();
            for (int i = 0; i < 4000; i++) newNames.add((Name) it.next());
            names = newNames;
        }
        // I'm not going to check if provenanceId is positive - if they use "updated" in the wrong context nothing should be returned
//        int count = 0;
        if (constrainToUpdated) {
//            Provenance toLookFor = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken).getAzquoMemoryDB().getProvenanceById(provenanceId);
/*            System.out.println("UPDATED SEARCH : Provenance we're looking for : " + toLookFor);
            System.out.println("First 100 : " + toLookFor);*/
            Iterator<Name> iterator = names.iterator();
            while (iterator.hasNext()){
                Name name = iterator.next();
                boolean remove = true;
                // ok the provenance id >= looks a bit hacky - if something updated it after (e.g. a validation sheet) then it's fair game also
                if (name.getProvenance().getId() >= provenanceId) {
                    remove = false;
                } else {
                    for (Name n : name.findAllChildren()) {
                        if (n.getProvenance().getId() >= provenanceId) {
                            remove = false;
                            break;
                        }
                    }
                }
                if (remove){
                    for (Value v : name.getValues()) {
                        if (v.getProvenance().getId() >= provenanceId) {
                            remove = false;
                            break;
                        }
                    }
                }
/*                if (count < 100){
                    System.out.println("name : " + name.getDefaultDisplayName() + " provenance : " + name.getProvenance());
                    System.out.println("matched (may be true due to values) : " + !remove);
                }*/
                if (remove){
                    iterator.remove();
                }
//                count++;
            }
        }
        return getUniqueNameStrings(getUniqueNames(names, forceFirstLevel));
    }

    public static List<FilterTriple> getFilterListForQuery(DatabaseAccessToken databaseAccessToken, String query, String filterName, String userName) throws Exception {
        //HACKING A CHECK FOR NAME.ATTRIBUTE (for default choices) - EFC, where is this used?
        boolean forceFirstLevel = false;
        if (query.toLowerCase().trim().endsWith("showparents")) { // a hack to force simple showing of parents regardless
            query = query.substring(0, query.indexOf("showparents"));
            forceFirstLevel = true;
        }
        List<String> languages = new ArrayList<>();
        languages.add(userName); // start with just the username in the languages
        final AzquoMemoryDBConnection connectionFromAccessToken = AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken);
        Name filterSets = NameService.findOrCreateNameInParent(connectionFromAccessToken, "Filter sets", null, false); // no languages - typically the set will exist
        //must be a local name in 'Filter sets' and be for this user,  start with just the username in the languages
        Name filterSet = NameService.findOrCreateNameInParent(connectionFromAccessToken, filterName, filterSets, true, languages);
        filterSet.setAttributeWillBePersisted(StringLiterals.DEFAULT_DISPLAY_NAME, null, connectionFromAccessToken);
        // now add in the default display name for a more typical languages list
        languages.add(StringLiterals.DEFAULT_DISPLAY_NAME);
        if (filterSet.getChildren() == null || filterSet.getChildren().size() == 0) {
            Collection<Name> possibleNames = NameQueryParser.parseQuery(connectionFromAccessToken, query, languages, true);
            for (Name possibleName : possibleNames) {
                filterSet.addChildWillBePersisted(possibleName, connectionFromAccessToken);
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
    public static String resolveQuery(DatabaseAccessToken databaseAccessToken, String query, String user, List<List<String>> contextSource) throws Exception {
        List<String> languages = NameService.getDefaultLanguagesList(user);
        Collection<Name> names = NameQueryParser.parseQuery(AzquoMemoryDBConnection.getConnectionFromAccessToken(databaseAccessToken), query, languages, true, contextSource);

        if (names != null && names.size() != 0){
            if (names.size()==1) {
                Name name = names.iterator().next();
                String nameString = name.getDefaultDisplayName();
                if (nameString==null) {
                    Map<String, String> atts = name.getAttributes();
                    nameString = "temporary name " + atts.get(atts.keySet().iterator().next());
                }
                return nameString + "- elements:" + name.getChildren().size();
            }
            return names.size() + " elements";
         };
        return "error";
    }


}