package com.azquo.toto.dao;

import com.azquo.toto.entity.Label;
import junit.framework.Assert;
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
 * Time: 18:11
 * Actually this function is in the standard dao test currently but no harm in putting it here too
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})
public class LabelDAOTest {

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
    public void testFindByName() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(l);
        Assert.assertTrue(labelDao.findByName(l.getName()) != null);
        labelDao.removeById(l);
        Assert.assertTrue(labelDao.findByName(l.getName()) == null);
    }
}
