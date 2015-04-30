<%--
  Created by IntelliJ IDEA.
  User: cawley
  Date: 24/04/15
  Time: 15:48
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
    <title>Manage Permissions</title>
</head>
<body>
<body>
<a href="/api/ManageReports">Manage Reports</a> &nbsp;<a href="/api/ManageDatabases">Manage Databases</a> &nbsp;<a href="/api/ManageUsers">Manage Users</a> &nbsp;<a href="/api/ManagePermissions">Manage Permissions</a> &nbsp;<br/>
<h1>Manage Permissions</h1><br/>
<a href="/api/ManagePermissions?editId=0">New</a>
<br/>
<table>
  <tr>
    <td>Start Date</td>
    <td>End Date</td>
    <td>Read List</td>
    <td>Write List</td>
    <td>Database</td>
    <td>Email</td>
    <td></td>
    <!-- password and salt pointless here -->
  </tr>
    <c:forEach items="${permissions}" var="permission">
      <tr>
        <td>${permission.startDate}</td>
        <td>${permission.endDate}</td>
        <td>${permission.readList}</td>
        <td>${permission.writeList}</td>
        <td>${permission.databaseName}</td>
        <td>${permission.userEmail}</td>
      <td><a href="/api/ManagePermissions?editId=${permission.id}">Edit</a>&nbsp;<a href="/api/ManagePermissions?deleteId=${permission.id}" onclick="return confirm('Are you sure?')">Delete</a></td>
    </tr>
  </c:forEach>
</table>






</body>
</html>
