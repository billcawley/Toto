package com.azquo.service;

import com.azquo.memorydb.Name;

/**
 * Created by cawley on 08/01/15.
 */
public class FormulaTerm {

    private final String source;

    public enum State {DOUBLE, STRING, NAME, OPERATOR}

    private State state;

    private Name name;
    private double doubleValue;

    public FormulaTerm(String source) {
        this.source = source;
        state = State.STRING;
    }

    public void setName(Name name){
        state = State.NAME;
        this.name = name;
    }

    public void setDouble(Double value){
        state = State.DOUBLE;
        this.doubleValue = value;
    }

    public void setOperator(){
        state = State.OPERATOR;
    }

}
