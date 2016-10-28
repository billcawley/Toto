package com.azquo.memorydb;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by bill on 05/08/15.
 *
 * To facilitate navigating around a database. A result of the reporting server/database server split, it might be worth considering how this would be coded from scratch given the split.
 *
 * TODO - stop this being used for provenance.
 *
 */
public class TreeNode implements Serializable{
    private String heading;
    private String name;
    private String value;
    private double dValue;
    private List<TreeNode> children;
    private int valueId;
    final private List<String> valueHistory; // now I'm adding this just have it as a string to move to the client

    public TreeNode(){
        heading = null;
        name = null;
        value = null;
        dValue = 0;
        children = new ArrayList<>();
        valueHistory = null;
    }

    // for a single value, it's here that it will have a value id
    public TreeNode(String name, String value, double dValue, int valueId, List<String> valueHistory) {
        this.heading = null;
        this.children = new ArrayList<>();
        this.name = name;
        this.value = value;
        this.dValue = dValue;
        this.valueId = valueId;
        this.valueHistory = valueHistory;
    }

    public TreeNode(String heading, String name, String value, double dValue, List<TreeNode> children){
        this.heading = heading;
        this.children = children;
        this.name = name;
        this.value = value;
        this.dValue = dValue;
        this.valueId = 0;
        this.valueHistory = null;
    }

    public void setHeading(String heading){
        this.heading = heading;
    }

    public String getHeading(){
        return this.heading;
    }

    public void setName(String name){
        this.name = name;
    }

    public String getName(){
        return this.name;
    }

    public void setValue(String value){
        this.value = value;
    }

    public String getValue(){
        return this.value;
    }
    public void setDvalue(double dValue){
        this.dValue = dValue;
    }

    public double getDvalue() {return this.dValue;}

    public void setChildren(List<TreeNode> children){
        this.children = children;
    }

    public List<TreeNode> getChildren(){
        return this.children;
    }

    public int getValueId() {
        return valueId;
    }

    public void setValueId(int valueId) {
        this.valueId = valueId;
    }

    public List<String> getValueHistory() {
        return valueHistory;
    }
}
