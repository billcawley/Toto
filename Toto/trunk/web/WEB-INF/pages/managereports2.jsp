<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Manage Reports - Azquo</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
</head>
<body>


<nav class="navbar is-black" role="navigation" aria-label="main navigation">
    <div class="navbar-brand">
        <a class="navbar-item" href="https://azquo.com">
            <img src="${logo}" alt="azquo">
        </a>
    </div>
    <div id="navbarBasicExample" class="navbar-menu">
        <div class="navbar-start">
            <a class="navbar-item${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReports') ? ' is-tab is-active' : ''}"
               href="/api/ManageReports">
                Reports</a>
            <a class="navbar-item${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageDatabases') ? ' is-tab is-active' : ''}"
               href="/api/ManageDatabases">
                Databases
            </a>
            <c:if test="${!developer}">
                <a class="navbar-item${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageUsers') ? ' is-tab is-active' : ''}"
                   href="/api/ManageUsers">
                    Users
                </a>
            </c:if>
            <c:if test="${!developer}">
                <a class="navbar-item${fn:startsWith(requestScope['javax.servlet.forward.path_info'], '/ManageReportSchedules') ? ' is-tab is-active' : ''}"
                   href="/api/ManageReportSchedules">Schedules
                </a>
            </c:if>
        </div>

        <div class="navbar-end">
            <c:if test="${sessionScope.LOGGED_IN_USERS_SESSION != null}">
                <a  class="navbar-item" href="/api/Login?select=true">Logged in under ${sessionScope.LOGGED_IN_USER_SESSION.user.businessName}. Switch business.</a>
            </c:if>
            <a class="navbar-item" href="/api/Login?logoff=true">Log Off</a>
        </div>
    </div>
</nav>


<div class="box">
    <h1 class="title">Manage Reports</h1>
    <table class="table is-striped is-fullwidth">
        <thead>
        <tr>
            <th>Database</th>
            <th>Author</th>
            <th>&nbsp;&nbsp;&nbsp;&nbsp;</th>
            <th>Report Name</th>
            <th>Explanation</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
        <c:forEach items="${reports}" var="report">
        <c:if test="${report.category != ''}">

        <tr>
            <td></td>
            <td></td>
            <td colspan="2"><b>${report.category}</b></td>
            <td></td>
            <td></td>
        </tr>
        </c:if>
        <tr>
            <td>${report.database}</td>
            <td>${report.author}</td>
            <td></td>
            <td><c:if test="${report.database != 'None'}"><a href="/api/Online?reportid=${report.id}&amp;database=${report.database}" target="_blank"></c:if>
                <span class="fa fa-table"></span>  ${report.untaggedReportName}<c:if test="${report.database != 'None'}"></a></c:if></td>
            <td>${report.explanation}</td>
            <td>
                <a href="/api/ManageReports?editId=${report.id}"  title="Edit ${report.reportName}" class="button is-small"><span class="fa fa-edit" title="Edit"></span></a>
                <a href="/api/ManageReports?deleteId=${report.id}" onclick="return confirm('Are you sure you want to delete ${report.reportName}?')" title="Delete ${report.reportName}" class="button is-small"><span class="fa fa-trash" title="Delete"></span> </a>
                <a href="/api/DownloadTemplate?reportId=${report.id}" title="Download" class="button is-small"><span class="fa fa-download" title="Download"></span> </a>
            </td>
        </tr>
        </c:forEach>
        </tbody>
    </table>
</div>



</body>
</html>