package com.azquo.spreadsheet.transport;

/*

Created 25th June 2019

Trying to move away from the generic typed pair

 */

import java.io.Serializable;

public class HeadingWithInterimLookup implements Serializable {
    private String heading;
    private String interimLookup;

    public HeadingWithInterimLookup(String heading, String interimLookup) {
        this.heading = heading;
        this.interimLookup = interimLookup;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading){
        this.heading = heading;
    }

    public String getInterimLookup() {
        return interimLookup;
    }
}
