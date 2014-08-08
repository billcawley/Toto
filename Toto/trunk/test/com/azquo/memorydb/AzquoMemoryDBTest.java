package com.azquo.memorydb;

import com.azquo.service.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 25/10/13
 * Time: 11:10
 * does a straight load from Mysql then we can test query speed
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/azquospringdispatcher-servlet.xml"})
public class AzquoMemoryDBTest {

    @Autowired
    NameService nameService;
    @Autowired
    ValueService valueService;
    @Autowired
    LoginService loginService;

    @Test
    public void testLoadData() throws Exception {

    }
}
