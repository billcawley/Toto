<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Users" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
	<h1>Recent User Activity - <a href="/api/ManageUsers?downloadRecentId=${id}">Download</a></h1>
	<table>
		<thead>
			<tr>
		    	<td>User Email</td>
				<td>Time</td>
				<td>Activity</td>
				<td>Parameters</td>
			</tr>
		</thead>
		<tbody>
		<c:forEach items="${useractivities}" var="useractivity">
			<tr>
				<td>${useractivity.user}</td>
				<td>${useractivity.timeStamp}</td>
				<td>${useractivity.activity}</td>
				<td>${useractivity.parametersForDisplay}</td>
			</tr>
		</c:forEach>
		</tbody>
	</table>
	<div class="centeralign">
		<a href="/api/ManageUsers" class="button"><span class="fa"></span>Back</a>&nbsp;
	</div>
</main>
<%@ include file="../includes/admin_footer.jsp" %>