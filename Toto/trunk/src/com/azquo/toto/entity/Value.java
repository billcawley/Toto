package com.azquo.toto.entity;

import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: cawley
 * Date: 22/10/13
 * Time: 22:31
 * To reflect a fundamental Toto idea : a piece of data which has labels attached
 */
public class Value extends StandardEntity{

    // leaving here as a reminder to consider proper logging

    //private static final Logger logger = Logger.getLogger(Value.class.getName());
    // this is stored in the DB as an int from .ordinal() so WATCH OUT changing orders here
    public enum Type {INT, DOUBLE, VARCHAR, TEXT, TIMESTAMP}

    private int provenanceId;
    private Type type;
    private int intValue;
    private double doubleValue;
    private String varChar;
    private String text;
    private Date timeStamp;
    private boolean deleted;


    public Value() {
        id = 0;
        provenanceId = 0;
        type = null;
        intValue = 0;
        doubleValue = 0;
        varChar = null;
        text = null;
        timeStamp = null;
        deleted = false;

    }

    public int getProvenanceId() {
        return provenanceId;
    }

    public void setProvenanceId(int provenanceId) {
        this.provenanceId = provenanceId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getIntValue() {
        return intValue;
    }

    public void setIntValue(int intValue) {
        this.intValue = intValue;
    }

    public double getDoubleValue() {
        return doubleValue;
    }

    public void setDoubleValue(double doubleValue) {
        this.doubleValue = doubleValue;
    }

    public String getVarChar() {
        return varChar;
    }

    public void setVarChar(String varChar) {
        this.varChar = varChar;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }

    public boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = provenanceId;
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + intValue;
        temp = Double.doubleToLongBits(doubleValue);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + (varChar != null ? varChar.hashCode() : 0);
        result = 31 * result + (text != null ? text.hashCode() : 0);
        result = 31 * result + (timeStamp != null ? timeStamp.hashCode() : 0);
        // add deleted . . do we care?
        return result;
    }

    @Override
    public String toString() {
        return "Value{" +
                "id=" + id +
                ", changeId=" + provenanceId +
                ", type=" + type +
                ", intValue=" + intValue +
                ", doubleValue=" + doubleValue +
                ", varChar='" + varChar + '\'' +
                ", text='" + text + '\'' +
                ", timeStamp=" + timeStamp +
                ", deleted=" + deleted+
                '}';
    }
}
