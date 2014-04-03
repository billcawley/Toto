package com.azquo.memorydb;

import com.azquo.memorydbdao.StandardDAO;
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
 */
public final class Value extends AzquoMemoryDBEntity {

    private static final Logger logger = Logger.getLogger(Value.class);

    private final Provenance provenance;
    private String text;//no longer final.   May be adjusted during imports (if duplicate lines are found will sum...)
    private String deletedInfo;

    private Set<Name> names;

    // to be used by the code when creating a new value
    // add the names after

    public Value(final AzquoMemoryDB azquoMemoryDB, final Provenance provenance, final String text, final String deletedInfo) throws Exception {
        super(azquoMemoryDB, 0);
        this.provenance = provenance;
        this.text = text;
        this.deletedInfo = deletedInfo;
        names = new HashSet<Name>();
    }

    // only to be used by azquomemory db, hence protected. What is notable is the setting of the id from the record in mysql

    protected Value(final AzquoMemoryDB azquoMemoryDB, final int id, final String jsonFromDB) throws Exception {
        super(azquoMemoryDB, id);
        JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);
        this.provenance = getAzquoMemoryDB().getProvenanceById(transport.provenanceId);
        this.text = transport.text;
        this.deletedInfo = transport.deletedInfo;
        names = new HashSet<Name>();
        //System.out.println("name ids" + transport.nameIds);
        for (Integer nameId : transport.nameIds) {
            Name name = getAzquoMemoryDB().getNameById(nameId);
            if (name != null) {
                names.add(name);
            } else {
                logger.info("Value referenced a name id that did not exist : " + nameId + " skipping");
            }
        }
        setNamesWillBePersisted(names);
        getAzquoMemoryDB().addValueToDb(this);
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public String getText() {
        return text;
    }

    public void setText(String text){
        this.text = text;
    }

    public String getDeletedInfo() {
        return deletedInfo;
    }

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

    public Set<Name> getNames() {
        return Collections.unmodifiableSet(names);
    }

    // make sure to adjust the values lists on the name objects :)
    // synchronised but I'm not sure if this is good enough? Certainly better then nothing

    public synchronized void setNamesWillBePersisted(final Set<Name> names) throws Exception {
        checkDatabaseForSet(names);
        // remove from where it is right now
        for (Name oldName : this.names) {
            oldName.removeFromValues(this);
        }
        this.names = names;
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
    protected StandardDAO.PersistedTable getPersistTable() {
        return StandardDAO.PersistedTable.value;
    }

    @Override
    public String getAsJson() {
        // yes could probably use list but lets match collection types . . .
        Set<Integer> nameIds = new LinkedHashSet<Integer>();
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
