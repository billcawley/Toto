package com.azquo.admin.database;

import com.azquo.admin.StandardEntity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.time.LocalDateTime;

/**
 * Created by Cawley on 08/01/14.
 * <p/>
 * Representing an Azquo memory database. These records are scanned on startup and teh databases loaded into memory
 */
public final class Database extends StandardEntity {

    LocalDateTime startDate;
    LocalDateTime endDate;
    int businessId;
    String name;
    String mySQLName;
    int nameCount;
    int valueCount;

    public Database(int id, LocalDateTime startDate
            , LocalDateTime endDate
            , int businessId
            , String name
            , String mySQLName
            , int nameCount
            , int valueCount) {
        this.id = id;
        this.startDate = startDate;
        this.endDate = endDate;
        this.businessId = businessId;
        this.name = name;
        this.mySQLName = mySQLName;
        this.nameCount = nameCount;
        this.valueCount = valueCount;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public int getBusinessId() {
        return businessId;
    }

    public void setBusinessId(int businessId) {
        this.businessId = businessId;
    }

    public String getName() {
        return name;
    }

    public String getUrlEncodedName() {
        if (name != null){
            try {
                URLEncoder.encode(name, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMySQLName() {
        return mySQLName;
    }

    public void setMySQLName(String mySQLName) {
        this.mySQLName = mySQLName;
    }

    public int getNameCount() {
        return nameCount;
    }

    public void setNameCount(int nameCount) {
        this.nameCount = nameCount;
    }

    public int getValueCount() {
        return valueCount;
    }

    public void setValueCount(int valueCount) {
        this.valueCount = valueCount;
    }

    @Override
    public String toString() {
        return "Database{" +
                "id=" + id +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", businessId=" + businessId +
                ", name='" + name + '\'' +
                ", mySQLName='" + mySQLName + '\'' +
                ", nameCount=" + nameCount +
                ", valueCount=" + valueCount +
                '}';
    }
}
