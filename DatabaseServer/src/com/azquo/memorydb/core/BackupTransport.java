package com.azquo.memorydb.core;

import com.azquo.memorydb.NameForBackup;
import com.azquo.memorydb.ProvenanceForBackup;
import com.azquo.memorydb.ValueForBackup;
import com.azquo.memorydb.dao.ValueDAO;
import com.csvreader.CsvWriter;
import net.openhft.koloboke.collect.set.hash.HashObjSets;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class BackupTransport {

    private final AzquoMemoryDB azquoMemoryDB;

    private static final int BATCH_SIZE = 100_000;

    BackupTransport(AzquoMemoryDB azquoMemoryDB) {
        this.azquoMemoryDB = azquoMemoryDB;
    }

    // this was on the report server side but since we want to make selective backups then it's best moved here and we then sftp the file to the report server if required

    public String createDBBackupFile(String dbname, Name subsetName) throws Exception {
        File tempFile = AzquoMemoryDB.getSftpTempDir().isEmpty() ? File.createTempFile(dbname.length() < 3 ? dbname + "---" : dbname, ".db") :
                new File(AzquoMemoryDB.getSftpTempDir() + "/" + System.currentTimeMillis() + dbname + ".db");
        System.out.println("attempting to create backup file " + tempFile.getAbsolutePath());
        Set<Provenance> subsetProvenance = null;
        Set<Name> subsetNames = null;
        Set<Value> subsetValues = null;
        if (subsetName != null){ // e.g. Jul-20. The idea is that all names above and below are copied along with related values and the names attached to those values
            subsetNames = HashObjSets.newMutableSet();
            subsetProvenance = HashObjSets.newMutableSet();
            subsetValues = HashObjSets.newMutableSet();
            subsetNames.add(subsetName);
            Collection<Name> allChildren = subsetName.findAllChildren();
            subsetNames.addAll(allChildren);
            subsetNames.addAll(subsetName.findAllParents());
            // now all the parents of the children
            for (Name child : allChildren){
                subsetNames.addAll(child.findAllParents());
            }
            // now all the values associated with the children
            for (Name child : allChildren){
                subsetValues.addAll(child.getValues());
            }
            // now the names and structures associated with those values
            subsetValues.remove(null); // may be an issue in a corrupt db
            for (Value value : subsetValues){
                subsetNames.addAll(value.getNames());
                for (Name n : value.getNames()){
                    subsetNames.addAll(n.getParents());
                }
            }
            for (Name n : subsetNames){
                subsetProvenance.add(n.getProvenance());
            }
            for (Value v : subsetValues){
                subsetProvenance.add(v.getProvenance());
            }
        }

        FileOutputStream fos = new FileOutputStream(tempFile);
        CsvWriter csvW = new CsvWriter(fos, '\t', StandardCharsets.UTF_8);
        csvW.setUseTextQualifier(false);
        csvW.write(dbname);
        csvW.endRecord();
        Collection<Provenance> provenancesForBackup = subsetProvenance != null ? subsetProvenance : new ArrayList<>(azquoMemoryDB.getAllProvenances());
        for (Provenance provenanceForBackup : provenancesForBackup) {
            csvW.write(provenanceForBackup.getId() + "");
            csvW.write(provenanceForBackup.getAsJson());
            csvW.endRecord();
        }
        csvW.write("NAMES");
        csvW.endRecord();
        Collection<Name> namesForBackup = subsetNames != null ? subsetNames : new ArrayList<>(azquoMemoryDB.getAllNames()); //
        for (Name nameForBackup : namesForBackup) {
            csvW.write(nameForBackup.getId() + "");
            csvW.write(nameForBackup.getProvenance().getId() + "");
            // attributes could have unhelpful chars
            csvW.write(nameForBackup.getAttributesForFastStore().replace("\n", "\\\\n").replace("\r", "").replace("\t", "\\\\t"));
            //base 64 encode the bytes
            byte[] encodedBytes;
            if (subsetNames != null){ // we have to chop children we can't find
                Set<Name> reducedChildren = HashObjSets.newMutableSet(nameForBackup.getChildren());
                reducedChildren.retainAll(subsetNames);
                ByteBuffer buffer = ByteBuffer.allocate(reducedChildren.size() * 4);
                for (Name name : reducedChildren) {
                    buffer.putInt(name.getId());
                }
                encodedBytes = Base64.getEncoder().encode(buffer.array());
            } else {
                encodedBytes = Base64.getEncoder().encode(nameForBackup.getChildrenIdsAsBytes());
            }

            String string64 = new String(encodedBytes);
            csvW.write(string64);
            if (subsetNames != null){ // we have to chop children we can't find
                Set<Name> reducedParents = HashObjSets.newMutableSet(nameForBackup.getParents());
                reducedParents.retainAll(subsetNames);
                csvW.write(reducedParents.size() + "");
            } else {
                csvW.write(nameForBackup.getParents().size() + "");
            }
            csvW.write(nameForBackup.getValueCount() + "");
            csvW.endRecord();
        }
        csvW.write("VALUES");
        csvW.endRecord();
        Collection<Value> valuesForBackup = subsetValues != null ? subsetValues :new ArrayList<>(azquoMemoryDB.getAllValues());

        for (Value valueForBackup : valuesForBackup) {
            csvW.write(valueForBackup.getId() + "");
            csvW.write(valueForBackup.getProvenance().getId() + "");
            // attributes could have unhelpful chars
            csvW.write(valueForBackup.getText().replace("\n", "\\\\n").replace("\r", "").replace("\t", "\\\\t"));
            //base 64 encode the bytes
            byte[] encodedBytes;
            if (subsetNames != null){ // we have to chop children we can't find
                Set<Name> reducedNames = HashObjSets.newMutableSet(valueForBackup.getNames());
                reducedNames.retainAll(subsetNames);
                ByteBuffer buffer = ByteBuffer.allocate(reducedNames.size() * 4);
                for (Name name : reducedNames) {
                    buffer.putInt(name.getId());
                }
                encodedBytes = Base64.getEncoder().encode(buffer.array());
            } else {
                encodedBytes = Base64.getEncoder().encode(valueForBackup.getNameIdsAsBytes());
            }
            String string64 = new String(encodedBytes);
            csvW.write(string64);
            csvW.endRecord();
        }

        csvW.write("VALUEHISTORY");
        csvW.endRecord();
        if (subsetValues == null){ // don't bother with value history if
            int batchNumber = 0;
            List<ValueHistory> valueHistoryForMinMaxId = ValueDAO.findValueHistoryForMinMaxId(azquoMemoryDB, batchNumber * BATCH_SIZE, (batchNumber + 1) * BATCH_SIZE);

            while (!valueHistoryForMinMaxId.isEmpty()) {
                for (ValueHistory valueForBackup : valueHistoryForMinMaxId) {
                    csvW.write(valueForBackup.getId() + "");
                    csvW.write(valueForBackup.getProvenance().getId() + "");
                    // attributes could have unhelpful chars
                    csvW.write(valueForBackup.getText().replace("\n", "\\\\n").replace("\r", "").replace("\t", "\\\\t"));
                    //base 64 encode the bytes
                    byte[] encodedBytes = Base64.getEncoder().encode(valueForBackup.getNameIdsAsBytes());
                    String string64 = new String(encodedBytes);
                    csvW.write(string64);
                    csvW.endRecord();
                }
                batchNumber++;
                valueHistoryForMinMaxId = ValueDAO.findValueHistoryForMinMaxId(azquoMemoryDB, batchNumber * BATCH_SIZE, (batchNumber + 1) * BATCH_SIZE);
            }
        }
        csvW.close();
        fos.close();
        return tempFile.getAbsolutePath();
    }

    // since there's one backup per db and a new database is created right before restore there should be no reason to be concerned about two restores running simultaneously
    private List<Name> namesFromBackup = new ArrayList<>();
    private List<byte[]> namesChildrenCacheFromBackup = new ArrayList<>();

    // ok, we need to create new names in a style similar to loading a database
    public synchronized void setBatchOfNamesFromBackup(List<NameForBackup> backupBatch) throws Exception {
        for (NameForBackup nameForBackup : backupBatch) {
            namesFromBackup.add(new StandardName(azquoMemoryDB, nameForBackup.getId(), nameForBackup.getProvenanceId(), null, nameForBackup.getAttributes().replace("\\\\n", "\n").replace("\\\\t", "\t"), nameForBackup.getNoParents(), nameForBackup.getNoValues(), true));
            azquoMemoryDB.setNextId(nameForBackup.getId());
            if (nameForBackup.getChildren().length > 1_000_000) {
                System.out.println("name with more than a million children : " + nameForBackup.getAttributes());
            }
            namesChildrenCacheFromBackup.add(nameForBackup.getChildren());
        }
    }

    // note this is not multithreaded nor does it do any integrity checks like the linker in the normal db load. Not so bothered about this for the moment

    public synchronized void linkNames() throws Exception {
        for (int i = 0; i < namesFromBackup.size(); i++) {
            Name name = namesFromBackup.get(i);
            name.link(namesChildrenCacheFromBackup.get(i), true);
        }
        // will NPE if hit after. Fair enough I think.
        namesFromBackup = null;
        namesChildrenCacheFromBackup = null;
        // bit of a hack, let's bump the next id and persist here
        azquoMemoryDB.getNextId(); // bump it up one, the logic later is get and increment;
        // persist in background
        new Thread(azquoMemoryDB::persistToDataStore).start();
    }

    public synchronized void setBatchOfValuesFromBackup(List<ValueForBackup> backupBatch) throws Exception {
        for (ValueForBackup valueForBackup : backupBatch) {
            new Value(azquoMemoryDB, valueForBackup.getId(), valueForBackup.getProvenanceId(), valueForBackup.getText().replace("\\\\n", "\n").replace("\\\\t", "\t"), valueForBackup.getNames(), true);
            azquoMemoryDB.setNextId(valueForBackup.getId());
        }
    }

    public synchronized void setBatchOfValueHistoriesFromBackup(List<ValueForBackup> backupBatch) {
        int sqlLimit = 10_000;
        int startPoint = 0;
        while (startPoint < backupBatch.size()) {
            if (startPoint + sqlLimit <= backupBatch.size()) {
                ValueDAO.insertValuesHistoriesFromBackup(azquoMemoryDB, backupBatch.subList(startPoint, startPoint + sqlLimit));
            } else {
                ValueDAO.insertValuesHistoriesFromBackup(azquoMemoryDB, backupBatch.subList(startPoint, backupBatch.size()));
            }
            startPoint += sqlLimit;
        }
    }

    public synchronized void setBatchOfProvenanceFromBackup(List<ProvenanceForBackup> backupBatch) throws Exception {
        for (ProvenanceForBackup provenanceForBackup : backupBatch) {
            new Provenance(azquoMemoryDB, provenanceForBackup.id, provenanceForBackup.json, true);
            azquoMemoryDB.setNextId(provenanceForBackup.id);
        }
    }
}