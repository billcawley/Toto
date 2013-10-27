package com.azquo.toto.dao;

import com.azquo.toto.entity.Label;
import com.azquo.toto.entity.Value;
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
 * DAO for Labels, under new model just used for persistence,will hopefully be pretty simple
 */
public class LabelDAO extends StandardDAO<Label> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "label";
    }

    // column names (except ID)

    public static final String NAME = "name";


    // associated table names, currently think here is a good place to put them. Where they're used.

    public enum SetDefinitionTable {label_set_definition, peer_set_definition}

    public static final String PARENTID = "parent_id";
    public static final String CHILDID = "child_id";
    public static final String POSITION = "position";


    @Override
    public Map<String, Object> getColumnNameValueMap(final Label label){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, label.getId());
        toReturn.put(NAME, label.getName());
        return toReturn;
    }

    public static final class LabelRowMapper implements RowMapper<Label> {

        private TotoMemoryDB totoMemoryDB;

        public LabelRowMapper(TotoMemoryDB totoMemoryDB){
            this.totoMemoryDB = totoMemoryDB;
        }
        @Override
        public Label mapRow(final ResultSet rs, final int row) throws SQLException {
            // not pretty, just make it work for the moment
            try {
                return new Label(totoMemoryDB, rs.getInt(ID), rs.getString(NAME));
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<Label> getRowMapper(TotoMemoryDB totoMemoryDB) {
        return new LabelRowMapper(totoMemoryDB);
    }

    /* this is probably now obsolete with the memoory DB
    public void store(final TotoMemoryDB totoMemoryDB, final Label label) throws DataAccessException {
        if (label.getName().contains(";")) {
            throw new InvalidDataAccessApiUsageException("Error, label name cannot contain ; :  " + label.getName());
        }
        final Label existing = findByName(totoMemoryDB.getDatabaseName(), label.getName());
        if (existing == null) { // no name exists, can store new label or update another to this name
            super.store(totoMemoryDB.getDatabaseName(), label);
        } else { // it does exist
            if (existing.getId() != label.getId()) { // either a new one or some other updated against an existing name, no good
                throw new InvalidDataAccessApiUsageException("Error, the label " + label.getName() + " already exists");
            } else { //updating one with teh same ID and name the only thing that could change is the flag
                if (existing.getLabelSetLookupNeedsRebuilding() != label.getLabelSetLookupNeedsRebuilding()){
                    super.store(totoMemoryDB.getDatabaseName(), label);
                }
            }
            // otherwise storing a name ID combo that's in the Db already, do nothing!
        }
    }*/

    // TODO : flags for label set lookup

    public void remove(final TotoMemoryDB totoMemoryDB, final Label label) throws DataAccessException {
        // ok this will unlink everywhere, should we actually delete then or not??
        List<Label> parents = findParents(totoMemoryDB, SetDefinitionTable.label_set_definition, label);
        for (Label parent : parents){
            unlinkParentAndChild(totoMemoryDB,SetDefinitionTable.label_set_definition, parent, label);
        }
        List<Label> children = findChildren(totoMemoryDB, SetDefinitionTable.label_set_definition, label, false);
        for (Label child : children){
            unlinkParentAndChild(totoMemoryDB, SetDefinitionTable.label_set_definition, label, child);
        }
        // now do the same for peers
        parents = findParents(totoMemoryDB, SetDefinitionTable.peer_set_definition, label);
        for (Label parent : parents){
            unlinkParentAndChild(totoMemoryDB,SetDefinitionTable.peer_set_definition, parent, label);
        }
        children = findChildren(totoMemoryDB, SetDefinitionTable.peer_set_definition, label, false);
        for (Label child : children){
            unlinkParentAndChild(totoMemoryDB, SetDefinitionTable.peer_set_definition, label, child);
        }
        removeById(totoMemoryDB, label);
    }

    public Label findByName(final TotoMemoryDB totoMemoryDB, final String name) throws DataAccessException {
        final String whereCondition = " where `" + NAME + "` = :" + NAME;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NAME, name);
        return findOneWithWhereSQLAndParameters(totoMemoryDB, whereCondition, namedParams, false);
    }

    // should this be public? I want the service to have direct access as the code will be normalised better . . .
    public List<Label> findChildren(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label label, final boolean sorted) throws DataAccessException {
        final String whereCondition = ", `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + getTableName() + "`." + ID + " = `" + setDefinitionTable + "`.`" + CHILDID + "` AND `" + setDefinitionTable + "`.`" + PARENTID + "` = :" + PARENTID + (sorted ? " order by `" + NAME + "`" : " order by `" + POSITION + "`");
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, label.getId());
        return findListWithWhereSQLAndParameters(totoMemoryDB, whereCondition, namedParams, false);
    }

    // for loading into the memory db

    public List<Integer> findParentIdsForLabel(final TotoMemoryDB totoMemoryDB, SetDefinitionTable setDefinitionTable, final Label label) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(CHILDID, label.getId());
        final String FIND_EXISTING_LINKS = "Select `" + PARENTID + "` from `" + totoMemoryDB + "`.`" + setDefinitionTable + "` where `" + CHILDID + "` = :" + CHILDID + " order by `" + POSITION + "`";
        return jdbcTemplate.queryForList(FIND_EXISTING_LINKS, namedParams, Integer.class);

    }

    public List<Integer> findChildIdsForLabel(final TotoMemoryDB totoMemoryDB, SetDefinitionTable setDefinitionTable, final Label label) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, label.getId());
        final String FIND_EXISTING_LINKS = "Select `" + CHILDID + "` from `" + totoMemoryDB + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID + " order by `" + POSITION + "`";
        return jdbcTemplate.queryForList(FIND_EXISTING_LINKS, namedParams, Integer.class);
    }


    public List<Label> findChildren(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label label, final int from, final int to) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, label.getId());
        if (from != -1) {
            namedParams.addValue("from", from);
        }
        if (to != -1) {
            namedParams.addValue("to", to);
        }
        final String whereCondition = ", `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + getTableName() + "`." + ID + " = `" + setDefinitionTable + "`.`" + CHILDID + "` AND `" + setDefinitionTable + "`.`" + PARENTID + "` = :" + PARENTID + (from != -1 ? " AND `" + setDefinitionTable + "`.`" + POSITION + "` >= :from" : "") + (to != -1 ? " AND `" + setDefinitionTable + "`.`" + POSITION + "` <= :to" : "") + " order by `" + POSITION + "`";
        return findListWithWhereSQLAndParameters(totoMemoryDB, whereCondition, namedParams, false);
    }

    public List<Label> findParents(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label label) throws DataAccessException {
        final String whereCondition = ", `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + getTableName() + "`." + ID + " = `" + setDefinitionTable + "`.`" + PARENTID + "` AND `" + setDefinitionTable + "`.`" + CHILDID + "` = :" + CHILDID;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(CHILDID, label.getId());
        return findListWithWhereSQLAndParameters(totoMemoryDB, whereCondition, namedParams, false);
    }

    public List<Label> findAllParents(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label label) throws DataAccessException {
        final List<Label> allParents = new ArrayList<Label>();
        List<Label> foundAtCurrentLevel = findParents(totoMemoryDB, setDefinitionTable, label);
        while (!foundAtCurrentLevel.isEmpty()) {
            allParents.addAll(foundAtCurrentLevel);
            List<Label> nextLevelList = new ArrayList<Label>();
            for (Label l : foundAtCurrentLevel) {
                nextLevelList.addAll(findParents(totoMemoryDB, setDefinitionTable, l));
            }
            if (nextLevelList.isEmpty()) { // noo more parents to find
                break;
            }
            foundAtCurrentLevel = nextLevelList;
        }
        return allParents;
    }

    public List<Label> findAllChildren(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label label) throws DataAccessException {
        final List<Label> allChildren = new ArrayList<Label>();
        List<Label> foundAtCurrentLevel = findChildren(totoMemoryDB,setDefinitionTable, label, false);
        while (!foundAtCurrentLevel.isEmpty()) {
            allChildren.addAll(foundAtCurrentLevel);
            List<Label> nextLevelList = new ArrayList<Label>();
            for (Label l : foundAtCurrentLevel) {
                nextLevelList.addAll(findChildren(totoMemoryDB, setDefinitionTable, l, false));
            }
            if (nextLevelList.isEmpty()) { // no more children to find
                break;
            }
            foundAtCurrentLevel = nextLevelList;
        }
        return allChildren;
    }



    public List<Label> findTopLevelLabels(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable) throws DataAccessException {
        final String whereCondition = " where `" + getTableName() + "`." + ID + " NOT IN (SELECT `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "`.`" + CHILDID + "` from `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "`)";
        // no parameters but follow the pattern
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        return findListWithWhereSQLAndParameters(totoMemoryDB, whereCondition, namedParams, false);
    }

    public int getMaxChildPosition(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label l) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, l.getId());
        final String FIND_MAX_POSITION = "Select max(" + POSITION + ") from `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID;
        // turns out looking for a null integer is actually the thing in this case as mysql returns null not no rows. I think :P
        Integer integer = jdbcTemplate.queryForObject(FIND_MAX_POSITION, namedParams, Integer.class);
        return (integer == null ? 0 : integer); // no records means we return 0 as the max position
    }

    public int getChildPosition(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label parent, final Label child) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        final String FIND_EXISTING_LINK_POSITION = "Select `" + POSITION + "` from `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID + " AND `" + CHILDID + "` = :" + CHILDID;
        List<Integer> existingPositionResult = jdbcTemplate.queryForList(FIND_EXISTING_LINK_POSITION, namedParams, Integer.class);
        if (!existingPositionResult.isEmpty()) {
            return existingPositionResult.get(0);// the compiler allows this?? Cool :)
        } else {
            return -1; // we'll call -1 not existing
        }
    }

    public boolean linkParentAndChild(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label parent, final Label child, int position) throws DataAccessException {
        if (child.getId() == parent.getId()){
            throw new InvalidDataAccessApiUsageException("cannot link label to itself!" + parent.getName());
        }
        List<Label> parentsToCheckForCircularReferences = findAllParents(totoMemoryDB,setDefinitionTable, parent);
        for (Label higherParent : parentsToCheckForCircularReferences){
            if (higherParent.getId() == child.getId()){
                throw new InvalidDataAccessApiUsageException("cannot create circular reference, " + child.getName() + " is above " + parent.getName());
            }
        }
        // init the parameters at the beginning, we can reuse them for all possible queries I think . . . .nice
        int existingMaxPosition = getMaxChildPosition(totoMemoryDB, setDefinitionTable, parent);
        if (position > (existingMaxPosition + 1)) { // if the position is greater than one above the top the chop it down
            position = existingMaxPosition + 1;
        }
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        namedParams.addValue(POSITION, position);
        //int numberDeleted = unlinkParentAndChild(parent,child);
        // Check if the link exists and if so at what position, The DB should NOT allow duplicate links!
        int existingPosition = getChildPosition(totoMemoryDB, setDefinitionTable, parent, child);
        if (existingPosition != -1) {
            if (existingPosition == position) { // the link already exists and in that position, return
                return false;
            } else { // we need to move the position
                if (position == (existingMaxPosition + 1)) {// can't allow a position that high if modifying the position rather than adding
                    position--;
                    // re rwite to the map since it's been updated . . . is this bad practice or acceptable here?
                    namedParams.addValue(POSITION, position);
                }
                System.out.println("existing position for  : " + child.getName() + ", " + existingPosition + " and new position : " + position);
                namedParams.addValue("EXISTINGPOSITION", existingPosition);
                // first move the positions that may be affected
                if (position > existingPosition) {
                    final String SHIFT_POSITIONS = "UPDATE `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = (`" + POSITION + "` - 1) where `" + PARENTID + "` = :" + PARENTID + " AND `" + POSITION + "` > :EXISTINGPOSITION AND `" + POSITION + "`<= :" + POSITION;
                    jdbcTemplate.update(SHIFT_POSITIONS, namedParams);
                } else { // this child is moving down in position, positions will have to be shifted up!
                    final String SHIFT_POSITIONS = "UPDATE `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = (`" + POSITION + "` + 1) where `" + PARENTID + "` = :" + PARENTID + " AND `" + POSITION + "` < :EXISTINGPOSITION AND `" + POSITION + "`>= :" + POSITION;
                    jdbcTemplate.update(SHIFT_POSITIONS, namedParams);
                }
            }
            // now move the existing which will right now have two links on that position
            final String SHIFT_POSITION = "UPDATE `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = :" + POSITION + " where `" + PARENTID + "` = :" + PARENTID + " AND `" + CHILDID + "` = :" + CHILDID;
            jdbcTemplate.update(SHIFT_POSITION, namedParams);
        } else { // insert a new one, shift positions up if necessary and sort out the lookup flags . . . .
            if (position < (existingMaxPosition + 1)) {// not right at the top, some positions need to be moved up
                final String SHIFT_POSITIONS = "UPDATE `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = (`" + POSITION + "` + 1) where `" + PARENTID + "` = :" + PARENTID + " AND `" + POSITION + "`>= :" + POSITION;
                jdbcTemplate.update(SHIFT_POSITIONS, namedParams);
            }
            // now make the link
            String updateSql = "INSERT INTO `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES (:" + PARENTID + ",:" + CHILDID + ",:" + POSITION + ")";
            jdbcTemplate.update(updateSql, namedParams);
            // TODO : lookup flags
        }
        return true;
    }

    public int unlinkParentAndChild(final TotoMemoryDB totoMemoryDB, final SetDefinitionTable setDefinitionTable, final Label parent, final Label child) throws DataAccessException {
        int existingPosition = getChildPosition(totoMemoryDB, setDefinitionTable, parent, child);
        if (existingPosition != -1) { // we have something to do!
            // drop the positions above the one we're deleting down . . .
            MapSqlParameterSource namedParams = new MapSqlParameterSource();
            namedParams.addValue(PARENTID, parent.getId());
            namedParams.addValue(CHILDID, child.getId());
            namedParams.addValue(POSITION, existingPosition);
            final String SHIFT_POSITIONS = "UPDATE `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = (`" + POSITION + "` - 1) where `" + PARENTID + "` = :" + PARENTID + " AND `" + POSITION + "` > :" + POSITION;
            jdbcTemplate.update(SHIFT_POSITIONS, namedParams);
            String updateSql = "DELETE from `" + totoMemoryDB.getDatabaseName() + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID + " and `" + CHILDID + "` = :" + CHILDID + "";
            return jdbcTemplate.update(updateSql, namedParams);
            // TODO : need to set that the look up tables need to be rebuilt appropriately
        }
        return 0; // nothing deleted
    }

}
