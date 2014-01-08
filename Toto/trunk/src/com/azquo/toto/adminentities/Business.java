package com.azquo.toto.adminentities;

import java.util.Date;

/**
 * Created by cawley on 07/01/14.
 */
public class Business extends StandardEntity{

    private boolean active;
    Date startDate;
    String businessName;
    int parentId;
    BusinessDetails businessDetails;

    public Business(int id, boolean active, Date startDate, String businessName, int parentId, BusinessDetails businessDetails) {
        this.id = id;
        this.active = active;
        this.startDate = startDate;
        this.businessName = businessName;
        this.parentId = parentId;
        this.businessDetails = businessDetails;
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

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public int getParentId() {
        return parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public BusinessDetails getBusinessDetails() {
        return businessDetails;
    }

    public void setBusinessDetails(BusinessDetails businessDetails) {
        this.businessDetails = businessDetails;
    }

    // for Gson mapping, these bits of data will be as gson in the DB. Hence if you want a new one you should just be able to add it here :)
    // Note gson does not need getters and setters, it just goes straight for the fields.
    public class BusinessDetails{
        String address1;
        String address2;
        String address3;
        String address4;
        String postcode;
        String telephone;
        String website;

        public String getAddress1() {
            return address1;
        }

        public void setAddress1(String address1) {
            this.address1 = address1;
        }

        public String getAddress2() {
            return address2;
        }

        public void setAddress2(String address2) {
            this.address2 = address2;
        }

        public String getAddress3() {
            return address3;
        }

        public void setAddress3(String address3) {
            this.address3 = address3;
        }

        public String getAddress4() {
            return address4;
        }

        public void setAddress4(String address4) {
            this.address4 = address4;
        }

        public String getPostcode() {
            return postcode;
        }

        public void setPostcode(String postcode) {
            this.postcode = postcode;
        }

        public String getTelephone() {
            return telephone;
        }

        public void setTelephone(String telephone) {
            this.telephone = telephone;
        }
        public String getWebsite() {
            return website;
        }

        public void setWebsite(String website) {
            this.website = website;
        }
    }

}
