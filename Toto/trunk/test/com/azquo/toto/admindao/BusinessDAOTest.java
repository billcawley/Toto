package com.azquo.toto.admindao;

import com.azquo.toto.adminentities.Business;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.Date;

/**
 * Created by cawley on 08/01/14.
 * just check the basics on the first admin DAO we're using
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})

public class BusinessDAOTest {

    @Autowired
    BusinessDAO businessDAO;

    @Test
    public void testBasics() throws Exception {

        Business b = new Business(0, new Date(),new Date(),"a new business", 0, new Business.BusinessDetails("address line 1","address line 2","address line 3","address line 4","here is a postcode","76897896","www.azquo.com","key"));
        System.out.println("id before insert : " + b.getId());
        businessDAO.store(b);
        System.out.println("id after insert : " + b.getId());
        System.out.println(businessDAO.findById(b.getId()));
        businessDAO.removeById(b);
    }
}
