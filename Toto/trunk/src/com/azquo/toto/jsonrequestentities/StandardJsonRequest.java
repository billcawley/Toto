package com.azquo.toto.jsonrequestentities;

/**
 * Created by cawley on 17/01/14
 * All json requests will have at leas these fields
 */
public abstract class StandardJsonRequest {

    public String user;
    public String password;
    public String database;
    public String connectionId;
    public String operation;
    public String jsonFunction;
    public String spreadsheetName;

}
