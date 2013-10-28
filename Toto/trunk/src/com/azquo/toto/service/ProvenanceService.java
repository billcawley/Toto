package com.azquo.toto.service;

import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 28/10/13
 * Time: 10:55
 * To change this template use File | Settings | File Templates.
 */
public class ProvenanceService {

    @Autowired
    private TotoMemoryDB totoMemoryDB;

    public Provenance getTestProvenance() throws Exception {
        return new Provenance(totoMemoryDB, "testuser", new java.util.Date(),"testimport", "testuser","rowheadings", "columnheadings", "context");
    }

}
