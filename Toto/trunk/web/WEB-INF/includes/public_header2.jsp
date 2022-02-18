<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %><%@ taglib prefix="kkjsp" uri="http://www.keikai.io/jsp/kk" %>
<!DOCTYPE html>
<html>
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>${title} - Azquo</title>
	<link rel="stylesheet" href="/css/bulma.css">
	<link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
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
	</style>
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
			<a class="navbar-item" href="/api/Online?reportid=1">Reports</a>

			<c:if test="${showInspect == true}"><a class="navbar-item" href="#" onclick="return inspectDatabase();" title="Inspect database"><span class="fa fa-eye"></span>&nbsp;Inspect database</a> <!--<span class="fa fa-question-circle" onclick="showInspectHelp(); return false;"></span>--></c:if>
			<c:if test="${xml == true}"><a class="navbar-item" href="#" onclick="postAjax('XML');return false;">Send XML</a></c:if>
			<c:if test="${xmlzip == true}"><a class="navbar-item" href="#" onclick="postAjax('XMLZIP');return false;">Download XML</a></c:if>
			<c:if test="${showTemplate == true}"><a class="navbar-item" href="#" onclick="window.location.assign(window.location.href+='&opcode=template')">View Template</a></c:if>
			<c:if test="${execute == true}"><a class="navbar-item" href="#" onclick="postAjax('ExecuteSave');window.location.assign(window.location.href+='&opcode=execute')">Execute</a></c:if>
			<c:if test="${userUploads == true}"><a class="navbar-item" href="/api/UserUpload">Validate File</a></c:if>
			<a id="unlockButton" <c:if test="${showUnlockButton == false}"></c:if> class="navbar-item" href="#" onclick="postAjax('Unlock')">Unlock</a>
			<a id="saveDataButton" <c:if test="${showSave == false}"> style="display:none;"</c:if> class="navbar-item" href="#" onclick="postAjax('Save')">Save Data</a>
			<a id="restoreDataButton" <c:if test="${showSave == false}"> style="display:none;"</c:if> class="navbar-item" href="#" onclick="postAjax('RestoreSavedValues')">Restore Saved Values</a>
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
<span id="lockedResult"><c:if test="${not empty lockedResult}"><textarea class="public" style="height:60px;width:400px;font:10px monospace;overflow:auto;font-family:arial;background:#f58030;color:#fff;font-size:14px;border:0">${lockedResult}</textarea></c:if></span>
