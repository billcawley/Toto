<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Azquo Users Activity" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
	<h1>Azquo Users Activity - <a href="/api/AzquoUsersReport?download=true">Download</a></h1>
	<table>
		<tbody>
<c:forEach var="entry" items="${businesses}" >
	<tr>
		<td colspan="7">${entry.key}</td>
	</tr>
	<tr>
		<td>User</td>
		<td>Number of Sessions</td>
		<td>Average Session</td>
		<td>Reports Accessed</td>
		<td>Last Session Date</td>
		<td>Errors</td>
		<td>Uploads/Downloads</td>
	</tr>
	<c:forEach items="${entry.value}" var="userline">
		<tr>
			<td>${userline['user']}</td>
			<td>${userline['numberofsessions']}</td>
			<td>${userline['averagesessiontime']}</td>
			<td>${userline['reportsaccessed']}</td>
			<td>${userline['lastaccesseddate']}</td>
			<td></td>
			<td></td>
		</tr>
	</c:forEach>
</c:forEach>
		</tbody>
	</table>
</main>
<%@ include file="../includes/admin_footer.jsp" %>