package com.azquo.jsonrequestentities;

import org.w3c.dom.NameList;

import java.util.Map;


/**
 * Created by bill on 30/05/14.
 */

public class NameListJson  extends StandardJsonRequest {
        public String name;
        public int id = 0;
        public int dataitems = 0;
        public int mydataitems = 0;
        public int elements = 0;
        public Map<String, String> attributes;
        public String[] peers = null;
        public NameListJson[]  children = null;
        public NameListJson[] names = null;

}




