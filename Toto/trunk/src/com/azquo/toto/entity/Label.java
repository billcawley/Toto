package com.azquo.toto.entity;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 16/10/13
 * Time: 19:17
 * A fundamental Toto object, labels only have names but they can have parent and child relationships with multiple
 * other labels. Sets of labels.
 *
 */
public class Label extends StandardEntity {

    // leaving here as a reminder to consider proper logging

    //private static final Logger logger = Logger.getLogger(Label.class.getName());

    private String name;
    private boolean labelSetLookupNeedsRebuilding;

    public Label() {
        id = 0;
        name = null;
        labelSetLookupNeedsRebuilding = true;
    }

    public Label(String name) {
        id = 0;
        this.name = name;
        labelSetLookupNeedsRebuilding = true;
    }
    // clone may be required here is we cache

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getLabelSetLookupNeedsRebuilding() {
        return labelSetLookupNeedsRebuilding;
    }

    public void setLabelSetLookupNeedsRebuilding(boolean labelSetLookupNeedsRebuilding) {
        this.labelSetLookupNeedsRebuilding = labelSetLookupNeedsRebuilding;
    }

    // Generated by Intellij, Dr. Mike thinks it a good idea, I'll follow suit

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Label label = (Label) o;

        return name.equals(label.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Label{" +
                "id='" + id + '\'' +
                "name='" + name + '\'' +
                ", labelSetLookupNeedsRebuilding=" + labelSetLookupNeedsRebuilding +
                '}';
    }
}
