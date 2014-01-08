package com.azquo.toto.adminentities;

/**
 * Created by cawley on 07/01/14.
 * <p/>
 * like the totmemory db one but more vanilla like ol feefo
 * <p/>
 * currently just defines id but I think that's fine, will still be useful for abstraction
 */
public abstract class StandardEntity {

    protected int id;
    // no setter for id, that should only be done by the constructor

    public final int getId() {
        return id;
    }

    public final void setId(int id) {
        this.id = id;
    }

}
