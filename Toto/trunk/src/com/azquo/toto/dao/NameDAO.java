package com.azquo.toto.dao;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:19
 * DAO for Names, under new model just used for persistence,will hopefully be pretty simple
 */
public class NameDAO extends StandardDAO<Name> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "name";
    }

    // column names (except ID)

    public static final String NAME = "name";


    // associated table names, currently think here is a good place to put them. Where they're used.

    public enum SetDefinitionTable {name_set_definition, peer_set_definition}

    public static final String PARENTID = "parent_id";
    public static final String CHILDID = "child_id";
    public static final String POSITION = "position";


    @Override
    public Map<String, Object> getColumnNameValueMap(final Name name){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, name.getId());
        toReturn.put(NAME, name.getName());
        return toReturn;
    }

    public static final class NameRowMapper implements RowMapper<Name> {

        private TotoMemoryDB totoMemoryDB;

        public NameRowMapper(TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }
        @Override
        public Name mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Name(totoMemoryDB, rs.getInt(ID), rs.getString(NAME));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Name> getRowMapper(TotoMemoryDB totoMemoryDB) {
        return new NameRowMapper(totoMemoryDB);
    }

    // for loading into the memory db

    public List<Integer> findParentIdsForName(final TotoMemoryDB totoMemoryDB, SetDefinitionTable setDefinitionTable, final Name name) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(CHILDID, name.getId());
        final String FIND_EXISTING_LINKS = "Select `" + PARENTID + "` from `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + CHILDID + "` = :" + CHILDID + " order by `" + POSITION + "`";
        return jdbcTemplate.queryForList(FIND_EXISTING_LINKS, namedParams, Integer.class);

    }

    public List<Integer> findChildIdsForName(final TotoMemoryDB totoMemoryDB, SetDefinitionTable setDefinitionTable, final Name name) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, name.getId());
        final String FIND_EXISTING_LINKS = "Select `" + CHILDID + "` from `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID + " order by `" + POSITION + "`";
        return jdbcTemplate.queryForList(FIND_EXISTING_LINKS, namedParams, Integer.class);
    }


    // these two functions used to have some complexity to do with data integrity, now they are simple as the memory db should take care of that

    public boolean linkParentAndChild(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Name parent, final Name child, int position) throws DataAccessException {
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        namedParams.addValue(POSITION, position);
        String updateSql = "INSERT INTO `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES (:" + PARENTID + ",:" + CHILDID + ",:" + POSITION + ")";
        jdbcTemplate.update(updateSql, namedParams);
        return true;
    }

    public boolean linkParentAndChildren(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Name parent) throws DataAccessException {

        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "INSERT INTO `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES ";
        int count = 1;
        for (Name child : parent.getChildren()) {
            // I'm taking off the check - I think it's so rare we'll just let the DB complain
            updateSql += "(:" + PARENTID + count + ",:" + CHILDID + count + ",:" + POSITION + count + "),";
            namedParams.addValue(PARENTID + count, parent.getId());
            namedParams.addValue(CHILDID + count, child.getId());
            namedParams.addValue(POSITION + count, count);
            count++;
        }
        updateSql = updateSql.substring(0, updateSql.length() - 1);
        long track = System.currentTimeMillis();
        jdbcTemplate.update(updateSql, namedParams);
        System.out.println("parent child link time : " + (System.currentTimeMillis() - track) + " for " + (count - 1) + " names");
        return true;
    }

    public int unlinkParentAndChild(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Name parent, final Name child) throws DataAccessException {
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        String updateSql = "DELETE from `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID + " and `" + CHILDID + "` = :" + CHILDID + "";
        return jdbcTemplate.update(updateSql, namedParams);
    }

    public int unlinkAllForParent(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Name parent) throws DataAccessException {
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        String updateSql = "DELETE from `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID;
        return jdbcTemplate.update(updateSql, namedParams);
    }

}
