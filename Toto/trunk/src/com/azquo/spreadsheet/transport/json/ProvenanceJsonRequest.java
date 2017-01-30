package com.azquo.spreadsheet.transport.json;

import java.io.Serializable;

/**
 * Created by edward on 30/11/16.
 */
public class ProvenanceJsonRequest implements Serializable {
    public int reportId;
    public String region;
    public int row;
    public int col;
}
