package com.azquo.memorydb;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by bill on 05/08/15.
 */
public class TreeNode implements Serializable{
    String heading;
    String name;
    String link;
    String value;
    List<TreeNode> children;

    public TreeNode(){
        heading = null;
        name= null;
        link= null;
        value = null;
        children= null;
    }


    public TreeNode(String heading, String name, List<TreeNode> children) {
        this.heading = heading;
        this.children = children;
        this.name = name;
        this.value = null;
        this.link = null;
    }


    public TreeNode(String name, String value) {
        this.heading = null;
        this.children = new ArrayList<TreeNode>();
        this.name = name;
        this.value = value;
        this.link = null;
    }

    public TreeNode(String heading, String name, String link, List<TreeNode> children){
        this.heading = heading;
        this.children = children;
        this.link = link;
        this.name = name;
        this.value = null;
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

    public void setLink(String link){
        this.link = link;

    }

    public String getLink(){
        return this.link;
    }

    public void setValue(String value){
        this.value = value;
    }

    public String getValue(){
        return this.value;
    }

    public void setChildren(List<TreeNode> children){
        this.children = children;
    }

    public List<TreeNode> getChildren(){
        return this.children;
    }
}
