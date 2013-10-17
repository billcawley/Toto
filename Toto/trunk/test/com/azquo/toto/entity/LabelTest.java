package com.azquo.toto.entity;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 17/10/13
 * Time: 18:35
 *
 */
public class LabelTest {

    Label test = new Label();

    @Before
    public void setUp() throws Exception {
        test.setName("testname");
    }

    @Test
    public void testGetTableName() throws Exception {
        Assert.assertTrue(test.getTableName().equals("label"));
    }

    @Test
    public void testGetColumnNameValueMap() throws Exception {
        Assert.assertTrue(test.getColumnNameValueMap().keySet().size() == 2);
    }
}
