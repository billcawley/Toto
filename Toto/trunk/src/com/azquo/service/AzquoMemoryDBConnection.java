package com.azquo.service;

import com.azquo.adminentities.User;
import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.Name;
import com.azquo.memorydb.Provenance;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created by cawley on 21/10/14.
 * I need a simple superclass to chuck around outside this package
 * but which will stop code outside this package getting to the DB object itself
 * not necessary functionally, trying to enforce code structure
 *
 * RIght, I'm going to chuck a few more things in her such as Provenance, Language and loose.
 * I'd rather not but right now the alternative is more messy.
 *
 * todo : address exactly what goes in here which is not a reference to the memory database
 */
public class AzquoMemoryDBConnection {

    private static final Logger logger = Logger.getLogger(AzquoMemoryDBConnection.class);

    private AzquoMemoryDB azquoMemoryDB;

    private List<Set<Name>>  readPermissions;

    protected final User user;

    private List<Set<Name>> writePermissions;

    protected AzquoMemoryDBConnection(AzquoMemoryDB azquoMemoryDB, User user){
        this.azquoMemoryDB = azquoMemoryDB;
        this.user = user;
        readPermissions = new ArrayList<Set<Name>>();
        writePermissions = new ArrayList<Set<Name>>();
    }

    protected AzquoMemoryDB getAzquoMemoryDB() {
        return azquoMemoryDB;
    }

    public boolean hasAzquoMemoryDB() {
        return azquoMemoryDB != null;
    }

    public String getCurrentDBName() {
        if (azquoMemoryDB!= null && azquoMemoryDB.getDatabase()!=null){
            return azquoMemoryDB.getDatabase().getMySQLName();
        }
        return null;
    }

    public com.azquo.adminentities.Database getCurrentDatabase() {
        if (azquoMemoryDB!=null){
            return azquoMemoryDB.getDatabase();
        }
        return null;
    }
    public int getMaxIdOnCurrentDB() {
        return azquoMemoryDB.getCurrentMaximumId();
    }
    // for debugging
    public void memoryReport() {
        azquoMemoryDB.memoryReport();
    }

    public String getLocalCurrentDBName(){
        return azquoMemoryDB.getDatabase().getName();
    }

    protected void setAzquoMemoryDB(final AzquoMemoryDB azquoMemoryDB) {
        this.azquoMemoryDB = azquoMemoryDB;
    }

    protected Provenance provenance = null;

    public Provenance getProvenance(){
        return getProvenance("in spreadsheet");

    }

    public Provenance getProvenance(String where) {
        if (provenance == null) {
            try {
                provenance = new Provenance(getAzquoMemoryDB(), where, new Date(), "", "", "-");
            } catch (Exception ignored) {
            }
        }
        return provenance;
    }

    public void setNewProvenance(String provenanceMethod, String provenanceName){
        setNewProvenance(provenanceMethod, provenanceName,"");
    }

    public void setNewProvenance(String provenanceMethod, String provenanceName, String context) {
        try {
            provenance = new Provenance(getAzquoMemoryDB(), user.getName(), new Date(), provenanceMethod, provenanceName,  context);
        } catch (Exception e) {
            logger.error("can't set a new provenance", e);
        }
    }

    public List<Set<Name>> getReadPermissions(){
        return this.readPermissions;
    }

    public void setReadPermissions(List<Set<Name>> names){
        this.readPermissions = names;
    }

    public List<Set<Name>> getWritePermissions(){
        return this.writePermissions;
    }

    public void setWritePermissions(List<Set<Name>> names){
        this.writePermissions = names;
    }

    public User getUser() {
        return user;
    }

    public int getBusinessId(){
        return user.getBusinessId();
    }

    public void persist() {
        azquoMemoryDB.saveDataToMySQL();
    }


}
