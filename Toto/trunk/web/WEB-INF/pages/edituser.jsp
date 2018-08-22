<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit/New User" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
	<h1>Edit/New User</h1>
	<div class="error">${error}</div>
	<form action="/api/ManageUsers" method="post">
	<input type="hidden" name="editId" value="${id}"/>
		<!-- no business id -->

			<table class="edit">
				<tbody>
					<tr>
					<td width="33%">
						<h3>User Details</h3>
						<div class="well">
							<div>
								<label for="email">Email/logon</label>
								<input name="email" id="email" value="${email}">
							</div>
							<div>
								<label for="name">Name</label>
								<input name="name" id="name" value="${name}">
							</div>
							<div>
								<label for="status">Status</label>
								<select name="status" id="status">
									<option value="ADMINISTRATOR" ${status =="ADMINISTRATOR"?"selected":""}>Administrator</option>
									<option value="DEVELOPER"  ${status =="DEVELOPER"?"selected":""}>Developer</option>
									<option value="MASTER"  ${status =="MASTER"?"selected":""}>Master</option>
									<option value="USER" ${status =="USER"?"selected":""}>User</option>
								</select>
							</div>
						</div>
					</td>
					<td width="33%">
						<h3>&nbsp;</h3>
						<div class="well">
							<div>
								<label for="endDate">End Date</label>
								<input name="endDate" id="endDate" value="${endDate}">
							</div>
							<div>
								<label for="status">Database</label>
								<select name="databaseId">
									<option value="0">None</option>
									<c:forEach items="${databases}" var="database">
										<option value="${database.id}"<c:if test="${database.id == user.databaseId}"> selected</c:if>>${database.name}</option>
									</c:forEach>
								</select>
							</div>
							<div>
								<label for="status">Report</label>
								<select name="reportId">
									<option value="0">None</option>
									<c:forEach items="${reports}" var="report">
										<option value="${report.id}"<c:if test="${report.id == user.reportId}"> selected</c:if>>${report.reportName}</option>
									</c:forEach>
								</select>
							</div>
						</div>
					</td>
					<td width="33%">
						<h3>Change Password</h3>
						<div class="well">
							<label for="selections">Selections</label>
							<input name="selections" id="selections" value="${selections}">
						</div>
						<div class="well">
							<div>
								<label for="password">Password</label>
								<input name="password" id="password" type="password">
							</div>
						</div>
					</td>
				</tbody>
			</table>		

		<div class="centeralign">
			<button type="submit" name="submit" value="save" class="button"><span class="fa fa-floppy-o"></span> Save </button>
		</div>
	</form>
	
</main>

<%@ include file="../includes/admin_footer.jsp" %>
