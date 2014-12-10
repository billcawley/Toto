package com.azquo.service;

import com.azquo.memorydb.AppEntity;
import com.azquo.memorydb.AzquoMemoryDB;
import com.azquo.memorydb.AzquoMemoryDBEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by cawley on 15/08/14.
 * OK to make custom app entities it seems the best way to do it is to pass services which implement certain functions to the main DB.
 *
 * The DB object can then iterate through the services calling more generic instantiation functions, the service will deal with that all the indexing etc
 *
 * persistence still dealt with by the main Azquo DB
 *
 *
 * E.g. the reviews feedback system. So we might start with Order/Product/Feedback as an example. I need to think carefully about this
 *
 * as mentioned in the app entity type this may be redundant since we're now able to do a  lot with sets and attributes
 *
 */
public abstract class AppEntityService <EntityType extends AppEntity>{

    @Autowired
    protected NamedParameterJdbcTemplate jdbcTemplate;

    // since this, and other maps, will not be held against the db object we need to record which DB entities are held against in the index
    // in this case the generic by ID index
    private final Map<AzquoMemoryDB, Map<Integer, EntityType>>  entityByIdMap = new HashMap<AzquoMemoryDB, Map<Integer, EntityType>>();

    // going to put table names here then pass the appropriate service to entities so they can all it from there

    public abstract String getTableName();

    public void checkCreateMySQLTable(AzquoMemoryDB azquoMemoryDB){
        // this first time check the table exists, there may be a better place to put this
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        jdbcTemplate.update("CREATE TABLE IF NOT EXISTS `" + azquoMemoryDB.getMySQLName() + "`.`" + getTableName().replace("`", "") + "` (`id` int(11) NOT NULL,`json` text COLLATE utf8_unicode_ci NOT NULL) ENGINE=InnoDB  DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;", namedParams);
    }

    public void addToIdMap(EntityType entity) throws Exception{
        if (entityByIdMap.get(entity.getAzquoMemoryDB()) == null){
            entityByIdMap.put(entity.getAzquoMemoryDB(), new HashMap<Integer, EntityType>());
        }
        if (entityByIdMap.get(entity.getAzquoMemoryDB()).get(entity.getId()) != null) {
            throw new Exception("tried to add an entity to the database with an existing id! new id = " + entity.getId());
        } else {
            entityByIdMap.get(entity.getAzquoMemoryDB()).put(entity.getId(), entity);
        }
    }

    public EntityType getById(AzquoMemoryDB azquoMemoryDB, int id){
        return entityByIdMap.get(azquoMemoryDB).get(id);
    }

    public Collection<EntityType> findAll(AzquoMemoryDB azquoMemoryDB){
        return entityByIdMap.get(azquoMemoryDB).values();
    }

    public abstract void loadEntityFromJson(AzquoMemoryDB azquoMemoryDB, int id, String json) throws Exception;

}
