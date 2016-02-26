package com.azquo.memorydb.service;

import com.azquo.memorydb.AzquoMemoryDBConnection;
import com.azquo.memorydb.Constants;
import com.azquo.memorydb.DatabaseAccessToken;
import com.azquo.memorydb.core.MemoryDBManager;
import com.azquo.memorydb.core.Name;
import com.azquo.memorydb.core.Value;
import com.azquo.memorydb.dao.HBaseDAO;
import com.azquo.memorydb.dao.MySQLDatabaseManager;
import com.azquo.spreadsheet.DSSpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 20/05/15.
 *
 * New admin stuff, we're no longer doing it via the rendered excel files. Basic functions called from the controllers
 *
 * It seems the copy stuff was unused?? Commenting for the moment
 *
 */
public class DSAdminService {

    public static String HBASE_PERSISTENCE_SUFFIX = "hbase";
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
    @Autowired
    HBaseDAO hBaseDAO;


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
        // no peers stuff any more
        for (Name child : name.getChildren()) {
            if (allowed == null || allowed.contains(child)) {
                copyName(toDB, child, name2, languages, allowed, dictionary);
            }
        }
        return name2;
    }

    public void copyDatabase(DatabaseAccessToken source, DatabaseAccessToken target, String nameList, List<String> readLanguages) throws Exception {
        AzquoMemoryDBConnection sourceConnection = dsSpreadsheetService.getConnectionFromAccessToken(source);
        AzquoMemoryDBConnection targetConnection = dsSpreadsheetService.getConnectionFromAccessToken(target);
        if (targetConnection == null) {
            throw new Exception("cannot log in to " + target.getPersistenceName());
        }
        targetConnection.setProvenance("generic admin", "transfer from", source.getPersistenceName(), "");
        //can't use 'nameService.decodeString as this may have multiple values in each list
        List<Set<Name>> namesToTransfer = nameService.decodeString(sourceConnection, nameList, readLanguages);
        //find the data to transfer
        Map<Set<Name>, Set<Value>> showValues = valueService.getSearchValues(namesToTransfer);

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
            valueService.storeValueWithProvenanceAndNames(targetConnection, valueService.addValues(showValues.get(nameValues)), names2);
        }
        targetConnection.persist();
    }

    public void emptyDatabase(String persistenceName) throws Exception {
        if (persistenceName.endsWith(HBASE_PERSISTENCE_SUFFIX)){
            hBaseDAO.clearTables(persistenceName);
        } else {
            mySQLDatabaseManager.emptyDatabase(persistenceName);
        }
        memoryDBManager.removeDBfromMap(persistenceName);
    }


    public void dropDatabase(String persistenceName) throws Exception {
        if (persistenceName.endsWith(HBASE_PERSISTENCE_SUFFIX)){
            hBaseDAO.removeRequiredTables(persistenceName);
        } else {
            mySQLDatabaseManager.dropDatabase(persistenceName);
        }
        memoryDBManager.removeDBfromMap(persistenceName);
    }

    public void createDatabase(final String persistenceName) throws Exception {
        if (memoryDBManager.isDBLoaded(persistenceName)){
            throw new Exception("cannot create new memory database one attached to that mysql database " + persistenceName + " already exists");
        }
        if (persistenceName.endsWith(HBASE_PERSISTENCE_SUFFIX)){
            hBaseDAO.createRequiredTables(persistenceName);
        } else {
            mySQLDatabaseManager.createNewDatabase(persistenceName);
        }
    }
}