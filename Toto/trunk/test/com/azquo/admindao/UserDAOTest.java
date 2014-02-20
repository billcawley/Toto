package com.azquo.admindao;

import com.azquo.adminentities.User;
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
@ContextConfiguration(locations = {"file:web/WEB-INF/azquospringdispatcher-servlet.xml"})

public class UserDAOTest {
    @Autowired
    UserDAO userDAO;

    @Test
    public void testBasics() throws Exception {

        User u = new User(0, new Date(), new Date(), 1, "user email", "the user name", "their status", "password thing", " password salt");
        System.out.println("id before insert : " + u.getId());
        userDAO.store(u);
        System.out.println("id after insert : " + u.getId());
        System.out.println(userDAO.findById(u.getId()));
        //userDAO.removeById(u);
    }
}
