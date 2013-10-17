package com.azquo.toto.service;

import com.azquo.toto.dao.LabelDAO;
import com.azquo.toto.entity.Label;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 18:50
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})
public class LabelEditorServiceTest {

    @Autowired
    LabelEditorService labelEditorService;
    @Autowired
    LabelDAO labelDao;

    @Before
    public void setUp() throws Exception {
        Label l = labelDao.findByName("eddtest");
        if (l != null){
            labelDao.removeById(l);
        }
    }

    @After
    public void tearDown() throws Exception {
        Label l = labelDao.findByName("eddtest");
        if (l != null){
            labelDao.removeById(l);
        }
    }

    @Test
    public void testGetByName() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(l);
        Assert.assertTrue(labelEditorService.findByName(l.getName()) != null);
        labelDao.removeById(l);
        Assert.assertTrue(labelEditorService.findByName(l.getName()) == null);
    }
}
