package com.azquo.spreadsheet.jsonentities;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 11/11/15.
 *
 * Used to be local as part of teh tree service, now it will be part of communication between the client and db server
 */
public class JsonChildren implements Serializable {
    // could use a map I suppose but why not define the structure properly
    public static class Node implements Serializable {
        public int id; // not final as I need to set the id (the node id) client side
        public String text; // also not final as I may need to qualify after setting
        public final boolean children;
        public int nameId; // I had to add these two in so that the client side knows about the name ids which it needs to keep track of
        int parentNameId;

        public Node(int id, String text, boolean children, int nameId, int parentNameId) {
            this.id = id;
            this.text = text;
            this.children = children;
            this.nameId = nameId;
            this.parentNameId = parentNameId;
        }
    }
    // public for jackson to see them
    public final int id;
    public final Map<String, Boolean> state;
    public final String text;
    public final List<Node> children;
    public final String type;

    public JsonChildren(int id, Map<String, Boolean> state, String text, List<Node> children, String type) {
        this.id = id;
        this.state = state;
        this.text = text;
        this.children = children;
        this.type = type;
    }
}
