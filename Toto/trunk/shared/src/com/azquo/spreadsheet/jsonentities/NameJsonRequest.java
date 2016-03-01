package com.azquo.spreadsheet.jsonentities;

import java.io.Serializable;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 17/01/14
 * to be used by the name controller and spreadsheet, easy way to parse the sent json with a jackson mapper
 *
 * Another class facilitating the database view and editing.
 *
 */
public class NameJsonRequest implements Serializable {
    public String user;
    public String password;
    public String database;
    public String operation;
    public String name;
    public int id = 0;
    public int newParent = 0;
    public int oldParent = 0;
    public int newPosition = 0;
    public Map<String, String> attributes;
    public boolean withData = false;
}
