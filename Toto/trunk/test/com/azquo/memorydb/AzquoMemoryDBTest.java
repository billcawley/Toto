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
@ContextConfiguration(locations = {"file:../../../../web/WEB-INF/azquospringdispatcher-servlet.xml"})
public class AzquoMemoryDBTest {

    @Autowired
    AzquoMemoryDB azquoMemoryDB;
    @Autowired
    NameService nameService;
    @Autowired
    ValueService valueService;
    @Autowired
    LoginService loginService;

    @Test
    public void testLoadData() throws Exception {

        LoggedInConnection loggedInConnection = loginService.login("tototest", "bill", "thew1password", 0, null);

        Name test1 = nameService.findByName(loggedInConnection, "www.examplesupplier.com");
        Name test2 = nameService.findByName(loggedInConnection, "S++");
//        Name test3 = nameService.findByName("Primary Strategy - Targeted Support");
//        Name test4 = nameService.findByName("Lynne Swainston");

        Set<Name> searchCriteria = new HashSet<Name>();
        searchCriteria.add(test1);
        searchCriteria.add(test2);
//        searchCriteria.add(test3);
//        searchCriteria.add(test4);
        long track = System.nanoTime();
        int count = 10000;
        for (int i = 0; i < count; i++) {
            List<Value> searchResults = valueService.findForNamesIncludeChildren(searchCriteria, false);
/*            for (Value v : searchResults){
                System.out.print("value ");
                for (Name n : v.getNames()){
                    System.out.print(n.getName() + " ");
                }
                System.out.println();
            }*/
            //System.out.println(searchResults.size() +  " records");
        }
        long average = (System.nanoTime() - track) / count;
        System.out.println("average " + average + "ns");
    }
}
