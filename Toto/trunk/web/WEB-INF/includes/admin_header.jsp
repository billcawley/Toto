<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html lang="en-GB">

<head>
	<title>${title} - Azquo 2 2 </title>
	<meta charset="UTF-8">
	<meta NAME="description" CONTENT="">
	<meta NAME="keywords" CONTENT="">
	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
	<link href='https://fonts.googleapis.com/css?family=Open+Sans:400,300,700,600' rel='stylesheet' type='text/css'>
	
	<link rel="shortcut icon" href="/favicon.ico" type="image/x-icon">
	<script type="text/javascript" src="https://code.jquery.com/jquery-2.1.4.min.js"></script>
	<script type="text/javascript" src="https://code.jquery.com/ui/1.11.4/jquery-ui.min.js"></script>
	<script type="text/javascript" src="/js/global.js"></script>
	
	<link href="https://code.jquery.com/ui/1.11.4/themes/black-tie/jquery-ui.css" rel="stylesheet" type="text/css">
	<link href="/css/style.css" rel="stylesheet" type="text/css">
</head>

<body>

<header>
	<div class="headerContainer">
	<div class="logo">
		<img src="/images/logo.png" alt="azquo">
	</div>
	<nav class="nav">
	<ul>
		<li><a href="/api/ManageReports"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReports') ? ' class="on"' : ''}>Reports</a></li>
		<li><a href="/api/ManageReportSchedules"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReportSchedules') ? ' class="on"' : ''}>Schedules</a></li>
		<li><a href="/api/ManageDatabases"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageDatabases') ? ' class="on"' : ''}>Databases</a></li>
		<li><a href="/api/ManageUsers"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageUsers') ? ' class="on"' : ''}>Users</a></li>
		<li><a href="/api/ManagePermissions"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManagePermissions') ? ' class="on"' : ''}>Permissions</a></li>
	</ul>
	</nav>
	</div>
</header>
