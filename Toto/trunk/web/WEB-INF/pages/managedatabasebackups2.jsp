<%--

Copyright (C) 2016 Azquo Ltd.
  Created by IntelliJ IDEA.
  User: edward
  Date: 22/07/16
  Time: 12:37
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Database Backups" />
<%@ include file="../includes/admin_header2.jsp" %>
<script>
    function showHideDiv(div) {
        var x = document.getElementById(div);
        if (x.style.display === "none") {
            x.style.display = "block";
        } else {
            x.style.display = "none";
        }
    }

    function clickRestore(){
        if (confirm('Are you sure you want to Restore ${backup.name}?')){
            showHideDiv('working');
            return true;
        } else {
            return false;
        }
    }
</script>

<div class="box">
    <h1>Restore a backup for ${database}</h1>
    Note : this will roll the database back or forward to the selected version. It will not affect reports associated with the database.
<div class="is-danger">${error}</div>
    <div id="working" class="loading" style="display:none"><h3>Working...</h3>
        <div class="loader"><span class="fa fa-spin fa-cog"></span></div>
    </div>
    <table class="table is-striped is-fullwidth">
    <thead>
    <tr>
        <th>Backup Name</th>
        <th>Date</th>
        <th></th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${backups}" var="backup">
        <tr>
            <td>${backup.name}</td>
            <td>${backup.date}</td>
            <td><a href="/api/ManageDatabaseBackups?restoreBackup=${backup.name}&databaseId=${databaseId}" onclick="return clickRestore()" class="button is-small alt" title="Restore ${backup.name}"><span class="fa fa-eject" title="Restore"></span></a></td>
        </tr>
    </c:forEach>
    </tbody>
</table>
</div>
<%@ include file="../includes/admin_footer.jsp" %>
