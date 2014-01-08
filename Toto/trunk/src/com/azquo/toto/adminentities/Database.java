package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Created by cawley on 08/01/14.
 *
 *   Active (boolean)
Start date
Business Id
Name
Name count
Value count
 */
public class Database extends StandardEntity {

    boolean active;
    Date startDate;
    int businessId;
    String name;
    int nameCount;
    int valueCount;

    public Database(int id, boolean active, Date startDate, int businessId, String name, int nameCount, int valueCount) {
        this.id = id;
        this.active = active;
        this.startDate = startDate;
        this.businessId = businessId;
        this.name = name;
        this.nameCount = nameCount;
        this.valueCount = valueCount;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
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

    public void setName(String name) {
        this.name = name;
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
                ", active=" + active +
                ", startDate=" + startDate +
                ", businessId=" + businessId +
                ", name='" + name + '\'' +
                ", nameCount=" + nameCount +
                ", valueCount=" + valueCount +
                '}';
    }
}
