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
<a href="/api/ManageReports">Manage Reports</a> &nbsp;<a href="/api/ManageDatabases">Manage Databases</a> &nbsp;<a href="/api/ManageUsers">Manage Users</a> &nbsp;<a href="/api/ManagePermissions">Manage Permissions</a> &nbsp;<br/>
<h1>Manage Databases</h1><br/>
<br/>${error}
<table>
  <tr>
  <form action="/api/ManageDatabases" method="post"><td>New Database</td><td><input name="createDatabase"/></td><td><input name="databaseType"/></td><td><input type="submit" name="Create Database" value="Create Database"/></td></form>
  </tr>
  <form action="/api/ManageDatabases" method="post">
    <tr>
    <td>Target Database</td><td><input name="backupTarget"/></td><td></td><td><input type="submit" name="Backup Database" value="Backup Database"/></td>
      </tr>
    <tr>
    <td>Summary Level</td><td><input name="summaryLevel"/></td><td></td><td></td>
    </tr>
  </form>
</table>
<br/><br/>
<table>
  <tr>
    <!--      <td>${database.id}</td> -->
    <td>Start Date</td>
    <td>End Date</td>
<!--    <td>${database.businessId}</td> -->
    <td>Name</td>
    <td>MySQLName</td>
      <td>Database type</td>
    <td>Name Count</td>
    <td>Value Count</td>
      <td></td>
      <td></td>
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
        <td>${database.databaseType}</td>
        <td>${database.nameCount}</td>
      <td>${database.valueCount}</td>
        <td><a href="#" onclick="window.open('/api/Jstree?op=new&database=${database.urlEncodedName}', '_blank', 'toolbar=no, status=no,scrollbars=yes, resizable=yes, top=150, left=200, width=600, height=600')">Inspect</a></td>
        <td><a href="/api/ManageDatabases?emptyId=${database.id}" onclick="return confirm('Are you sure?')">Empty</a></td>
        <td><a href="/api/ManageDatabases?deleteId=${database.id}" onclick="return confirm('Are you sure?')">Delete</a></td>
        <td>
            <c:if test="${database.loaded}">
                <a href="/api/ManageDatabases?unloadId=${database.id}">Unload</a>
                    </c:if>

        </td>
    </tr>
  </c:forEach>
</table>
<br/><br/>
Uploads
<br/><br/>
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
<table>
    <tr>
        <form action="/api/ManageDatabases" method="post" enctype="multipart/form-data"><td>Upload File</td><td><input type="file" name="uploadFile"></td>
            <td>
                <select name="database">
                    <option value="">None</option>
            <c:forEach items="${databases}" var="database">
                <option value="${database.name}">${database.name}</option>
            </c:forEach>
            </select>
            </td>
            <td>Use database type ? <input type="checkbox" name="useType"/></td>
            <td><input type="submit" name="Upload" value="Upload"/></td></form>
    </tr>
</table>
</body>
</html>
