package com.azquo.admin.business;

import com.azquo.admin.StandardEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Copyright (C) 2016 Azquo Ltd.
 * <p>
 * Created by cawley on 07/01/14.
 * <p>
 * Details of an Azquo customer, mutable as almost certainly will be updated
 */
public final class Business extends StandardEntity {

    private String businessName;
    private BusinessDetails businessDetails;
    private String bannerColor;
    private String ribbonColor;
    private String ribbonLinkColor;
    private String sideMenuColor;
    private String sideMenuLinkColor;
    private String logo;
    private String cornerLogo;
    // as in charterhouse.gingerblack.com or whatever. Change branding on the login page
    private String serverName;
    private boolean newDesign;

    public Business(int id
            , String businessName
            , BusinessDetails businessDetails
            , String bannerColor
            , String ribbonColor
            , String ribbonLinkColor
            , String sideMenuColor
            , String sideMenuLinkColor
            , String logo
            , String cornerLogo
            , String serverName
    , boolean newDesign) {
        this.id = id;
        this.businessName = businessName;
        this.businessDetails = businessDetails;
        this.bannerColor = bannerColor;
        this.ribbonColor = ribbonColor;
        this.ribbonLinkColor = ribbonLinkColor;
        this.sideMenuColor = sideMenuColor;
        this.sideMenuLinkColor = sideMenuLinkColor;
        this.logo = logo;
        this.cornerLogo = cornerLogo;
        this.serverName = serverName;
        this.newDesign = newDesign;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessDirectory() {
        return (businessName + "                    ").substring(0, 20).trim().replaceAll("[^A-Za-z0-9_]", "");
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public BusinessDetails getBusinessDetails() {
        return businessDetails;
    }

    public void setBusinessDetails(BusinessDetails businessDetails) {
        this.businessDetails = businessDetails;
    }

    public String getBannerColor() {
        return bannerColor;
    }

    public void setBannerColor(String bannerColor) {
        this.bannerColor = bannerColor;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getRibbonColor() {
        return ribbonColor;
    }

    public void setRibbonColor(String ribbonColor) {
        this.ribbonColor = ribbonColor;
    }

    public String getRibbonLinkColor() {
        return ribbonLinkColor;
    }

    public void setRibbonLinkColor(String ribbonLinkColor) {
        this.ribbonLinkColor = ribbonLinkColor;
    }

    public String getSideMenuColor() {
        return sideMenuColor;
    }

    public void setSideMenuColor(String sideMenuColor) {
        this.sideMenuColor = sideMenuColor;
    }

    public String getSideMenuLinkColor() {
        return sideMenuLinkColor;
    }

    public void setSideMenuLinkColor(String sideMenuLinkColor) {
        this.sideMenuLinkColor = sideMenuLinkColor;
    }

    public String getCornerLogo() {
        return cornerLogo;
    }

    public void setCornerLogo(String cornerLogo) {
        this.cornerLogo = cornerLogo;
    }

    public boolean isNewDesign() {
        return newDesign;
    }

    public void setNewDesign(boolean newDesign) {
        this.newDesign = newDesign;
    }

    @Override
    public String toString() {
        return "Business{" +
                "businessName='" + businessName + '\'' +
                ", businessDetails=" + businessDetails +
                ", bannerColor='" + bannerColor + '\'' +
                ", ribbonColor='" + ribbonColor + '\'' +
                ", sideMenuColor='" + sideMenuColor + '\'' +
                ", logo='" + logo + '\'' +
                ", serverName='" + serverName + '\'' +
                '}';
    }

    // for Jackson mapping, these bits of data will be as json in the DB. Hence if you want a new one you should just be able to add it here :)
    public static class BusinessDetails implements Serializable {
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