package com.azquo.spreadsheet.transport;

import java.io.Serializable;
import java.util.List;

/**
 * Created by edward on 17/01/17.
 *
 * TreeNode was being used for displaying provenance, this made no sense, this class should fix that.
 */
public class ProvenanceDetailsForDisplay implements Serializable {

    // show the cell's value and the context and headings
    final String headline;
    // if the cell is from a function say what it is
    final String function;

    final List<ProvenanceForDisplay> auditForDisplayList;
    public ProvenanceDetailsForDisplay(String headline, String function, List<ProvenanceForDisplay> auditForDisplayList) {
        this.headline = headline;
        this.function = function;
        this.auditForDisplayList = auditForDisplayList;
    }

    public String getHeadline() {
        return headline;
    }

    public String getFunction() {
        return function;
    }

    public List<ProvenanceForDisplay> getAuditForDisplayList() {
        return auditForDisplayList;
    }


}