<%--
Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT

Created by IntelliJ IDEA.
  User: cawley
  Date: 24/04/15
  Time: 15:48
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Databases" />
<%@ include file="../includes/admin_header.jsp" %>


<main class="databases">
<h1>Manage Databases</h1>
<div class="error">${error}</div>
<div class="tabs">
	<ul>
		<li><a href="#tab1">Uploads</a></li>
		<li><a href="#tab2">DB Management</a></li>
		<li><a href="#tab3">Maintenance</a></li>
	</ul>
<!-- Uploads -->
	<div id="tab1" style="display:none">
	<h3>Uploads</h3>
	<div class="well">
	<form action="/api/ManageDatabases" method="post" enctype="multipart/form-data">
	<table>
		<tbody>
			<tr>
				<td><label for="uploadFile">Upload File:</label> <input id="uploadFile" type="file" name="uploadFile"></td>
			<td>
				<label for="uploadDatabase">Database:</label>
				<select name="database" id="uploadDatabase">
					<option value="">None</option>
					<c:forEach items="${databases}" var="database">
						<option value="${database.name}" <c:if test="${database.name == lastSelected}"> selected </c:if>>${database.name}</option>
					</c:forEach>
				</select>
			</td>
			<td><!-- <label for="useType">Use database type?</label> <input id="useType" type="checkbox" name="useType"/>--></td>
			<td><input type="submit" name="Upload" value="Upload" class="button "/></td>
			</tr>
		</tbody>
	</table>	
	</form>
	</div>
	<!-- Archive List -->
	<table>
		<thead>
			<tr>
				<td>Date</td>
				<td>Business Name</td>
				<td>Database Name</td>
				<td>User Name</td>
				<td>File Name</td>
				<td>File Type</td>
				<td>Comments</td>
				<td></td>
			</tr>
		</thead>
		<tbody>
		<c:forEach items="${uploads}" var="upload">
			<tr>
				<td>${upload.date}</td>
				<td>${upload.businessName}</td>
				<td>${upload.databaseName}</td>
				<td>${upload.userName}</td>
				<td>${upload.fileName}</td>
				<td>${upload.fileType}</td>
				<td>${upload.comments}</td>
				<td><c:if test="${upload.downloadable}"><a href="/api/DownloadFile?uploadRecordId=${upload.id}">Download</a></c:if></td>
			</tr>
		</c:forEach>
		</tbody>
	</table>




	</div>
<!-- END Uploads -->		
<!-- DB Management -->		
	<div id="tab2" style="display:none">
		<h3>Create new database:</h3>
		<div class="well">
		<form action="/api/ManageDatabases" method="post">
			<table>
				<tr>
					<td><label for="createDatabase">Database Name:</label> <input name="createDatabase" id="createDatabase"/></td>
					<td><!-- <label for="databaseType">Database Type:</label> <input name="databaseType" id="databaseType"/>--></td>
					<td>
	<c:if test="${serverList == true}">
						<label for="databaseServerId">Select Server:</label>
						<select name="databaseServerId" id="databaseServerId">
							<c:forEach items="${databaseServers}" var="databaseServer">
								<option value="${databaseServer.id}">${databaseServer.name} - ${databaseServer.ip}</option>
							</c:forEach>
						</select>
	</c:if>
					</td>
					<td>
						<input type="submit" name="Create Database" value="Create Database" class="button"/>
					</td>
				</tr>	
			</table>
		</form>
		</div>
		<!-- Database Options Table -->
		<table>
			<thead>
				<tr>
					<!--<td>${database.id}</td> -->
					<!--<td>${database.businessId}</td>-->
					<td>Name</td>
					<td>Persistence Name</td>
					<td></td>
					<td>Name Count</td>
					<td>Value Count</td>
					<td></td>
					<td></td>
					<td></td>
					<td></td>
					<td></td>
				</tr>
			</thead>
			<tbody>
				<c:forEach items="${databases}" var="database">
				<tr>
					<!--<td>${database.id}</td>-->
					<!--<td>${database.businessId}</td> -->
					<td>${database.name}</td>
					<td>${database.persistenceName}</td>
					<td><a href="/api/ManageDatabaseBackups?databaseId=${database.id}">Manage Backups</a></td>
					<td>${database.nameCount}</td>
					<td>${database.valueCount}</td>
					<td><a href="/api/Jstree?op=new&database=${database.urlEncodedName}" data-title="${database.urlEncodedName}" class="button small inspect" title="Inspect"><span class="fa fa-eye" title="Inspect ${database.name}"></span></a></td>
					<td><a href="/api/ManageDatabases?emptyId=${database.id}" onclick="return confirm('Are you sure you want to Empty ${database.name}?')" class="button small" title="Empty ${database.name}"><span class="fa fa-bomb" title="Empty"></span></a></td>
					<td><a href="/api/ManageDatabases?deleteId=${database.id}" onclick="return confirm('Are you sure you want to Delete ${database.name}?')" class="button small alt" title="Delete ${database.name}"><span class="fa fa-trash" title="Delete"></span> </a></td>
					<td><c:if test="${database.loaded}"><a href="/api/ManageDatabases?unloadId=${database.id}" class="button small" title="Unload ${database.name}"><span class="fa fa-eject" title="Unload"></span></a></c:if></td>
				</tr>
				</c:forEach>
			</tbody>
		</table>	
		<!-- Database Backup -->
		<div class="well">
			<form action="/api/ManageDatabases" method="post">
			<table>	
				<tbody>
					<tr>
						<td><label for="backupTarget">Target Database:</label> <input name="backupTarget" id="backupTarget"/></td>
						<td><label for="summaryLevel">Summary Level:</label> <input name="summaryLevel" id="summaryLevel"/></td>
						<td><input type="submit" name="Backup Database" value="Backup Database" class="button"/></td>
					</tr>
				</tbody>
			</table>
			</form>		
		</div>
	</div>
<!-- END DB Management -->		
<!-- Maintenance -->	
	<div id="tab3" style="display:none">
		<h3>Memory report for servers:</h3>
		<div class="well">
			<c:forEach items="${databaseServers}" var="databaseServer">
				<a href="/api/MemoryReport?serverIp=${databaseServer.ip}" target="new" class="button report">${databaseServer.name}</a>
			</c:forEach>
		</div>
	</div>
<!-- END Maintenance -->	
</div>
</main>

<!-- tabs -->
<script type="text/javascript">
	$(document).ready(function(){
		$('.tabs').tabs();
	});
</script>

<%@ include file="../includes/admin_footer.jsp" %>
