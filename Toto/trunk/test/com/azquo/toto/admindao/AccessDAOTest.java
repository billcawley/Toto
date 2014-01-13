package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.Access;
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

public class AccessDAOTest {
    @Autowired
    AccessDAO accessDAO;

    @Test
    public void testBasics() throws Exception {

        Access a = new Access(0, new Date(), new Date(),2, 3,"read list", "write list");
        System.out.println("id before insert : " + a.getId());
        accessDAO.store(a);
        System.out.println("id after insert : " + a.getId());
        System.out.println(accessDAO.findById(a.getId()));
        accessDAO.removeById(a);
    }
}
