package com.azquo.memorydb.dao;

import com.azquo.memorydb.core.AzquoMemoryDB;
import com.azquo.memorydb.core.AzquoMemoryDBEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 30/10/15.
 *
 * Just a way to factor a few things off from the new fast value and name DAO classes. Maybe should be renamed to include MySQL in the name
 *
 * Maybe rename to DAO utils? Factoring changing now I'm going static - might need to check it all makes sense.
 */
class FastDAO {

    static final String ID = "id";

    // this value is not picked randomly, tests have it faster than 1k or 10k. It seems with imports bigger is not necessarily better. Possibly to do with query parsing overhead.

    static final int UPDATELIMIT = 2500; // going to 2.5k as now some of the records can be quite large! Also simultaneous.

    // possibly very similar to what's in JsonRecordDAO. Not sure if it's worth factoring (if so use a list of ids)

    static void bulkDelete(final String persistenceName, final List<? extends AzquoMemoryDBEntity> entities, String tableName) throws DataAccessException {
        if (!entities.isEmpty()) {
            final MapSqlParameterSource namedParams = new MapSqlParameterSource();
            final StringBuilder updateSql = new StringBuilder("delete from `" + persistenceName + "`.`" + tableName + "` where " + ID + " in (");
            int count = 1;
            for (AzquoMemoryDBEntity azquoMemoryDBEntity : entities) {
                if (count == 1) {
                    updateSql.append(azquoMemoryDBEntity.getId());
                } else {
                    updateSql.append(",").append(azquoMemoryDBEntity.getId());
                }
                count++;
            }
            updateSql.append(")");
            //System.out.println(updateSql.toString());
            JdbcTemplateUtils.update(updateSql.toString(), namedParams);
        }
    }

    static int findMaxId(final String persistenceName, String tableName) throws DataAccessException {
        final String SQL_SELECT_ALL = "Select max(id) from `" + persistenceName + "`.`" + tableName + "`";
        Integer toReturn = JdbcTemplateUtils.queryForObject (SQL_SELECT_ALL, JsonRecordDAO.EMPTY_PARAMETERS_MAP, Integer.class);
        return toReturn != null ? toReturn : 0; // otherwise we'll get a null pinter boxing to int!
    }
}