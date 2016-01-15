package com.azquo.admin.controller;

import com.azquo.admin.InvoiceService;
import com.azquo.admin.business.InvoiceDetails;
import com.azquo.admin.business.InvoiceDetailsDAO;
import com.azquo.spreadsheet.LoggedInUser;
import com.azquo.spreadsheet.SpreadsheetService;
import com.azquo.spreadsheet.controller.LoginController;
import org.apache.fop.apps.FOPException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Created by edward on 07/01/16.
 *
 */

@Controller
@RequestMapping("/ManageInvoiceDetails")
public class ManageInvoiceDetailsController {

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private InvoiceDetailsDAO invoiceDetailsDao;
    private static final Logger logger = Logger.getLogger(ManageReportsController.class);

    @RequestMapping
    public String handleRequest(ModelMap model, HttpServletRequest request
    ) throws TransformerException, IOException, FOPException

    {
        LoggedInUser loggedInUser = (LoggedInUser) request.getSession().getAttribute(LoginController.LOGGED_IN_USER_SESSION);

        if (loggedInUser == null
                || !loggedInUser.getUser().getEmail().contains("@azquo.com")
                ) {
            return "redirect:/api/Login";
        } else {
            if (request.getParameter("new") != null){
                InvoiceDetails invoiceDetails = new InvoiceDetails(0, "", "", 0, 0, 0, "", LocalDate.now(), "", "", "", false, "");
                invoiceDetailsDao.store(invoiceDetails);
            }
            List<InvoiceDetails> invoiceDetailsList = invoiceDetailsDao.findAll();
            StringBuilder error = new StringBuilder();
            if (request.getParameter("submit") != null){
                for (InvoiceDetails invoiceDetails : invoiceDetailsList) {
                    boolean store = false;
                    String customerReference = request.getParameter("customerreference" + invoiceDetails.getId());
                    if (customerReference != null && !customerReference.equals(invoiceDetails.getCustomerReference())) {
                        invoiceDetails.setCustomerReference(customerReference);
                        store = true;
                    }
                    String serviceDescription = request.getParameter("servicedescription" + invoiceDetails.getId());
                    if (serviceDescription != null && !serviceDescription.equals(invoiceDetails.getServiceDescription())) {
                        invoiceDetails.setServiceDescription(serviceDescription);
                        store = true;
                    }
                    int quantity = ServletRequestUtils.getIntParameter(request, "quantity" + invoiceDetails.getId(), 0);
                    if (quantity != invoiceDetails.getQuantity()) {
                        invoiceDetails.setQuantity(quantity);
                        store = true;
                    }
                    int unitCost = ServletRequestUtils.getIntParameter(request, "unitcost" + invoiceDetails.getId(), 0);
                    if (unitCost != invoiceDetails.getUnitCost()) {
                        invoiceDetails.setUnitCost(unitCost);
                        store = true;
                    }
                    int paymentTerms = ServletRequestUtils.getIntParameter(request, "paymentterms" + invoiceDetails.getId(), 0);
                    if (paymentTerms != invoiceDetails.getPaymentTerms()) {
                        invoiceDetails.setPaymentTerms(paymentTerms);
                        store = true;
                    }
                    String poReference = request.getParameter("poreference" + invoiceDetails.getId());
                    if (poReference != null && !poReference.equals(invoiceDetails.getPoReference())) {
                        invoiceDetails.setPoReference(poReference);
                        store = true;
                    }
                    String invoiceDate = request.getParameter("invoicedate" + invoiceDetails.getId());
                    try{
                        if (!invoiceDetails.getInvoiceDateFormatted().equals(invoiceDate)){
                            invoiceDetails.setInvoiceDate(LocalDate.parse(invoiceDate, InvoiceDetails.dateFormatter));
                            store = true;
                        }
                    } catch (DateTimeParseException e) {
                        error.append("Invoice Date format not yyyy-MM-dd<br/>");
                    }

                    String invoicePeriod = request.getParameter("invoiceperiod" + invoiceDetails.getId());
                    if (invoicePeriod != null && !invoicePeriod.equals(invoiceDetails.getInvoicePeriod())) {
                        invoiceDetails.setInvoicePeriod(invoicePeriod);
                        store = true;
                    }

                    String invoiceNo = request.getParameter("invoiceno" + invoiceDetails.getId());
                    if (invoiceNo!= null && !invoiceNo.equals(invoiceDetails.getInvoiceNo())) {
                        invoiceDetails.setInvoiceNo(invoiceNo);
                        store = true;
                    }

                    String invoiceAddress = request.getParameter("invoiceaddress" + invoiceDetails.getId());
                    if (invoiceAddress != null && !invoiceAddress.equals(invoiceDetails.getInvoiceAddress())) {
                        invoiceDetails.setInvoiceAddress(invoiceAddress);
                        store = true;
                    }

                    boolean novat = ServletRequestUtils.getBooleanParameter(request, "novat" + invoiceDetails.getId(), false);
                    if (novat != invoiceDetails.getNoVat()) {
                        invoiceDetails.setNoVat(novat);
                        store = true;
                    }

                    boolean sendnow = ServletRequestUtils.getBooleanParameter(request, "sendnow" + invoiceDetails.getId(), false);

                    String sendTo = request.getParameter("sendto" + invoiceDetails.getId());
                    if (sendTo != null && !sendTo.equals(invoiceDetails.getSendTo())) {
                        invoiceDetails.setSendTo(sendTo);
                        store = true;
                    }
                    if (store) {
                        invoiceDetailsDao.store(invoiceDetails);
                    }
                    if (sendnow){
                        invoiceService.sendInvoiceEmail(invoiceDetails);
                    }
                }
            }
                int deleteId = ServletRequestUtils.getIntParameter(request, "deleteId", 0);
            if (deleteId != 0){
                InvoiceDetails invoiceDetails = invoiceDetailsDao.findById(deleteId);
                if (invoiceDetails != null){
                    invoiceDetailsDao.removeById(invoiceDetails);
                    invoiceDetailsList = invoiceDetailsDao.findAll(); // and refresh the list!
                }
            }
            model.put("invoiceDetailsList", invoiceDetailsList);
            if (error.length() > 0){
                model.put("error", error.toString());
            }
            model.put("startInvoicesAt", invoiceDetailsDao.findMaxId() + 1);
            model.put("todaysDate", InvoiceDetails.dateFormatter.format(LocalDate.now()));
            model.put("beginningOfMonth", InvoiceDetails.dateFormatter.format(LocalDate.now().withDayOfMonth(1)));
            return "manageinvoicedetails";
        }
    }
}