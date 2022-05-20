<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Reports" />
<%@ include file="../includes/admin_header2.jsp" %>
<div class="box">
<!--    <h1 class="title">Manage Reports</h1> -->
    <table class="table is-striped is-fullwidth">
        <thead>
        <tr>
            <th>Database</th>
            <th>Author</th>
            <th>&nbsp;&nbsp;&nbsp;&nbsp;</th>
            <th>Report Name</th>
<c:if test="${showexplanation}">
            <th>Explanation</th>
</c:if>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${reports}" var="report">
        <c:if test="${report.category != ''}">

        <tr>
            <td></td>
            <td></td>
            <td colspan="2"><b>${report.category}</b></td>
            <td></td>
            <td></td>
        </tr>
        </c:if>
        <tr>
            <td>${report.database}</td>
            <td style="white-space: nowrap;">${report.author}</td>
            <td></td>
            <td><c:if test="${report.database != 'None'}"><a href="/api/Online?reportid=${report.id}&amp;database=${report.database}" target="_blank"></c:if>
                <span class="fa fa-table"></span>  ${report.untaggedReportName}<c:if test="${report.database != 'None'}"></a></c:if></td>
            <c:if test="${showexplanation}">
            <td>${report.explanation}</td>
            </c:if>
            <td style="white-space: nowrap;">
                <a href="/api/ManageReports?editId=${report.id}"  title="Edit ${report.reportName}" class="button is-small"><span class="fa fa-edit" title="Edit"></span></a>
                <a href="/api/ManageReports?deleteId=${report.id}" onclick="return confirm('Are you sure you want to delete ${report.reportName}?')" title="Delete ${report.reportName}" class="button is-small"><span class="fa fa-trash" title="Delete"></span> </a>
                <a href="/api/DownloadTemplate?reportId=${report.id}" title="Download" class="button is-small"><span class="fa fa-download" title="Download"></span> </a>
            </td>
        </tr>
        </c:forEach>
        </tbody>
    </table>
    Designed reports
    <table class="table is-striped is-fullwidth">
        <tbody>
        <c:forEach items="${adhoc_reports}" var="report">
            <tr>
                <td>${report.database}</td>
                <td><a href="/api/Online?reportid=${report.id}&amp;database=${report.database}" target="_blank"> <span class="fa fa-table"></span>  ${report.reportName}</a></td>
                <td>${report.explanation}</td>
                <td>
                    <c:if test="${sessionScope.test != null}">

                    <a href="/api/ManageReports?editId=${report.id}"  title="Edit ${report.reportName}" class="button small fa fa-edit"></a>
                    </c:if>
                    <a href="/api/ManageReports?deleteId=${report.id}" onclick="return confirm('Are you sure you want to delete ${report.reportName}?')" class="button is-small" title="Delete ${report.reportName}"><span class="fa fa-trash" title="Delete"></span> </a>
                    <a href="/api/DownloadTemplate?reportId=${report.id}" class="button is-small" title="Download"><span class="fa fa-download" title="Download"></span> </a>
                </td>
            </tr>
        </c:forEach>
        <tr>
            <td></td>
            <c:if test="${sessionScope.test != null}">
                <td><a href="/api/ManageReports?createnewreport=tobeentered" target="_blank"> <span class="fa fa-table"></span> NEW REPORT</a></td>
            </c:if>
        </tr>
        </tbody>
    </table>
</div>
<%@ include file="../includes/admin_footer.jsp" %>
