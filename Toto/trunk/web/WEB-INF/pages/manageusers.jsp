<%--
  Created by IntelliJ IDEA.
  User: cawley
  Date: 24/04/15
  Time: 15:47
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
    <title>Manage Users</title>
</head>
<body>
Manage Users - todo : add pagination<br/>
<a href="/api/ManageUsers?editId=0">New</a>
<br/>
<table>
  <tr>
    <td>Id</td>
    <td>Start Date</td>
    <td>End Date</td>
    <td>Business Id</td>
    <td>User Email</td>
    <td>Name</td>
    <td>Status</td>
    <td></td>
    <!-- password and salt pointless here -->
  </tr>
<c:forEach items="${users}" var="user">
  <tr>
    <td>${user.id}</td>
    <td>${user.startDate}</td>
    <td>${user.endDate}</td>
    <td>${user.businessId}</td>
    <td>${user.email}</td>
    <td>${user.name}</td>
    <td>${user.status}</td>
    <td><a href="/api/ManageUsers?editId=${user.id}">Edit</a>&nbsp;<a href="/api/ManageUsers?deleteId=${user.id}" onclick="return confirm('Are you sure?')">Delete</a></td>
  </tr>
</c:forEach>
</table>

</body>
</html>
