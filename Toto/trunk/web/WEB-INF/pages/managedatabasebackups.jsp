<%--

Copyright (C) 2016 Azquo Ltd. Public source releases are under the AGPLv3, see LICENSE.TXT
  Created by IntelliJ IDEA.
  User: edward
  Date: 22/07/16
  Time: 12:37
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Database Backups" />
<%@ include file="../includes/admin_header.jsp" %>


<main class="databases">
    <h1>Restore a backup for ${database}</h1>
    Note : this will roll the database back or forward to the selected version. It will not affect reports associated with the database.
<div class="error">${error}</div>
<table>
    <thead>
    <tr>
        <td>Backup Name</td>
        <td>Date</td>
        <td></td>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${backups}" var="backup">
        <tr>
            <td>${backup.name}</td>
            <td>${backup.date}</td>
            <td><a href="/api/ManageDatabaseBackups?restoreBackup=${backup.name}&databaseId=${databaseId}" onclick="return confirm('Are you sure you want to Restore ${backup.name}?')" class="button small alt" title="Restore ${backup.name}"><span class="fa fa-eject" title="Restore"></span></a></td>
        </tr>
    </c:forEach>
    </tbody>
</table>
</main>
<%@ include file="../includes/admin_footer.jsp" %>