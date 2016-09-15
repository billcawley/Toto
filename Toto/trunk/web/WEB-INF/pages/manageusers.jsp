<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Users" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
	<h1>Manage Users</h1>
		
	<table>
		<thead>
			<tr>
		    	<td>User Email</td>
				<td>Name</td>
				<td>End Date</td>
				<!--<td>Business Id</td>-->
				
				<td>Status</td>
				<td width="30"></td>
				<td width="30"></td>
				<!-- password and salt pointless here -->
			</tr>
		</thead>
		<tbody>
		<c:forEach items="${users}" var="user">
			<tr>
				<td>${user.email}</td>
				<td>${user.name}</td>

				<td>${user.endDate}</td>
				<!--<td>${user.businessId}</td>-->
				
				<td>${user.status}</td>
				<td><a href="/api/ManageUsers?deleteId=${user.id}" title="Delete ${user.name}" onclick="return confirm('Are you sure?')" class="button small alt fa fa-trash"></a></td>
				<td><a href="/api/ManageUsers?editId=${user.id}" title="Edit ${user.name}" class="button small fa fa-edit"></a></td>
			</tr>
		</c:forEach>
		</tbody>
	</table>

	<div class="centeralign">
		<a href="/api/ManageUsers?editId=0" class="button"><span class="fa fa-plus-circle"></span> Add New User</a>&nbsp;<c:if test="${showDownload}"><a href="/api/CreateExcelForDownload" class="button">Download Users and Permissions as Excel</a></c:if>
	</div>
</main>
<%@ include file="../includes/admin_footer.jsp" %>