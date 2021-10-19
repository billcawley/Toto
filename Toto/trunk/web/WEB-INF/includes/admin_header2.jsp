<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html>
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>${title} - Azquo</title>
	<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css">
	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
</head>
<body>
<nav class="navbar is-black" role="navigation" aria-label="main navigation">
	<div class="navbar-brand">
		<a class="navbar-item" href="https://azquo.com">
			<img src="${logo}" alt="azquo">
		</a>
	</div>
	<div id="navbarBasicExample" class="navbar-menu">
		<div class="navbar-start">
			<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReports') ? ' is-active' : ''}"
			   href="/api/ManageReports">
				Reports</a>
			<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageDatabases') ? ' is-active' : ''}"
			   href="/api/ManageDatabases">
				Databases
			</a>
			<c:if test="${!developer}">
				<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageUsers') ? ' is-active' : ''}"
				   href="/api/ManageUsers">
					Users
				</a>
			</c:if>
			<c:if test="${!developer}">
				<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReportSchedules') ? ' is-active' : ''}"
				   href="/api/ManageReportSchedules">Schedules
				</a>
			</c:if>
		</div>

		<div class="navbar-end">
			<c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
				<a  class="navbar-item" href="/api/Login?select=true">Logged in under ${sessionScope.LOGGED_IN_USER_SESSION.user.businessName}. Switch business.</a>
			</c:if>
			<a class="navbar-item" href="/api/Login?logoff=true">Log Off</a>
		</div>
	</div>
</nav>
