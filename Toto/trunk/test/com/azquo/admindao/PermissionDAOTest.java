package com.azquo.admindao;

import com.azquo.adminentities.Permission;
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

public class PermissionDAOTest {
    @Autowired
    PermissionDAO permissionDAO;

    @Test
    public void testBasics() throws Exception {

        Permission a = new Permission(0, new Date(), new Date(), 2, 3, "read list", "write list");
        System.out.println("id before insert : " + a.getId());
        permissionDAO.store(a);
        System.out.println("id after insert : " + a.getId());
        System.out.println(permissionDAO.findById(a.getId()));
        permissionDAO.removeById(a);
    }
}
