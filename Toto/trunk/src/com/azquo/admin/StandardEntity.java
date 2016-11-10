package com.azquo.admin;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 07/01/14.
 * the objects relating to the master db extend this
 * currently just defines id but I think that's fine, will still be useful for abstraction
 */
public abstract class StandardEntity {

    protected int id;

    public final int getId() {
        return id;
    }

    public final void setId(int id) {
        this.id = id;
    }

}