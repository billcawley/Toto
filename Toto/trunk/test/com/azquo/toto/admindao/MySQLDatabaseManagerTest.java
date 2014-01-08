package com.azquo.toto.admindao;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Created by cawley on 08/01/14.
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})

public class MySQLDatabaseManagerTest {

    @Autowired
    MySQLDatabaseManager mySQLDatabaseManager;

    @Test
    public void testCreateNewDatabase() throws Exception {
        mySQLDatabaseManager.createNewDatabase("anotherdatabase12234");
    }
}
