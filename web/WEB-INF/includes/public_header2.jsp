<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %><%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<!DOCTYPE html>
<html>
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>${title} - Azquo</title>
	<link rel="stylesheet" href="/css/bulma.css">
	<link rel="stylesheet" href="/quickview/bulma-quickview.min.css">
	<!-- <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css"> -->
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0/css/all.min.css">
	<!-- required for inspect - presumably zap at some point -->
	<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jquery/2.1.4/jquery.min.js"></script>
	<script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/jquery-ui.min.js"></script>
	<link href="https://cdnjs.cloudflare.com/ajax/libs/jqueryui/1.11.4/themes/black-tie/jquery-ui.css" rel="stylesheet" type="text/css">

	<script type="text/javascript" src="/js/global.js"></script>
	<c:if test="${requirezss}">
		<kkjsp:head/>
	</c:if>
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
					   href="/api/Online?reportid=1" style="color: ${ribbonLinkColor}">
						Reports</a>

			<c:if test="${showInspect == true}"><a class="navbar-item is-tab" href="#" onclick="return inspectDatabase();" title="Inspect database" style="color: ${ribbonLinkColor}"><span class="fa fa-eye"></span>&nbsp;Inspect database</a> <!--<span class="fa fa-question-circle" onclick="showInspectHelp(); return false;"></span>--></c:if>
			<c:if test="${reloadExternal == true}"><a class="navbar-item is-tab" href="#" onclick="postAjax('RELOADEXTERNAL');return false;" style="color: ${ribbonLinkColor}">Reload External Data</a></c:if>
			<c:if test="${xml == true}"><a class="navbar-item is-tab" href="#" onclick="postAjax('XML');return false;" style="color: ${ribbonLinkColor}">Send XML</a></c:if>
			<c:if test="${xmlzip == true}"><a class="navbar-item is-tab" href="#" onclick="postAjax('XMLZIP');return false;" style="color: ${ribbonLinkColor}">Download XML</a></c:if>
			<c:if test="${showTemplate == true}"><a class="navbar-item is-tab" href="#" onclick="window.location.assign(window.location.href+='&opcode=template')" style="color: ${ribbonLinkColor}">View Template</a></c:if>
			<c:if test="${execute == true}"><a class="navbar-item is-tab" href="#" onclick="postAjax('ExecuteSave');window.location.assign(window.location.href+='&opcode=execute')" style="color: ${ribbonLinkColor}">Execute</a></c:if>
			<c:if test="${userUploads == true}"><a class="navbar-item is-tab" href="/api/UserUpload" style="color: ${ribbonLinkColor}">Validate File</a></c:if>
			<a id="unlockButton" <c:if test="${showUnlockButton == false}"></c:if> class="navbar-item is-tab" href="#" onclick="postAjax('Unlock')" style="color: ${ribbonLinkColor}">Unlock</a>
			<a id="saveDataButton" <c:if test="${showSave == false}"> style="display:none;"</c:if> class="navbar-item is-tab" href="#" onclick="postAjax('Save')" style="color: ${ribbonLinkColor}">Save Data</a>
			<a id="restoreDataButton" <c:if test="${showSave == false}"> style="display:none;"</c:if> class="navbar-item is-tab" href="#" onclick="postAjax('RestoreSavedValues')" style="color: ${ribbonLinkColor}">Restore Saved Values</a>
		</div>
		<div class="navbar-end">
			<c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
				<a class="navbar-item is-tab" href="/api/Login?select=true" style="color: ${ribbonLinkColor}"><i class="fa-solid fa-sitemap"></i></a>
			</c:if>
			<a class="navbar-item is-tab" href="/api/Login?logoff=true" style="color: ${ribbonLinkColor}">Sign Out</a>
			<div class="navbar-item has-dropdown is-hoverable">
				<a class="navbar-link is-arrowless"  style="color: ${ribbonLinkColor}">
					<span class="fa fa-bars"></span>
				</a>
				<div class="navbar-dropdown is-boxed is-right">
					<a class="navbar-item"  href="#" onclick="postAjax('XLS'); return false;" title="Download as XLSX (Excel)"><span class="fa fa-file-excel-o"></span>&nbsp;&nbsp;Download as XLSX (Excel)</a>
					<c:if test="${showTemplate == true}"><a class="navbar-item"   href="#" onclick="window.location.assign(window.location.href+='&opcode=template')">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;View Template</a></c:if>
					<c:if test="${showInspect == true}"><a class="navbar-item"  href="#" onclick="return inspectDatabase();" title="Inspect database"><span class="fa fa-eye"></span>&nbsp;&nbsp;Inspect database</a>
					<a class="navbar-item"  href="#" onclick="return auditDatabase();" title="Audit Database"><span class="fa fa-eye"></span>&nbsp;&nbsp;Audit Database</a></c:if>
					<a class="navbar-item"  href="#" onclick="return postAjax('FREEZE');" title="Upload file"><span class="fa fa-link"></span>&nbsp;&nbsp;Freeze</a>
					<a class="navbar-item"  href="#" onclick="return postAjax('UNFREEZE');" title="Upload file"><span class="fa fa-unlink"></span>&nbsp;&nbsp;Unfreeze</a>
					<c:if test="${masterUser == true}">
						<a class="navbar-item"  href="/api/CreateExcelForDownload?action=DOWNLOADUSERS" title="Download User List">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Download User List</a>
						<a class="navbar-item"  href="/api/CreateExcelForDownload?action=DOWNLOADREPORTSCHEDULES" title="Download Report Schedules">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Download Report Schedules</a>
					</c:if>
				</div>
			</div>
		</div>
	</div>
</nav>
<c:if test="${reports != null && sessionScope.test != null}">
<button class="button" data-show="quickview" data-target="quickviewDefault" style="
	position: fixed;
    top: 6%;
        z-index: 34;
"><i class="fa-solid fa-chevron-right"></i></button>

<div id="quickviewDefault" class="quickview is-left" style="background-color: ${sideMenuColor}">
	<header class="quickview-header">
		<p style="color:${sideMenuLinkColor}" class="title">Reports</p>
		<span class="delete" data-dismiss="quickview"></span>
	</header>

	<div class="quickview-body">
		<div class="quickview-block">
			<c:forEach items="${reports}" var="report">
				<c:if test="${report.database != 'None'}">
					<c:if test="${report.category != ''}">
						<hr style="height: 0px">
						&nbsp;&nbsp;<span style="color:${sideMenuLinkColor};text-decoration: underline">${report.category}</span>
						<hr style="height: 0px">
					</c:if>
					<a href="/api/Online?reportid=${report.id}&amp;database=${report.database}&amp;permissionid=${report.untaggedReportName}"   style="color:${sideMenuLinkColor}">
						&nbsp;&nbsp;&nbsp;&nbsp;${report.untaggedReportName}<br/>
					</a>
				</c:if>
			</c:forEach>

		</div>
	</div>

</div>
</c:if>

<span id="lockedResult"><c:if test="${not empty lockedResult}"><textarea class="public" style="height:60px;width:400px;font:10px monospace;overflow:auto;font-family:arial;background:#f58030;color:#fff;font-size:14px;border:0">${lockedResult}</textarea></c:if></span>
