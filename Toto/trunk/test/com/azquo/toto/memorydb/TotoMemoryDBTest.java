package com.azquo.toto.memorydb;

import com.azquo.toto.dao.ValueDAO;
import com.azquo.toto.service.LabelService;
import com.azquo.toto.service.ValueService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 11:10
 * To change this template use File | Settings | File Templates.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})
public class TotoMemoryDBTest {

    @Autowired
    TotoMemoryDB totoMemoryDB;
    @Test
    public void testLoadData() throws Exception {

        totoMemoryDB.loadData();

    }
}
