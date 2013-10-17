package com.azquo.toto.dao;

import com.azquo.toto.entity.Label;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:19
 * DAO for Labels, since labels will be part of set structures sql in here could get hairy
 */
public class LabelDAO extends StandardDAO {
    /*public List getSuppliersForCascadingAttributeSetId(int id) throws DataAccessException {
        final String whereCondition = " where cascading_attribute_set_id = :cascading_attribute_set_id";
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("cascading_attribute_set_id", id);
        return findListWithWhereSQLAndParameters(new Merchant(),whereCondition,namedParams,true);
    }*/

    public Label getByName(final String name) throws DataAccessException {
        final String whereCondition = " where name = :name";
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue("name", name);
        return (Label)findOneWithWhereSQLAndParameters(new Label(),whereCondition,namedParams,true);
    }
}
