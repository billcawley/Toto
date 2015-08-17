package com.azquo.memorydb.core;

import com.azquo.memorydb.dao.StandardDAO;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.log4j.Logger;

import java.util.*;

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
 */
public final class Value extends AzquoMemoryDBEntity {

    private static final Logger logger = Logger.getLogger(Value.class);

    // issue of final here and bits of the init being in a try block. Need to have a little think about that
    private Provenance provenance;
    private String text;//no longer final.   May be adjusted during imports (if duplicate lines are found will sum...)
    private String deletedInfo;

    // changing to array to save memory
    private Name[] names;

    // to be used by the code when creating a new value
    // add the names after

    public Value(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance, final String text, final String deletedInfo) throws Exception {
        super(azquoMemoryDB, 0);
        this.provenance = provenance;
        this.text = text;
        this.deletedInfo = deletedInfo;
        names = new Name[0];
        // added 10/12/2014, wasn't there before, why??? I suppose it just worked. Inconsistent though!
        getAzquoMemoryDB().addValueToDb(this);
    }

    // only to be used by azquomemory db, hence protected. What is notable is the setting of the id from the record in mysql

    protected Value(final AzquoMemoryDB azquoMemoryDB, final int id, final String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);
        try {
            JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);
            this.provenance = getAzquoMemoryDB().getProvenanceById(transport.provenanceId);
            // tested, .intern here saves memory
            this.text = transport.text.intern();
            this.deletedInfo = transport.deletedInfo;
            Set<Name> newNames = new HashSet<>();
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

    public synchronized void setDeletedInfoWillBePersisted(final String deletedInfo) throws Exception {
        if (!deletedInfo.equals(this.deletedInfo)) {
            this.deletedInfo = deletedInfo;
            setNeedsPersisting();
        }
    }

    @Override
    public String toString() {
        return "Value{" +
                "id=" + getId() +
                ", provenance=" + provenance +
                ", text='" + text + '\'' +
                ", deleted=" + deletedInfo +
                '}';
    }

    public Collection<Name> getNames() {
        return Collections.unmodifiableList(Arrays.asList(names)); // should be ok?
    }
    // switch to list internally, hope overhead won't be too much, want the memory saving

    public void setNames(final Set<Name> names) {
        Name[] newNames = new Name[names.size()];
        names.toArray(newNames);
        this.names = newNames; // keep it atomic - don't throw the array from here into toArray where there will be an array copy
    }

    // make sure to adjust the values lists on the name objects :)
    // synchronised but I'm not sure if this is good enough? Certainly better then nothing
    // change to a list internally

    public synchronized void setNamesWillBePersisted(final Set<Name> newNameSet) throws Exception {
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

    // for Jackson mapping, trying to attach to actual fields would be dangerous in terms of allowing unsafe access
    // think important to use a linked hash map to preserve order.
    private static class JsonTransport {
        public int provenanceId;
        public String text;
        public String deletedInfo;
        public Set<Integer> nameIds;

        @JsonCreator
        private JsonTransport(@JsonProperty("provenanceId") int provenanceId, @JsonProperty("text") String text
                , @JsonProperty("deletedInfo") String deletedInfo, @JsonProperty("nameIds") Set<Integer> nameIds) {
            this.provenanceId = provenanceId;
            this.text = text;
            this.deletedInfo = deletedInfo;
            this.nameIds = nameIds;
        }
    }

    @Override
    protected String getPersistTable() {
        return StandardDAO.PersistedTable.value.name();
    }

    @Override
    public String getAsJson() {
        // yes could probably use list but lets match collection types . . .
        Set<Integer> nameIds = new LinkedHashSet<>();
        for (Name name : names) {
            nameIds.add(name.getId());
        }
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(provenance.getId(), text, deletedInfo, nameIds));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }
}
