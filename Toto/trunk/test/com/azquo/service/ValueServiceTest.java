package com.azquo.service;

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
@ContextConfiguration(locations = {"file:web/WEB-INF/azquospringdispatcher-servlet.xml"})

public class ValueServiceTest {


    @Autowired
    ValueService valueService;
    @Autowired
    NameService nameService;
    @Autowired
    ProvenanceService provenanceService;
    @Autowired
    LoginService loginService;


    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testCsvImport() throws Exception {


    }

}
