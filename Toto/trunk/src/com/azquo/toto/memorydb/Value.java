package com.azquo.toto.memorydb;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 22/10/13
 * Time: 22:31
 * To reflect a fundamental Toto idea : a piece of data which has names attached
 * Delete solution is to unlink and jam the old links in delete_info.
 * Can worry about how to restore later.
 *
 */
public final class Value extends TotoMemoryDBEntity {

    // leaving here as a reminder to consider proper logging

    //private static final Logger logger = Logger.getLogger(Value.class.getName());

    private final Provenance provenance;
    private final String text;
    private String deletedInfo;

    private Set<Name> names;



    public Value(final TotoMemoryDB totoMemoryDB, final Provenance provenance, final String text, final String deletedInfo) throws Exception {
        super(totoMemoryDB, 0);
        this.provenance = provenance;
        this.text = text;
        this.deletedInfo = deletedInfo;
        names = new HashSet<Name>();
    }

    public Value(final TotoMemoryDB totoMemoryDB, final int id, final String jsonFromDB) throws Exception {
        super(totoMemoryDB, id);
        JsonTransport transport = jacksonMapper.readValue(jsonFromDB, JsonTransport.class);
        this.provenance = getTotoMemoryDB().getProvenanceById(transport.provenanceId);
        this.text = transport.text;
        this.deletedInfo = transport.deletedInfo;
        names = new HashSet<Name>();
        //System.out.println("name ids" + transport.nameIds);
        for (Integer nameId : transport.nameIds){
            names.add(getTotoMemoryDB().getNameById(nameId));
        }
        setNamesWillBePersisted(names);
        getTotoMemoryDB().addValueToDb(this);
    }

    @Override
    protected void setNeedsPersisting() {
        getTotoMemoryDB().setValueNeedsPersisting(this);
    }

    @Override
    protected void classSpecificSetAsPersisted(){
        getTotoMemoryDB().removeValueNeedsPersisting(this);
    }

    public Provenance getProvenance() {
        return provenance;
    }

    public String getText() {
        return text;
    }

    public String getDeletedInfo() {
        return deletedInfo;
    }

    public synchronized void setDeletedInfoWillBePersisted(final String deletedInfo) throws Exception {
        if (!deletedInfo.equals(this.deletedInfo)){
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
                ", deleted=" + deletedInfo+
                '}';
    }

    public Set<Name> getNames() {
        return Collections.unmodifiableSet(names);
    }

    public synchronized void setNamesWillBePersisted(final Set<Name> names) throws Exception {
        checkDatabaseForSet(names);


        for (Name oldName : this.names){
            oldName.removeFromValues(this);
        }

        this.names = names;

        // set all parents on the new list
        for (Name newName : this.names){
            newName.addToValues(this);
        }
        setNeedsPersisting();
    }


    // for Jackson mapping, trying to attach to actual fields would be dangerous in terms of allowing unsafe access
    // think important to use a linked hash map to preserve order.
    private static class JsonTransport{
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

    // suppose no harm in being public

    public String getAsJson() {
        // yes could probably use list but lets match collection types . . .
        Set<Integer> nameIds = new LinkedHashSet<Integer>();
        for (Name name : names){
            nameIds.add(name.getId());
        }
        try {
            return jacksonMapper.writeValueAsString(new JsonTransport(provenance.getId(),text,deletedInfo,nameIds));
        } catch (Exception e){
            e.printStackTrace();
        }
        return "";
    }


}
