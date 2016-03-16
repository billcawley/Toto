<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Invoices" />
<%@ include file="../includes/admin_header.jsp" %>
<script type="text/javascript">
    function setInvoiceNumbers(){
        var start = ${startInvoicesAt};
        <c:forEach items="${invoiceDetailsList}" var="invoiceDetails">
        if (document.getElementById("customerreference${invoiceDetails.id}").value != ''){
            document.getElementById("invoiceno${invoiceDetails.id}").value = document.getElementById("customerreference${invoiceDetails.id}").value + "-" + start;
        } else {
            document.getElementById("invoiceno${invoiceDetails.id}").value = start;
        }
        start++;
        </c:forEach>
    }

    function setTodaysDate(){
        <c:forEach items="${invoiceDetailsList}" var="invoiceDetails">
        document.getElementById("invoicedate${invoiceDetails.id}").value = '${todaysDate}';
        </c:forEach>
    }

    function setBeginningOfMonth(){
        <c:forEach items="${invoiceDetailsList}" var="invoiceDetails">
        document.getElementById("invoicedate${invoiceDetails.id}").value = '${beginningOfMonth}';
        </c:forEach>
    }


</script>
<main>
    <h1>Manage Invoices To Send</h1><br/>
    <form action="/api/ManageInvoiceDetails" method="post">
        ${error}
        <table>
            <thead>
            <tr>
                <td>Invoice No</td>
                <td>Customer Ref</td>
                <td>Service Description</td>
                <td>Quantity</td>
                <td>Unit Cost</td>
                <td>Payment Terms</td>
                <td>PO Reference</td>
                <td>Invoice Date</td>
                <td>Invoice Period</td>
                <td>Invoice Address</td>
                <td>Exclude VAT</td>
                <td>Send To</td>
                <td>Send Now</td>
                <td>Delete</td>
            </tr>
            </thead>
            <tbody>
            <c:forEach items="${invoiceDetailsList}" var="invoiceDetails">
                <tr>
                    <td><input name="invoiceno${invoiceDetails.id}" id="invoiceno${invoiceDetails.id}" size="10" value="${invoiceDetails.invoiceNo}"/></td>
                    <td><input name="customerreference${invoiceDetails.id}" id="customerreference${invoiceDetails.id}" size="10" value="${invoiceDetails.customerReference}"/></td>
                    <td><input name="servicedescription${invoiceDetails.id}" value="${invoiceDetails.serviceDescription}"/></td>
                    <td><input name="quantity${invoiceDetails.id}" size="3" value="${invoiceDetails.quantity}"/></td>
                    <td><input name="unitcost${invoiceDetails.id}"  size="6" value="${invoiceDetails.unitCost}"/></td>
                    <td><input name="paymentterms${invoiceDetails.id}"  size="3" value="${invoiceDetails.paymentTerms}"/></td>
                    <td><input name="poreference${invoiceDetails.id}" size="10" value="${invoiceDetails.poReference}"/></td>
                    <td><input name="invoicedate${invoiceDetails.id}" id="invoicedate${invoiceDetails.id}"  size="10" value="${invoiceDetails.invoiceDateFormatted}"/></td>
                    <td><input name="invoiceperiod${invoiceDetails.id}" value="${invoiceDetails.invoicePeriod}"/></td>
                    <td><textarea name="invoiceaddress${invoiceDetails.id}" rows="3" cols="20">${invoiceDetails.invoiceAddress}</textarea></td>
                    <td><input name="novat${invoiceDetails.id}" type="checkbox" <c:if test="${invoiceDetails.noVat}"> checked</c:if>/></td>
                    <td><input name="sendto${invoiceDetails.id}" value="${invoiceDetails.sendTo}"/></td>
                    <td><input name="sendnow${invoiceDetails.id}" type="checkbox"/></td>
                    <td><a href="/api/ManageInvoiceDetails?deleteId=${invoiceDetails.id}" class="button small alt" title="Delete ${report.reportName}"><span class="fa fa-trash" title="Delete"></span> </a></td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
        <div class="centeralign">
            <input type="submit" name="submit" value="Save Changes" class="button"/>
            <a href="/api/ManageInvoiceDetails?new=true" class="button alt">Add new invoice</a>
            <button onclick="setInvoiceNumbers(); return false;" class="button alt">Assign Invoice Numbers</button>
            <button onclick="setTodaysDate(); return false;" class="button alt">Assign Today's Date</button>
            <button onclick="setBeginningOfMonth(); return false;" class="button alt">Assign Beginning Of The Month</button>
        </div>
    </form>
</main>
<%@ include file="../includes/admin_footer.jsp" %>
