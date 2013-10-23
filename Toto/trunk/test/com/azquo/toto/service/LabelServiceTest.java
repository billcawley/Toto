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

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 18:50
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})
public class LabelServiceTest {

    @Autowired
    LabelService labelService;
    @Autowired
    LabelDAO labelDao;
/*
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
        Assert.assertTrue(labelService.findByName(l.getName()) != null);
        labelDao.removeById(l);
        Assert.assertTrue(labelService.findByName(l.getName()) == null);
    }

    @Test
    public void testFindOrCreate() throws Exception {
        Label l = labelService.findOrCreateLabel("eddtest");
        l = labelService.findOrCreateLabel("eddtest");
        labelDao.removeById(l);
    }

    @Test
    public void testFindChildrenAtLevel() throws Exception {
        labelService.findChildrenAtLevel(labelService.findOrCreateLabel("eddtest"), 2);
        labelDao.removeById(labelService.findOrCreateLabel("eddtest"));
    }

    @Test
    public void testCreateMembers() throws Exception {
        Label l = labelService.findOrCreateLabel("eddtest");
        List<String> tocreate = new ArrayList<String>();
        tocreate.add("test1");
        tocreate.add("test2");
        labelService.createMembers(l, tocreate);
        labelService.removeMember(l, "test1");
        labelService.removeMember(l, "test2");
        labelDao.removeById(labelDao.findByName("test1"));
        labelDao.removeById(labelDao.findByName("test2"));
        labelDao.removeById(l);
    }

    @Test
    public void testCreateMember() throws Exception {
        Label l = labelService.findOrCreateLabel("eddtest");
        labelService.createMember(l, "test1", null,1);
        labelService.removeMember(l, "test1");
        labelDao.removeById(labelDao.findByName("test1"));
        labelDao.removeById(l);
    }

    @Test
    public void testRename() throws Exception {
        labelService.findOrCreateLabel("eddtest");
        labelService.renameLabel("eddtest", "eddtest1");
        Assert.assertTrue(labelService.findByName("eddtest1") != null);
        labelDao.removeById(labelService.findByName("eddtest1"));
    }
*/
    //rename
}
