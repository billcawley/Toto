package com.azquo.admin.business;

import com.azquo.admin.StandardEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by cawley on 07/01/14.
 *
 * Details of an Azquo customer, mutable as almost certainly will be updated
 */
public final class Business extends StandardEntity {

    private String businessName;
    private BusinessDetails businessDetails;

    public Business(int id
            , String businessName
            , BusinessDetails businessDetails) {
        this.id = id;
        this.businessName = businessName;
        this.businessDetails = businessDetails;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    BusinessDetails getBusinessDetails() {
        return businessDetails;
    }

    public void setBusinessDetails(BusinessDetails businessDetails) {
        this.businessDetails = businessDetails;
    }

    @Override
    public String toString() {
        return "Business{" +
                "id=" + id +
                ", businessName='" + businessName + '\'' +
                ", businessDetails=" + businessDetails +
                '}';
    }

    // for Jackson mapping, these bits of data will be as json in the DB. Hence if you want a new one you should just be able to add it here :)
    public static class BusinessDetails {
        // I think these need to be public for json
        public String address1;
        public String address2;
        public String address3;
        public String address4;
        public String postcode;
        public String telephone;
        public String website;
        public String validationKey; // temporary place to store validation key

        @JsonCreator
        public BusinessDetails(@JsonProperty("address1") String address1
                , @JsonProperty("address2") String address2
                , @JsonProperty("address3") String address3
                , @JsonProperty("address4") String address4
                , @JsonProperty("postcode") String postcode
                , @JsonProperty("telephone") String telephone
                , @JsonProperty("website") String website
                , @JsonProperty("validationKey") String validationKey) {
            this.address1 = address1;
            this.address2 = address2;
            this.address3 = address3;
            this.address4 = address4;
            this.postcode = postcode;
            this.telephone = telephone;
            this.website = website;
            this.validationKey = validationKey;
        }

        @Override
        public String toString() {
            return "BusinessDetails{" +
                    "address1='" + address1 + '\'' +
                    ", address2='" + address2 + '\'' +
                    ", address3='" + address3 + '\'' +
                    ", address4='" + address4 + '\'' +
                    ", postcode='" + postcode + '\'' +
                    ", telephone='" + telephone + '\'' +
                    ", website='" + website + '\'' +
                    '}';
        }
    }
}
