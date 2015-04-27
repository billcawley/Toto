<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
    <title>Manage Reports</title>
</head>
<body>
<form action="/api/ManageReports" method="post">
<table>
    <tr>
        <!--            <td>Report id</td>
       <td>Business Id</td>-->
        <td>Database ID</td>
        <td>Report Name</td>
        <td>User Groups</td>
        <!-- <td>File Name</td> -->
        <td>Explanation</td>
    </tr>
     <c:forEach items="${reports}" var="report">
         <tr>
             <!--            <td>${report.id}</td>
            <td>${report.businessId}</td>-->
             <td>${report.databaseId}</td>
             <td><a href="/api/Online?reportid=1&amp;opcode=loadsheet&amp;reporttoload=${report.id}"> ${report.reportName}</a></td>
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
