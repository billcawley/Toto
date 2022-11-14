package com.azquo.spreadsheet.transport.json;

import java.io.Serializable;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 17/01/14
 * <p>
 * Just used by JSTree, cutting things out, now just used to set attributes I think. No longer shared.
 */
public class NameJsonRequest implements Serializable {
    public int id = 0;
    public Map<String, String> attributes;
}