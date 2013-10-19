package com.azquo.toto.entity;

import org.springframework.jdbc.core.RowMapper;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:23
 *
 * OK, this is similar to the proposed new Feefo pattern (if it's ever used!) but no sharding for the moment
 * Adding sharding may not be too difficult.
 *
 */

public abstract class StandardEntity {

    public static final String ID = "id";

    protected int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public abstract String getTableName();

    public abstract Map<String, Object> getColumnNameValueMap();

    public abstract RowMapper<? extends StandardEntity> getRowMapper();

}
