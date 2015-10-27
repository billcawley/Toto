<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Reports" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
<h1>Manage Reports</h1>
	<form action="/api/ManageReports" method="post">
		<table>
			<thead>
				<tr>
					<!--            
					<td>Report id</td>
					<td>Business Id</td>
					-->
					<td>Database</td>
					<td>Report Name</td>
					<td>Business Type</td>
					<td>Report Category</td>
					<td>User Groups</td>
					<!-- <td>File Name</td> -->
					<td>Explanation</td>
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
				 <td><a href="/api/Online?reportid=1&amp;opcode=loadsheet&amp;reporttoload=${report.id}&amp;database=${report.database}" target="_blank"> <span class="fa fa-table"></span>  ${report.reportName}</a></td>
				 <td><input name="businessType${report.id}" value="${report.databaseType}" /></td>
				 <td><input name="reportCategory${report.id}" value="${report.reportCategory}" /></td>
				 <td><input name="userStatus${report.id}" value="${report.userStatus}" /></td>
				 <!-- <td>${report.filename}</td> -->
				 <td><textarea name="explanation${report.id}" rows="3">${report.explanation}</textarea></td>
			</tr>
		</c:forEach>
		</tbody>
		</table>
		
		<div class="centeralign">
			<input type="submit" value="Save Changes" class="button"/>
		</div>
	</form>
</main>


<%@ include file="../includes/admin_footer.jsp" %>