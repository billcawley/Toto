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
Manage Databases<br/>
<br/>${error}
<table>
  <tr>
  <form action="/api/ManageDatabases" method="post"><td>New Database</td><td><input name="createDatabase"/></td><td><input type="submit" name="Create Database" value="Create Database"/></td></form>
  </tr>
  <form action="/api/ManageDatabases" method="post">
    <tr>
    <td>Target Database</td><td><input name="backupTarget"/></td><td><input type="submit" name="Backup Database" value="Backup Database"/></td>
      </tr>
    <tr>
    <td>Summary Level</td><td><input name="summaryLevel"/></td><td></td>
    </tr>
  </form>
</table>
<table>
  <tr>
    <!--      <td>${database.id}</td> -->
    <td>Start Date</td>
    <td>End Date</td>
<!--    <td>${database.businessId}</td> -->
    <td>Name</td>
    <td>MySQLName</td>
    <td>Name Count</td>
    <td>Value Count</td>
    <td></td>
  </tr>
  <c:forEach items="${databases}" var="database">
    <tr>
<!--      <td>${database.id}</td> -->
      <td>${database.startDate}</td>
      <td>${database.endDate}</td>
      <!-- <td>${database.businessId}</td> -->
      <td>${database.name}</td>
      <td>${database.mySQLName}</td>
      <td>${database.nameCount}</td>
      <td>${database.valueCount}</td>
      <td><a href="/api/ManageDatabases?deleteId=${database.id}" onclick="return confirm('Are you sure?')">Delete</a></td>
    </tr>
  </c:forEach>
</table>
Uploads
<table>
  <tr>
    <td>Date</td>
    <td>Business Name</td>
    <td>Database Name</td>
    <td>User Name</td>
    <td>File Name</td>
    <td>File Type</td>
    <td>Comments</td>
  </tr>
          public final Date date;
        public final String businessName;
        public final String databaseName;
        public final String userName;
        public final String fileName;
        public final String fileType;
        public final String comments;

  <c:forEach items="${uploads}" var="upload">
    <tr>
      <td>${upload.date}</td>
      <td>${upload.businessName}</td>
      <td>${upload.databaseName}</td>
      <td>${upload.userName}</td>
      <td>${upload.fileName}</td>
      <td>${upload.fileType}</td>
      <td>${upload.comments}</td>
    </tr>
  </c:forEach>
</table>

</body>
</html>
