package com.azquo.admin.business;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 12/01/16.
 */
public class InvoiceSent extends InvoiceDetails {

    final LocalDateTime dateTimeSent;

    public InvoiceSent(int id, String customerReference, String serviceDescription, int quantity, int unitCost, int paymentTerms, String poReference, LocalDate invoiceDate, String invoicePeriod, String invoiceNo, String invoiceAddress, boolean noVat, String sendTo, LocalDateTime dateTimeSent) {
        super(id, customerReference, serviceDescription, quantity, unitCost, paymentTerms, poReference, invoiceDate, invoicePeriod, invoiceNo, invoiceAddress, noVat, sendTo);
        this.dateTimeSent = dateTimeSent;
    }

    public LocalDateTime getDateTimeSent() {
        return dateTimeSent;
    }
}
