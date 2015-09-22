<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
  <link rel='stylesheet' id='bootstrap-css'  href='/wp-content/themes/stylish-v1.2.2/styles/bootstrap/css/bootstrap.css?ver=3.9.5' type='text/css' media='all' />
  <link rel='stylesheet' id='bootstrap-responsive-css'  href='https://www.azquo.com/wp-content/themes/stylish-v1.2.2/styles/bootstrap/css/bootstrap-responsive.css?ver=3.9.5' type='text/css' media='all' />
  <title>Manage Reports</title>
</head>
<body>
<a href="/api/ManageReports">Manage Reports</a> &nbsp;<a href="/api/ManageReportSchedules">Manage Report Schedules</a> &nbsp;<a href="/api/ManageDatabases">Manage Databases</a> &nbsp;<a href="/api/ManageUsers">Manage Users</a> &nbsp;<a href="/api/ManagePermissions">Manage Permissions</a> &nbsp;<br/>
<h1>Manage Report Schedules</h1><br/>
<form action="/api/ManageReportSchedules" method="post">
  <table>
    <tr>
      <!--            <td>Report id</td>
     <td>Business Id</td>-->
      <td>Period</td>
      <td>Recipients</td>
      <td>Next Due</td>
      <td>Database</td>
      <td>Report</td>
      <td>Type</td>
      <td>Parameters</td>
    </tr>
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
        <td><input name="recipients${reportSchedule.id}" value="${reportSchedule.recipients}" size="60"/></td>
        <td><input name="nextDue${reportSchedule.id}" value="${reportSchedule.nextDueFormatted}" size="16"/></td>
        <td><select name="databaseId${reportSchedule.id}">
            <c:forEach items="${databases}" var="database">
            <option value="${database.id}"<c:if test="${database.id == reportSchedule.databaseId}"> selected</c:if>>${database.name}</option>
        </c:forEach>
        </select>
        </td>
        <td><select name="reportId${reportSchedule.id}">
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
        <td><textarea name="parameters${reportSchedule.id}" cols="50" rows="3">${reportSchedule.parameters}</textarea></td>
      </tr>
    </c:forEach>
  </table>
  <input type="submit" name="submit" value="Save Changes"/>
  <a href="/api/ManageReportSchedules?new=true">Add new schedule</a>
</form>
</body>
</html>
