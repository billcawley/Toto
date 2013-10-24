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
    public enum Type {INT, DOUBLE, VARCHAR, TEXT, TIMESTAMP};

    private Date timeChanged;
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
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Value value = (Value) o;

        if (provenanceId != value.provenanceId) return false;
        if (Double.compare(value.doubleValue, doubleValue) != 0) return false;
        if (intValue != value.intValue) return false;
        if (text != null ? !text.equals(value.text) : value.text != null) return false;
        if (timeChanged != null ? !timeChanged.equals(value.timeChanged) : value.timeChanged != null) return false;
        if (timeStamp != null ? !timeStamp.equals(value.timeStamp) : value.timeStamp != null) return false;
        if (type != value.type) return false;
        if (varChar != null ? !varChar.equals(value.varChar) : value.varChar != null) return false;
        if (deleted != value.deleted) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = timeChanged != null ? timeChanged.hashCode() : 0;
        result = 31 * result + provenanceId;
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
                ", timeChanged=" + timeChanged +
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
