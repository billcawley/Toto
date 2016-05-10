package com.azquo.spreadsheet.jsonentities;

import java.io.Serializable;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 17/01/14
 *
 * Just used by JSTree, cutting things out,
 *
 */
public class NameJsonRequest implements Serializable {
    public String operation;
    public int id = 0;
    public Map<String, String> attributes;
}
