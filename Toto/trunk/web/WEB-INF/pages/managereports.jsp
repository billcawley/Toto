<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Reports" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
	<h1>Manage Reports</h1>
	<table>
		<thead>
		<tr>
			<!--
            <td>Report id</td>
            <td>Business Id</td>
            -->
			<td>Database</td>
			<td>Author</td>
			<td>&nbsp;&nbsp;&nbsp;&nbsp;</td>
			<td>Report Name</td>
<!--			<td>Category</td> -->
		<!-- <td>File Name</td> -->
			<td style="max-width:500px">Explanation</td>
			<td></td>
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
				<!--
				 <td>${report.id}</td>
				 <td>${report.businessId}</td>
				 -->
				<td>${report.database}</td>
				<td style="white-space: nowrap">${report.author}</td>
				<td></td>
				<!-- should reportid be 1??? -->
				<td style="white-space:nowrap"><c:if test="${report.database != 'None'}"><a href="/api/Online?reportid=${report.id}&amp;database=${report.database}" target="_blank"></c:if>
					<span class="fa fa-table"></span>  ${report.untaggedReportName}<c:if test="${report.database != 'None'}"></a></c:if></td>
				<!-- <td>${report.filename}</td> -->
				<td>${report.explanation}</td>
				<td style="width:100px"><a href="/api/ManageReports?editId=${report.id}"  title="Edit ${report.reportName}" class="button small fa fa-edit"></a>
					<a href="/api/ManageReports?deleteId=${report.id}" onclick="return confirm('Are you sure you want to delete ${report.reportName}?')" class="button small" title="Delete ${report.reportName}"><span class="fa fa-trash" title="Delete"></span> </a>
					<a href="/api/DownloadTemplate?reportId=${report.id}" class="button small" title="Download"><span class="fa fa-download" title="Download"></span> </a>
				</td>
			</tr>
		</c:forEach>
		</tbody>
	</table>
	<h2>Ad Hoc Reports</h2>
	<table>
		<tbody>
		<c:forEach items="${adhoc_reports}" var="report">
			<tr>
				<!--
				 <td>${report.id}</td>
				 <td>${report.businessId}</td>
				 -->
				<td>${report.database}</td>
				<!-- should reportid be 1??? -->
				<td><a href="/api/Online?reportid=${report.id}&amp;database=${report.database}" target="_blank"> <span class="fa fa-table"></span>  ${report.reportName}</a></td>
				<!-- <td>${report.filename}</td> -->
				<td>${report.explanation}</td>
				<td><a href="/api/ManageReports?editId=${report.id}"  title="Edit ${report.reportName}" class="button small fa fa-edit"></a>
					<a href="/api/ManageReports?deleteId=${report.id}" onclick="return confirm('Are you sure you want to delete ${report.reportName}?')" class="button small" title="Delete ${report.reportName}"><span class="fa fa-trash" title="Delete"></span> </a>
					<a href="/api/DownloadTemplate?reportId=${report.id}" class="button small" title="Download"><span class="fa fa-download" title="Download"></span> </a>
				</td>
			</tr>
		</c:forEach>
		<tr>
			<td></td>
			<td><a href="/api/Online?reportid=ADHOC" target="_blank"> <span class="fa fa-table"></span> NEW AD-HOC REPORT</a></td>
		</tr>

		</tbody>
	</table>


<%@ include file="../includes/admin_footer.jsp" %>