package com.azquo.memorydb;

import com.azquo.service.AppEntityService;

/**
 * Created by cawley on 18/08/14.
 *
 * Has to be created with it's service for the data table name. And probably a good idea for it to have the service anyway
 *
 * Edd note in Dec 14 : the way we're going with sets this may not be used. Will leave it here for the moment.
 *
 */
public abstract class AppEntity extends AzquoMemoryDBEntity{

    protected final AppEntityService service;

    public String getPersistTable(){
        return service.getTableName();
    }

    protected AppEntity(AzquoMemoryDB azquoMemoryDB, int id, AppEntityService service) throws Exception {
        super(azquoMemoryDB, id, true);
        this.service = service;
        this.setNeedsPersisting();
        // I did add to the id map here but it threw a compiler warning. Much wasted time trying to zap it . . .
    }
}
