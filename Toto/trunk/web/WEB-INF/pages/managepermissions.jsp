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
Manage Permissions
<c:forEach items="${permissions}" var="permission">
  <tr>
    <td>${permission.id}</td>
    <td>${permission.startDate}</td>
    <td>${permission.endDate}</td>
    <td>${permission.userId}</td>
    <td>${permission.databaseId}</td>
    <td>${permission.readList}</td>
    <td>${permission.writeList}</td>
    <td>${permission.database}</td>
    <td>${permission.email}</td>
  </tr>
</c:forEach>
</table>
</body>
</html>
