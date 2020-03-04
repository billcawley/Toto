package com.azquo.admin;

import java.io.Serializable;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 07/01/14.
 * the objects relating to the master db extend this
 * currently just defines id but I think that's fine, will still be useful for abstraction
 *
 * If not serializable then classes which extend it which are serializable might end up with id being reset to 0. Found this out the hard way.
 */
public abstract class StandardEntity implements Serializable {

    protected int id;

    public final int getId() {
        return id;
    }

    public final void setId(int id) {
        this.id = id;
    }

}