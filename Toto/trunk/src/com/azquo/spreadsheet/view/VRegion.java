package com.azquo.spreadsheet.view;

/**
* Created by cawley on 20/02/15
 *
 * Extracted by edd from Azquo book. Makes that class a little smaller and I want a green light on it.
 * IntelliJ so far can't make the connection between the velocity file and functions in here. Aside from the aforementioned green light
 * it would enable checking on the vm file. Would probably be one of the benefits of a more "proper" use of velocity.
*/
public class VRegion {
    String name;
    String maxrows;

    String maxcols;
    String hiderows;
    String sortable;
    String rowdata;
    String coldata;
    String contextdata;

    public String getname() {
        return name;
    }

    public String getmaxrows() {
        return maxrows;
    }

    public String getmaxcols() {
        return maxcols;
    }

    public String gethiderows() {
        return hiderows;
    }

    public String getsortable() {
        return sortable;
    }

    public String getrowdata() {
        return rowdata;
    }

    public String getcoldata() {
        return coldata;
    }

    public String getcontextdata() {
        return contextdata;
    }


}
