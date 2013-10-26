package com.azquo.toto.dao;

import com.azquo.toto.entity.Label;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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

    String databaseName = "tototest";

    public void clearEddtestLabels(){
        Label l = labelDao.findByName(databaseName,"eddtest");
        if (l != null){
            labelDao.removeById(databaseName,l);
        }
        Label l2 = labelDao.findByName(databaseName,"eddtest2");
        if (l2 != null){
            labelDao.removeById(databaseName,l2);
        }
    }

    @Before
    public void setUp() throws Exception {
        clearEddtestLabels();
    }

/*    @Test
    public void testColumnNameValueMap() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        Assert.assertTrue(labelDao.getColumnNameValueMap(l).keySet().size() == 3);
    }

    @Test
    public void testStore() throws Exception {
        // make label 1 and 2
        Label l1 = new Label();
        l1.setName("eddtest");
        labelDao.store(databaseName,l1);
        Label l2 = new Label();
        l2.setName("eddtest2");
        labelDao.store(databaseName,l2);



        // now try tyo make a new one with an existing label
        l2 = new Label();
        l2.setName("eddtest");
        // this should throw an exception
        try{
            labelDao.store(databaseName,l2);
            Assert.fail("the DAO should have thrown an exception by now");
        } catch (DataAccessException dae){
            System.out.println(dae.getMessage());
        }


        // now try to update an existing one with another existing label
        l2 = labelDao.findByName(databaseName,"eddtest2");
        l2.setName("eddtest");
        // this should throw an exception but this time by an update
        try{
            labelDao.store(databaseName,l2);
            Assert.fail("the DAO should have thrown an exception by now");
        } catch (DataAccessException dae){
            System.out.println(dae.getMessage());
        }


        clearEddtestLabels();
    }

    @Test
    public void testRemove() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(databaseName, l);
        Label l2 = new Label();
        l2.setName("eddtest1");
        labelDao.insert(databaseName, l2);
        // this should clean links first
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, l,l2, 0);
        labelDao.remove(databaseName, l);
        labelDao.remove(databaseName, l2);
    }

    @Test
    public void testFindByName() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(databaseName, l);
        Assert.assertTrue(labelDao.findByName(databaseName, l.getName()) != null);
        labelDao.removeById(databaseName, l);
        Assert.assertTrue(labelDao.findByName(databaseName, l.getName()) == null);
    }

    @Test
    public void testFindChildren() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(databaseName,l);
        Label l2 = new Label();
        l2.setName("eddtest1");
        labelDao.insert(databaseName, l2);
        Label l3 = new Label();
        l3.setName("eddtest3");
        labelDao.insert(databaseName, l3);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l, l2, 0);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l, l3, 0);
        Assert.assertTrue(labelDao.findChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l,false).size() == 2);
        labelDao.remove(databaseName, l2);
        labelDao.remove(databaseName, l3);
        Assert.assertTrue(labelDao.findChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l, false).size() == 1);
        labelDao.remove(databaseName, l);
    }

    // will test find parents too
    @Test
    public void testFindAllParents() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(databaseName,l);
        Label l2 = new Label();
        l2.setName("eddtest1");
        labelDao.insert(databaseName, l2);
        Label l3 = new Label();
        l3.setName("eddtest3");
        labelDao.insert(databaseName, l3);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l, l2, 0);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l2, l3, 0);
        Assert.assertTrue(labelDao.findAllParents(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l3).size() == 2);
        labelDao.remove(databaseName, l2);
        labelDao.remove(databaseName, l);
        Assert.assertTrue(labelDao.findAllParents(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l3).size() == 0);
        labelDao.remove(databaseName, l3);
    }

    // will test find parents too
    @Test
    public void testFindAllChildren() throws Exception {
        Label l = new Label();
        l.setName("eddtest");
        labelDao.insert(databaseName,l);
        Label l2 = new Label();
        l2.setName("eddtest1");
        labelDao.insert(databaseName, l2);
        Label l3 = new Label();
        l3.setName("eddtest3");
        labelDao.insert(databaseName, l3);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l, l2, 0);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l2, l3, 0);
        Assert.assertTrue(labelDao.findAllChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l).size() == 2);
        labelDao.remove(databaseName, l2);
        labelDao.remove(databaseName, l3);
        Assert.assertTrue(labelDao.findAllChildren(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l).size() == 0);
        labelDao.remove(databaseName, l);
    }

    @Test
    public void testMaxPosition() throws Exception {
        Label l1 = new Label();
        l1.setName("eddtest");
        labelDao.store(databaseName,l1);
        Label l2 = new Label();
        l2.setName("eddtest2");
        labelDao.store(databaseName,l2);
        System.out.println("max position when there's no children : " + labelDao.getMaxChildPosition(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, l1));
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, l1, l2, 2);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l1, l2, 4);
        System.out.println("max position when there's children : " + labelDao.getMaxChildPosition(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, l1));
        // need too sys out println to confirm the link here
        labelDao.unlinkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l1, l2);
        clearEddtestLabels();
    }

    @Test
    public void testLinking() throws Exception {
        Label l1 = new Label();
        l1.setName("eddtest");
        labelDao.store(databaseName, l1);
        Label l2 = new Label();
        l2.setName("eddtest2");
        labelDao.store(databaseName, l2);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, l1, l2, 0);
        labelDao.linkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition,l1,l2,0);
        // need too sys out println to confirm the link here
        labelDao.unlinkParentAndChild(databaseName, LabelDAO.SetDefinitionTable.label_set_definition, l1, l2);
        clearEddtestLabels();
    }
  */
}
