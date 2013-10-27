package com.azquo.toto.dao;

import com.azquo.toto.entity.Name;
import com.azquo.toto.memorydb.TotoMemoryDB;
import org.junit.Before;
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
    private NameDAO nameDao;

    @Autowired
    private TotoMemoryDB totoMemoryDB;

    @Before
    public void setUp() throws Exception {
        Name n = nameDao.findByName(totoMemoryDB, "eddtest");
        if (n != null){
            nameDao.removeById(totoMemoryDB,n);
        }
    }
/*
    @Test
    public void testUpdateById() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.store(databaseName,l);
        l.setName("thing");
        nameDao.updateById(databaseName,l);
        nameDao.removeById(databaseName,l);
    }

    @Test
    public void testInsert() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName,l);
        nameDao.removeById(databaseName,l);
    }


    @Test
    public void testStore() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.store(databaseName,l);
        l.setName("thing");
        nameDao.store(databaseName,l);
        nameDao.removeById(databaseName,l);
    }

    @Test
    public void testFindById() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName,l);
        Assert.assertTrue(nameDao.findById(databaseName,l.getId()) != null);
        nameDao.removeById(databaseName,l);
    }

    @Test
    public void testFindAll() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName,l);
        Assert.assertTrue(nameDao.findAll(databaseName).size() > 0);
        nameDao.removeById(databaseName,l);
    }

    // deleted find list test as find all does that.

    // right now this one is dealt with in the set up, it may not be in future

    @Test
    public void testFindOneWithWhereSQLAndParameters() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName,l);
        l = nameDao.findByName(databaseName,l.getName());
        nameDao.removeById(databaseName,l);
    }*/
}
