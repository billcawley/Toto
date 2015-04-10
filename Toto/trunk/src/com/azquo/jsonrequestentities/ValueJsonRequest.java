package com.azquo.jsonrequestentities;

/**
 * Created by bill on 03/03/14.
 *
 */
public class ValueJsonRequest {
         public String rowheadings;
         public String columnheadings;
         public String context;
         public String region;
         public String lockmap;
         public String editeddata;
         public String searchbynames;
         public String jsonfunction;
         public String user;
         public String password;
         public String filtercount;//batch count of lines. If entire batch is empty it will be ignored   RowHeadings only
         public String restrictcount; //show the top n rows by value (or bottom -n rows if n is negative)  RowHeadings only
         public String spreadsheetname;
         public String database;
}