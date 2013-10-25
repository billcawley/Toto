package com.azquo.toto.memorydb;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.dao.ProvenanceDAO;
import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.entity.Label;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 10:33
 * OK, SQL isn't fast enough, it will be persistence but not much more. Need to think about how a Toto memory DB would work
 */
public class TotoMemoryDB {

    @Autowired
    private ValueDAO valueDAO;
    @Autowired
    private LabelDAO labelDAO;
    @Autowired
    private ProvenanceDAO provenanceDAO;


    private HashMap<String, Label> labelMap;

    private boolean readyToGo;

    public TotoMemoryDB(){
        boolean readyToGo = false;
    }

    public void loadData(){
        // here we'll populate the memory DB from the database

//        List<Label> =


        readyToGo = true;
    }

}
