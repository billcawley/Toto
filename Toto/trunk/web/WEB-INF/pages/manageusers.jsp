<%--
  Created by IntelliJ IDEA.
  User: cawley
  Date: 24/04/15
  Time: 15:47
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<html>
<head>
    <title>Manage Users</title>
</head>
<body>
Manage Users
<c:forEach items="${users}" var="user">
  <tr>
    <td>${user.id}</td>
    <td>${user.startDate}</td>
    <td>${user.endDate}</td>
    <td>${user.businessId}</td>
    <td>${user.email}</td>
    <td>${user.name}</td>
    <td>${user.status}</td>
    <td>${user.password}</td>
    <td>${user.salt}</td>
  </tr>
</c:forEach>
</table>

</body>
</html>
