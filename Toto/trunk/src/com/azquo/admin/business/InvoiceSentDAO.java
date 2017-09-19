package com.azquo.admin.business;

import com.azquo.DateUtils;
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
 * Created by edward on 12/01/16.
 */
public final class InvoiceSentDAO  {

    private static final String TABLENAME = "invoice_sent";

    private static final String DATETIMESENT = "date_time_sent";

    public static Map<String, Object> getColumnNameValueMap(final InvoiceSent invoiceSent) {
        final Map<String, Object> toReturn = new HashMap<>();
        toReturn.put(StandardDAO.ID, invoiceSent.getId());
        toReturn.put(InvoiceDetailsDAO.CUSTOMERREFERENCE, invoiceSent.getCustomerReference());
        toReturn.put(InvoiceDetailsDAO.SERVICEDESCRIPTION, invoiceSent.getServiceDescription());
        toReturn.put(InvoiceDetailsDAO.QUANTITY, invoiceSent.getQuantity());
        toReturn.put(InvoiceDetailsDAO.UNITCOST, invoiceSent.getUnitCost());
        toReturn.put(InvoiceDetailsDAO.PAYMENTTERMS, invoiceSent.getPaymentTerms());
        toReturn.put(InvoiceDetailsDAO.POREFERENCE, invoiceSent.getPoReference());
        toReturn.put(InvoiceDetailsDAO.INVOICEDATE, DateUtils.getDateFromLocalDateTime(invoiceSent.getInvoiceDate().atStartOfDay())); // new date classes and old date ones used by jdbc don't seem to play nice
        toReturn.put(InvoiceDetailsDAO.INVOICEPERIOD, invoiceSent.getInvoicePeriod());
        toReturn.put(InvoiceDetailsDAO.INVOICENO, invoiceSent.getInvoiceNo());
        toReturn.put(InvoiceDetailsDAO.INVOICEADDRESS, invoiceSent.getInvoiceAddress());
        toReturn.put(InvoiceDetailsDAO.NOVAT, invoiceSent.getNoVat());
        toReturn.put(InvoiceDetailsDAO.SENDTO, invoiceSent.getSendTo());
        toReturn.put(DATETIMESENT, DateUtils.getDateFromLocalDateTime(invoiceSent.getDateTimeSent()));
        return toReturn;
    }

    private static final class InvoiceSentRowMapper implements RowMapper<InvoiceSent> {
        @Override
        public InvoiceSent mapRow(final ResultSet rs, final int row) throws SQLException {
            try {
                return new InvoiceSent(rs.getInt(StandardDAO.ID)
                        , rs.getString(InvoiceDetailsDAO.CUSTOMERREFERENCE)
                        , rs.getString(InvoiceDetailsDAO.SERVICEDESCRIPTION)
                        , rs.getInt(InvoiceDetailsDAO.QUANTITY)
                        , rs.getInt(InvoiceDetailsDAO.UNITCOST)
                        , rs.getInt(InvoiceDetailsDAO.PAYMENTTERMS)
                        , rs.getString(InvoiceDetailsDAO.POREFERENCE)
                        , DateUtils.getLocalDateTimeFromDate(rs.getDate(InvoiceDetailsDAO.INVOICEDATE)).toLocalDate()
                        , rs.getString(InvoiceDetailsDAO.INVOICEPERIOD)
                        , rs.getString(InvoiceDetailsDAO.INVOICENO)
                        , rs.getString(InvoiceDetailsDAO.INVOICEADDRESS)
                        , rs.getBoolean(InvoiceDetailsDAO.NOVAT)
                        , rs.getString(InvoiceDetailsDAO.SENDTO)
                        , DateUtils.getLocalDateTimeFromDate(rs.getDate(InvoiceDetailsDAO.INVOICEDATE))
                );
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    private static final InvoiceSentRowMapper invoiceSentRowmapper = new InvoiceSentRowMapper();

    public static InvoiceSent findById(int id){
        return StandardDAO.findById(id, TABLENAME, invoiceSentRowmapper);
    }

    public static void removeById(InvoiceSent invoiceSent){
        StandardDAO.removeById(invoiceSent, TABLENAME);
    }

    public static void store(InvoiceSent invoiceSent){
        StandardDAO.store(invoiceSent, TABLENAME, getColumnNameValueMap(invoiceSent));
    }
}
