package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.Business;
import com.azquo.toto.adminentities.Database;
import com.azquo.toto.adminentities.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Date;

/**
 * Created by cawley on 08/01/14.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})

public class DatabaseDAOTest {
    @Autowired
    DatabaseDAO databaseDAO;

    @Test
    public void testBasics() throws Exception {

        Database d = new Database(0, true, new Date(),324,"qegwr4 2352452435 ","mysqlname", 123,456);
        System.out.println("id before insert : " + d.getId());
        databaseDAO.store(d);
        System.out.println("id after insert : " + d.getId());
        System.out.println(databaseDAO.findById(d.getId()));
        databaseDAO.removeById(d);
    }
}
