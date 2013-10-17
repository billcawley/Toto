package com.azquo.toto.dao;

import com.azquo.toto.entity.Label;
import org.junit.Assert;
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
 * Time: 15:55
 * StandardDAO is abstract so pick a simple implementation and hit the inherited functions, tests for other DAOs can just be the extra functions
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})

public class StandardDAOTest {

    @Autowired
    private LabelDAO labelDao;

    @Before
    public void setUp() throws Exception {
        Label l = labelDao.findByName("eddtest");
        if (l != null){
            labelDao.removeById(l);
        }
    }

    @Test
    public void testUpdateById() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.store(l);
        l.setName("thing");
        labelDao.updateById(l);
        labelDao.removeById(l);
    }

    @Test
    public void testInsert() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(l);
        labelDao.removeById(l);
    }

/*    @Test
    public void testInsert() throws Exception {

    }*/

    @Test
    public void testStore() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.store(l);
        l.setName("thing");
        labelDao.store(l);
        labelDao.removeById(l);
    }

    @Test
    public void testFindById() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(l);
        Assert.assertTrue(labelDao.findById(l) != null);
        labelDao.removeById(l);
    }

    @Test
    public void testFindAll() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(l);
        Assert.assertTrue(labelDao.findAll(l).size() > 0);
        labelDao.removeById(l);
    }

    // deleted find list test as find all does that.

    // right now this one is dealt with in the set up, it may not be in future

    @Test
    public void testFindOneWithWhereSQLAndParameters() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(l);
        l = labelDao.findByName(l.getName());
        labelDao.removeById(l);
    }
}
