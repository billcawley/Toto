package com.azquo.spreadsheet.transport;

import java.io.Serializable;
import java.util.List;

/**
 * Created by edward on 17/01/17.
 *
 * TreeNode was being used for displaying provenance, this made no sense, this class should fix that.
 */
public class ProvenanceDetailsForDisplay implements Serializable {

    // if the cell is from a function say what it is
    final String function;

    final List<ProvenanceForDisplay> procenanceForDisplayList;
    public ProvenanceDetailsForDisplay(String function, List<ProvenanceForDisplay> procenanceForDisplayList) {
        this.function = function;
        this.procenanceForDisplayList = procenanceForDisplayList;
    }

    public String getFunction() {
        return function;
    }

    public List<ProvenanceForDisplay> getProcenanceForDisplayList() {
        return procenanceForDisplayList;
    }
}