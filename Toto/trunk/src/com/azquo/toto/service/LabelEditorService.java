package com.azquo.toto.service;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.entity.Label;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 14:18
 * To change this template use File | Settings | File Templates.
 */
public class LabelEditorService {

    @Autowired
    private LabelDAO labelDAO;

    public Label findByName(String name){
        return labelDAO.findByName(name);
    }
}
