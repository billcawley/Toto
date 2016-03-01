package com.azquo.admin.business;

import com.azquo.admin.StandardEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 07/01/16.
 *
 * As in details from which an invoice can be generated. Not relevant to core Azquo functionality, we just needed a way of managing our invoices.
 */
public class InvoiceDetails extends StandardEntity {
    
    public static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    String customerReference;
    String serviceDescription;
    int quantity;
    int unitCost;
    int paymentTerms;
    String poReference;
    LocalDate invoiceDate;
    String invoicePeriod;
    String invoiceNo;
    String invoiceAddress;
    boolean noVat;
    String sendTo;

    public InvoiceDetails(int id, String customerReference, String serviceDescription, int quantity, int unitCost,
                          int paymentTerms, String poReference, LocalDate invoiceDate, String invoicePeriod,
                          String invoiceNo, String invoiceAddress, boolean noVat, String sendTo) {
        this.id = id;
        this.customerReference = customerReference;
        this.serviceDescription = serviceDescription;
        this.quantity = quantity;
        this.unitCost = unitCost;
        this.paymentTerms = paymentTerms;
        this.poReference = poReference;
        this.invoiceDate = invoiceDate;
        this.invoicePeriod = invoicePeriod;
        this.invoiceNo = invoiceNo;
        this.invoiceAddress = invoiceAddress;
        this.noVat = noVat;
        this.sendTo = sendTo;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public void setCustomerReference(String customerReference) {
        this.customerReference = customerReference;
    }

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getUnitCost() {
        return unitCost;
    }

    public void setUnitCost(int unitCost) {
        this.unitCost = unitCost;
    }

    public int getPaymentTerms() {
        return paymentTerms;
    }

    public void setPaymentTerms(int paymentTerms) {
        this.paymentTerms = paymentTerms;
    }

    public String getPoReference() {
        return poReference;
    }

    public void setPoReference(String poReference) {
        this.poReference = poReference;
    }

    public LocalDate getInvoiceDate() {
        return invoiceDate;
    }

    // putting this in here is easier for the jsp to use it as a property
    public String getInvoiceDateFormatted() {
        return dateFormatter.format(invoiceDate);
    }

    public void setInvoiceDate(LocalDate invoiceDate) {
        this.invoiceDate = invoiceDate;
    }

    public String getInvoicePeriod() {
        return invoicePeriod;
    }

    public void setInvoicePeriod(String invoicePeriod) {
        this.invoicePeriod = invoicePeriod;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }

    public String getInvoiceAddress() {
        return invoiceAddress;
    }

    public void setInvoiceAddress(String invoiceAddress) {
        this.invoiceAddress = invoiceAddress;
    }

    public boolean getNoVat() {
        return noVat;
    }

    public void setNoVat(boolean noVat) {
        this.noVat = noVat;
    }

    public String getSendTo() {
        return sendTo;
    }

    public void setSendTo(String sendTo) {
        this.sendTo = sendTo;
    }
}
