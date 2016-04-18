<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Permissions" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
	<h1>Manage Permissions</h1>
	
	<table>
		<thead>
			<tr>
				<td>Database</td>
				<td>Report</td>
				<td>Email</td>
				<td>Read List</td>
				<td>Write List</td>
				<td width="30"></td>
				<td width="30"></td>
				<!-- password and salt pointless here -->
			</tr>
		</thead>
		<tbody>
		<c:forEach items="${permissions}" var="permission">
			<tr>
				<td>${permission.databaseName}</td>
				<td>${permission.reportId}</td>
				<td>${permission.userEmail}</td>
				<td>${permission.readList}</td>
				<td>${permission.writeList}</td>
				<td><a href="/api/ManagePermissions?deleteId=${permission.id}" title="Delete Permission" onclick="return confirm('Are you sure?')" class="button small alt fa fa-trash"></a></td>
				<td><a href="/api/ManagePermissions?editId=${permission.id}" title="Edit Permission" class="button small fa fa-edit"></a></td>
			</tr>
		</c:forEach>
	</table>
	
	<div class="centeralign">
		<a href="/api/ManagePermissions?editId=0" class="button"><span class="fa fa-plus-circle"></span> Add New Permission</a>
	</div>

</main>
<%@ include file="../includes/admin_footer.jsp" %>