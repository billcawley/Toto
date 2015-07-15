package com.azquo.spreadsheet.jsonentities;

import java.io.Serializable;

/**
 * Created by cawley on 17/01/14
 * All json requests will have at leas these fields
 */
public abstract class StandardJsonRequest implements Serializable {

    public String user;
    public String password;
    public String database;
    public String operation;
    public String jsonFunction;
    public String spreadsheetName;

}
