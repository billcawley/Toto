<%-- Copyright (C) 2016 Azquo Ltd. --%><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html lang="en-GB">

<head>
	<title>${title} - Azquo</title>
	<meta charset="UTF-8">
	<meta NAME="description" CONTENT="">
	<meta NAME="keywords" CONTENT="">
	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
	<link href='https://fonts.googleapis.com/css?family=Open+Sans:400,300,700,600' rel='stylesheet' type='text/css'>
	
	<link rel="shortcut icon" href="/favicon.ico" type="image/x-icon">
	<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
	<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>
	<script type="text/javascript" src="/js/global.js"></script>
	<script type="text/javascript">

		function pingExcelInterfaceFlag(flag){
			$.get(
					"/api/Excel?toggle=" + flag,
					{paramOne : 1, paramX : 'abc'},
					function(data) {
						//alert('page content: ' + data);
					}
			);
		}

	</script>

	<link href="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/themes/black-tie/jquery-ui.css" rel="stylesheet" type="text/css">
	<link href="/css/style.css" rel="stylesheet" type="text/css">
	<style>
		header .nav ul li a.on {background-color:${bannerColor}}
		.ui-widget .ui-widget-header li.ui-state-active {background-color:${bannerColor}}
		.button {background-color:${bannerColor}}
		a:link {color:${bannerColor}}
		a:visited {color:${bannerColor}}
	</style>
</head>

<body>

<header>
	<div class="headerContainer">
	<div class="logo">
		<img src="${logo}" alt="azquo">
	</div>
	<nav class="nav">
	<ul>
		<c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
			<li><a href="/api/Login?select=true">Logged in under : ${sessionScope.LOGGED_IN_USER_SESSION.user.businessName}. Switch business.</a></li>
		</c:if>
<!--		<li><span  class="tickspan">Excel<input name="excel" type="checkbox" id="excelinterface" <c:if test="${sessionScope.excelToggle}"> checked</c:if> onchange="pingExcelInterfaceFlag(this.checked)"></span></li> -->
		<li><a href="/api/ManageReports"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReports') ? ' class="on"' : ''}>Reports</a></li>
		<li><a href="/api/ManageDatabases"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageDatabases') ? ' class="on"' : ''}>Databases</a></li>
		<c:if test="${!developer}">
		<li><a href="/api/ManageUsers"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageUsers') ? ' class="on"' : ''}>Users</a></li>
		</c:if>
		<c:if test="${!developer}">
			<li><a href="/api/ManageReportSchedules"${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReportSchedules') ? ' class="on"' : ''}>Schedules</a></li>
		</c:if>
		<li><a href="/api/Login?logoff=true">Log Off</a></li>
	</ul>
	</nav>
	</div>
</header>
