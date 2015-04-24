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
    <title>Manage Databases</title>
</head>
<body>
Manage Databases
<table>
  <c:forEach items="${databases}" var="database">
    <tr>
      <td>${database.id}</td>
      <td>${database.startDate}</td>
      <td>${database.endDate}</td>
      <td>${database.businessId}</td>
      <td>${database.name}</td>
      <td>${database.mySQLName}</td>
      <td>${database.nameCount}</td>
      <td>${database.valueCount}</td>
    </tr>
  </c:forEach>
</table>
<table>
  <c:forEach items="${uploads}" var="upload">
    <tr>
      <td>${upload.id}</td>
      <td>${upload.date}</td>
      <td>${upload.businessId}</td>
      <td>${upload.databaseId}</td>
      <td>${upload.userId}</td>
      <td>${upload.fileName}</td>
      <td>${upload.fileType}</td>
      <td>${upload.comments}</td>
    </tr>
  </c:forEach>
</table>

</body>
</html>
