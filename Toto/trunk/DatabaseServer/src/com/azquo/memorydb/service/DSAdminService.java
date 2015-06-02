package com.azquo.memorydb.service;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.MemoryDBManager;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.dao.MySQLDatabaseManager;
import com.azquo.spreadsheet.DSSpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Created by cawley on 20/05/15.
 */
public class DSAdminService {

    @Autowired
    DSSpreadsheetService dsSpreadsheetService;
    @Autowired
    NameService nameService;
    @Autowired
    ValueService valueService;
    @Autowired
    MySQLDatabaseManager mySQLDatabaseManager;
    @Autowired
    MemoryDBManager memoryDBManager;


    private Name copyName(AzquoMemoryDBConnection toDB, Name name, Name parent, List<String> languages, Collection<Name> allowed, Map<Name, Name> dictionary) throws Exception {
        Name name2 = dictionary.get(name);
        if (name2 != null) {
            return name2;
        }
        //consider ALL names as local.  Global names will be found from dictionary
        name2 = nameService.findOrCreateNameInParent(toDB, name.getDefaultDisplayName(), parent, true, languages);
        for (String attName : name.getAttributes().keySet()) {
            name2.setAttributeWillBePersisted(attName, name.getAttribute(attName));
        }
        LinkedHashMap<Name, Boolean> peers2 = new LinkedHashMap<Name, Boolean>();
        for (Name peer : name.getPeers().keySet()) {
            Name peer2 = copyName(toDB, peer, null, languages, null, dictionary);//assume that peers can be found globally
            peers2.put(peer2, name.getPeers().get(peer));
        }
        if (peers2.size() > 0) {
            name2.setPeersWillBePersisted(peers2);
        }
        for (Name child : name.getChildren()) {
            if (allowed == null || allowed.contains(child)) {
                copyName(toDB, child, name2, languages, allowed, dictionary);
            }
        }
        return name2;
    }

    // will be purely DB side

    public void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws Exception {
        AzquoMemoryDBConnection sourceConnection = dsSpreadsheetService.getConnectionFromAccessToken(source);
        AzquoMemoryDBConnection targetConnection = dsSpreadsheetService.getConnectionFromAccessToken(target);
        if (targetConnection == null) {
            throw new Exception("cannot log in to " + target.getDatabaseMySQLName());
        }
        targetConnection.setNewProvenance("transfer from", source.getDatabaseMySQLName());
        //can't use 'nameService.decodeString as this may have multiple values in each list
        List<Set<Name>> namesToTransfer = nameService.decodeString(sourceConnection, nameList, readLanguages);
        //find the data to transfer
        Map<Set<Name>, Set<Value>> showValues = valueService.getSearchValues(namesToTransfer);

        //extract the names from this data
        final Set<Name> namesFound = new HashSet<Name>();
        for (Set<Name> nameValues : showValues.keySet()) {
            for (Name name : nameValues) {
                namesFound.add(name);
            }
        }
        // todo, check why we have a different language lists, can't we use the same?
        List<String> languages = new ArrayList<String>();
        languages.add(Constants.DEFAULT_DISPLAY_NAME);
        //transfer each name and its parents.
        Map<Name, Name> dictionary = new HashMap<Name, Name>();
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
            Set<Name> names2 = new HashSet<Name>();
            for (Name name : nameValues) {
                names2.add(dictionary.get(name));

            }
            valueService.storeValueWithProvenanceAndNames(targetConnection, valueService.addValues(showValues.get(nameValues)), names2);
        }
        targetConnection.persist();
    }

    public void emptyDatabase(String mysqlName) throws Exception {
        mySQLDatabaseManager.emptyDatabase(mysqlName);
        memoryDBManager.removeDBfromMap(mysqlName);
    }


    public void dropDatabase(String mysqlName) throws Exception {
        mySQLDatabaseManager.dropDatabase(mysqlName);
        memoryDBManager.removeDBfromMap(mysqlName);
    }

    public void createDatabase(final String mysqlName) throws Exception {
        mySQLDatabaseManager.createNewDatabase(mysqlName);
        memoryDBManager.addNewToDBMap(mysqlName);
    }


}
