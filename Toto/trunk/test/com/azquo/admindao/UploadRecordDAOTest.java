package com.azquo.admindao;

import com.azquo.adminentities.UploadRecord;
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
@ContextConfiguration(locations = {"file:../../../../web/WEB-INF/azquospringdispatcher-servlet.xml"})

public class UploadRecordDAOTest {
    @Autowired
    UploadRecordDAO uploadRecordDAO;

    @Test
    public void testBasics() throws Exception {

        UploadRecord ur = new UploadRecord(0, new Date(), 1, 2, 3, "thing1", "thing2", "thing3");

        System.out.println("id before insert : " + ur.getId());
        uploadRecordDAO.store(ur);
        System.out.println("id after insert : " + ur.getId());
        System.out.println(uploadRecordDAO.findById(ur.getId()));
        uploadRecordDAO.removeById(ur);
    }
}
