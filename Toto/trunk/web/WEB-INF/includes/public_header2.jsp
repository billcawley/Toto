<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %><%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<!DOCTYPE html>
<html>
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>${title} - Azquo</title>
	<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css">
	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
	<script type="text/javascript" src="/js/global.js"></script>
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
			<a class="navbar-item" href="/api/ManageReports">Reports</a>

			<a class="navbar-item" href="#" onclick="return inspectDatabase();" title="Inspect database"><span class="fa fa-eye"></span>&nbsp;Inspect database</a> <!--<span class="fa fa-question-circle" onclick="showInspectHelp(); return false;"></span>-->
			<c:if test="${xml == true}"><a class="navbar-item" href="#" onclick="postAjax('XML');return false;">Send XML</a></c:if>
			<c:if test="${xmlzip == true}"><a class="navbar-item" href="#" onclick="postAjax('XMLZIP');return false;">Download XML</a></c:if>
			<c:if test="${showTemplate == true}"><a class="navbar-item" href="#" onclick="window.location.assign(window.location.href+='&opcode=template')">View Template</a></c:if>
			<c:if test="${execute == true}"><a class="navbar-item" href="#" onclick="postAjax('ExecuteSave');window.location.assign(window.location.href+='&opcode=execute')">Execute</a></c:if>
		</div>
		<div class="navbar-end">
			<c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
				<a class="navbar-item" href="/api/Login?select=true">Switch Business</a>
			</c:if>
			<a class="navbar-item" href="/api/Login?logoff=true">Log Off</a>
			<div class="navbar-item has-dropdown is-hoverable">
				<a class="navbar-link is-arrowless" >
					<span class="fa fa-bars"></span>
				</a>
				<div class="navbar-dropdown is-boxed is-right">
					<a class="navbar-item"  href="#" onclick="postAjax('XLS'); return false;" title="Download as XLSX (Excel)"><span class="fa fa-file-excel-o"></span>&nbsp;&nbsp;Download as XLSX (Excel)</a>
					<c:if test="${showTemplate == true}"><a class="navbar-item"   href="#" onclick="window.location.assign(window.location.href+='&opcode=template')">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;View Template</a></c:if>
					<a class="navbar-item"  href="#" onclick="return inspectDatabase();" title="Inspect database"><span class="fa fa-eye"></span>&nbsp;&nbsp;Inspect database</a>
					<a class="navbar-item"  href="#" onclick="return auditDatabase();" title="Audit Database"><span class="fa fa-eye"></span>&nbsp;&nbsp;Audit Database</a>
					<a class="navbar-item"  href="#" onclick="return uploadFile();" title="Upload file"><span class="fa fa-cloud-upload"></span>&nbsp;&nbsp;Upload file</a>
					<a class="navbar-item"  href="#" onclick="return postAjax('FREEZE');" title="Upload file"><span class="fa fa-link"></span>&nbsp;&nbsp;Freeze</a>
					<a class="navbar-item"  href="#" onclick="return postAjax('UNFREEZE');" title="Upload file"><span class="fa fa-unlink"></span>&nbsp;&nbsp;Unfreeze</a>
					<c:if test="${masterUser == true}">
						<a class="navbar-item"  href="/api/CreateExcelForDownload?action=DOWNLOADUSERS" title="Download User List">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Download User List</a>
						<a class="navbar-item"  href="/api/CreateExcelForDownload?action=DOWNLOADREPORTSCHEDULES" title="Download Report Schedules">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Download Report Schedules</a>
					</c:if>
					<a class="navbar-item"  href="/api/UserUpload#tab2" title="Upload Data">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Data Validation</a>

				</div>
			</div>
		</div>
	</div>
</nav>
