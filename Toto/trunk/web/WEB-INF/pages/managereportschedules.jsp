<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
  <link rel='stylesheet' id='bootstrap-css'  href='/wp-content/themes/stylish-v1.2.2/styles/bootstrap/css/bootstrap.css?ver=3.9.5' type='text/css' media='all' />
  <link rel='stylesheet' id='bootstrap-responsive-css'  href='https://www.azquo.com/wp-content/themes/stylish-v1.2.2/styles/bootstrap/css/bootstrap-responsive.css?ver=3.9.5' type='text/css' media='all' />
  <title>Manage Reports</title>
</head>
<body>
<a href="/api/ManageReports">Manage Reports</a> &nbsp;<a href="/api/ManageDatabases">Manage Databases</a> &nbsp;<a href="/api/ManageUsers">Manage Users</a> &nbsp;<a href="/api/ManagePermissions">Manage Permissions</a> &nbsp;<br/>
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
        <td><input name="period${reportSchedule.id}" value="${reportSchedule.period}" size="10"/></td>
        <td><input name="recipients${reportSchedule.id}" value="${reportSchedule.recipients}" size="100"/></td>
        <td><input name="nextDue${reportSchedule.id}" value="${reportSchedule.nextDue}" size="16"/></td>
        <td><input name="database${reportSchedule.id}" value="${reportSchedule.databaseId}" size="10"/></td>
        <td><input name="report${reportSchedule.id}" value="${reportSchedule.reportId}" size="10"/></td>
        <td><input name="type${reportSchedule.id}" value="${reportSchedule.type}" size="10"/></td>
        <td><textarea name="parameters${reportSchedule.id}" cols="50" rows="3">${reportSchedule.parameters}</textarea></td>
      </tr>
    </c:forEach>
  </table>
  <input type="submit" name="submit" value="Save Changes"/>
  <a href="/api/ManageReportSchedules?new=true">Add new schedule</a>
</form>
</body>
</html>
