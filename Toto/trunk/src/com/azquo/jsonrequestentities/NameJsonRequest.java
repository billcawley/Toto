package com.azquo.jsonrequestentities;

import java.util.Map;

/**
 * Created by cawley on 17/01/14
 * to be used by the name controller and spreadsheet, easy way to parse the sent json with a jackson mapper
 */
public class NameJsonRequest extends StandardJsonRequest {
    public String name;
    public int id = 0;
    public int newParent = 0;
    public int oldParent = 0;
    public int newPosition = 0;
    public Map<String, String> attributes;
    public boolean withData = false;
}
