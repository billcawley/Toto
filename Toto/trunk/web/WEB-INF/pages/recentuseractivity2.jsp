<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Recent User Activity" />
<%@ include file="../includes/admin_header2.jsp" %>

<div class="box">
	Recent User Activity - <a href="/api/ManageUsers?downloadRecentId=${id}">Download</a>
	<table class="table is-striped is-fullwidth">
		<thead>
			<tr>
		    	<th>User Email</th>
				<th>Time</th>
				<th>Activity</th>
				<th>Parameters</th>
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
	<div>
		<a href="/api/ManageUsers" class="button is-small"><span class="fa"></span>Back</a>&nbsp;
	</div>
</div>
<%@ include file="../includes/admin_footer.jsp" %>