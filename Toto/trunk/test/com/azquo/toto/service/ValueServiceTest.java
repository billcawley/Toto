package com.azquo.toto.service;

import com.azquo.toto.dao.ValueDAO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 23/10/13
 * Time: 23:17
 * Test for the value service, an important business
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})

public class ValueServiceTest {

    String databaseName = "tototest"; // hard code here for the moment
    @Autowired
    ValueService valueService;
    @Autowired
    ValueDAO valueDao;


    @Before
    public void setUp() throws Exception {

    }

    @Test
    public void testFindByLabels() throws Exception {

    }

    @Test
    public void testStoreValueWithLabels() throws Exception {

    }
}
