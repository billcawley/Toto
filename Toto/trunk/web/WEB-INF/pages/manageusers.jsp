<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Users" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
	<h1>Manage Users</h1>
	<div class="error">${error}</div>
	<table>
		<thead>
			<tr>
		    	<td>User Email</td>
				<td>Name</td>
				<td>End Date</td>
				<!--<td>Business Id</td>-->
				
				<td>Status</td>
				<td>Start Menu</td>
				<td>Database</td>
				<td>Selections</td>
				<td>Recent Activity</td>
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
				<td>${user.reportName}</td>
				<td>${user.databaseName}</td>
				<td>${user.selections}</td>
				<td><a href="/api/ManageUsers?recentId=${user.id}">${user.recentActivity}</a></td>
				<td><a href="/api/ManageUsers?deleteId=${user.id}" title="Delete ${user.name}" onclick="return confirm('Are you sure?')" class="button small alt fa fa-trash"></a></td>
				<td><a href="/api/ManageUsers?editId=${user.id}" title="Edit ${user.name}" class="button small fa fa-edit"></a></td>
			</tr>
		</c:forEach>
		</tbody>
	</table>

	<div class="centeralign">
		<a href="/api/ManageUsers?editId=0" class="button"><span class="fa fa-plus-circle"></span> Add New User</a>&nbsp;
		<a href="/api/CreateExcelForDownload?action=DOWNLOADUSERS" class="button">Download Users as Excel</a> &nbsp;
		<form action="/api/ManageUsers" method="post" enctype="multipart/form-data"><input type="submit" name="Upload" value="Upload User List" class="button "/>&nbsp;
		<input id="uploadFile" type="file" name="uploadFile"></form>
	</div>
</main>
<%@ include file="../includes/admin_footer.jsp" %>