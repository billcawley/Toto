<%--
  Created by IntelliJ IDEA.
  User: cawley
  Date: 28/04/15
  Time: 12:10
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
  <title>Edit/New User</title>
</head>
<body>
Edit/New User<br/>
<form action="/api/ManageUsers" method="post">
  <input type="hidden" name="editId" value="${id}"/>
  <!-- no business id -->
<table>
  <tr>
    <td>Start Date</td>
    <td>${startDate}</td>
  </tr>
  <tr>
    <td>End Date</td>
    <td><input name="endDate" value="${endDate}"></td>
  </tr>
  <tr>
    <td>Email</td>
    <td><input name="email" value="${email}"></td>
  </tr>
  <tr>
    <td>Name</td>
    <td><input name="name" value="${name}"></td>
  </tr>
  <tr>
    <td>Status</td>
    <td><input name="status" value="${status}"></td>
  </tr>
  <tr>
    <td>Password</td>
    <td><input name="password"></td>
  </tr>
</table>
  ${error}
  <input type="submit" name="submit" value="Submit"/>
</form>

</body>
</html>
