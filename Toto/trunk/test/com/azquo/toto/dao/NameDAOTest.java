package com.azquo.toto.dao;

import com.azquo.toto.memorydb.Name;
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
 * Time: 18:11
 * Actually this function is in the standard dao test currently but no harm in putting it here too
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})
public class NameDAOTest {

    @Autowired
    private NameDAO nameDao;

    @Autowired
    private TotoMemoryDB totoMemoryDB;

    public void clearEddtestLabels(){
    }

    @Before
    public void setUp() throws Exception {
        clearEddtestLabels();
    }

/*    @Test
    public void testColumnNameValueMap() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        Assert.assertTrue(nameDao.getColumnNameValueMap(l).keySet().size() == 3);
    }

    @Test
    public void testStore() throws Exception {
        // make label 1 and 2
        Name l1 = new Name();
        l1.setName("eddtest");
        nameDao.store(databaseName,l1);
        Name l2 = new Name();
        l2.setName("eddtest2");
        nameDao.store(databaseName,l2);



        // now try tyo make a new one with an existing label
        l2 = new Name();
        l2.setName("eddtest");
        // this should throw an exception
        try{
            nameDao.store(databaseName,l2);
            Assert.fail("the DAO should have thrown an exception by now");
        } catch (DataAccessException dae){
            System.out.println(dae.getMessage());
        }


        // now try to update an existing one with another existing label
        l2 = nameDao.findByName(databaseName,"eddtest2");
        l2.setName("eddtest");
        // this should throw an exception but this time by an update
        try{
            nameDao.store(databaseName,l2);
            Assert.fail("the DAO should have thrown an exception by now");
        } catch (DataAccessException dae){
            System.out.println(dae.getMessage());
        }


        clearEddtestLabels();
    }

    @Test
    public void testRemove() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName, l);
        Name l2 = new Name();
        l2.setName("eddtest1");
        nameDao.insert(databaseName, l2);
        // this should clean links first
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition, l,l2, 0);
        nameDao.remove(databaseName, l);
        nameDao.remove(databaseName, l2);
    }

    @Test
    public void testFindByName() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName, l);
        Assert.assertTrue(nameDao.findByName(databaseName, l.getName()) != null);
        nameDao.removeById(databaseName, l);
        Assert.assertTrue(nameDao.findByName(databaseName, l.getName()) == null);
    }

    @Test
    public void testFindChildren() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName,l);
        Name l2 = new Name();
        l2.setName("eddtest1");
        nameDao.insert(databaseName, l2);
        Name l3 = new Name();
        l3.setName("eddtest3");
        nameDao.insert(databaseName, l3);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l, l2, 0);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l, l3, 0);
        Assert.assertTrue(nameDao.findChildren(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l,false).size() == 2);
        nameDao.remove(databaseName, l2);
        nameDao.remove(databaseName, l3);
        Assert.assertTrue(nameDao.findChildren(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l, false).size() == 1);
        nameDao.remove(databaseName, l);
    }

    // will test find parents too
    @Test
    public void testFindAllParents() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName,l);
        Name l2 = new Name();
        l2.setName("eddtest1");
        nameDao.insert(databaseName, l2);
        Name l3 = new Name();
        l3.setName("eddtest3");
        nameDao.insert(databaseName, l3);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l, l2, 0);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l2, l3, 0);
        Assert.assertTrue(nameDao.findAllParents(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l3).size() == 2);
        nameDao.remove(databaseName, l2);
        nameDao.remove(databaseName, l);
        Assert.assertTrue(nameDao.findAllParents(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l3).size() == 0);
        nameDao.remove(databaseName, l3);
    }

    // will test find parents too
    @Test
    public void testFindAllChildren() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName,l);
        Name l2 = new Name();
        l2.setName("eddtest1");
        nameDao.insert(databaseName, l2);
        Name l3 = new Name();
        l3.setName("eddtest3");
        nameDao.insert(databaseName, l3);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l, l2, 0);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l2, l3, 0);
        Assert.assertTrue(nameDao.findAllChildren(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l).size() == 2);
        nameDao.remove(databaseName, l2);
        nameDao.remove(databaseName, l3);
        Assert.assertTrue(nameDao.findAllChildren(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l).size() == 0);
        nameDao.remove(databaseName, l);
    }

    @Test
    public void testMaxPosition() throws Exception {
        Name l1 = new Name();
        l1.setName("eddtest");
        nameDao.store(databaseName,l1);
        Name l2 = new Name();
        l2.setName("eddtest2");
        nameDao.store(databaseName,l2);
        System.out.println("max position when there's no children : " + nameDao.getMaxChildPosition(databaseName, NameDAO.SetDefinitionTable.label_set_definition, l1));
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition, l1, l2, 2);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l1, l2, 4);
        System.out.println("max position when there's children : " + nameDao.getMaxChildPosition(databaseName, NameDAO.SetDefinitionTable.label_set_definition, l1));
        // need too sys out println to confirm the link here
        nameDao.unlinkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l1, l2);
        clearEddtestLabels();
    }

    @Test
    public void testLinking() throws Exception {
        Name l1 = new Name();
        l1.setName("eddtest");
        nameDao.store(databaseName, l1);
        Name l2 = new Name();
        l2.setName("eddtest2");
        nameDao.store(databaseName, l2);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition, l1, l2, 0);
        nameDao.linkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition,l1,l2,0);
        // need too sys out println to confirm the link here
        nameDao.unlinkParentAndChild(databaseName, NameDAO.SetDefinitionTable.label_set_definition, l1, l2);
        clearEddtestLabels();
    }
  */
}
