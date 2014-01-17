package com.azquo.toto.jsonrequestentities;

/**
 * Created by cawley on 17/01/14.
 */
public class NameJsonRequest extends StandardJsonRequest {
    public String name;
    public int newParent = 0;
    public int oldParent = 0;
    public int newPosition = 0;
    public String attributes;
    public boolean withData = false;
}
