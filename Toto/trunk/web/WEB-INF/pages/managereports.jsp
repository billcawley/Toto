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
					<td>Report Name</td>
					<!-- <td>File Name</td> -->
                    <td>Explanation</td>
                    <td></td>
				</tr>
			</thead>
		<tbody>
		<c:forEach items="${reports}" var="report">
			<tr>
				 <!--            
				 <td>${report.id}</td>
				 <td>${report.businessId}</td>
				 -->
				 <td>${report.database}</td>
				 <!-- should reportid be 1??? -->
				 <td><a href="/api/Online?oreportid=${report.id}&amp;database=${report.database}" target="_blank"> <span class="fa fa-table"></span>  ${report.reportName}</a></td>
				 <!-- <td>${report.filename}</td> -->
				 <td>${report.explanation}</td>
				 <td><a href="/api/ManageReports?editId=${report.id}"  title="Edit ${report.reportName}" class="button small fa fa-edit"></a>
				 <a href="/api/ManageReports?deleteId=${report.id}" onclick="return confirm('Are you sure you want to delete ${report.reportName}?')" class="button small alt" title="Delete ${report.reportName}"><span class="fa fa-trash" title="Delete"></span> </a>
				 </td>
			</tr>
		</c:forEach>
		</tbody>
		</table>
</main>


<%@ include file="../includes/admin_footer.jsp" %>