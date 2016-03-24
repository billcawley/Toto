package com.azquo.admin.business;

import com.azquo.admin.StandardDAO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 07/01/16. Not relevant to core Azquo functionality, we just needed a way of managing our invoices.
 *
 */
public final class InvoiceDetailsDAO extends StandardDAO<InvoiceDetails> {

    @Override
    public String getTableName() {
        return "invoice_details";
    }

    static final String CUSTOMERREFERENCE = "customer_reference";
    static final String SERVICEDESCRIPTION = "service_description";
    static final String QUANTITY = "quantity";
    static final String UNITCOST = "unit_cost";
    static final String PAYMENTTERMS = "payment_terms";
    static final String POREFERENCE = "po_reference";
    static final String INVOICEDATE = "invoice_date";
    static final String INVOICEPERIOD = "invoice_period";
    static final String INVOICENO = "invoice_no";
    static final String INVOICEADDRESS = "invoice_address";
    static final String NOVAT = "no_vat";
    static final String SENDTO = "send_to";

    @Override
    public Map<String, Object> getColumnNameValueMap(final InvoiceDetails invoiceDetails) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(ID, invoiceDetails.getId());
        toReturn.put(CUSTOMERREFERENCE, invoiceDetails.getCustomerReference());
        toReturn.put(SERVICEDESCRIPTION, invoiceDetails.getServiceDescription());
        toReturn.put(QUANTITY, invoiceDetails.getQuantity());
        toReturn.put(UNITCOST, invoiceDetails.getUnitCost());
        toReturn.put(PAYMENTTERMS, invoiceDetails.getPaymentTerms());
        toReturn.put(POREFERENCE, invoiceDetails.getPoReference());
        toReturn.put(INVOICEDATE, Date.from(invoiceDetails.getInvoiceDate().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant())); // new date classes and old date ones used by jdbc don't seem to play nice
        toReturn.put(INVOICEPERIOD, invoiceDetails.getInvoicePeriod());
        toReturn.put(INVOICENO, invoiceDetails.getInvoiceNo());
        toReturn.put(INVOICEADDRESS, invoiceDetails.getInvoiceAddress());
        toReturn.put(NOVAT, invoiceDetails.getNoVat());
        toReturn.put(SENDTO, invoiceDetails.getSendTo());
        return toReturn;
    }

    private final class InvoiceDetailsRowMapper implements RowMapper<InvoiceDetails> {
        @Override
        public InvoiceDetails mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new InvoiceDetails(rs.getInt(ID)
                        , rs.getString(CUSTOMERREFERENCE)
                        , rs.getString(SERVICEDESCRIPTION)
                        , rs.getInt(QUANTITY)
                        , rs.getInt(UNITCOST)
                        , rs.getInt(PAYMENTTERMS)
                        , rs.getString(POREFERENCE)
                        , getLocalDateTimeFromDate(rs.getDate(INVOICEDATE)).toLocalDate()
                        , rs.getString(INVOICEPERIOD)
                        , rs.getString(INVOICENO)
                        , rs.getString(INVOICEADDRESS)
                        , rs.getBoolean(NOVAT)
                        , rs.getString(SENDTO)
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    @Override
    public RowMapper<InvoiceDetails> getRowMapper() {
        return new InvoiceDetailsRowMapper();
    }

/*    public Business findByName(final String businessName) {
        final MapSqlParameterSource namedParams = new MapSqlParameterSource();
        namedParams.addValue(BUSINESSNAME, businessName);
        return findOneWithWhereSQLAndParameters(" WHERE `" + BUSINESSNAME + "` = :" + BUSINESSNAME, namedParams);
    }*/
}
