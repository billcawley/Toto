package com.azquo.memorydb.core;

import com.azquo.memorydb.NameForBackup;
import com.azquo.memorydb.ProvenanceForBackup;
import com.azquo.memorydb.ValueForBackup;
import com.azquo.memorydb.dao.ValueDAO;

import java.util.ArrayList;
import java.util.List;

public class BackupTransport {

    private final AzquoMemoryDB azquoMemoryDB;

    private static final int BATCH_SIZE = 100_000;

    public BackupTransport(AzquoMemoryDB azquoMemoryDB) {
        this.azquoMemoryDB = azquoMemoryDB;
    }

    private List<NameForBackup> namesForBackup = null;

    public synchronized List<NameForBackup> getBatchOfNamesForBackup(int batchNumber)  {
        if (namesForBackup == null || namesForBackup.size() != azquoMemoryDB.getNameCount()){
            namesForBackup = new ArrayList<>();
            for (Name name : azquoMemoryDB.getAllNames()){
                namesForBackup.add(new NameForBackup(name.getId(), name.getProvenance().getId(), name.getAttributesForFastStore(), name.getChildrenIdsAsBytes(), name.getParents().size(), name.getValueCount()));
            }
        }
        if (batchNumber * BATCH_SIZE >= namesForBackup.size()){
            return new ArrayList<>();
        } else if ((batchNumber + 1) * BATCH_SIZE >= namesForBackup.size()){ // just the last block then
            return new ArrayList<>(namesForBackup.subList(batchNumber * BATCH_SIZE, namesForBackup.size()));
        } else { // a chunk from the list with more to come after
            return new ArrayList<>(namesForBackup.subList(batchNumber * BATCH_SIZE, (batchNumber + 1) * BATCH_SIZE));
        }
    }

    private List<ValueForBackup> valuesForBackup = null;

    public synchronized List<ValueForBackup> getBatchOfValuesForBackup(int batchNumber)  {
        if (valuesForBackup == null || valuesForBackup.size() != azquoMemoryDB.getValueCount()){
            valuesForBackup = new ArrayList<>();
            for (Value value : azquoMemoryDB.getAllValues()){
                valuesForBackup.add(new ValueForBackup(value.getId(), value.getProvenance().getId(), value.getText(), value.getNameIdsAsBytes()));
            }
        }
        if (batchNumber * BATCH_SIZE >= valuesForBackup.size()){
            return new ArrayList<>();
        } else if ((batchNumber + 1) * BATCH_SIZE >= valuesForBackup.size()){ // just the last block then
            return new ArrayList<>(valuesForBackup.subList(batchNumber * BATCH_SIZE, valuesForBackup.size()));
        } else { // a chunk from the list with more to come after
            return new ArrayList<>(valuesForBackup.subList(batchNumber * BATCH_SIZE, (batchNumber + 1) * BATCH_SIZE));
        }
    }

    public synchronized List<ValueForBackup> getBatchOfValuesHistoryForBackup(int batchNumber)  {
        List<ValueHistory> valueHistoryForMinMaxId = ValueDAO.findValueHistoryForMinMaxId(azquoMemoryDB, batchNumber * BATCH_SIZE, (batchNumber + 1) * BATCH_SIZE);
        List<ValueForBackup> toReturn = new ArrayList<>();
        for (ValueHistory valueHistory : valueHistoryForMinMaxId){
            toReturn.add(new ValueForBackup(valueHistory.getId(), valueHistory.getProvenance().getId(), valueHistory.getText(), valueHistory.getNameIdsAsBytes()));
        }
        return toReturn;
    }

    private List<ProvenanceForBackup> provenancesForBackup = null;

    public synchronized List<ProvenanceForBackup> getBatchOfProvenanceForBackup(int batchNumber)  {
        if (provenancesForBackup == null || provenancesForBackup.size() != azquoMemoryDB.getAllProvenances().size()){
            provenancesForBackup = new ArrayList<>();
            for (Provenance provenance : azquoMemoryDB.getAllProvenances()){
                provenancesForBackup.add(new ProvenanceForBackup(provenance.getId(), provenance.getAsJson()));
            }
        }
        if (batchNumber * BATCH_SIZE >= provenancesForBackup.size()){
            return new ArrayList<>();
        } else if ((batchNumber + 1) * BATCH_SIZE >= provenancesForBackup.size()){ // just the last block then
            return new ArrayList<>(provenancesForBackup.subList(batchNumber * BATCH_SIZE, provenancesForBackup.size()));
        } else { // a chunk from the list with more to come after
            return new ArrayList<>(provenancesForBackup.subList(batchNumber * BATCH_SIZE, (batchNumber + 1) * BATCH_SIZE));
        }
    }

    // since there's one backup per db and a new database is created right before restore there should be no reason to be concerned about two restores running simultaneously
    private List<Name> namesFromBackup = new ArrayList<>();
    private List<byte[]> namesChildrenCacheFromBackup = new ArrayList<>();

    // ok, we need to create new names in a style similar to loading a database
    public synchronized void setBatchOfNamesFromBackup(List<NameForBackup> backupBatch) throws Exception {
        for (NameForBackup nameForBackup : backupBatch){
            namesFromBackup.add(new Name(azquoMemoryDB, nameForBackup.getId(), nameForBackup.getProvenanceId(), nameForBackup.getAttributes(), nameForBackup.getNoParents(), nameForBackup.getNoValues(), true));
            azquoMemoryDB.setNextId(nameForBackup.getId());
            namesChildrenCacheFromBackup.add(nameForBackup.getChildren());
        }
    }

    // note this is not multithreaded nor does it do any integrity checks like the linker in the normal db load. Not so bothered about this for the moment

    public synchronized void linkNames() throws Exception {
        for (int i = 0; i < namesFromBackup.size(); i++){
            Name name = namesFromBackup.get(i);
            name.link(namesChildrenCacheFromBackup.get(i), true);
        }
        // will NPE if hit after. Fair enough I think.
        namesFromBackup = null;
        namesChildrenCacheFromBackup = null;
        // bit of a hack, let's persist here
        azquoMemoryDB.persistToDataStore();
    }

    public synchronized void setBatchOfValuesFromBackup(List<ValueForBackup> backupBatch) throws Exception {
        for (ValueForBackup valueForBackup: backupBatch){
            new Value(azquoMemoryDB, valueForBackup.getId(), valueForBackup.getProvenanceId(), valueForBackup.getText(), valueForBackup.getNames(), true);
            azquoMemoryDB.setNextId(valueForBackup.getId());
        }
    }

    public synchronized void setBatchOfValueHistoriesFromBackup(List<ValueForBackup> backupBatch) throws Exception {
        int sqlLimit = 10_000;
        int startPoint = 0;
        while (startPoint < backupBatch.size()){
            if (startPoint + sqlLimit <= backupBatch.size()){
                ValueDAO.insertValuesHistoriesFromBackup(azquoMemoryDB, backupBatch.subList(startPoint, startPoint + sqlLimit));
            } else {
                ValueDAO.insertValuesHistoriesFromBackup(azquoMemoryDB, backupBatch.subList(startPoint, backupBatch.size()));
            }
            startPoint += sqlLimit;
        }
    }

    public synchronized void setBatchOfProvenanceFromBackup(List<ProvenanceForBackup> backupBatch) throws Exception {
        for (ProvenanceForBackup provenanceForBackup: backupBatch){
            new Provenance(azquoMemoryDB, provenanceForBackup.id, provenanceForBackup.json, true);
            azquoMemoryDB.setNextId(provenanceForBackup.id);
        }
    }
}