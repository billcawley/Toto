<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html>
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>${title} - Azquo</title>


	<link rel="preconnect" href="https://fonts.googleapis.com">
	<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
	<link href="https://fonts.googleapis.com/css2?family=Roboto&display=swap" rel="stylesheet">
	<link rel="stylesheet" href="/sass/mystyles.css">
	<!-- <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css"> -->
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
	<!-- required for inspect - presumably zap at some point -->
	<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
	<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>
	<link href="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/themes/black-tie/jquery-ui.css" rel="stylesheet" type="text/css">
	<script type="text/javascript" src="/js/global.js"></script>
	<style>
		.ui-dialog .ui-tabs-panel{min-height:350px; background:#ECECEC; padding:5px 5px 0px 5px; }
		.ui-dialog .ui-tabs-panel iframe{min-height:350px; background:#FFF;}

		header .nav ul li a.on {background-color:${bannerColor}}
		.ui-widget .ui-widget-header li.ui-state-active {background-color:${bannerColor}}
		a:link {color:${bannerColor}}
		a:visited {color:${bannerColor}}
		.navbar {background-color:${ribbonColor}}
	</style>
</head>
<body>
<nav class="navbar" role="navigation" aria-label="main navigation">
	<div class="navbar-brand">
		<a class="navbar-item" href="/api/Online?reportid=1">
			<img src="${cornerLogo}">
		</a>
	</div>
	<div id="navbarBasicExample" class="navbar-menu">
		<div class="navbar-start">

					<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReports') ? ' is-active' : ''}"
					   href="/api/ManageReports" style="color: ${ribbonLinkColor}">
						Reports</a>
			<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageDatabases') ? ' is-active' : ''}"
			   href="/api/ManageDatabases" style="color: ${ribbonLinkColor}">
				Databases
			</a>
			<c:if test="${!developer}">
				<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageUsers') ? ' is-active' : ''}"
				   href="/api/ManageUsers" style="color: ${ribbonLinkColor}">
					Users
				</a>
			</c:if>
<!--
EFC note - while

<c:if test="${!developer}">
				<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageImportSchedules') ? ' is-active' : ''}"
				   href="/api/ManageImportSchedules" style="color: ${ribbonLinkColor}">Import Schedules
				</a>
			</c:if>-->
			<c:if test="${!developer}">
				<a class="navbar-item is-tab${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReportSchedules') ? ' is-active' : ''}"
				   href="/api/ManageReportSchedules" style="color: ${ribbonLinkColor}">Report Schedules
				</a>
			</c:if>
		</div>

		<div class="navbar-end">
			<a class="navbar-item is-tab" href="/api/ManageReports?newdesign=overview" style="color: ${ribbonLinkColor}">Beta</a>
			<c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
				<a  class="navbar-item is-tab" href="/api/Login?select=true" style="color: ${ribbonLinkColor}"><!--Logged in under ${sessionScope.LOGGED_IN_USER_SESSION.user.businessName}. --><i class="fa-solid fa-sitemap"></i></a>
			</c:if>
			<a class="navbar-item is-tab" href="/api/Login?logoff=true" style="color: ${ribbonLinkColor}">Sign Out</a>
		</div>
	</div>
</nav>
