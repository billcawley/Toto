<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Manage Database Backups"/>
<%@ include file="../includes/new_header.jsp" %>
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

<div class="az-content">
    <main>
        <div class="az-users-view">
            <div class="az-section-heading">
                <h3>Manage Report Schedules</h3>
            </div>
            <div class="az-section-body">
                    <h1>Restore a backup for ${database}</h1>
                    Note : this will roll the database back or forward to the selected version. It will not affect
                    reports associated with the database.
                    <div class="has-text-danger">${error}</div>
                    <div id="working" style="display:none"><h3>Working...</h3>
                    </div>
                    <table class="az-table">
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
                                <td>
                                    <a href="/api/ManageDatabaseBackups?restoreBackup=${backup.name}&databaseId=${databaseId}"
                                       onclick="return clickRestore()"
                                       title="Restore ${backup.name}">Restore</a>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>

            </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
