package com.azquo.toto.service;

import com.azquo.toto.memorydb.Provenance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 28/10/13
 * Time: 10:55
 * Do we actually need a provenance service??
 */
public final class ProvenanceService {



    public Provenance getTestProvenance(LoggedInConnection loggedInConnection) throws Exception {
        //return new Provenance(totoMemoryDB, "testuser", new java.util.Date(),"testimport", "testuser","rowheadings", "columnheadings", "context");
        return loggedInConnection.getTotoMemoryDB().getProvenanceById(1);
    }

}
