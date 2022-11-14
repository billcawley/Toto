package com.azquo.memorydb.dao;

import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd.
 *
 * now we've simplified the way records are persisted we need to be able to move them around
 */
public class Statistics {

    private final int id;
    private LocalDateTime ts;
    private String name;
    private Double number;
    private String value;

    public Statistics(int id, LocalDateTime ts, String name, Double number, String value) {
        this.id = id;
        this.ts = ts;
        this.name = name;
        this.number = number;
        this.value = value;
    }

    public int getId() {
        return id;
    }

    public LocalDateTime getTs() {
        return ts;
    }

    public String getName() {
        return name;
    }

    public Double getNumber() {
        return number;
    }

    public String getValue() {
        return value;
    }

    public void setTs(LocalDateTime ts) {
        this.ts = ts;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNumber(Double number) {
        this.number = number;
    }

    public void addToNumber(Double number) {
        this.number += number;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
