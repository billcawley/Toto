<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit/New Permission" />
<%@ include file="../includes/admin_header.jsp" %>

<main>
	<h1>Edit/New Permission</h1>
	<div class="error">${error}</div>
	<form action="/api/ManagePermissions" method="post">
		<input type="hidden" name="editId" value="${id}"/>
		<!-- no business id -->
		
			<table class="edit">
				<tbody>
					<tr>
					<td width="33%">
						<h3>Permission Details</h3>
						<div class="well">
							<div>
								<label for="datebaseId">Database</label>
								<select name="databaseId">
									<c:forEach items="${databases}" var="database">
										<option value="${database.id}"<c:if test="${database.id == databaseId}"> selected</c:if>>${database.name}</option>
									</c:forEach>
								</select>
							</div>
							<div>
								<label for="user">Email</label>
								<select name="userId">
									<c:forEach items="${users}" var="user">
										<option value="${user.id}"<c:if test="${user.id == userId}"> selected</c:if>>${user.email}</option>
									</c:forEach>
								</select>
							</div>
						</div>
					</td>
					<td width="33%">
						<h3>Start / End Date</h3>
						<div class="well">
							<div>
								<label for="startDate">Start Date</label>
								<input name="startDate" id="startDate" value="${startDate}">
							</div>
							<div>
								<label for="endDate">End Date</label>
								<input name="endDate" id="endDate" value="${endDate}">
							</div>
						</div>
					</td>
					<td width="33%">
						<h3>Read / Write List</h3>
						<div class="well">
							<div>
								<label for="readList">Read List</label>
								<input name="readList" id="readList" value="${readList}">
							</div>
							<div>
								<label for="writeList">Write List</label>
								<input name="writeList" id="writeList" value="${writeList}">
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
