package com.azquo.toto.dao;

import com.azquo.toto.memorydb.Name;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.sql.SQLException;
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
public final class NameDAO extends StandardDAO<Name> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "name";
    }

    // column names (except ID)

    public static final String NAME = "name";
    public static final String PROVENANCEID = "provenance_id";



    // associated table names, currently think here is a good place to put them. Where they're used.
    // used to be the same structure on teh two tables but peer needed additive

    public static final String NAMESETDEFINTION = "name_set_definition";
    public static final String PEERSETDEFINTION = "peer_set_definition";

    public static final String PARENTID = "parent_id";
    public static final String CHILDID = "child_id";
    public static final String POSITION = "position";
    public static final String NAMEID = "name_id";
    public static final String PEERID = "peer_id";
    public static final String ADDITIVE = "additive";


    @Override
    public Map<String, Object> getColumnNameValueMap(final Name name){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, name.getId());
        toReturn.put(NAME, name.getName());
        return toReturn;
    }

    public static class NameRowMapper implements RowMapper<Name> {

        private final TotoMemoryDB totoMemoryDB;

        public NameRowMapper(TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }
        @Override
        public final Name mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Name(totoMemoryDB, rs.getInt(ID), totoMemoryDB.getProvenanceById(rs.getInt(PROVENANCEID)), rs.getString(NAME), rs.getBoolean(ADDITIVE));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static class CommaSeparatedParentNameIdsRowMapper implements RowMapper<String> {

        @Override
        public final String mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return rs.getInt(PARENTID) + "," + rs.getInt(CHILDID);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static class CommaSeparatedNamePeerIdsRowMapper implements RowMapper<String> {

        @Override
        public final String mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return rs.getInt(NAMEID) + "," + rs.getInt(PEERID) + "," + rs.getBoolean(ADDITIVE);
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

    // these functions used to have some complexity to do with data integrity, now they are simple as the memory db should take care of that

    public boolean linkParentAndChild(final TotoMemoryDB totoMemoryDB, final Name parent, final Name child, int position) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        namedParams.addValue(POSITION, position);
        String updateSql = "INSERT INTO `" + totoMemoryDB.getDatabaseName() + "`.`" + NAMESETDEFINTION + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES (:" + PARENTID + ",:" + CHILDID + ",:" + POSITION + ")";
        jdbcTemplate.update(updateSql, namedParams);
        return true;
    }

    public boolean linkNameAndPeer(final TotoMemoryDB totoMemoryDB, final Name name, final Name peer, int position, boolean additive) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NAMEID, name.getId());
        namedParams.addValue(PEERID, peer.getId());
        namedParams.addValue(POSITION, position);
        namedParams.addValue(ADDITIVE, additive);
        String updateSql = "INSERT INTO `" + totoMemoryDB.getDatabaseName() + "`.`" + PEERSETDEFINTION + "` (`" + NAMEID + "`,`" + PEERID + "`,`" + POSITION + "`,`" + ADDITIVE + "`) VALUES (:" + NAMEID + ",:" + PEERID + ",:" + POSITION + ",:" + ADDITIVE + ")";
        jdbcTemplate.update(updateSql, namedParams);
        return true;
    }

    public boolean linkParentAndChildren(final TotoMemoryDB totoMemoryDB, final Name parent) throws DataAccessException {

        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "INSERT INTO `" + totoMemoryDB.getDatabaseName() + "`.`" + NAMESETDEFINTION + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES ";
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

    public int unlinkAllChildrenForParent(final TotoMemoryDB totoMemoryDB, final Name parent) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        String updateSql = "DELETE from `" + totoMemoryDB.getDatabaseName() + "`.`" + NAMESETDEFINTION + "` where `" + PARENTID + "` = :" + PARENTID;
        return jdbcTemplate.update(updateSql, namedParams);
    }

    public int unlinkAllPeersForName(final TotoMemoryDB totoMemoryDB, final Name name) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NAMEID, name.getId());
        String updateSql = "DELETE from `" + totoMemoryDB.getDatabaseName() + "`.`" + PEERSETDEFINTION + "` where `" + NAMEID + "` = :" + NAMEID;
        return jdbcTemplate.update(updateSql, namedParams);
    }

    /*SELECT *
FROM `name_set_definition`
ORDER BY parent_id, position*/
    public List<String> findAllParentChildLinksOrderByParentIdPosition(final TotoMemoryDB totoMemoryDB) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        final String FIND_EXISTING_LINK = "Select `" + PARENTID + "`,`" + CHILDID + "` from `" + totoMemoryDB.getDatabaseName() + "`.`" + NAMESETDEFINTION + "` order by `" + PARENTID + "`,`" + POSITION + "`";
        return jdbcTemplate.query(FIND_EXISTING_LINK, namedParams, new CommaSeparatedParentNameIdsRowMapper());

    }

    public List<String> findAllPeerLinksOrderByNameIdPosition(final TotoMemoryDB totoMemoryDB) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        final String FIND_EXISTING_LINK = "Select `" + NAMEID + "`,`" + PEERID + "`,`" + ADDITIVE + "` from `" + totoMemoryDB.getDatabaseName() + "`.`" + PEERSETDEFINTION + "` order by `" + NAMEID + "`,`" + POSITION + "`";
        return jdbcTemplate.query(FIND_EXISTING_LINK, namedParams, new CommaSeparatedNamePeerIdsRowMapper());

    }

}
