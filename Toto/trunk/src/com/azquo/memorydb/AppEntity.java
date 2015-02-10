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
public abstract class AppEntity<ServiceType extends AppEntityService> extends AzquoMemoryDBEntity{

    private final ServiceType service;

    public String getPersistTable(){
        return service.getTableName();
    }

    protected <T extends AppEntity> AppEntity(AzquoMemoryDB azquoMemoryDB, int id, ServiceType service) throws Exception {
        super(azquoMemoryDB, id, true);
        this.service = service;
        // this should be done in the super constructor but it will null pointer without the service being set . . .
        this.setNeedsPersisting();
        service.addToIdMap(this); // I'm letting this annoy me far too much :)
    }
}
