package com.azquo.toto.memorydb;

import com.azquo.toto.service.NameService;
import com.azquo.toto.service.ValueService;
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
 * To change this template use File | Settings | File Templates.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"file:web/WEB-INF/totospringdispatcher-servlet.xml"})
public class TotoMemoryDBTest {

    @Autowired
    TotoMemoryDB totoMemoryDB;
    @Autowired
    NameService nameService;
    @Autowired
    ValueService valueService;
    @Test
    public void testLoadData() throws Exception {

        Name test1 = nameService.findByName("Time Activity");
        Name test2 = nameService.findByName("Total All Methods");
//        Name test3 = nameService.findByName("Primary Strategy - Targeted Support");
//        Name test4 = nameService.findByName("Lynne Swainston");

        Set<Name> searchCriteria = new HashSet<Name>();
        searchCriteria.add(test1);
        searchCriteria.add(test2);
//        searchCriteria.add(test3);
//        searchCriteria.add(test4);
        long track = System.nanoTime();
        int count = 100;
        for (int i = 0; i < count; i++){
            List<Value> searchResults = valueService.findForNames(searchCriteria);
            System.out.println(searchResults.size() +  " records");
        }
        long average = (System.nanoTime() - track) / count;
        System.out.println("records " + average + "ns");
    }
}
