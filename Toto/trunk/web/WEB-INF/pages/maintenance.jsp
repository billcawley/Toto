<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Backup/Maintenance"/>
<%@ include file="../includes/new_header.jsp" %>
<div class="az-content">
    <main>
        <div class="az-users-view">
            <div id="working" style="display:none"><h3>Working...</h3></div>
            <div class="az-section-heading">
                <h3>Backup/Maintenance</h3>
            </div>
            <div class="az-section-body">
                <div class="az-table">
                    <div>
                        WARNING : the database specified internally by the zip or "Database" here will zap a
                        database
                        and
                        associated
                        reports and auto backups if they exist before it restores the file contents.
                        <form onsubmit="document.getElementById('working').style.display = 'block';"
                              action="/api/ManageDatabases" method="post"
                              enctype="multipart/form-data">
                            <input type="hidden" name="backup" value="true"/>
                            <input type="hidden" name="newdesign" value="maintenance"/>
                            <nav>
                                        Select Backup File&nbsp;&nbsp;<input class="file-input is-small" type="file"
                                                                 name="uploadFile">
                                        Database &nbsp;
                                        <input type="text"
                                               size="40"
                                               name="database">
                                <button type="submit">Upload</button>
                            </nav>
                        </form>
                    </div>
                </div>
                <br/><br/>
                <div class="az-table">
                    Download Custom Backup. For advanced users - specify a subset of a database to download.
                    <nav>
                        <form onsubmit="document.getElementById('working').style.display = 'block';" action="/api/DownloadBackup" method="get">
                            <input type="hidden" name="newdesign" value="maintenance"/>
                                        <select name="id">
                                            <c:forEach items="${databases}" var="database">
                                                <option value="${database.id}" <c:if
                                                        test="${database.name == lastSelected}"> selected </c:if>>${database.name}</option>
                                            </c:forEach>
                                        </select>
                                        &nbsp;Name&nbsp;Subset&nbsp;&nbsp;
                                        <input class="input is-small" type="text"
                                               size="40"
                                               name="namesubset">
                                    <button type="submit"> Download</button>
                        </form>
                    </nav>
                </div>
                <br/><br/>
                <div class="az-table">
                    <nav>
                        <div>Memory/CPU report for servers&nbsp;&nbsp;&nbsp;</div>
                        <div>
                            <c:forEach items="${databaseServers}" var="databaseServer">
                                <button onclick="window.open('/api/MemoryReport?serverIp=${databaseServer.ip}')"
                                        type="button">${databaseServer.name}</button>
                            </c:forEach>
                        </div>
                        <div>
                            <button onclick="window.open('/api/UserLog')"
                                    class="button" target="new">User Log</button>
                        </div>
                    </nav>
                </div>
            </div>
        </div>
    </main>
</div>
<%@ include file="../includes/new_footer.jsp" %>
