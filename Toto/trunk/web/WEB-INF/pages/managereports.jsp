<%--


--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<html>
<head>
    <title>Manage Reports</title>
</head>
<body>
<table>
     <c:forEach items="${reports}" var="report">
        <tr>
            <td>${report.id}</td>
            <td>${report.businessId}</td>
            <td>${report.databaseId}</td>
            <td>${report.reportName}</td>
            <td>${report.userStatus}</td>
            <td>${report.filename}</td>
            <td>${report.explanation}</td>
        </tr>
    </c:forEach>
    </table>
</body>
</html>
