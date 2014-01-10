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

    private final Provenance provenance;
    private final double doubleValue;
    private final String text;
    private String deletedInfo;

    private Set<Name> names;

    private boolean namesChanged;


    public Value(final TotoMemoryDB totoMemoryDB, final Provenance provenance, final double doubleValue, final String text, final String deletedInfo) throws Exception {
        this(totoMemoryDB,0,provenance,doubleValue,text,deletedInfo);
    }

    public Value(final TotoMemoryDB totoMemoryDB, final int id, final Provenance provenance, final double doubleValue, final String text, final String deletedInfo) throws Exception {
        super(totoMemoryDB, id);
        this.provenance = provenance;
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

    public Provenance getProvenance() {
        return provenance;
    }

    public double getDoubleValue() {
        return doubleValue;
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
            entityColumnsChanged = true;
            setNeedsPersisting();
        }
    }

    @Override
    public String toString() {
        return "Value{" +
                "id=" + getId() +
                ", provenance=" + provenance +
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
