package com.azquo.toto.dao;

import com.azquo.toto.entity.Label;
import com.azquo.toto.entity.StandardEntity;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:19
 * DAO for Labels, since labels will be part of set structures sql in here could get hairy
 */
public class LabelDAO extends StandardDAO {

    // associated table names, currently think here is a good place to put them. Where they're used.

    public static final String LABELSETDEFINITION = "label_set_definition";
    public static final String PARENTID = "parent_id";
    public static final String CHILDID = "child_id";
    public static final String POSITION = "position";

    // I think I'm going to specific store for Label due to unique name constraints
    // simply ignore stores where the id and label exist otherwise throw exception for existing label whether updating or adding
    public void store(final Label label) throws DataAccessException {
        Label existing = findByName(label.getName());
        if (existing == null){ // no name exists, can store new label or update one
            super.store(label);
        } else { // it does exist
            if (existing.getId() != label.getId()){
                throw new InvalidDataAccessApiUsageException("Error, the label " + label.getName() + " already exists");
            }
            // otherwise storing a name ID combo that's in the Db already, do nothing!
        }
    }

    // TODO : make delete clear up label set definition and flags for label set lookup

    public Label findByName(final String name) throws DataAccessException {
        final String whereCondition = " where `" + Label.NAME + "` = :" + Label.NAME;
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(Label.NAME, name);
        return (Label)findOneWithWhereSQLAndParameters(new Label(),whereCondition,namedParams,false);
    }

    public int getMaxChildPosition(final Label l) throws DataAccessException {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(PARENTID, l.getId());
        final String FIND_BY_ID = "Select max(" + POSITION + ") from `" + LABELSETDEFINITION + "` where `" + PARENTID + "` = :" + PARENTID;
        Integer integer =  (Integer)jdbcTemplate.queryForObject(FIND_BY_ID, namedParams, Integer.class);
        return (integer == null ? 0 : integer.intValue()); // no records means we return 0 as the max position
    }

    public void linkParentAndChild(final Label parent, final Label child, int position) throws DataAccessException {
        // Clear the link if it exists. Maybe better to check if it exists in future
        // Something TODO, it will have the benefit that we won't need to set the look up tables need to be rebuilt flags - what about position change on a link if we do this??
        unlinkParentAndChild(parent,child);
        // now make the link
        MapSqlParameterSource namedParams = new MapSqlParameterSource(); // clear it
        String updateSql = "INSERT INTO `" + LABELSETDEFINITION + "` (`" + PARENTID + "`,`" + CHILDID + "`,`" + POSITION + "`) VALUES (:" + PARENTID + ",:" + CHILDID + ",:" + POSITION + ")";
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        namedParams.addValue(POSITION, position);
        jdbcTemplate.update(updateSql, namedParams);
        // need to set that the look up tables need to be rebuilt appropriately
    }

    public void unlinkParentAndChild(final Label parent, final Label child) throws DataAccessException {
        MapSqlParameterSource namedParams = new MapSqlParameterSource();
        String updateSql = "DELETE from `" + LABELSETDEFINITION + "` where `" + PARENTID + "` = :" + PARENTID + " and `" + CHILDID + "` = :" + CHILDID + "";
        namedParams.addValue(PARENTID, parent.getId());
        namedParams.addValue(CHILDID, child.getId());
        jdbcTemplate.update(updateSql, namedParams);
        // need to set that the look up tables need to be rebuilt appropriately
    }
}
