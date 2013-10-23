package com.azquo.toto.dao;

import com.azquo.toto.entity.Label;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
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
 * DAO for Labels, since labels will be part of set structures sql in here could get hairy
 */
public class LabelDAO extends StandardDAO<Label> {

    // the default table name for this data.
    @Override
    public String getTableName() {
        return "label";
    }

    // column names (except ID)

    public static final String NAME = "name";
    public static final String LABELSETLOOKUPNEEDSREBUILDING = "label_set_lookup_needs_rebuilding";


    // associated table names, currently think here is a good place to put them. Where they're used.

    public enum SetDefinitionTable {label_set_definition, peer_set_definition}

    // TODO : support for prefixes or overrides on label_set_definition, peer_set_definition

    public static final String PARENTID = "parent_id";
    public static final String CHILDID = "child_id";
    public static final String POSITION = "position";


    @Override
    public Map<String, Object> getColumnNameValueMap(final Label label){
        final Map<String, Object> toReturn = new HashMap<String, Object>();
        toReturn.put(ID, label.getId());
        toReturn.put(NAME, label.getName());
        toReturn.put(LABELSETLOOKUPNEEDSREBUILDING, label.getLabelSetLookupNeedsRebuilding());
        return toReturn;
    }

    public static final class LabelRowMapper implements RowMapper<Label> {
        @Override
        public Label mapRow(final ResultSet rs, final int row) throws SQLException {
            final Label label = new Label();
            label.setId(rs.getInt(ID));
            label.setName(rs.getString(NAME));
            label.setLabelSetLookupNeedsRebuilding(rs.getBoolean(LABELSETLOOKUPNEEDSREBUILDING));
            return label;
        }
    }

    @Override
    public RowMapper<Label> getRowMapper() {
        return new LabelRowMapper();
    }
    // I think I'm going to specific store for Label due to unique name constraints
    // simply ignore stores where the id and label exist otherwise throw exception for existing label whether updating or adding
    public void store(final Label label, String databaseName) throws DataAccessException {
        if (label.getName().contains(";")) {
            throw new InvalidDataAccessApiUsageException("Error, label name cannot contain ; :  " + label.getName());
        }
        Label existing = findByName(databaseName, label.getName());
        if (existing == null) { // no name exists, can store new label or update one
            super.store(databaseName, label);
        } else { // it does exist
            if (existing.getId() != label.getId()) {
                throw new InvalidDataAccessApiUsageException("Error, the label " + label.getName() + " already exists");
            }
            // otherwise storing a name ID combo that's in the Db already, do nothing!
        }
    }

    // TODO : make delete clear up label set definition ("compress" positions also) and flags for label set lookup
    // ok this will unlink everywhere then delete . . . or not?

/*    public void remove(final Label label) throws DataAccessException {
        List<Label> parents = findParents(label, SetDefinitionTable.label_set_definition);
        for (Label parent : parents){
            unlinkParentAndChild(parent, label, SetDefinitionTable.label_set_definition);
        }
        List<Label> children = findChildren(label, false, SetDefinitionTable.label_set_definition);
        for (Label parent : parents){
            unlinkParentAndChild(parent, label, SetDefinitionTable.label_set_definition);
        }
    }*/

    public Label findByName(String databaseName, final String name) throws DataAccessException {
        final String whereCondition = " where `" + NAME + "` = :" + NAME;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(NAME, name);
        return findOneWithWhereSQLAndParameters(databaseName, whereCondition, namedParams, false);
    }

/*    public List<Label> findChildren(final Label label) throws DataAccessException {
        return findChildren(label, false, LABELSETDEFINITION);
    }*/

/*    public List<Label> findChildren(final Label label, final boolean sorted) throws DataAccessException {
        return findChildren(label, sorted, LABELSETDEFINITION);
    }*/

    // should this be public? I want the service to have direct access as the code will be normalised better . . .
    public List<Label> findChildren(String databaseName, final SetDefinitionTable setDefinitionTable, final Label label, final boolean sorted) throws DataAccessException {
        final String whereCondition = ", `" + databaseName + "`.`" + setDefinitionTable + "` where `" + getTableName() + "`." + ID + " = `" + setDefinitionTable + "`.`" + CHILDID + "` AND `" + setDefinitionTable + "`.`" + PARENTID + "` = :" + PARENTID + (sorted ? " order by `" + NAME + "`" : " order by `" + POSITION + "`");
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, label.getId());
        return findListWithWhereSQLAndParameters(databaseName, whereCondition, namedParams, false);
    }

    public List<Label> findChildren(String databaseName, final SetDefinitionTable setDefinitionTable, final Label label, final int from, final int to) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, label.getId());
        if (from != -1) {
            namedParams.addValue("from", from);
        }
        if (to != -1) {
            namedParams.addValue("to", to);
        }
        final String whereCondition = ", `" + databaseName + "`.`" + setDefinitionTable + "` where `" + getTableName() + "`." + ID + " = `" + setDefinitionTable + "`.`" + CHILDID + "` AND `" + setDefinitionTable + "`.`" + PARENTID + "` = :" + PARENTID + (from != -1 ? " AND `" + setDefinitionTable + "`.`" + POSITION + "` >= :from" : "") + (to != -1 ? " AND `" + setDefinitionTable + "`.`" + POSITION + "` <= :to" : "") + " order by `" + POSITION + "`";
        return findListWithWhereSQLAndParameters(databaseName, whereCondition, namedParams, false);
    }

/*    public List<Label> findParents(final Label label, final SetDefinitionTable setDefinitionTable) throws DataAccessException {
        final String whereCondition = ", `" + setDefinitionTable + "` where `" + getTableName() + "`." + ID + " = `" + setDefinitionTable + "`.`" + PARENTID + "` AND `" + setDefinitionTable + "`.`" + CHILDID + "` = :" + CHILDID;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(CHILDID, label.getId());
        return findListWithWhereSQLAndParameters(whereCondition, namedParams, false);
    }*/

    public int getMaxChildPosition(final String databaseName, final SetDefinitionTable setDefinitionTable, final Label l) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, l.getId());
        final String FIND_MAX_POSITION = "Select max(" + POSITION + ") from `" + databaseName + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID;
        Integer integer = jdbcTemplate.queryForObject(FIND_MAX_POSITION, namedParams, Integer.class);
        return (integer == null ? 0 : integer); // no records means we return 0 as the max position
    }

    public int getChildPosition(final String databaseName, final SetDefinitionTable setDefinitionTable, final Label parent, final Label child) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        final String FIND_EXISTING_LINK_POSITION = "Select `" + POSITION + "` from `" + databaseName + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID + " AND `" + CHILDID + "` = :" + CHILDID;
        List<Integer> existingPositionResult = jdbcTemplate.queryForList(FIND_EXISTING_LINK_POSITION, namedParams, Integer.class);
        if (!existingPositionResult.isEmpty()) {
            return existingPositionResult.get(0);// the compiler allows this?? Cool :)
        } else {
            return -1; // we'll call -1 not existing
        }
    }

    public boolean linkParentAndChild(final String databaseName, final SetDefinitionTable setDefinitionTable, final Label parent, final Label child, int position) throws DataAccessException {
        // init the parameters at the beginning, we can reuse them for all possible queries I think . . . .nice
        int existingMaxPosition = getMaxChildPosition(databaseName, setDefinitionTable, parent);
        if (position > (existingMaxPosition + 1)) { // if the position is greater than one above the top the chop it down
            position = existingMaxPosition + 1;
        }
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        namedParams.addValue(POSITION, position);
        //int numberDeleted = unlinkParentAndChild(parent,child);
        // Check if the link exists and if so at what position, The DB should NOT allow duplicate links!
        int existingPosition = getChildPosition(databaseName, setDefinitionTable, parent, child);
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
                    final String SHIFT_POSITIONS = "UPDATE `" + databaseName + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = (`" + POSITION + "` - 1) where `" + PARENTID + "` = :" + PARENTID + " AND `" + POSITION + "` > :EXISTINGPOSITION AND `" + POSITION + "`<= :" + POSITION;
                    jdbcTemplate.update(SHIFT_POSITIONS, namedParams);
                } else { // this child is moving down in position, positions will have to be shifted up!
                    final String SHIFT_POSITIONS = "UPDATE `" + databaseName + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = (`" + POSITION + "` + 1) where `" + PARENTID + "` = :" + PARENTID + " AND `" + POSITION + "` < :EXISTINGPOSITION AND `" + POSITION + "`>= :" + POSITION;
                    jdbcTemplate.update(SHIFT_POSITIONS, namedParams);
                }
            }
            // now move the existing which will right now have two links on that position
            final String SHIFT_POSITION = "UPDATE `" + databaseName + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = :" + POSITION + " where `" + PARENTID + "` = :" + PARENTID + " AND `" + CHILDID + "` = :" + CHILDID;
            jdbcTemplate.update(SHIFT_POSITION, namedParams);
        } else { // insert a new one, shift positions up if necessary and sort out the lookup flags . . . .
            // TODO : check for circular references!
            if (position < (existingMaxPosition + 1)) {// not right at the top, some positions need to be moved up
                final String SHIFT_POSITIONS = "UPDATE `" + databaseName + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = (`" + POSITION + "` + 1) where `" + PARENTID + "` = :" + PARENTID + " AND `" + POSITION + "`>= :" + POSITION;
                jdbcTemplate.update(SHIFT_POSITIONS, namedParams);
            }
            // now make the link
            String updateSql = "INSERT INTO `" + databaseName + "`.`" + setDefinitionTable + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES (:" + PARENTID + ",:" + CHILDID + ",:" + POSITION + ")";
            jdbcTemplate.update(updateSql, namedParams);
            // TODO : lookup flags
        }
        return true;
    }

    public int unlinkParentAndChild(final String databaseName, final SetDefinitionTable setDefinitionTable, final Label parent, final Label child) throws DataAccessException {
        int existingPosition = getChildPosition(databaseName, setDefinitionTable, parent, child);
        if (existingPosition != -1) { // we have something to do!
            // drop the positions above the one we're deleting down . . .
            MapSqlParameterSource namedParams = new MapSqlParameterSource();
            namedParams.addValue(PARENTID, parent.getId());
            namedParams.addValue(CHILDID, child.getId());
            namedParams.addValue(POSITION, existingPosition);
            final String SHIFT_POSITIONS = "UPDATE `" + databaseName + "`.`" + setDefinitionTable + "` set `" + POSITION + "` = (`" + POSITION + "` - 1) where `" + PARENTID + "` = :" + PARENTID + " AND `" + POSITION + "` > :" + POSITION;
            jdbcTemplate.update(SHIFT_POSITIONS, namedParams);
            String updateSql = "DELETE from `" + databaseName + "`.`" + setDefinitionTable + "` where `" + PARENTID + "` = :" + PARENTID + " and `" + CHILDID + "` = :" + CHILDID + "";
            return jdbcTemplate.update(updateSql, namedParams);
            // TODO : need to set that the look up tables need to be rebuilt appropriately
        }
        return 0; // nothing deleted
    }

}
