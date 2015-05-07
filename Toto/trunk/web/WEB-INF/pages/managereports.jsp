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
<h1>Manage Reports</h1><br/>
<form action="/api/ManageReports" method="post">
<table>
    <tr>
        <!--            <td>Report id</td>
       <td>Business Id</td>-->
        <td>Database</td>
        <td>Report Name</td>
        <td>Report Category</td>
        <td>User Groups</td>
        <!-- <td>File Name</td> -->
        <td>Explanation</td>
    </tr>
     <c:forEach items="${reports}" var="report">
         <tr>
             <!--            <td>${report.id}</td>
            <td>${report.businessId}</td>-->
             <td>${report.database}</td>
             <!-- should reportid be 1??? -->
             <td><a href="/api/Online?reportid=1&amp;opcode=loadsheet&amp;reporttoload=${report.id}"> ${report.reportName}</a></td>
             <td><input name="reportCategory${report.id}" value="${report.reportCategory}" size="50"/></td>
             <td><input name="userStatus${report.id}" value="${report.userStatus}" size="50"/></td>
             <!-- <td>${report.filename}</td> -->
             <td><textarea name="explanation${report.id}" cols="50" rows="3">${report.explanation}</textarea></td>
         </tr>
    </c:forEach>
    </table>
    <input type="submit" value="Save Changes"/>
</form>
</body>
</html>
