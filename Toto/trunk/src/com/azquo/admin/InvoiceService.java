package com.azquo.admin;

import com.azquo.admin.business.InvoiceDetails;
import com.azquo.admin.business.InvoiceSent;
import com.azquo.admin.business.InvoiceSentDAO;
import com.azquo.util.AzquoMailer;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.mail.EmailAttachment;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.MimeConstants;
import org.springframework.beans.factory.annotation.Autowired;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.StringTokenizer;

/**
 * Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
 *
 * Created by edward on 11/01/16.
 * Code moved from the old JSP, probably should be tidied in time. Hacky code for invoices not part of standard Azquo functionality.
 */
public class InvoiceService {

    @Autowired
    AzquoMailer azquoMailer;

    @Autowired
    InvoiceSentDAO invoiceSentDAO;

    public boolean sendInvoiceEmail(InvoiceDetails invoiceDetails) throws TransformerException, IOException, FOPException {
        // note : I'm not allowing currency to be overridden
        double currencyValue = invoiceDetails.getUnitCost() * invoiceDetails.getQuantity();
        double vat = currencyValue * 0.2;
        if (invoiceDetails.getNoVat()){
            vat = 0;
        }
        double total = currencyValue + vat;


// -           SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy");

        // check here that we won't get a keyword error on month!!!!!

/*    String invoicePeriod;
    String ponumber = "";
    invoicePeriod = month;*/

        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        EmailAttachment emailAttachment = new EmailAttachment();
        emailAttachment.setDescription("AzquoInvoice" + dateFormat.format(invoiceDetails.getInvoiceDate()));
        emailAttachment.setName("AzquoInvoice" + dateFormat.format(invoiceDetails.getInvoiceDate()) + ".pdf");

        FopFactory fopFactory = FopFactory.newInstance();
        TransformerFactory tFactory = TransformerFactory.newInstance();
//        response.setContentType("application/pdf");
        String invoiceAddress = invoiceDetails.getInvoiceAddress();
        //String address1 = invoiceAddress.contains("\n") ? invoiceAddress.substring(0, invoiceAddress.indexOf("\n")) : invoiceAddress;
        //address1 = address1.contains("\r") ? address1.substring(0, address1.indexOf("\r")) : address1;
//        response.addHeader("Content-Disposition", "attachment; filename=AzquoInvoice" + address1 + month + ".pdf");
        File temp = File.createTempFile(emailAttachment.getName(), "pdf");
        emailAttachment.setPath(temp.getPath()); // I think this will do it?
        FileOutputStream fos = new FileOutputStream(temp);
        Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF,  fos);
        Transformer transformer = tFactory.newTransformer();
        StringBuilder template = new StringBuilder();
        String inputLine;
        try {
            DataInputStream in = new DataInputStream(getClass().getClassLoader().getResourceAsStream("azquoinvoice.fop"));// used to have a hard coded link, now it's moved to /src this should be better
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            while ((inputLine = br.readLine()) != null) {
                // Print the content on the console
                template.append(inputLine);
            }
            //Close the input stream
            in.close();
        } catch (Exception e) {//Catch exception if any
            System.out.println("Error reading azquoinvoice.fop: " + e.getMessage());
        }
        String invoiceFOP = template.toString();
        NumberFormat nf = NumberFormat.getInstance();
        nf.setMinimumFractionDigits(2);
        int termsInt = invoiceDetails.getPaymentTerms();
        LocalDate dateDue = invoiceDetails.getInvoiceDate().plusDays(termsInt);
        DateTimeFormatter monthDisplay = DateTimeFormatter.ofPattern("MMMMM");
        //invoiceFOP = invoiceFOP.replace("INVOICEMONTH",monthDisplay.format(i.getDate()), true);
        String invoiceTitle = "Monthly fee for developmennt of RaceAdvisor App";
        invoiceFOP = invoiceFOP.replace("INVOICETITLE", StringEscapeUtils.escapeXml(invoiceTitle));
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String dateDueI = StringEscapeUtils.escapeXml(df.format(dateDue));
        String paidWithThanks = "";
        //if (i.getDatePaid() !=null) paidWithThanks = "Paid with thanks " + Database.dateFormat2.format(i.getDatePaid());
        invoiceFOP = invoiceFOP.replace("INVOICENAME", "INVOICE");
        invoiceFOP = invoiceFOP.replace("PAIDWITHTHANKS", paidWithThanks);
        invoiceFOP = invoiceFOP.replace("DATEDUE", dateDueI);
        invoiceFOP = invoiceFOP.replace("PONUMBER", StringEscapeUtils.escapeXml(invoiceDetails.getPoReference()));
        int lineNo = 2;
        invoiceFOP = invoiceFOP.replace("INVOICEL1", "");
        invoiceFOP = invoiceFOP.replace("INVOICEPER1", invoiceDetails.getInvoicePeriod());
        invoiceFOP = invoiceFOP.replace("INVOICEDESC1", invoiceDetails.getServiceDescription());
        invoiceFOP = invoiceFOP.replace("INVOICEQUANT1", invoiceDetails.getQuantity() + "");
        invoiceFOP = invoiceFOP.replace("INVOICEMON1", invoiceDetails.getUnitCost() + "");
        invoiceFOP = invoiceFOP.replace("INVOICEN1", nf.format(currencyValue));

        // hack for 2 lines . . .
/*    currencyValue += 1100;
    vat = currencyValue * 0.2;
    total = currencyValue + vat;

    invoiceFOP = invoiceFOP.replace("INVOICEL2", "");
    invoiceFOP = invoiceFOP.replace("INVOICEPER2", month);
    invoiceFOP = invoiceFOP.replace("INVOICEDESC2", "Days modifying daily_racing_stats.php and add_betfair_ids.php");
    invoiceFOP = invoiceFOP.replace("INVOICEQUANT2", "2.0");
    invoiceFOP = invoiceFOP.replace("INVOICEMON2", "550");
    invoiceFOP = invoiceFOP.replace("INVOICEN2", nf.format(1100.0));

    // hack for 2 lines . . .
    currencyValue += 1833;
    vat = currencyValue * 0.2;
    total = currencyValue + vat;
    lineNo = 4;
    invoiceFOP = invoiceFOP.replace("INVOICEL3", "");
    invoiceFOP = invoiceFOP.replace("INVOICEPER3", month);
    invoiceFOP = invoiceFOP.replace("INVOICEDESC3", "InfusionSoft App Payment");
    invoiceFOP = invoiceFOP.replace("INVOICEQUANT3", "1");
    invoiceFOP = invoiceFOP.replace("INVOICEMON3", "1833");
    invoiceFOP = invoiceFOP.replace("INVOICEN3", nf.format(1833.0));*/

        while (lineNo < 10) {
            invoiceFOP = invoiceFOP.replace("INVOICEL" + lineNo, "");
            invoiceFOP = invoiceFOP.replace("INVOICEPER" + lineNo, "");
            invoiceFOP = invoiceFOP.replace("INVOICEDESC" + lineNo, "");
            invoiceFOP = invoiceFOP.replace("INVOICEQUANT" + lineNo, "");
            invoiceFOP = invoiceFOP.replace("INVOICEMON" + lineNo, "");
            invoiceFOP = invoiceFOP.replace("INVOICEN" + lineNo, "");
            lineNo++;

        }

        invoiceFOP = invoiceFOP.replace("INVOICECURRENCY", StringEscapeUtils.escapeXml("GBP"));
        invoiceFOP = invoiceFOP.replace("INVOICEDATE", StringEscapeUtils.escapeXml(df.format(invoiceDetails.getInvoiceDate())));
        String iNo = invoiceDetails.getInvoiceNo();
        invoiceFOP = invoiceFOP.replace("INVOICENUMBER", StringEscapeUtils.escapeXml(iNo));
        invoiceFOP = invoiceFOP.replace("INVOICEVAT", StringEscapeUtils.escapeXml(nf.format(vat)));
        invoiceFOP = invoiceFOP.replace("INVOICEAMOUNT", StringEscapeUtils.escapeXml(nf.format(currencyValue)));
        invoiceFOP = invoiceFOP.replace("INVOICETOTAL", StringEscapeUtils.escapeXml(nf.format(total)));
        invoiceFOP = invoiceFOP.replace("INVOICESAGEREF", StringEscapeUtils.escapeXml(invoiceDetails.getCustomerReference()));


        String text = invoiceFOP;
        //invoiceAddress = invoiceAddress.replace("&","&#38;");
        String address = invoiceAddress.trim();
        int line = 1;
        StringTokenizer st = new StringTokenizer(address, "\n");
        while (st.hasMoreTokens()) {
            text = text.replace("ADDRESS" + line, StringEscapeUtils.escapeXml(st.nextToken()));
            line++;
        }
        text = text.replace("ADDRESS" + line, ""); // blank other lines . . .
        line++;
        text = text.replace("ADDRESS" + line, ""); // blank other lines . . .
        line++;
        text = text.replace("ADDRESS" + line, ""); // blank other lines . . .
        line++;
        text = text.replace("ADDRESS" + line, ""); // blank other lines . . .
        line++;

        text = text.replace("PAYMENTTERMS", termsInt + " Days");

        invoiceFOP = text;


        Source src = new StreamSource(new StringReader(invoiceFOP));
        Result res = new SAXResult(fop.getDefaultHandler());

        transformer.transform(src, res);
        fos.close();
        if (azquoMailer.sendEMail(invoiceDetails.getSendTo(), invoiceDetails.getSendTo(), "Azquo Invoice " + invoiceDetails.getInvoiceNo(), "Please find attached", null, Collections.singletonList(emailAttachment))){
            InvoiceSent invoiceSent = new InvoiceSent(0,
                    invoiceDetails.getCustomerReference(),
                    invoiceDetails.getServiceDescription(),
                    invoiceDetails.getQuantity(),
                    invoiceDetails.getUnitCost(),
                    invoiceDetails.getPaymentTerms(),
                    invoiceDetails.getPoReference(),
                    invoiceDetails.getInvoiceDate(),
                    invoiceDetails.getInvoicePeriod(),
                    invoiceDetails.getInvoiceNo(),
                    invoiceDetails.getInvoiceAddress(),
                    invoiceDetails.getNoVat(),
                    invoiceDetails.getSendTo(),
                    LocalDateTime.now());
            invoiceSentDAO.store(invoiceSent);
            return true;
        } else {
            System.out.println("Failed sending invoice . . .");
            return false;
        }
    }
}
