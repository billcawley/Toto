<%-- Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT --%><%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Edit/New User" />
<%@ include file="../includes/admin_header.jsp" %>
<main>

<h1>Manage Report Schedules</h1><br/>
<form action="/api/ManageReportSchedules" method="post">
    <div class="error">${error}</div>
		<table>
			<thead>
				<tr>
					<!-- <td>Report id</td>
					<td>Business Id</td>-->
					<td>Period</td>
					<td>Recipients</td>
					<td>Next Due</td>
					<td>Database</td>
					<td>Report</td>
					<td>Type</td>
					<td>Parameters</td>
					<td></td>
				</tr>
			</thead>
			<tbody>
			<c:forEach items="${reportSchedules}" var="reportSchedule">
				<tr>
					<td>
						<select name="period${reportSchedule.id}">
							<option<c:if test="${reportSchedule.period == 'HOURLY'}"> selected</c:if>>HOURLY</option>
							<option<c:if test="${reportSchedule.period == 'DAILY'}"> selected</c:if>>DAILY</option>
							<option<c:if test="${reportSchedule.period == 'WEEKLY'}"> selected</c:if>>WEEKLY</option>
							<option<c:if test="${reportSchedule.period == 'MONTHLY'}"> selected</c:if>>MONTHLY</option>
						</select>
					</td>
					<td><input name="recipients${reportSchedule.id}" value="${reportSchedule.recipients}"/></td>
					<td><input name="nextDue${reportSchedule.id}" value="${reportSchedule.nextDueFormatted}" /></td>
					<td>
						<select name="databaseId${reportSchedule.id}">
							<c:forEach items="${databases}" var="database">
								<option value="${database.id}"<c:if test="${database.id == reportSchedule.databaseId}"> selected</c:if>>${database.name}</option>
							</c:forEach>
						</select>
					</td>
					<td>
						<select name="reportId${reportSchedule.id}">
							<c:forEach items="${reports}" var="report">
								<option value="${report.id}"<c:if test="${report.id == reportSchedule.reportId}"> selected</c:if>>${report.reportName}</option>
							</c:forEach>
						</select>
					</td>
					<td>
						<select name="type${reportSchedule.id}">
							<option<c:if test="${reportSchedule.type == 'PDF'}"> selected</c:if>>PDF</option>
							<option<c:if test="${reportSchedule.type == 'XLS'}"> selected</c:if>>XLS</option>
						</select>
					</td>
					<td><textarea name="parameters${reportSchedule.id}" rows="3">${reportSchedule.parameters}</textarea></td>
					<td><a href="" class="button small alt"><span class="fa fa-trash"></span></a></td>
					</tr>
			</c:forEach>
			</tbody>
		</table>
		<div class="centeralign">
			<input type="submit" name="submit" value="Save Changes" class="button"/>
			<a href="/api/ManageReportSchedules?new=true" class="button">Add new schedule</a>&nbsp;
		</div>
	</form>
    <div class="centeralign">
        <form action="/api/ManageReportSchedules" method="post" enctype="multipart/form-data"><input type="submit" name="Upload" value="Upload ReportSchedule" class="button "/>&nbsp;
            <input id="uploadFile" type="file" name="uploadFile"></form>
    </div>
</main>
<%@ include file="../includes/admin_footer.jsp" %>
