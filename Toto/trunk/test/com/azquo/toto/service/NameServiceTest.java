package com.azquo.toto.service;

import com.azquo.toto.dao.NameDAO;
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
 * Time: 18:50
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})
public class NameServiceTest {

    @Autowired
    NameService nameService;
    @Autowired
    NameDAO nameDao;

    @Autowired
    private TotoMemoryDB totoMemoryDB;
    @Before
    public void setUp() throws Exception {
    }
/*
    @After
    public void tearDown() throws Exception {
        Name l = nameDao.findByName(databaseName,"eddtest");
        if (l != null){
            nameDao.removeById(databaseName,l);
        }
    }

    @Test
    public void testGetByName() throws Exception {
        Name l = new Name();
        l.setName("eddtest");
        nameDao.insert(databaseName,l);
        Assert.assertTrue(nameService.findByName(l.getName()) != null);
        nameDao.removeById(databaseName,l);
        Assert.assertTrue(nameService.findByName(l.getName()) == null);
    }

    @Test
    public void testFindOrCreate() throws Exception {
        nameService.findOrCreateLabel("eddtest");
        Name l = nameService.findOrCreateLabel("eddtest");
        nameDao.removeById(databaseName, l);
    }

    @Test
    public void testFindChildrenAtLevel() throws Exception {
        nameService.findChildrenAtLevel(nameService.findOrCreateLabel("eddtest"), 2);
        nameDao.removeById(databaseName,nameService.findOrCreateLabel("eddtest"));
    }

    @Test
    public void testCreateMembers() throws Exception {
        Name l = nameService.findOrCreateLabel("eddtest");
        List<String> tocreate = new ArrayList<String>();
        tocreate.add("test1");
        tocreate.add("test2");
        nameService.createMembers(l, tocreate);
        nameService.removeMember(l, "test1");
        nameService.removeMember(l, "test2");
        nameDao.removeById(databaseName,nameDao.findByName(databaseName,"test1"));
        nameDao.removeById(databaseName, nameDao.findByName(databaseName, "test2"));
        nameDao.removeById(databaseName,l);
    }

    @Test
    public void testCreateMember() throws Exception {
        Name l = nameService.findOrCreateLabel("eddtest");
        nameService.createMember(l, "test1", null, 1);
        nameService.removeMember(l, "test1");
        nameDao.removeById(databaseName,nameDao.findByName(databaseName,"test1"));
        nameDao.removeById(databaseName,l);
    }

    @Test
    public void testRename() throws Exception {
        nameService.findOrCreateLabel("eddtest");
        nameService.renameLabel("eddtest", "eddtest1");
        Assert.assertTrue(nameService.findByName("eddtest1") != null);
        nameDao.removeById(databaseName, nameService.findByName("eddtest1"));
    }

    @Test
    public void logFullLabelHierarchy() throws Exception {
        // force db to toto for this
        List<Name> topLabels = nameDao.findTopLevelLabels("toto", NameDAO.SetDefinitionTable.label_set_definition);
        nameService.setDatabaseName("toto");
        for (Name topLabel : topLabels){
            nameService.logLabelHierarchy(topLabel, 0);
        }
    }
*/
    //rename
}
