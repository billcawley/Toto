package com.azquo.toto.entity;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 09:23
 *
 * OK, this is similar to the proposed new Feefo pattern (if it's ever used!) but no sharding for the moment
 * Adding sharding may not be too difficult.
 *
 * Update after some thinking and learning about generics : entity objects should have as little reference to the database as possible.
 * Hence I'm going to move the row mapper, table name and column name value map out of here. All we can really say about an Entity at the
 * moment is that it has an id.
 *
 */

public abstract class StandardEntity{

    protected int id;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

}
