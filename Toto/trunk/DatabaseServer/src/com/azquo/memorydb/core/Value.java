package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.StandardDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 22/10/13
 * Time: 22:31
 * To reflect a fundamental Azquo idea : a piece of data which has names attached
 * Delete solution is to unlink names and jam the old links in delete_info.
 * Can worry about how to restore later.
 * Notable that the names list object here is what defines the relationship between values and names, value sets against each name is just a lookup
 * I'm using an array internally to save memory,
 * <p>
 * todo - should store a double value also? Will it save loads of parsing?
 */
public final class Value extends AzquoMemoryDBEntity {

    private static final Logger logger = Logger.getLogger(Value.class);

    // issue of final here and bits of the init being in a try block. Need to have a little think about that
    private Provenance provenance;
    private String text;//no longer final.   May be adjusted during imports (if duplicate lines are found will sum...)

    // changing to array to save memory
    private Name[] names;

    // to be used by the code when creating a new value
    // add the names after
    private static AtomicInteger newValue = new AtomicInteger(0);
    public Value(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance, final String text) throws Exception {
        super(azquoMemoryDB, 0);
        newValue.incrementAndGet();
        this.provenance = provenance;
        this.text = text;
        names = new Name[0];
        // added 10/12/2014, wasn't there before, why??? I suppose it just worked. Inconsistent though!
        getAzquoMemoryDB().addValueToDb(this);
    }

    // only to be used by azquomemory db, hence protected. What is notable is the setting of the id from the record in mysql

    private static AtomicInteger newValue1 = new AtomicInteger(0);
    protected Value(final AzquoMemoryDB azquoMemoryDB, final int id, final String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);
        newValue1.incrementAndGet();
        try {
            JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);
            this.provenance = getAzquoMemoryDB().getProvenanceById(transport.provenanceId);
            // tested, .intern here saves memory
            this.text = transport.text.intern();
            Set<Name> newNames = new HashSet<>(transport.nameIds.size());
            //System.out.println("name ids" + transport.nameIds);
            for (Integer nameId : transport.nameIds) {
                Name name = getAzquoMemoryDB().getNameById(nameId);
                if (name != null) {
                    newNames.add(name);
                } else {
                    logger.info("Value referenced a name id that did not exist : " + nameId + " skipping");
                }
            }
            // todo - is this efficient? I mean the values being added into names, rewriting the arrays etc.
            // I guess do some testing? New time reporting on DB loading should show if it's worth investigating
            names = new Name[0]; // simply to stop the call below getting shirty about possible old names that aren't there
            setNamesWillBePersisted(newNames); //again I know this looks a bit odd, part of the hack for lists internally
            getAzquoMemoryDB().addValueToDb(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // todo address this new fastloader one being public
    private static AtomicInteger newValue2 = new AtomicInteger(0);
    public Value(final AzquoMemoryDB azquoMemoryDB, final int id, final int provenanceId, String text, byte[] namesCache) throws Exception {
        super(azquoMemoryDB, id);
        newValue2.incrementAndGet();
        this.provenance = getAzquoMemoryDB().getProvenanceById(provenanceId);
        this.text = text.intern();
        // ok populate the names here but unlike before we're not doing any managing of the names themselves (as in adding this value to them), this just sets the names straight in there so to speak
        int noNames = namesCache.length / 4;
        ByteBuffer byteBuffer = ByteBuffer.wrap(namesCache);
        // we assume the names are loaded (though they may not be linked yet)
        Name[] newNames = new Name[noNames];
        for (int i = 0; i < noNames; i++) {
            newNames[i] = getAzquoMemoryDB().getNameById(byteBuffer.getInt(i * 4));
        }
        this.names = newNames;
        // this should be fine being handled here while loading a db - was storing this against the name but too much space
        for (Name newName : this.names) {
            newName.addToValues(this, true); // true here means don't check duplicates
        }
        getAzquoMemoryDB().addValueToDb(this);
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    // notable that this is never used, do we care about undelete functionality?
/*    public String getDeletedInfo() {
        return deletedInfo;
    }*/

    @Override
    public String toString() {
        return "Value{" +
                "id=" + getId() +
                ", provenance=" + provenance +
                ", text='" + text + '\'' +
                '}';
    }

    private static AtomicInteger getNamesCount = new AtomicInteger(0);
    public Collection<Name> getNames() {
        getNamesCount.incrementAndGet();
        return Collections.unmodifiableList(Arrays.asList(names)); // should be ok?
    }

    // make sure to adjust the values lists on the name objects :)
    // synchronised but I'm not sure if this is good enough? Certainly better then nothing
    // change to a list internally

    private static AtomicInteger setNamesWillBePersistedCount = new AtomicInteger(0);
    public synchronized void setNamesWillBePersisted(final Set<Name> newNameSet) throws Exception {
        setNamesWillBePersistedCount.incrementAndGet();
        checkDatabaseForSet(newNameSet);
        // remove from where it is right now
        for (Name oldName : this.names) {
            oldName.removeFromValues(this);
        }
        // same as above, copy into a local array before switching over
        Name[] newNames = new Name[newNameSet.size()];
        newNameSet.toArray(newNames);
        this.names = newNames; // keep it atomic - don't throw the array from here into toArray where there will be an array copy
        // set this against names on the new list
        for (Name newName : this.names) {
            newName.addToValues(this);
        }
        setNeedsPersisting();
    }

    // I think that's all that's needed now we're not saving deleted info?
    private static AtomicInteger deleteCount = new AtomicInteger(0);
    public void delete() throws Exception {
        deleteCount.incrementAndGet();
        needsDeleting = true;
        for (Name newName : this.names) {
            newName.removeFromValues(this);
        }
        setNeedsPersisting();
    }


    // for Jackson mapping, trying to attach to actual fields would be dangerous in terms of allowing unsafe access
    // think important to use a linked hash map to preserve order.
    private static class JsonTransport {
        public int provenanceId;
        public String text;
        public Set<Integer> nameIds;

        @JsonCreator
        private JsonTransport(@JsonProperty("provenanceId") int provenanceId, @JsonProperty("text") String text
                , @JsonProperty("nameIds") Set<Integer> nameIds) {
            this.provenanceId = provenanceId;
            this.text = text;
            this.nameIds = nameIds;
        }
    }

    @Override
    protected String getPersistTable() {
        return StandardDAO.PersistedTable.value.name();
    }

    private static AtomicInteger getAsJsonCount = new AtomicInteger(0);
    @Override
    public String getAsJson() {
        getAsJsonCount.incrementAndGet();
        // yes could probably use list but lets match collection types . . .
        Set<Integer> nameIds = new LinkedHashSet<>();
        for (Name name : names) {
            nameIds.add(name.getId());
        }
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(provenance.getId(), text, nameIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static AtomicInteger getNameIdsAsBytesCount = new AtomicInteger(0);
    public byte[] getNameIdsAsBytes() {
        getNameIdsAsBytesCount.incrementAndGet();
        ByteBuffer buffer = ByteBuffer.allocate(names.length * 4);
        for (Name name : names) {
            buffer.putInt(name.getId());
        }
        return buffer.array();
    }

    public static void printFunctionCountStats(){
        System.out.println("######### VALUE FUNCTION COUNTS");
        System.out.println("newValue\t\t\t\t\t\t\t\t" + newValue.get());
        System.out.println("newValue1\t\t\t\t\t\t\t\t" + newValue1.get());
        System.out.println("newValue2\t\t\t\t\t\t\t\t" + newValue2.get());
        System.out.println("getNamesCount\t\t\t\t\t\t\t\t" + getNamesCount.get());
        System.out.println("setNamesWillBePersistedCount\t\t\t\t\t\t\t\t" + setNamesWillBePersistedCount.get());
        System.out.println("deleteCount\t\t\t\t\t\t\t\t" + deleteCount.get());
        System.out.println("getAsJsonCount\t\t\t\t\t\t\t\t" + getAsJsonCount.get());
        System.out.println("getNameIdsAsBytesCount\t\t\t\t\t\t\t\t" + getNameIdsAsBytesCount.get());
    }

    public static void clearFunctionCountStats() {
        newValue.set(0);
        newValue1.set(0);
        newValue2.set(0);
        getNamesCount.set(0);
        setNamesWillBePersistedCount.set(0);
        deleteCount.set(0);
        getAsJsonCount.set(0);
        getNameIdsAsBytesCount.set(0);
    }

}
