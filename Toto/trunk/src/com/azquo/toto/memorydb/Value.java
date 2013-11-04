package com.azquo.toto.memorydb;

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

    private final int provenanceId;
    private final double doubleValue;
    private final String text;
    private String deletedInfo;

    private Set<Name> names;

    private boolean namesChanged;


    /* This should ONLY be called by a totoMemory database and should stay with that database. I'm wondering how to enforce that.
    What's bugging me is a value or any entity for that matter being created outside of the db and hence having
    an id that's not proper, the id is significant for equals + hash. Though an externally created entity could not
    join the wrong DB as far as the master maps go it could screw things up in sets

    Edit : going to try this through standard entity
     */
    // for convenience, the id is only used by DAOs. It occurs that I could just hack the next id in the db object but this would be messy and less clear
    public Value(final TotoMemoryDB totoMemoryDB, final int provenanceId, final double doubleValue, final String text, final String deletedInfo) throws Exception {
        this(totoMemoryDB,0,provenanceId,doubleValue,text,deletedInfo);
    }

    public Value(final TotoMemoryDB totoMemoryDB, final int id, final int provenanceId, final double doubleValue, final String text, final String deletedInfo) throws Exception {
        super(totoMemoryDB, id);
        this.provenanceId = provenanceId;
        this.doubleValue = doubleValue;
        this.text = text;
        this.deletedInfo = deletedInfo;
        names = new HashSet<Name>();
        namesChanged = false;
    }

    @Override
    protected void addToDb() throws Exception {
        getTotoMemoryDB().addValueToDb(this);
    }

    @Override
    protected void setNeedsPersisting() {
        getTotoMemoryDB().setValueNeedsPersisting(this);
    }

    @Override
    protected void classSpecificSetAsPersisted(){
        namesChanged = false;
        getTotoMemoryDB().removeValueNeedsPersisting(this);
    }

    public boolean getNamesChanged() {
        return namesChanged;
    }

    public int getProvenanceId() {
        return provenanceId;
    }

/*    public Provenance getProvenance() {
        return getTotoMemoryDB().getProvenanceById(provenanceId);
    }*/

    public double getDoubleValue() {
        return doubleValue;
    }

    public String getText() {
        return text;
    }

    public String getDeletedInfo() {
        return deletedInfo;
    }

    public synchronized void setDeletedInfoWillBePersisted(String deletedInfo) throws Exception {
        if (!deletedInfo.equals(this.deletedInfo)){
            this.deletedInfo = deletedInfo;
            entityColumnsChanged = true;
            setNeedsPersisting();
        }
    }

    @Override
    public String toString() {
        return "Value{" +
                "id=" + getId() +
                ", changeId=" + provenanceId +
                ", doubleValue=" + doubleValue +
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

        if (!getTotoMemoryDB().getNeedsLoading()){ // while loading we don't want to set any persistence flags
            namesChanged = true;
            setNeedsPersisting();
        }
    }
}
