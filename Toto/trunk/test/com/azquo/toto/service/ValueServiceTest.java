package com.azquo.toto.service;

import com.azquo.toto.memorydb.Provenance;
import com.azquo.toto.memorydb.Name;
import com.csvreader.CsvReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;

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
